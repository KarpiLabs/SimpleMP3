package io.karpilabs.simplemp3.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.karpilabs.simplemp3.data.quickconnect.QuickConnectServer
import io.karpilabs.simplemp3.data.quickconnect.QuickConnectSession
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class QuickConnectViewModel @Inject constructor(
    private val server: QuickConnectServer
) : ViewModel() {

    val session: StateFlow<QuickConnectSession> = server.session

    fun startPortal() {
        server.start()
    }

    fun stopPortal() {
        server.stop()
    }

    override fun onCleared() {
        server.stop()
        super.onCleared()
    }
}
