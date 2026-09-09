package com.example.miaubertoplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MiaubertoWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        val action = intent.action ?: return
        if (action.startsWith("com.example.miaubertoplayer.WIDGET_")) {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controllerFuture: ListenableFuture<MediaController> =
                MediaController.Builder(context, sessionToken).buildAsync()

            controllerFuture.addListener({
                try {
                    val controller = controllerFuture.get()
                    when (action) {
                        ACTION_PLAY_PAUSE -> {
                            if (controller.isPlaying) controller.pause() else controller.play()
                        }
                        ACTION_PREV -> controller.seekToPreviousMediaItem()
                        ACTION_NEXT -> controller.seekToNextMediaItem()
                        ACTION_SHUFFLE -> controller.shuffleModeEnabled = !controller.shuffleModeEnabled
                        ACTION_REPEAT -> {
                            controller.repeatMode = when (controller.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }
                    }
                    
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, MiaubertoWidgetReceiver::class.java))
                    for (id in ids) {
                        updateWidgetState(context, appWidgetManager, id, controller)
                    }
                } catch (e: Exception) {
                    // Si el servicio no está corriendo, abrir MainActivity de forma segura
                    val launchApp = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(launchApp)
                }
            }, MoreExecutors.directExecutor())
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.miaubertoplayer.WIDGET_PLAY_PAUSE"
        const val ACTION_PREV = "com.example.miaubertoplayer.WIDGET_PREV"
        const val ACTION_NEXT = "com.example.miaubertoplayer.WIDGET_NEXT"
        const val ACTION_SHUFFLE = "com.example.miaubertoplayer.WIDGET_SHUFFLE"
        const val ACTION_REPEAT = "com.example.miaubertoplayer.WIDGET_REPEAT"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.miauberto_widget_layout)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpenApp = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_title, pendingOpenApp)
            views.setOnClickPendingIntent(R.id.widget_cat_emoji, pendingOpenApp)

            views.setOnClickPendingIntent(R.id.btn_widget_play, createPendingIntent(context, ACTION_PLAY_PAUSE, 1))
            views.setOnClickPendingIntent(R.id.btn_widget_prev, createPendingIntent(context, ACTION_PREV, 2))
            views.setOnClickPendingIntent(R.id.btn_widget_next, createPendingIntent(context, ACTION_NEXT, 3))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun updateWidgetState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, controller: MediaController) {
            val views = RemoteViews(context.packageName, R.layout.miauberto_widget_layout)
            
            val mediaMetadata = controller.currentMediaItem?.mediaMetadata
            val trackTitle = mediaMetadata?.title?.toString() ?: "Miauberto Player"
            val artist = mediaMetadata?.artist?.toString() ?: "Sin reproducción"

            views.setTextViewText(R.id.widget_title, trackTitle)
            views.setTextViewText(R.id.widget_status, artist)

            val playIcon = if (controller.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            views.setImageViewResource(R.id.btn_widget_play, playIcon)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, MiaubertoWidgetReceiver::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
