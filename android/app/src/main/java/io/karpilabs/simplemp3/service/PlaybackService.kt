package io.karpilabs.simplemp3.service

import android.app.PendingIntent
import android.content.Intent

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import io.karpilabs.simplemp3.MainActivity
import io.karpilabs.simplemp3.data.prefs.AppPreferences
import io.karpilabs.simplemp3.data.repository.MusicRepository
import io.karpilabs.simplemp3.data.storage.LargeFileStorageManager
import io.karpilabs.simplemp3.widget.PlayerWidgetUpdater
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    companion object {
        private const val TAG = "PlaybackService"
    }

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var repository: MusicRepository

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var storageManager: LargeFileStorageManager

    private var librarySession: MediaLibrarySession? = null
    private var callback: LibrarySessionCallback? = null

    private val playerListener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    callback?.recordPlayForCurrent()
                }
                updateCustomLayout()
                PlayerWidgetUpdater.publishFromPlayer(this@PlaybackService, player)
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                if (player.isPlaying) {
                    callback?.recordPlayForCurrent()
                }
                PlayerWidgetUpdater.publishFromPlayer(this@PlaybackService, player)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                PlayerWidgetUpdater.publishFromPlayer(this@PlaybackService, player)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateCustomLayout()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateCustomLayout()
            }

            /**
             * A dead file (purged offline download, missing SD card track, etc.) would
             * otherwise stall the queue silently — bad on a drive where the user can't
             * look at the screen. Skip past it instead of leaving playback stuck.
             */
            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Playback error on \"${player.currentMediaItem?.mediaId}\": ${error.errorCodeName}", error)
                // Preserve intent: only resume automatically if playback was actually wanted.
                val wasPlaying = player.playWhenReady
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    if (wasPlaying) player.play()
                } else {
                    player.pause()
                }
            }
        }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Audio attributes / focus and become-noisy handling are configured once on the
        // injected ExoPlayer in ServiceModule — no need to re-apply them here.
        // Keep advancing through the queue when a track ends.
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false
        player.addListener(playerListener)

        val sessionCallback =
            LibrarySessionCallback(
                repository,
                player,
                appPreferences,
                storageManager,
            )
        callback = sessionCallback

        val sessionActivity =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        librarySession =
            MediaLibrarySession
                .Builder(this, player, sessionCallback)
                .setSessionActivity(sessionActivity)
                .setId("SimpleMP3_Session")
                .build()

        updateCustomLayout()
        PlayerWidgetUpdater.publishFromPlayer(this, player)
    }

    /**
     * Surface shuffle / repeat as notification + Auto custom actions so drivers
     * can toggle them without opening the phone app.
     */
    @OptIn(UnstableApi::class)
    private fun updateCustomLayout() {
        val session = librarySession ?: return
        val shuffleOn = player.shuffleModeEnabled
        val repeatMode = player.repeatMode

        val shuffleButton =
            CommandButton
                .Builder(CommandButton.ICON_SHUFFLE_ON)
                .setDisplayName(if (shuffleOn) "Shuffle on" else "Shuffle off")
                .setSessionCommand(LibrarySessionCallback.CUSTOM_SHUFFLE)
                .setEnabled(true)
                .build()

        val repeatIcon =
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                else -> CommandButton.ICON_REPEAT_OFF
            }
        val repeatLabel =
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> "Repeat one"
                Player.REPEAT_MODE_ALL -> "Repeat all"
                else -> "Repeat off"
            }
        val repeatButton =
            CommandButton
                .Builder(repeatIcon)
                .setDisplayName(repeatLabel)
                .setSessionCommand(LibrarySessionCallback.CUSTOM_REPEAT)
                .setEnabled(true)
                .build()

        session.setCustomLayout(listOf(shuffleButton, repeatButton))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = librarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = librarySession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        player.removeListener(playerListener)
        librarySession?.run {
            // Do not release shared singleton player here if other components need it —
            // stop and clear instead; full release on process death.
            player.stop()
            player.clearMediaItems()
            release()
            librarySession = null
        }
        callback = null
        super.onDestroy()
    }
}
