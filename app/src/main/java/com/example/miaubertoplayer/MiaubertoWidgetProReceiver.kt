package com.example.miaubertoplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MiaubertoWidgetProReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                when (action) {
                    MiaubertoWidgetReceiver.ACTION_PLAY_PAUSE -> {
                        if (controller.isPlaying) controller.pause() else controller.play()
                    }
                    MiaubertoWidgetReceiver.ACTION_PREV -> controller.seekToPreviousMediaItem()
                    MiaubertoWidgetReceiver.ACTION_NEXT -> controller.seekToNextMediaItem()
                    MiaubertoWidgetReceiver.ACTION_SHUFFLE -> controller.shuffleModeEnabled = !controller.shuffleModeEnabled
                    MiaubertoWidgetReceiver.ACTION_REPEAT -> {
                        controller.repeatMode = when (controller.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    }
                }
                
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, MiaubertoWidgetProReceiver::class.java))
                for (id in ids) {
                    updateWidgetState(context, appWidgetManager, id, controller)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.miauberto_widget_pro_layout)

            val openAppIntent = Intent(context, MainActivity::class.java)
            val pendingOpenApp = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, pendingOpenApp)
            views.setOnClickPendingIntent(R.id.widget_cat_emoji, pendingOpenApp)
            views.setOnClickPendingIntent(R.id.btn_widget_sleep, pendingOpenApp)

            views.setOnClickPendingIntent(R.id.btn_widget_play, createPendingIntent(context, MiaubertoWidgetReceiver.ACTION_PLAY_PAUSE, 10))
            views.setOnClickPendingIntent(R.id.btn_widget_prev, createPendingIntent(context, MiaubertoWidgetReceiver.ACTION_PREV, 11))
            views.setOnClickPendingIntent(R.id.btn_widget_next, createPendingIntent(context, MiaubertoWidgetReceiver.ACTION_NEXT, 12))
            views.setOnClickPendingIntent(R.id.btn_widget_shuffle, createPendingIntent(context, MiaubertoWidgetReceiver.ACTION_SHUFFLE, 13))
            views.setOnClickPendingIntent(R.id.btn_widget_repeat, createPendingIntent(context, MiaubertoWidgetReceiver.ACTION_REPEAT, 14))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun updateWidgetState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, controller: MediaController) {
            val views = RemoteViews(context.packageName, R.layout.miauberto_widget_pro_layout)
            
            val mediaMetadata = controller.currentMediaItem?.mediaMetadata
            val trackTitle = mediaMetadata?.title?.toString() ?: "Miauberto Player Pro"
            val artist = mediaMetadata?.artist?.toString() ?: "Sin reproducción"

            views.setTextViewText(R.id.widget_title, trackTitle)
            views.setTextViewText(R.id.widget_status, artist)

            val playIcon = if (controller.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            views.setImageViewResource(R.id.btn_widget_play, playIcon)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, MiaubertoWidgetProReceiver::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
