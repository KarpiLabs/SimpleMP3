package io.karpilabs.simplemp3.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
    ): ExoPlayer {
        val audioAttributes =
            AudioAttributes
                .Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

        // Larger buffers help movie-length MP3s (seek / rebuffer less often on slow storage).
        val loadControl =
            DefaultLoadControl
                .Builder()
                .setBufferDurationsMs(
                    // minBufferMs
                    30_000,
                    // maxBufferMs
                    180_000,
                    // bufferForPlaybackMs
                    2_000,
                    // bufferForPlaybackAfterRebufferMs
                    5_000,
                ).setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(/* backBufferDurationMs */ 60_000, /* retainBackBufferFromKeyframe */ true)
                .build()

        return ExoPlayer
            .Builder(context)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
    }
}
