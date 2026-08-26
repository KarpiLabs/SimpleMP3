package io.karpilabs.simplemp3.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.repository.MusicRepository
import io.karpilabs.simplemp3.data.stream.StreamSaveManager
import io.karpilabs.simplemp3.data.stream.StreamSaveProgress
import io.karpilabs.simplemp3.player.PlayerConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StreamUiState(
    val urlInput: String = "",
    val titleInput: String = "",
)

@HiltViewModel
class StreamViewModel
    @Inject
    constructor(
        private val saveManager: StreamSaveManager,
        private val playerConnection: PlayerConnection,
        repository: MusicRepository,
    ) : ViewModel() {
        private val share = SharingStarted.WhileSubscribed(5_000)

        val progress: StateFlow<StreamSaveProgress> = saveManager.progress

        val saved: StateFlow<List<TrackEntity>> =
            repository.streamTracks.stateIn(viewModelScope, share, emptyList())

        private val _ui = MutableStateFlow(StreamUiState())
        val ui: StateFlow<StreamUiState> = _ui.asStateFlow()

        fun setUrl(url: String) {
            _ui.value = _ui.value.copy(urlInput = url)
        }

        fun pasteUrl(url: String) {
            _ui.value = _ui.value.copy(urlInput = url.trim())
        }

        fun setTitle(title: String) {
            _ui.value = _ui.value.copy(titleInput = title)
        }

        /** Play the stream live without saving it. */
        fun playLive() {
            val url = _ui.value.urlInput.trim()
            if (url.isBlank()) return
            playerConnection.playStreamUrl(url, _ui.value.titleInput.trim())
        }

        /** Stream-copy the stream to an offline .m4a and add it to the library. */
        fun save() {
            val url = _ui.value.urlInput.trim()
            if (url.isBlank()) return
            viewModelScope.launch {
                saveManager.save(url, _ui.value.titleInput.trim()).onSuccess {
                    _ui.value = StreamUiState()
                }
            }
        }

        fun remove(trackId: Long) {
            viewModelScope.launch { saveManager.removeSaved(trackId) }
        }
    }
