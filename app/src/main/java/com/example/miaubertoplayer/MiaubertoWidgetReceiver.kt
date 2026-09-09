package com.example.miaubertoplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

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
            val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                this.action = action
            }
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                // Fallback para abrir la app de forma segura si el SO bloquea el inicio directo
                val launchApp = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(launchApp)
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.miaubertoplayer.WIDGET_PLAY_PAUSE"
        const val ACTION_PREV = "com.example.miaubertoplayer.WIDGET_PREV"
        const val ACTION_NEXT = "com.example.miaubertoplayer.WIDGET_NEXT"

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
