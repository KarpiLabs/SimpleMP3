package io.karpilabs.simplemp3.service

import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import io.karpilabs.simplemp3.data.local.AlbumRow
import io.karpilabs.simplemp3.data.local.PlaylistWithMeta
import io.karpilabs.simplemp3.data.local.TrackEntity

object MediaIds {
    const val ROOT = "root"
    const val PLAYLISTS = "playlists"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val SONGS = "songs"
    const val RECENT = "recent"
    const val OFFLINE = "offline"

    /** Resume last session / continue listening */
    const val CONTINUE = "continue"

    /** Liked Songs system playlist */
    const val LIKED = "liked"

    /** YouTube → MP3 downloads */
    const val YOUTUBE = "youtube"

    /** Current player queue (Up Next) */
    const val QUEUE = "queue"

    const val PLAYLIST_PREFIX = "playlist:"
    const val ALBUM_PREFIX = "album:"
    const val ARTIST_PREFIX = "artist:"
    const val TRACK_PREFIX = "track:"

    /** Prefix for “shuffle then play this collection” actions. */
    const val SHUFFLE_PREFIX = "shuffle:"

    /** Ad-hoc live network stream not backed by a library track. */
    const val STREAM_PREFIX = "stream:"

    fun stream(url: String) = "$STREAM_PREFIX$url"

    fun playlist(id: Long) = "$PLAYLIST_PREFIX$id"

    fun album(name: String) = "$ALBUM_PREFIX${Uri.encode(name)}"

    fun artist(name: String) = "$ARTIST_PREFIX${Uri.encode(name)}"

    fun track(id: Long) = "$TRACK_PREFIX$id"

    fun shuffleOf(targetMediaId: String) = "$SHUFFLE_PREFIX$targetMediaId"

    fun parseAlbum(mediaId: String): String? =
        mediaId.removePrefix(ALBUM_PREFIX).takeIf { mediaId.startsWith(ALBUM_PREFIX) }?.let {
            Uri.decode(it)
        }

    fun parseArtist(mediaId: String): String? =
        mediaId.removePrefix(ARTIST_PREFIX).takeIf { mediaId.startsWith(ARTIST_PREFIX) }?.let {
            Uri.decode(it)
        }

    fun parsePlaylistId(mediaId: String): Long? =
        mediaId
            .removePrefix(PLAYLIST_PREFIX)
            .toLongOrNull()
            .takeIf { mediaId.startsWith(PLAYLIST_PREFIX) }

    fun parseTrackId(mediaId: String): Long? =
        mediaId
            .removePrefix(TRACK_PREFIX)
            .toLongOrNull()
            .takeIf { mediaId.startsWith(TRACK_PREFIX) }

    fun isShuffle(mediaId: String): Boolean = mediaId.startsWith(SHUFFLE_PREFIX)

    fun unwrapShuffle(mediaId: String): String? = mediaId.removePrefix(SHUFFLE_PREFIX).takeIf { isShuffle(mediaId) && it.isNotBlank() }
}

object MediaItemFactory {
    /** Android Auto content-style hints: grid vs. list layout for a browsable node's children. */
    const val EXTRA_CONTENT_STYLE_BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
    const val EXTRA_CONTENT_STYLE_PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
    const val CONTENT_STYLE_LIST = 1
    const val CONTENT_STYLE_GRID = 2

    fun root(): MediaItem =
        browsable(
            mediaId = MediaIds.ROOT,
            title = "Simple MP3",
            isPlayable = false,
            // Root categories (Albums, Artists, Playlists…) read better as a grid with art.
            browsableHint = CONTENT_STYLE_GRID,
            playableHint = CONTENT_STYLE_LIST,
        )

    fun category(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        isPlayable: Boolean = false,
        artworkUri: String? = null,
        browsableHint: Int = CONTENT_STYLE_GRID,
        playableHint: Int = CONTENT_STYLE_LIST,
    ): MediaItem =
        browsable(
            mediaId = mediaId,
            title = title,
            subtitle = subtitle,
            isPlayable = isPlayable,
            artworkUri = artworkUri,
            browsableHint = browsableHint,
            playableHint = playableHint,
        )

    /**
     * Non-browsable play action shown at the top of a folder
     * (Android Auto “Shuffle play”).
     */
    @OptIn(UnstableApi::class)
    fun shufflePlayAction(
        targetMediaId: String,
        subtitle: String? = "Play in random order",
    ): MediaItem {
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle("Shuffle play")
                .setSubtitle(subtitle)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        return MediaItem
            .Builder()
            .setMediaId(MediaIds.shuffleOf(targetMediaId))
            .setMediaMetadata(metadata)
            .build()
    }

