package com.example.miaubertoplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class MiaubertoWidgetMiniReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        if (action == ACTION_MINI_PLAY_PAUSE) {
            val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                this.action = action
            }
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        const val ACTION_MINI_PLAY_PAUSE = "com.example.miaubertoplayer.WIDGET_MINI_PLAY_PAUSE"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.miauberto_widget_mini_layout)

            val intent = Intent(context, MiaubertoWidgetMiniReceiver::class.java).apply {
                action = ACTION_MINI_PLAY_PAUSE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 20, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.btn_widget_mini_play, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
