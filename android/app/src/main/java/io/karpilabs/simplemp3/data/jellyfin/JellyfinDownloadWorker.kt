package io.karpilabs.simplemp3.data.jellyfin

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background Jellyfin download/import worker.
 *
 * Intentionally does **not** run as a foreground service (no
 * FOREGROUND_SERVICE_DATA_SYNC) so Play Console FGS approval is not required.
 * Downloads may be deferred longer when the app is backgrounded.
 */
@HiltWorker
class JellyfinDownloadWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val syncManager: JellyfinSyncManager,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val mode = inputData.getString(KEY_MODE) ?: MODE_ITEMS

            return try {
                when (mode) {
                    MODE_PLAYLIST -> {
                        val id = inputData.getString(KEY_PLAYLIST_ID).orEmpty()
                        val name = inputData.getString(KEY_PLAYLIST_NAME).orEmpty().ifBlank { "Playlist" }
                        if (id.isBlank()) return Result.failure()
                        val remote = JellyfinItem(id = id, name = name, type = "Playlist")
                        syncManager.importPlaylist(remote).fold(
                            onSuccess = { Result.success() },
                            onFailure = { Result.retry() },
                        )
                    }
                    else -> {
                        val ids =
                            inputData
                                .getString(KEY_ITEM_IDS)
                                .orEmpty()
                                .split(',')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                        if (ids.isEmpty()) return Result.success()
                        val items = syncManager.resolveItemsByIds(ids)
                        if (items.isEmpty()) return Result.retry()
                        syncManager.syncItems(items).fold(
                            onSuccess = { Result.success() },
                            onFailure = { Result.retry() },
                        )
                    }
                }
            } catch (_: Exception) {
                Result.retry()
            }
        }

        companion object {
            const val KEY_MODE = "mode"
            const val KEY_ITEM_IDS = "item_ids"
            const val KEY_PLAYLIST_ID = "playlist_id"
            const val KEY_PLAYLIST_NAME = "playlist_name"
            const val MODE_ITEMS = "items"
            const val MODE_PLAYLIST = "playlist"
        }
    }
