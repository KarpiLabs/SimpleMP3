package io.karpilabs.simplemp3.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE source = :source ORDER BY title COLLATE NOCASE ASC")
    fun getTracksBySource(source: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE source = :source ORDER BY title COLLATE NOCASE ASC")
    suspend fun getTracksBySourceOnce(source: String): List<TrackEntity>

    @Query("SELECT id FROM tracks WHERE source = :source")
    suspend fun getTrackIdsBySource(source: String): List<Long>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE jellyfinId = :jellyfinId LIMIT 1")
    suspend fun getByJellyfinId(jellyfinId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getTracksByIds(ids: List<Long>): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    fun searchTracks(query: String): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT * FROM tracks
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    suspend fun searchTracksOnce(query: String): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC LIMIT :limit")
    fun getRecentlyAdded(limit: Int = 30): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY trackNumber ASC, title ASC")
    fun getTracksByAlbum(album: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY trackNumber ASC, title ASC")
    suspend fun getTracksByAlbumOnce(album: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE artist = :artist ORDER BY album ASC, trackNumber ASC")
    fun getTracksByArtist(artist: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artist = :artist ORDER BY album ASC, trackNumber ASC")
    suspend fun getTracksByArtistOnce(artist: String): List<TrackEntity>

    @Query(
        """
        SELECT album AS name,
               artist AS subtitle,
               COUNT(*) AS trackCount,
               SUM(duration) AS totalDuration,
               MIN(artworkUri) AS artworkUri,
               MIN(albumId) AS albumId
        FROM tracks
        GROUP BY album, artist
        ORDER BY album COLLATE NOCASE ASC
        """
    )
    fun getAlbums(): Flow<List<AlbumRow>>

    @Query(
        """
        SELECT album AS name,
               artist AS subtitle,
               COUNT(*) AS trackCount,
               SUM(duration) AS totalDuration,
               MIN(artworkUri) AS artworkUri,
               MIN(albumId) AS albumId
        FROM tracks
        GROUP BY album, artist
        ORDER BY album COLLATE NOCASE ASC
        """
    )
    suspend fun getAlbumsOnce(): List<AlbumRow>

    @Query(
        """
        SELECT artist AS name,
               '' AS subtitle,
               COUNT(*) AS trackCount,
               SUM(duration) AS totalDuration,
               MIN(artworkUri) AS artworkUri,
               MIN(artistId) AS albumId
        FROM tracks
        GROUP BY artist
        ORDER BY artist COLLATE NOCASE ASC
        """
    )
    fun getArtists(): Flow<List<AlbumRow>>

    @Query(
        """
        SELECT artist AS name,
               '' AS subtitle,
               COUNT(*) AS trackCount,
               SUM(duration) AS totalDuration,
               MIN(artworkUri) AS artworkUri,
               MIN(artistId) AS albumId
        FROM tracks
        GROUP BY artist
        ORDER BY artist COLLATE NOCASE ASC
        """
    )
    suspend fun getArtistsOnce(): List<AlbumRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()

    @Query("DELETE FROM tracks WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracksByIds(ids: List<Long>)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    @Query("SELECT id FROM tracks")
    suspend fun getAllTrackIds(): List<Long>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int

    @Query("SELECT COUNT(*) FROM tracks")
    fun observeTrackCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE source = :source")
    fun observeCountBySource(source: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE source = :source")
    suspend fun getCountBySource(source: String): Int

    @Query(
        """
        SELECT DISTINCT folderPath FROM tracks
        WHERE folderPath IS NOT NULL AND folderPath != ''
        ORDER BY folderPath COLLATE NOCASE ASC
        """
    )
    fun getDistinctFolderPaths(): Flow<List<String>>

    @Query(
        """
        SELECT DISTINCT folderPath FROM tracks
        WHERE folderPath IS NOT NULL AND folderPath != ''
        ORDER BY folderPath COLLATE NOCASE ASC
        """
    )
    suspend fun getDistinctFolderPathsOnce(): List<String>

    @Query(
        """
        SELECT * FROM tracks
        WHERE folderPath = :folderPath
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    fun getTracksByFolder(folderPath: String): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT * FROM tracks
        WHERE folderPath = :folderPath
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    suspend fun getTracksByFolderOnce(folderPath: String): List<TrackEntity>

    @Query(
        """
        SELECT COUNT(*) FROM tracks WHERE folderPath = :folderPath
        """
    )
    suspend fun countTracksInFolder(folderPath: String): Int

    @Query(
        """
        SELECT * FROM tracks
        WHERE source IN ('jellyfin', 'youtube', 'lan')
          AND size >= :minSize
        ORDER BY size DESC
        """
    )
    suspend fun getLargeAppOwnedTracks(minSize: Long): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE source IN ('jellyfin', 'youtube', 'lan')
          AND storageState = 'hot'
          AND neverCompress = 0
          AND size >= :minSize
          AND lastPlayedAt < :idleBeforeMs
        ORDER BY size DESC
        LIMIT :limit
        """
    )
    suspend fun getHotIdleLargeTracks(
        minSize: Long,
        idleBeforeMs: Long,
        limit: Int = 20
    ): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE source IN ('jellyfin', 'youtube', 'lan')
          AND isSizeOptimized = 0
          AND neverCompress = 0
          AND size >= :minSize
          AND duration >= :minDurationMs
        ORDER BY size DESC
        LIMIT :limit
        """
    )
    suspend fun getUnoptimizedLargeTracks(
        minSize: Long,
        minDurationMs: Long,
        limit: Int = 10
    ): List<TrackEntity>
}

data class AlbumRow(
    val name: String,
    val subtitle: String,
    val trackCount: Int,
    val totalDuration: Long,
    val artworkUri: String?,
    val albumId: Long
)
