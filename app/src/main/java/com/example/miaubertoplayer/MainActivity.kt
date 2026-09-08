package com.example.miaubertoplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiaubertoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    MiaubertoPlayerScreen(activity = this)
                }
            }
        }
    }
}

@Composable
fun MiaubertoPlayerScreen(activity: ComponentActivity) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val sharedPrefs = remember { context.getSharedPreferences("MiaubertoPrefs", Context.MODE_PRIVATE) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Cargar playlist guardada previamente en SharedPreferences
    var playlist by remember {
        mutableStateOf<List<Uri>>(
            sharedPrefs.getStringSet("saved_playlist", emptySet())
                ?.map { Uri.parse(it) } ?: emptyList()
        )
    }

    var currentIndex by remember { mutableIntStateOf(-1) }
    var lyricsText by remember {
        mutableStateOf(
            if (playlist.isNotEmpty()) "Se cargaron ${playlist.size} archivos guardados."
            else "Selecciona archivos multimedia para empezar."
        )
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isCarMode by remember { mutableStateOf(false) }
    var miaubertoEmoji by remember { mutableStateOf("😴") }
    var miaubertoStatusText by remember {
        mutableStateOf(
            if (playlist.isNotEmpty()) "Miauberto recordó tu lista anterior."
            else "Miauberto está descansando..."
        )
    }
    var clickCountBySpam by remember { mutableIntStateOf(0) }
    var gestureOverlayText by remember { mutableStateOf("") }

    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var sleepTimerText by remember { mutableStateOf("⏱️ Off") }
    var timerObj by remember { mutableStateOf<CountDownTimer?>(null) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (playing) {
                        miaubertoEmoji = if (isCarMode) "🚗😼" else "🕶️😼"
                        miaubertoStatusText = if (isCarMode) "Modo Coche Activo" else "Miauberto está disfrutando la música."
                    } else {
                        miaubertoEmoji = "😴"
                        miaubertoStatusText = "En pausa. Miauberto se durmió."
                    }
                }
            })
        }
    }

    fun triggerSpamReaction() {
        clickCountBySpam++
        if (clickCountBySpam >= 4) {
            miaubertoEmoji = "😾💢"
            miaubertoStatusText = "¡Oye! Deja de cambiar la canción tan rápido."
        }
    }

    fun startSleepTimer(minutes: Int) {
        timerObj?.cancel()
        if (minutes == 0) {
            sleepTimerText = "⏱️ Off"
            return
        }
        val millis = minutes * 60 * 1000L
        timerObj = object : CountDownTimer(millis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minsLeft = millisUntilFinished / 1000 / 60
                val secsLeft = (millisUntilFinished / 1000) % 60
                sleepTimerText = String.format("⏱️ %02d:%02d", minsLeft, secsLeft)
            }

            override fun onFinish() {
                exoPlayer.pause()
                sleepTimerText = "⏱️ Off"
                miaubertoEmoji = "😴💤"
                miaubertoStatusText = "Temporizador finalizado. Miauberto se fue a dormir."
            }
        }.start()
    }

    // Selector de archivos con retención de permisos (Persistable URI Permission)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            playlist = uris
            currentIndex = 0
            playMedia(context, exoPlayer, playlist[0])
            lyricsText = "Cargado: ${playlist[0].lastPathSegment ?: "Archivo multimedia"}"
            clickCountBySpam = 0

            // Guardar lista en SharedPreferences
            val uriStrings = uris.map { it.toString() }.toSet()
            sharedPrefs.edit().putStringSet("saved_playlist", uriStrings).apply()
            miaubertoStatusText = "¡Lista de reproducción guardada!"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            timerObj?.cancel()
            exoPlayer.release()
        }
    }

    if (isCarMode) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🚗 MODO COCHE", color = Color(0xFFE50914), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = { isCarMode = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) { Text("❌ Salir", fontSize = 14.sp) }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = miaubertoEmoji, fontSize = 64.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (currentIndex in playlist.indices) playlist[currentIndex].lastPathSegment ?: "Reproduciendo" else "Sin archivo",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                Button(
                    onClick = {
                        if (playlist.isNotEmpty() && currentIndex > 0) {
                            currentIndex--
                            playMedia(context, exoPlayer, playlist[currentIndex])
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                    enabled = currentIndex > 0,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) { Text("⏮", fontSize = 36.sp) }

                Button(
                    onClick = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                ) { Text(if (isPlaying) "⏸" else "▶", fontSize = 42.sp) }

                Button(
                    onClick = {
                        if (playlist.isNotEmpty() && currentIndex < playlist.size - 1) {
                            currentIndex++
                            playMedia(context, exoPlayer, playlist[currentIndex])
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                    enabled = playlist.isNotEmpty() && currentIndex < playlist.size - 1,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) { Text("⏭", fontSize = 36.sp) }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(text = miaubertoEmoji, fontSize = 32.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MIAUBERTO PLAYER",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = miaubertoStatusText,
                color = Color(0xFFFF8A80),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color.Black, shape = RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val width = size.width
                                if (offset.x < width / 2) {
                                    exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                    gestureOverlayText = "⏪ -10s"
                                } else {
                                    exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                                    gestureOverlayText = "⏩ +10s"
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { gestureOverlayText = "" }
                        ) { change, dragAmount ->
                            change.consume()
                            val width = size.width
                            val isLeftSide = change.position.x < width / 2

                            if (isLeftSide) {
                                val layoutParams = activity.window.attributes
                                var currentBrightness = if (layoutParams.screenBrightness < 0) 0.5f else layoutParams.screenBrightness
                                currentBrightness = (currentBrightness - (dragAmount.y / 1000f)).coerceIn(0.1f, 1.0f)
                                layoutParams.screenBrightness = currentBrightness
                                activity.window.attributes = layoutParams
                                gestureOverlayText = "☀️ Brillo: ${(currentBrightness * 100).toInt()}%"
                            } else {
                                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val delta = if (dragAmount.y < 0) 1 else -1
                                val newVolume = (currentVolume + delta).coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                gestureOverlayText = "🔊 Vol: ${(newVolume * 100 / maxVolume)}%"
                            }
                        }
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (gestureOverlayText.isNotEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            text = gestureOverlayText,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            AudioVisualizerBars(isPlaying = isPlaying)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = {
                            currentSpeed = when (currentSpeed) {
                                0.5f -> 1.0f
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                else -> 0.5f
                            }
                            exoPlayer.playbackParameters = PlaybackParameters(currentSpeed)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("🚀 ${currentSpeed}x", fontSize = 11.sp) }

                    Button(
                        onClick = { isCarMode = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("🚗 Coche", fontSize = 11.sp) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { startSleepTimer(15) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("15m", fontSize = 11.sp) }

                    Button(
                        onClick = { startSleepTimer(30) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("30m", fontSize = 11.sp) }

                    Button(
                        onClick = { startSleepTimer(0) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text(sleepTimerText, fontSize = 11.sp) }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("audio/*", "video/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                    modifier = Modifier.weight(1f)
                ) { Text("📁 Abrir") }

                Button(
                    onClick = {
                        if (playlist.isNotEmpty() && currentIndex > 0) {
                            currentIndex--
                            playMedia(context, exoPlayer, playlist[currentIndex])
                            triggerSpamReaction()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                    enabled = currentIndex > 0
                ) { Text("⏮") }

                Button(
                    onClick = {
                        if (playlist.isNotEmpty() && currentIndex < playlist.size - 1) {
                            currentIndex++
                            playMedia(context, exoPlayer, playlist[currentIndex])
                            triggerSpamReaction()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                    enabled = playlist.isNotEmpty() && currentIndex < playlist.size - 1
                ) { Text("⏭") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "🎤 Info: $lyricsText",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    itemsIndexed(playlist) { index, uri ->
                        val isSelected = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) Color(0xFF333333) else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Text(
                                text = uri.lastPathSegment ?: "Archivo ${index + 1}",
                                color = if (isSelected) Color(0xFFE50914) else Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    currentIndex = index
                                    playMedia(context, exoPlayer, playlist[index])
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("▶", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Hecho por: Miauberto",
                color = Color(0xFFE50914),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AudioVisualizerBars(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        val durations = listOf(400, 600, 350, 500, 700, 450, 550, 380, 620, 480)
        durations.forEachIndexed { index, duration ->
            val heightMultiplier by if (isPlaying) {
                infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = duration, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar_$index"
                )
            } else {
                remember { mutableFloatStateOf(0.1f) }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightMultiplier)
                    .background(Color(0xFFE50914), shape = RoundedCornerShape(2.dp))
            )
        }
    }
}

private fun playMedia(context: Context, exoPlayer: ExoPlayer, uri: Uri) {
    val mediaItem = MediaItem.fromUri(uri)
    exoPlayer.setMediaItem(mediaItem)
    exoPlayer.prepare()
    exoPlayer.play()

    val intent = Intent(context, PlaybackService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

@Composable
fun MiaubertoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        ),
        content = content
    )
}
