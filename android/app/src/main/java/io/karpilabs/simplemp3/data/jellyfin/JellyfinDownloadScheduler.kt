package io.karpilabs.simplemp3.data.jellyfin

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import io.karpilabs.simplemp3.data.prefs.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinDownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Queue a background download of specific Jellyfin item IDs (comma-separated in worker).
     * Honors Wi‑Fi-only preference via WorkManager network constraints.
     */
    suspend fun enqueueItemIds(itemIds: List<String>, uniqueName: String = WORK_ITEMS) {
        if (itemIds.isEmpty()) return
        val wifiOnly = appPreferences.isWifiOnlyDownloads()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<JellyfinDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    JellyfinDownloadWorker.KEY_MODE to JellyfinDownloadWorker.MODE_ITEMS,
                    JellyfinDownloadWorker.KEY_ITEM_IDS to itemIds.joinToString(",")
                )
            )
            .addTag(TAG)
            .build()

        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    suspend fun enqueuePlaylistImport(playlistId: String, playlistName: String) {
        val wifiOnly = appPreferences.isWifiOnlyDownloads()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<JellyfinDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    JellyfinDownloadWorker.KEY_MODE to JellyfinDownloadWorker.MODE_PLAYLIST,
                    JellyfinDownloadWorker.KEY_PLAYLIST_ID to playlistId,
                    JellyfinDownloadWorker.KEY_PLAYLIST_NAME to playlistName
                )
            )
            .addTag(TAG)
            .build()

        workManager.enqueueUniqueWork(
            "$WORK_PLAYLIST-$playlistId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun observeWork(): Flow<List<WorkInfo>> =
        workManager.getWorkInfosByTagFlow(TAG)

    fun isRunningFlow(): Flow<Boolean> =
        observeWork().map { list ->
            list.any {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }
        }

    companion object {
        const val TAG = "jellyfin_download"
        const val WORK_ITEMS = "jellyfin_items_download"
        const val WORK_PLAYLIST = "jellyfin_playlist_import"
    }
}
