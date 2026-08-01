package io.karpilabs.simplemp3.data.scanner

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.karpilabs.simplemp3.data.local.FolderBrowser
import io.karpilabs.simplemp3.data.local.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * @param allowedRoots empty = include every music folder; otherwise only tracks whose
     * [TrackEntity.folderPath] is equal to or under one of the roots.
     */
    suspend fun scan(allowedRoots: Set<String> = emptySet()): List<TrackEntity> =
        withContext(Dispatchers.IO) {
            val tracks = mutableListOf<TrackEntity>()
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

            val projection = mutableListOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.IS_MUSIC
            )
            // RELATIVE_PATH is the scoped-storage folder (e.g. "Music/Rock/").
            projection += MediaStore.Audio.Media.RELATIVE_PATH
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                projection += MediaStore.Audio.Media.DATA
            }

            val selection =
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
            val selectionArgs = arrayOf("10000") // skip clips under 10s
            val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

            context.contentResolver.query(
                collection,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val relativeCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                @Suppress("DEPRECATION")
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: "Unknown Title"
                    val artist = cursor.getString(artistCol)?.takeIf {
                        it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true)
                    } ?: "Unknown Artist"
                    val album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() }
                        ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdCol)
                    val artistId = cursor.getLong(artistIdCol)
                    val duration = cursor.getLong(durationCol)
                    val dateAdded = cursor.getLong(dateAddedCol) * 1000L
                    val year = cursor.getInt(yearCol)
                    val trackNumber = cursor.getInt(trackCol) % 1000
                    val size = cursor.getLong(sizeCol)

                    val relative = if (relativeCol >= 0) cursor.getString(relativeCol) else null
                    val absolute = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val folderPath = resolveFolderPath(relative, absolute)

                    if (!FolderBrowser.matchesAnyRoot(folderPath, allowedRoots)) {
                        continue
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    // Per-track content URI resolves via ContentResolver.loadThumbnail (API 29+).
                    val artworkUri = contentUri

                    tracks += TrackEntity(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        artistId = artistId,
                        uri = contentUri,
                        duration = duration,
                        artworkUri = artworkUri,
                        dateAdded = dateAdded,
                        year = year,
                        trackNumber = trackNumber,
                        folderPath = folderPath,
                        size = size
                    )
                }
            }
            tracks
        }

    /**
     * All distinct folder paths on the device (ignores library root filter).
     * Used by Settings so the user can pick roots even when the library is filtered.
     */
    suspend fun listAllFolderPaths(): List<String> = withContext(Dispatchers.IO) {
        val folders = linkedSetOf<String>()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = mutableListOf(
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.DURATION
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            projection += MediaStore.Audio.Media.DATA
        }
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("10000")

        context.contentResolver.query(
            collection,
            projection.toTypedArray(),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val relativeCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            @Suppress("DEPRECATION")
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val relative = if (relativeCol >= 0) cursor.getString(relativeCol) else null
                val absolute = if (dataCol >= 0) cursor.getString(dataCol) else null
                val path = resolveFolderPath(relative, absolute)
                if (path.isNotEmpty()) folders += path
            }
        }
        folders.sortedBy { it.lowercase() }
    }

    private fun resolveFolderPath(relativePath: String?, absolutePath: String?): String {
        val fromRelative = FolderBrowser.normalize(relativePath)
        if (fromRelative.isNotEmpty()) return fromRelative

        val abs = absolutePath?.trim().orEmpty()
        if (abs.isEmpty()) return ""
        // Absolute path → parent directory, then try to strip common storage roots
        // so browsing stays relative (Music/… rather than /storage/emulated/0/Music/…).
        val parent = abs.substringBeforeLast('/', missingDelimiterValue = "")
            .ifBlank { return "" }
        val stripped = parent
            .removePrefix("/storage/emulated/0/")
            .removePrefix("/sdcard/")
            .removePrefix("/mnt/sdcard/")
        return FolderBrowser.normalize(stripped)
    }
}
