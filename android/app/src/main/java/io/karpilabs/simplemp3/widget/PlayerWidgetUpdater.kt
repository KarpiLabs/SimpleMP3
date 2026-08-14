package io.karpilabs.simplemp3.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Size
import android.widget.RemoteViews
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import io.karpilabs.simplemp3.R
import io.karpilabs.simplemp3.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Pushes Media3 session state into [PlayerWidgetProvider] instances.
 * Called from [PlaybackService] on play / metadata changes and on demand from the provider.
 */
object PlayerWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var debounceJob: Job? = null
    private val lastArtUri = AtomicReference<String?>(null)
    private val lastArtBitmap = AtomicReference<Bitmap?>(null)

    data class Snapshot(
        val title: String,
        val artist: String,
        val artworkUri: String?,
        val isPlaying: Boolean,
        val hasQueue: Boolean,
    )

    fun requestRefresh(context: Context) {
        if (!PlayerWidgetProvider.hasWidgets(context)) return
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    publishFromController(app, controller)
                }
                MediaController.releaseFuture(future)
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun publishFromController(
        context: Context,
        player: Player,
    ) {
        val meta = player.mediaMetadata
        val title =
            meta.title
                ?.toString()
                .orEmpty()
                .ifBlank { if (player.mediaItemCount > 0) "Unknown title" else "" }
        val artist = meta.artist?.toString().orEmpty()
        val art = meta.artworkUri?.toString()
        publish(
            context,
            Snapshot(
                title = title,
                artist = artist,
                artworkUri = art,
                isPlaying = player.isPlaying,
                hasQueue = player.mediaItemCount > 0,
            ),
        )
    }

    fun publishFromPlayer(
        context: Context,
        player: Player,
    ) {
        if (!PlayerWidgetProvider.hasWidgets(context)) return
        // Debounce bursty player events (seek/shuffle layout refresh).
        debounceJob?.cancel()
        debounceJob =
            scope.launch {
                delay(80)
                publishFromController(context, player)
            }
    }

    fun publish(
        context: Context,
        snapshot: Snapshot,
    ) {
        if (!PlayerWidgetProvider.hasWidgets(context)) return
        val app = context.applicationContext
        scope.launch {
            val artBitmap = loadArt(app, snapshot.artworkUri)
            val views = buildViews(app, snapshot, artBitmap)
            val manager = AppWidgetManager.getInstance(app)
            val ids = PlayerWidgetProvider.widgetIds(app)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }
    }

    private fun buildViews(
        context: Context,
        snapshot: Snapshot,
        art: Bitmap?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_player)
        PlayerWidgetProvider.bindClicks(context, views)

        if (snapshot.hasQueue && snapshot.title.isNotBlank()) {
            views.setTextViewText(R.id.widget_title, snapshot.title)
            views.setTextViewText(
                R.id.widget_artist,
                snapshot.artist.ifBlank { " " },
            )
        } else {
            views.setTextViewText(
                R.id.widget_title,
                context.getString(R.string.widget_nothing_playing),
            )
            views.setTextViewText(
                R.id.widget_artist,
                context.getString(R.string.widget_tap_to_open),
            )
        }

        views.setImageViewResource(
            R.id.widget_play_pause,
            if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )

        if (art != null) {
            views.setImageViewBitmap(R.id.widget_art, art)
        } else {
            views.setImageViewResource(R.id.widget_art, R.drawable.ic_widget_art_placeholder)
        }
        return views
    }

    private suspend fun loadArt(
        context: Context,
        artworkUri: String?,
    ): Bitmap? {
        if (artworkUri.isNullOrBlank()) {
            lastArtUri.set(null)
            lastArtBitmap.set(null)
            return null
        }
        if (artworkUri == lastArtUri.get()) {
            return lastArtBitmap.get()
        }
        val bitmap =
            withContext(Dispatchers.IO) {
                runCatching {
                    val uri = artworkUri.toUri()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(uri, Size(168, 168), null)
                    } else {
                        @Suppress("DEPRECATION")
                        android.provider.MediaStore.Images.Media
                            .getBitmap(
                                context.contentResolver,
                                uri,
                            )?.let { src ->
                                Bitmap.createScaledBitmap(src, 168, 168, true).also {
                                    if (it !== src) src.recycle()
                                }
                            }
                    }
                }.getOrNull()
            }
        lastArtUri.set(artworkUri)
        lastArtBitmap.set(bitmap)
        return bitmap
    }
}
