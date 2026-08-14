package io.karpilabs.simplemp3.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.repository.MusicRepository
import io.karpilabs.simplemp3.data.storage.LargeFileStorageManager
import io.karpilabs.simplemp3.data.youtube.YoutubeDownloadManager
import io.karpilabs.simplemp3.data.youtube.YoutubeDownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YoutubeUiState(
    val urlInput: String = "",
)

@HiltViewModel
class YoutubeViewModel
    @Inject
    constructor(
        private val downloadManager: YoutubeDownloadManager,
        private val repository: MusicRepository,
        private val storageManager: LargeFileStorageManager,
    ) : ViewModel() {
        private val share = SharingStarted.WhileSubscribed(5_000)

        val progress: StateFlow<YoutubeDownloadProgress> = downloadManager.progress

        val downloads: StateFlow<List<TrackEntity>> =
            repository.youtubeTracks.stateIn(viewModelScope, share, emptyList())

        val downloadCount: StateFlow<Int> =
            repository.youtubeTrackCount.stateIn(viewModelScope, share, 0)

        private val _ui = MutableStateFlow(YoutubeUiState())
        val ui: StateFlow<YoutubeUiState> = _ui.asStateFlow()

        fun setUrl(url: String) {
            _ui.value = _ui.value.copy(urlInput = url)
        }

        fun pasteAndSet(url: String) {
            _ui.value = _ui.value.copy(urlInput = url.trim())
        }

        fun download() {
            val url = _ui.value.urlInput.trim()
            if (url.isBlank()) return
            viewModelScope.launch {
                downloadManager.download(url).onSuccess {
                    _ui.value = _ui.value.copy(urlInput = "")
                }
            }
        }

        fun remove(trackId: Long) {
            viewModelScope.launch {
                downloadManager.removeDownload(trackId)
            }
        }

        fun toggleNeverCompress(trackId: Long) {
            viewModelScope.launch {
                val current =
                    downloads.value.firstOrNull { it.id == trackId }
                        ?: repository.getTrack(trackId)
                        ?: return@launch
                storageManager.setNeverCompress(trackId, !current.neverCompress)
            }
        }

        fun clearAll() {
            viewModelScope.launch {
                downloadManager.clearAll()
            }
        }
    }
