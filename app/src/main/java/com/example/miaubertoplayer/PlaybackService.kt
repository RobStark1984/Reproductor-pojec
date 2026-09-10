package com.example.miaubertoplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private var widgetSleepTimerMinutes = 0
    private var widgetTimerObj: CountDownTimer? = null

    companion object {
        private const val CHANNEL_ID = "miauberto_playback_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                updateWidgetsUI()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) {
                    startForegroundServiceWithNotification()
                }
                updateWidgetsUI()
            }
        })

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {})
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Miauberto Reproducción",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación de control de música de Miauberto Player"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceWithNotification() {
        val p = player ?: return
        val title = p.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Miauberto Player"
        val artist = p.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Reproduciendo música"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val p = player

        if (p != null && action != null) {
            when (action) {
                MiaubertoWidgetReceiver.ACTION_PLAY_PAUSE,
                MiaubertoWidgetProReceiver.ACTION_PRO_PLAY_PAUSE,
                MiaubertoWidgetMiniReceiver.ACTION_MINI_PLAY_PAUSE -> {
                    if (p.isPlaying) p.pause() else p.play()
                }
                MiaubertoWidgetReceiver.ACTION_PREV,
                MiaubertoWidgetProReceiver.ACTION_PRO_PREV,
                MiaubertoWidgetMiniReceiver.ACTION_MINI_REWIND -> {
                    p.seekTo((p.currentPosition - 5000).coerceAtLeast(0))
                }
                MiaubertoWidgetReceiver.ACTION_NEXT,
                MiaubertoWidgetProReceiver.ACTION_PRO_NEXT,
                MiaubertoWidgetMiniReceiver.ACTION_MINI_FFWD -> {
                    p.seekTo((p.currentPosition + 5000).coerceAtMost(p.duration))
                }
                MiaubertoWidgetProReceiver.ACTION_PRO_SHUFFLE -> {
                    p.shuffleModeEnabled = !p.shuffleModeEnabled
                }
                MiaubertoWidgetProReceiver.ACTION_PRO_REPEAT -> {
                    p.repeatMode = when (p.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
                MiaubertoWidgetProReceiver.ACTION_PRO_SLEEP -> {
                    val nextMins = when (widgetSleepTimerMinutes) {
                        0 -> 15
                        15 -> 30
                        30 -> 60
                        else -> 0
                    }
                    setWidgetSleepTimer(nextMins)
                }
            }
            updateWidgetsUI()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun setWidgetSleepTimer(minutes: Int) {
        widgetTimerObj?.cancel()
        widgetSleepTimerMinutes = minutes

        if (minutes == 0) {
            updateWidgetsUI()
            return
        }

        val millis = minutes * 60 * 1000L
        widgetTimerObj = object : CountDownTimer(millis, 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                player?.pause()
                widgetSleepTimerMinutes = 0
                updateWidgetsUI()
            }
        }.start()
    }

    private fun updateWidgetsUI() {
        val p = player ?: return
        val appWidgetManager = AppWidgetManager.getInstance(this)

        val title = p.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Miauberto Player"
        val statusText = buildString {
            append(p.currentMediaItem?.mediaMetadata?.artist?.toString() ?: if (p.isPlaying) "Reproduciendo" else "En pausa")
            if (widgetSleepTimerMinutes > 0) {
                append(" • ⏱️ ${widgetSleepTimerMinutes}m")
            }
            if (p.shuffleModeEnabled) {
                append(" • 🔀")
            }
            when (p.repeatMode) {
                Player.REPEAT_MODE_ALL -> append(" • 🔁")
                Player.REPEAT_MODE_ONE -> append(" • 🔂")
            }
        }

        val playIcon = if (p.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        // 1. Widget Compacto
        val comp1 = ComponentName(this, MiaubertoWidgetReceiver::class.java)
        val ids1 = appWidgetManager.getAppWidgetIds(comp1)
        for (id in ids1) {
            val views = RemoteViews(packageName, R.layout.miauberto_widget_layout)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_status, statusText)
            views.setImageViewResource(R.id.btn_widget_play, playIcon)
            MiaubertoWidgetReceiver.updateWidget(this, appWidgetManager, id)
            appWidgetManager.updateAppWidget(id, views)
        }

        // 2. Widget Pro
        val comp2 = ComponentName(this, MiaubertoWidgetProReceiver::class.java)
        val ids2 = appWidgetManager.getAppWidgetIds(comp2)
        for (id in ids2) {
            val views = RemoteViews(packageName, R.layout.miauberto_widget_pro_layout)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_status, statusText)
            views.setImageViewResource(R.id.btn_widget_play, playIcon)
            MiaubertoWidgetProReceiver.updateWidget(this, appWidgetManager, id)
            appWidgetManager.updateAppWidget(id, views)
        }

        // 3. Widget Mini
        val comp3 = ComponentName(this, MiaubertoWidgetMiniReceiver::class.java)
        val ids3 = appWidgetManager.getAppWidgetIds(comp3)
        for (id in ids3) {
            val views = RemoteViews(packageName, R.layout.miauberto_widget_mini_layout)
            views.setImageViewResource(R.id.btn_widget_mini_play, playIcon)
            MiaubertoWidgetMiniReceiver.updateWidget(this, appWidgetManager, id)
            appWidgetManager.updateAppWidget(id, views)
        }

        // Forzar actualización global enviando un Broadcast al sistema
        val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        sendBroadcast(updateIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        widgetTimerObj?.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
