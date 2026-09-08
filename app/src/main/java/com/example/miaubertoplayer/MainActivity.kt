package com.example.miaubertoplayer

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var playlist by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var lyricsText by remember { mutableStateOf("Selecciona archivos multimedia para empezar.") }

    var isPlaying by remember { mutableStateOf(false) }
    var miaubertoEmoji by remember { mutableStateOf("😴") }
    var miaubertoStatusText by remember { mutableStateOf("Miauberto está descansando...") }
    var clickCountBySpam by remember { mutableIntStateOf(0) }
    var gestureOverlayText by remember { mutableStateOf("") }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (playing) {
                        miaubertoEmoji = "🕶️😼"
                        miaubertoStatusText = "Miauberto está disfrutando la música."
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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            playlist = uris
            currentIndex = 0
            playMedia(exoPlayer, playlist[0])
            lyricsText = "Cargado: ${playlist[0].lastPathSegment ?: "Archivo multimedia"}"
            clickCountBySpam = 0
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 8.dp)
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
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // CONTENEDOR DEL REPRODUCTOR CON DETECCIÓN DE GESTOS
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
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
                            // Control de Brillo (Lado Izquierdo)
                            val layoutParams = activity.window.attributes
                            var currentBrightness = if (layoutParams.screenBrightness < 0) 0.5f else layoutParams.screenBrightness
                            currentBrightness = (currentBrightness - (dragAmount.y / 1000f)).coerceIn(0.1f, 1.0f)
                            layoutParams.screenBrightness = currentBrightness
                            activity.window.attributes = layoutParams
                            gestureOverlayText = "☀️ Brillo: ${(currentBrightness * 100).toInt()}%"
                        } else {
                            // Control de Volumen (Lado Derecho)
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

            // Indicador flotante en pantalla para gestos
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

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                modifier = Modifier.weight(1f)
            ) {
                Text("📁 Abrir")
            }
            Button(
                onClick = {
                    if (playlist.isNotEmpty() && currentIndex > 0) {
                        currentIndex--
                        playMedia(exoPlayer, playlist[currentIndex])
                        triggerSpamReaction()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                enabled = currentIndex > 0
            ) {
                Text("⏮")
            }
            Button(
                onClick = {
                    if (playlist.isNotEmpty() && currentIndex < playlist.size - 1) {
                        currentIndex++
                        playMedia(exoPlayer, playlist[currentIndex])
                        triggerSpamReaction()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                enabled = playlist.isNotEmpty() && currentIndex < playlist.size - 1
            ) {
                Text("⏭")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "🎤 Letra / Info",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lyricsText,
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uri.lastPathSegment ?: "Archivo ${index + 1}",
                            color = if (isSelected) Color(0xFFE50914) else Color.White,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Hecho por: Miauberto",
            color = Color(0xFFE50914),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun playMedia(exoPlayer: ExoPlayer, uri: Uri) {
    val mediaItem = MediaItem.fromUri(uri)
    exoPlayer.setMediaItem(mediaItem)
    exoPlayer.prepare()
    exoPlayer.play()
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
