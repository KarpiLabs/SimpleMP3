package io.karpilabs.simplemp3.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["systemType"], unique = true)],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSystem: Boolean = false,
    /** favorites | recently_played — unique when set */
    val systemType: String? = null,
) {
    companion object {
        const val SYSTEM_FAVORITES = "favorites"
        const val SYSTEM_RECENTLY_PLAYED = "recently_played"
        const val SYSTEM_JELLYFIN = "jellyfin_offline"
        const val SYSTEM_YOUTUBE = "youtube_downloads"
        const val SYSTEM_LAN = "lan_imports"
    }
}
