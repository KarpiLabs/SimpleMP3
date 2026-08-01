package io.karpilabs.simplemp3.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import io.karpilabs.simplemp3.data.drive.DriveBackupManager
import io.karpilabs.simplemp3.data.drive.DriveBackupProgress
import io.karpilabs.simplemp3.data.drive.DriveBackupRemote
import io.karpilabs.simplemp3.data.drive.GoogleDriveGateway
import io.karpilabs.simplemp3.data.drive.MediaBackupEstimate
import io.karpilabs.simplemp3.data.prefs.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriveUiState(
    val signedInEmail: String? = null,
    val hasDriveScope: Boolean = false,
    val backups: List<DriveBackupRemote> = emptyList(),
    val mediaEstimate: MediaBackupEstimate = MediaBackupEstimate(0, 0),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class DriveViewModel @Inject constructor(
    private val gateway: GoogleDriveGateway,
    private val backupManager: DriveBackupManager,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val share = SharingStarted.WhileSubscribed(5_000)

    val includeMedia: StateFlow<Boolean> = appPreferences.driveIncludeMediaFlow
        .stateIn(viewModelScope, share, false)
    val wifiOnly: StateFlow<Boolean> = appPreferences.driveWifiOnlyFlow
        .stateIn(viewModelScope, share, true)
    val lastBackupMs: StateFlow<Long> = appPreferences.driveLastBackupMsFlow
        .stateIn(viewModelScope, share, 0L)

    val progress: StateFlow<DriveBackupProgress> = backupManager.progress

    private val _ui = MutableStateFlow(DriveUiState())
    val ui: StateFlow<DriveUiState> = _ui.asStateFlow()

    init {
        refreshAccount()
        viewModelScope.launch {
            val est = backupManager.estimateAppOwnedMedia()
            _ui.update { it.copy(mediaEstimate = est) }
        }
    }

    fun refreshAccount() {
        val account = gateway.lastAccount()
        _ui.update {
            it.copy(
                signedInEmail = account?.email,
                hasDriveScope = gateway.hasDriveScope(account)
            )
        }
        if (account != null && gateway.hasDriveScope(account)) {
            refreshBackupList()
        }
    }

    fun signInIntent(): Intent = gateway.signInIntent()

    fun onSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                appPreferences.setDriveLastAccount(account.email)
                _ui.update {
                    it.copy(
                        signedInEmail = account.email,
                        hasDriveScope = gateway.hasDriveScope(account),
                        error = null,
                        message = "Signed in as ${account.email}"
                    )
                }
                refreshBackupList()
            } catch (e: ApiException) {
                _ui.update {
                    it.copy(error = "Sign-in failed (${e.statusCode}): ${e.message ?: "try again"}")
                }
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message ?: "Sign-in failed") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            gateway.signOut()
            appPreferences.setDriveLastAccount(null)
            _ui.update {
                it.copy(
                    signedInEmail = null,
                    hasDriveScope = false,
                    backups = emptyList(),
                    message = "Signed out"
                )
            }
        }
    }

    fun setIncludeMedia(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setDriveIncludeMedia(enabled) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setDriveWifiOnly(enabled) }
    }

    fun refreshBackupList() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            backupManager.listRemoteBackups()
                .onSuccess { list ->
                    _ui.update { it.copy(busy = false, backups = list) }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(busy = false, error = e.message ?: "Could not list backups")
                    }
                }
        }
    }

    fun backupNow() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, message = null) }
            val include = appPreferences.isDriveIncludeMedia()
            backupManager.createAndUploadBackup(include)
                .onSuccess { remote ->
                    val est = backupManager.estimateAppOwnedMedia()
                    _ui.update {
                        it.copy(
                            busy = false,
                            message = "Backup uploaded · ${remote.name}",
                            mediaEstimate = est
                        )
                    }
                    refreshBackupList()
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(busy = false, error = e.message ?: "Backup failed")
                    }
                }
        }
    }

    fun restore(backup: DriveBackupRemote) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, message = null) }
            backupManager.restoreFromRemote(backup.fileId, backup.name)
                .onSuccess { summary ->
                    _ui.update { it.copy(busy = false, message = summary) }
                }
                .onFailure { e ->
                    _ui.update {
                        it.copy(busy = false, error = e.message ?: "Restore failed")
                    }
                }
        }
    }

    fun deleteBackup(backup: DriveBackupRemote) {
        viewModelScope.launch {
            backupManager.deleteRemote(backup.fileId)
                .onSuccess {
                    _ui.update { it.copy(message = "Deleted ${backup.name}") }
                    refreshBackupList()
                }
                .onFailure { e ->
                    _ui.update { it.copy(error = e.message ?: "Delete failed") }
                }
        }
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun consumeError() {
        _ui.update { it.copy(error = null) }
    }
}
