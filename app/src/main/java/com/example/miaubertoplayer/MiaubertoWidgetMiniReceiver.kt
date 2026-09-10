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

        if (action.startsWith("com.example.miaubertoplayer.WIDGET_MINI_")) {
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
        const val ACTION_MINI_REWIND = "com.example.miaubertoplayer.WIDGET_MINI_REWIND"
        const val ACTION_MINI_PLAY_PAUSE = "com.example.miaubertoplayer.WIDGET_MINI_PLAY_PAUSE"
        const val ACTION_MINI_FFWD = "com.example.miaubertoplayer.WIDGET_MINI_FFWD"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.miauberto_widget_mini_layout)

            views.setOnClickPendingIntent(R.id.btn_widget_mini_rewind, createPendingIntent(context, ACTION_MINI_REWIND, 21))
            views.setOnClickPendingIntent(R.id.btn_widget_mini_play, createPendingIntent(context, ACTION_MINI_PLAY_PAUSE, 22))
            views.setOnClickPendingIntent(R.id.btn_widget_mini_ffwd, createPendingIntent(context, ACTION_MINI_FFWD, 23))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, MiaubertoWidgetMiniReceiver::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
