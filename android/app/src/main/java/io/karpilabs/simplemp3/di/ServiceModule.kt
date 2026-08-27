package io.karpilabs.simplemp3.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.karpilabs.simplemp3.data.prefs.AppPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        appPreferences: AppPreferences,
    ): ExoPlayer {
        val audioAttributes =
            AudioAttributes
                .Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

        // Larger buffers help movie-length MP3s and flaky stream URLs (seek / rebuffer
        // less often on slow storage). User-configurable; applied at process start.
        val buffer = appPreferences.getBufferProfileBlocking()
        val loadControl =
            DefaultLoadControl
                .Builder()
                .setBufferDurationsMs(
                    buffer.minBufferMs,
                    buffer.maxBufferMs,
                    buffer.forPlaybackMs,
                    buffer.forPlaybackAfterRebufferMs,
                ).setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(/* backBufferDurationMs */ 60_000, /* retainBackBufferFromKeyframe */ true)
                .build()

        // This is an audio player: never select video/image tracks. For HLS that
        // advertises audio-only renditions, ExoPlayer will use those instead of
        // downloading a video variant.
        val trackSelector = DefaultTrackSelector(context)
        trackSelector.setParameters(
            trackSelector
                .buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_IMAGE, true),
        )

        return ExoPlayer
            .Builder(context)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
    }
}
