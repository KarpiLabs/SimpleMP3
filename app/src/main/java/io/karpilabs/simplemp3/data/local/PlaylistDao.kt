package io.karpilabs.simplemp3.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY isSystem DESC, updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY isSystem DESC, updatedAt DESC")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getPlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistOnce(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE systemType = :type LIMIT 1")
    suspend fun getSystemPlaylist(type: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id AND isSystem = 0")
    suspend fun deletePlaylistById(id: Long)

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """
    )
    fun getTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """
    )
    suspend fun getTracksForPlaylistOnce(playlistId: Long): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun getTrackCount(playlistId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getTrackCountOnce(playlistId: Long): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM playlist_tracks
            WHERE playlistId = :playlistId AND trackId = :trackId
        )
        """
    )
    suspend fun containsTrack(playlistId: Long, trackId: Long): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM playlist_tracks
            WHERE playlistId = :playlistId AND trackId = :trackId
        )
        """
    )
    fun observeContainsTrack(playlistId: Long, trackId: Long): Flow<Boolean>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(crossRef: PlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(crossRefs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query(
        """
        SELECT pt.* FROM playlist_tracks pt
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """
    )
    suspend fun getCrossRefs(playlistId: Long): List<PlaylistTrackCrossRef>

    @Transaction
    suspend fun addTrackToEnd(playlistId: Long, trackId: Long) {
        if (containsTrack(playlistId, trackId)) return
        val next = getMaxPosition(playlistId) + 1
        insertPlaylistTrack(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                position = next
            )
        )
        touchPlaylist(playlistId)
    }

    @Transaction
    suspend fun moveTrack(playlistId: Long, trackId: Long, toPosition: Int) {
        val refs = getCrossRefs(playlistId).toMutableList()
        val fromIndex = refs.indexOfFirst { it.trackId == trackId }
        if (fromIndex < 0) return
        val item = refs.removeAt(fromIndex)
        val target = toPosition.coerceIn(0, refs.size)
        refs.add(target, item)
        refs.forEachIndexed { index, ref ->
            insertPlaylistTrack(ref.copy(position = index))
        }
        touchPlaylist(playlistId)
    }

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: Long, now: Long = System.currentTimeMillis())

    @Query(
        """
        SELECT p.*,
               (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount,
               (SELECT t.artworkUri FROM playlist_tracks pt
                INNER JOIN tracks t ON t.id = pt.trackId
                WHERE pt.playlistId = p.id
                ORDER BY pt.position ASC LIMIT 1) AS firstArtworkUri
        FROM playlists p
        ORDER BY p.isSystem DESC, p.updatedAt DESC
        """
    )
    fun getPlaylistsWithMeta(): Flow<List<PlaylistWithMeta>>

    @Query(
        """
        SELECT p.*,
               (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount,
               (SELECT t.artworkUri FROM playlist_tracks pt
                INNER JOIN tracks t ON t.id = pt.trackId
                WHERE pt.playlistId = p.id
                ORDER BY pt.position ASC LIMIT 1) AS firstArtworkUri
        FROM playlists p
        ORDER BY p.isSystem DESC, p.updatedAt DESC
        """
    )
    suspend fun getPlaylistsWithMetaOnce(): List<PlaylistWithMeta>
}

data class PlaylistWithMeta(
    val id: Long,
    val name: String,
    val description: String,
    val coverUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isSystem: Boolean,
    val systemType: String?,
    val trackCount: Int,
    val firstArtworkUri: String?
) {
    val displayCover: String?
        get() = coverUri ?: firstArtworkUri
}
