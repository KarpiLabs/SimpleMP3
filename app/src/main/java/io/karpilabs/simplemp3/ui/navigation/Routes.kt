package io.karpilabs.simplemp3.ui.navigation

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val JELLYFIN = "jellyfin"
    const val YOUTUBE = "youtube"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val ALBUM_DETAIL = "album/{albumName}"
    const val ARTIST_DETAIL = "artist/{artistName}"

    fun playlistDetail(id: Long) = "playlist/$id"
    fun albumDetail(name: String) = "album/${android.net.Uri.encode(name)}"
    fun artistDetail(name: String) = "artist/${android.net.Uri.encode(name)}"
}
