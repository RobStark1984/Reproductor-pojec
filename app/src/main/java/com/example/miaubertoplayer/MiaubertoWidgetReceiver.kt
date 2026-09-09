package com.example.miaubertoplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.miaubertoplayer.WIDGET_PLAY_PAUSE"
        const val ACTION_PREV = "com.example.miaubertoplayer.WIDGET_PREV"
        const val ACTION_NEXT = "com.example.miaubertoplayer.WIDGET_NEXT"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.miauberto_widget_layout)

            // Abrir la app al hacer clic en el nombre o emoji
            val openAppIntent = Intent(context, MainActivity::class.java)
            val pendingOpenApp = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, pendingOpenApp)
            views.setOnClickPendingIntent(R.id.widget_cat_emoji, pendingOpenApp)

            // Asignar intentes de control multimedia a los botones del widget
            views.setOnClickPendingIntent(R.id.btn_widget_play, createPendingIntent(context, ACTION_PLAY_PAUSE, 1))
            views.setOnClickPendingIntent(R.id.btn_widget_prev, createPendingIntent(context, ACTION_PREV, 2))
            views.setOnClickPendingIntent(R.id.btn_widget_next, createPendingIntent(context, ACTION_NEXT, 3))

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