    fun fromPlaylist(playlist: PlaylistWithMeta): MediaItem =
        browsable(
            mediaId = MediaIds.playlist(playlist.id),
            title = playlist.name,
            subtitle =
                buildString {
                    append(playlist.trackCount)
                    append(if (playlist.trackCount == 1) " song" else " songs")
                    if (playlist.description.isNotBlank()) {
                        append(" · ")
                        append(playlist.description)
                    }
                },
            artworkUri = playlist.displayCover,
            isPlayable = playlist.trackCount > 0,
            folderType = MediaMetadata.FOLDER_TYPE_PLAYLISTS,
            // Contains tracks — render as a list, not a grid.
            browsableHint = CONTENT_STYLE_LIST,
            playableHint = CONTENT_STYLE_LIST,
        )

    fun fromAlbum(album: AlbumRow): MediaItem =
        browsable(
            mediaId = MediaIds.album(album.name),
            title = album.name,
            subtitle = "${album.subtitle} · ${album.trackCount} songs",
            artworkUri = album.artworkUri,
            isPlayable = true,
            folderType = MediaMetadata.FOLDER_TYPE_ALBUMS,
            browsableHint = CONTENT_STYLE_LIST,
            playableHint = CONTENT_STYLE_LIST,
        )

    fun fromArtist(artist: AlbumRow): MediaItem =
        browsable(
            mediaId = MediaIds.artist(artist.name),
            title = artist.name,
            subtitle = "${artist.trackCount} songs",
            artworkUri = artist.artworkUri,
            isPlayable = true,
            folderType = MediaMetadata.FOLDER_TYPE_ARTISTS,
            browsableHint = CONTENT_STYLE_LIST,
            playableHint = CONTENT_STYLE_LIST,
        )

    @OptIn(UnstableApi::class)
    fun fromTrack(
        track: TrackEntity,
        playable: Boolean = true,
    ): MediaItem {
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setIsPlayable(playable)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .apply {
                    if (track.duration > 0) setDurationMs(track.duration)
                    track.artworkUri?.let { setArtworkUri(it.toUri()) }
                    if (track.trackNumber > 0) setTrackNumber(track.trackNumber)
                    if (track.year > 0) setReleaseYear(track.year)
                }.build()

        val builder =
            MediaItem
                .Builder()
                .setMediaId(MediaIds.track(track.id))
                .setUri(track.uri)
                .setMediaMetadata(metadata)
        if (track.isRemoteStream && isHlsUrl(track.uri)) {
            builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }
        return builder.build()
    }

    fun fromTracks(tracks: List<TrackEntity>): List<MediaItem> = tracks.map { fromTrack(it) }

    /**
     * A live network stream (progressive URL or HLS `.m3u8`) played directly, not
     * backed by a library track. The [MimeTypes.APPLICATION_M3U8] hint lets the HLS
     * source factory take `.m3u8` URLs that don't carry a helpful extension/header.
     */
    @OptIn(UnstableApi::class)
    fun fromStream(
        url: String,
        title: String,
    ): MediaItem {
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle(title.ifBlank { "Stream" })
                .setArtist("Live stream")
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        val builder =
            MediaItem
                .Builder()
                .setMediaId(MediaIds.stream(url))
                .setUri(url)
                .setMediaMetadata(metadata)
        if (isHlsUrl(url)) {
            builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }
        return builder.build()
    }

    private fun isHlsUrl(url: String): Boolean =
        url.substringBefore('#').substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    @OptIn(UnstableApi::class)
    private fun browsable(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        artworkUri: String? = null,
        isPlayable: Boolean = false,
        folderType: @MediaMetadata.FolderType Int = MediaMetadata.FOLDER_TYPE_MIXED,
        browsableHint: Int = CONTENT_STYLE_GRID,
        playableHint: Int = CONTENT_STYLE_LIST,
    ): MediaItem {
        // Tells Android Auto how to lay out THIS node's children once entered.
        val extras =
            android.os.Bundle().apply {
                putInt(EXTRA_CONTENT_STYLE_BROWSABLE, browsableHint)
                putInt(EXTRA_CONTENT_STYLE_PLAYABLE, playableHint)
            }
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsBrowsable(true)
                .setIsPlayable(isPlayable)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .setFolderType(folderType)
                .setExtras(extras)
                .apply {
                    artworkUri?.let { setArtworkUri(it.toUri()) }
                }.build()

        return MediaItem
            .Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }
}
