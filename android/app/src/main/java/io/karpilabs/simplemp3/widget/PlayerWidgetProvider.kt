package io.karpilabs.simplemp3.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import io.karpilabs.simplemp3.MainActivity
import io.karpilabs.simplemp3.R
import io.karpilabs.simplemp3.service.PlaybackService

/**
 * Home-screen media widget: album art, title/artist, prev / play-pause / next.
 * State is pushed from [PlayerWidgetUpdater] when the Media3 session changes.
 */
class PlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Paint a shell immediately, then refresh from the live session if available.
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildShellViews(context))
        }
        PlayerWidgetUpdater.requestRefresh(context.applicationContext)
    }

    override fun onEnabled(context: Context) {
        PlayerWidgetUpdater.requestRefresh(context.applicationContext)
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        when (action) {
            ACTION_PLAY_PAUSE, ACTION_SKIP_NEXT, ACTION_SKIP_PREVIOUS -> {
                dispatchPlayerAction(context.applicationContext, action)
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun dispatchPlayerAction(
        context: Context,
        action: String,
    ) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    when (action) {
                        ACTION_PLAY_PAUSE -> {
                            if (controller.isPlaying) controller.pause() else controller.play()
                        }
                        ACTION_SKIP_NEXT -> controller.seekToNextMediaItem()
                        ACTION_SKIP_PREVIOUS -> {
                            if (controller.currentPosition > 3_000) {
                                controller.seekTo(0)
                            } else {
                                controller.seekToPreviousMediaItem()
                            }
                        }
                    }
                    // Push latest state back into widgets (session may lag a frame).
                    PlayerWidgetUpdater.publishFromController(context, controller)
                }
                MediaController.releaseFuture(future)
            },
            MoreExecutors.directExecutor(),
        )
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "io.karpilabs.simplemp3.widget.PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "io.karpilabs.simplemp3.widget.SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "io.karpilabs.simplemp3.widget.SKIP_PREVIOUS"

        fun buildShellViews(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_player).also { views ->
                bindClicks(context, views)
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_nothing_playing))
                views.setTextViewText(R.id.widget_artist, context.getString(R.string.widget_tap_to_open))
                views.setImageViewResource(R.id.widget_play_pause, R.drawable.ic_widget_play)
                views.setImageViewResource(R.id.widget_art, R.drawable.ic_widget_art_placeholder)
            }

        fun bindClicks(
            context: Context,
            views: RemoteViews,
        ) {
            val openApp =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    pendingFlags(),
                )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            views.setOnClickPendingIntent(R.id.widget_art, openApp)
            views.setOnClickPendingIntent(R.id.widget_title, openApp)
            views.setOnClickPendingIntent(R.id.widget_artist, openApp)

            views.setOnClickPendingIntent(
                R.id.widget_play_pause,
                broadcastPi(context, ACTION_PLAY_PAUSE, 1),
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                broadcastPi(context, ACTION_SKIP_NEXT, 2),
            )
            views.setOnClickPendingIntent(
                R.id.widget_prev,
                broadcastPi(context, ACTION_SKIP_PREVIOUS, 3),
            )
        }

        private fun broadcastPi(
            context: Context,
            action: String,
            requestCode: Int,
        ): PendingIntent {
            val intent = Intent(context, PlayerWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(context, requestCode, intent, pendingFlags())
        }

        private fun pendingFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun widgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return manager.getAppWidgetIds(ComponentName(context, PlayerWidgetProvider::class.java))
        }

        fun hasWidgets(context: Context): Boolean = widgetIds(context).isNotEmpty()
    }
}
