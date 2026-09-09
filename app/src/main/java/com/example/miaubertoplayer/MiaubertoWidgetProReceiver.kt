package com.example.miaubertoplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class MiaubertoWidgetProReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        if (action.startsWith("com.example.miaubertoplayer.WIDGET_PRO_")) {
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
        const val ACTION_PRO_PLAY_PAUSE = "com.example.miaubertoplayer.WIDGET_PRO_PLAY_PAUSE"
        const val ACTION_PRO_PREV = "com.example.miaubertoplayer.WIDGET_PRO_PREV"
        const val ACTION_PRO_NEXT = "com.example.miaubertoplayer.WIDGET_PRO_NEXT"
        const val ACTION_PRO_SHUFFLE = "com.example.miaubertoplayer.WIDGET_PRO_SHUFFLE"
        const val ACTION_PRO_REPEAT = "com.example.miaubertoplayer.WIDGET_PRO_REPEAT"
        const val ACTION_PRO_SLEEP = "com.example.miaubertoplayer.WIDGET_PRO_SLEEP"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.miauberto_widget_pro_layout)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpenApp = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Tocar el título o la carita de Miauberto abre la app
            views.setOnClickPendingIntent(R.id.widget_title, pendingOpenApp)
            views.setOnClickPendingIntent(R.id.widget_cat_emoji, pendingOpenApp)

            // Todos los botones de control ejecutan acciones en segundo plano directamente en el servicio
            views.setOnClickPendingIntent(R.id.btn_widget_play, createPendingIntent(context, ACTION_PRO_PLAY_PAUSE, 10))
            views.setOnClickPendingIntent(R.id.btn_widget_prev, createPendingIntent(context, ACTION_PRO_PREV, 11))
            views.setOnClickPendingIntent(R.id.btn_widget_next, createPendingIntent(context, ACTION_PRO_NEXT, 12))
            views.setOnClickPendingIntent(R.id.btn_widget_shuffle, createPendingIntent(context, ACTION_PRO_SHUFFLE, 13))
            views.setOnClickPendingIntent(R.id.btn_widget_repeat, createPendingIntent(context, ACTION_PRO_REPEAT, 14))
            views.setOnClickPendingIntent(R.id.btn_widget_sleep, createPendingIntent(context, ACTION_PRO_SLEEP, 15))

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
