package io.karpilabs.simplemp3.data.scanner

import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import io.karpilabs.simplemp3.data.local.FolderBrowser
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.local.externalItemIdToTrackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Walks persistable SAF trees (SD card, USB, user-picked folders) and builds
 * [TrackEntity] rows MediaStore never indexed.
 */
@Singleton
class SafFolderScanner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun scanTrees(treeUris: List<String>): List<TrackEntity> =
            withContext(Dispatchers.IO) {
                if (treeUris.isEmpty()) return@withContext emptyList()
                val out = ArrayList<TrackEntity>()
                for (raw in treeUris) {
                    val tree = Uri.parse(raw)
                    val treeId =
                        runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
                            ?: continue
                    val rootDoc = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
                    val rootName = queryDisplayName(rootDoc) ?: "Folder"
                    walk(tree, rootDoc, tree.toString(), rootName, out)
                }
                out
            }

        fun treeDisplayName(treeUri: String): String {
            val tree = Uri.parse(treeUri)
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            if (treeId != null) {
                val rootDoc = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
                queryDisplayName(rootDoc)?.let { return it }
                return treeId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Folder" }
            }
            return "Folder"
        }

        private fun walk(
            tree: Uri,
            dir: Uri,
            treeUri: String,
            folderPath: String,
            out: MutableList<TrackEntity>,
        ) {
            val children =
                runCatching {
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                        tree,
                        DocumentsContract.getDocumentId(dir),
                    )
                }.getOrNull() ?: return
            queryChildren(children).use { cursor ->
                if (cursor == null) return
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idCol < 0 || mimeCol < 0) return
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol) ?: continue
                    val mime = cursor.getString(mimeCol).orEmpty()
                    val name = if (nameCol >= 0) cursor.getString(nameCol) else null
                    val child = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val childName = name?.takeIf { it.isNotBlank() && !it.startsWith('.') } ?: continue
                        walk(tree, child, treeUri, FolderBrowser.normalize("$folderPath/$childName"), out)
                    } else if (isAudio(mime, name)) {
                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                        val modified = if (modifiedCol >= 0) cursor.getLong(modifiedCol) else 0L
                        readTrack(child, treeUri, folderPath, name, size, modified)?.let { out += it }
                    }
                }
            }
        }

        private fun queryChildren(uri: Uri): Cursor? =
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    ),
                    null,
                    null,
                    null,
                )
            }.getOrNull()

        private fun queryDisplayName(uri: Uri): String? {
            return runCatching {
                context.contentResolver
                    .query(
                        uri,
                        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
            }.getOrNull()?.takeIf { !it.isNullOrBlank() }
        }

        private fun isAudio(
            mime: String,
            name: String?,
        ): Boolean {
            if (mime.lowercase().startsWith("audio/")) return true
            val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
            return ext in AUDIO_EXTENSIONS
        }

        private fun readTrack(
            uri: Uri,
            treeUri: String,
            folderPath: String,
            fallbackName: String?,
            size: Long,
            modified: Long,
        ): TrackEntity? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                val duration =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?: 0L
                if (duration in 1 until MIN_DURATION_MS) return null
                val fallback = fallbackName?.substringBeforeLast('.')?.ifBlank { null } ?: "Unknown Title"
                val title =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: fallback
                val artist =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
                        ?: retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                        ?: "Unknown Artist"
                val album =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Unknown Album"
                val year =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                        ?.take(4)
                        ?.toIntOrNull()
                        ?: 0
                val trackNumber =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                        ?.substringBefore('/')
                        ?.toIntOrNull()
                        ?: 0
                val genre =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                val id = externalItemIdToTrackId("saf:$uri")
                TrackEntity(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    uri = uri.toString(),
                    duration = duration,
                    dateAdded = modified.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    year = year,
                    trackNumber = trackNumber,
                    genre = genre,
                    folderPath = FolderBrowser.normalize(folderPath),
                    size = size.coerceAtLeast(0L),
                    source = TrackEntity.SOURCE_SAF,
                    jellyfinId = treeUri,
                    isOffline = true,
                )
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }

        companion object {
            private const val MIN_DURATION_MS = 10_000L
            val AUDIO_EXTENSIONS =
                setOf(
                    "mp3",
                    "m4a",
                    "aac",
                    "flac",
                    "ogg",
                    "oga",
                    "opus",
                    "wav",
                    "wma",
                    "aiff",
                    "aif",
                    "alac",
                )
        }
    }
