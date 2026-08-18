package io.karpilabs.simplemp3.ui.navigation

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"
    const val JELLYFIN = "jellyfin"
    const val YOUTUBE = "youtube"
    const val QUICK_CONNECT = "quick_connect"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val ALBUM_DETAIL = "album/{albumName}"
    const val ARTIST_DETAIL = "artist/{artistName}"
    const val FOLDER_DETAIL = "folder/{folderPath}"
    const val LIBRARY_FOLDERS = "library_folders"
    const val HIDDEN_SONGS = "hidden_songs"

    fun playlistDetail(id: Long) = "playlist/$id"

    fun albumDetail(name: String) = "album/${android.net.Uri.encode(name)}"

    fun artistDetail(name: String) = "artist/${android.net.Uri.encode(name)}"

    fun folderDetail(path: String) = "folder/${android.net.Uri.encode(path)}"
}
