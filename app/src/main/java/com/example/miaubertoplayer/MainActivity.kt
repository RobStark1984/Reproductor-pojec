package com.example.miaubertoplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadataRetriever
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class AudioTrackInfo(
    val uri: Uri,
    val title: String,
    val artist: String
)

data class LrcLine(val timeMs: Long, val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiaubertoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
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
    val sharedPrefs = remember { context.getSharedPreferences("MiaubertoPrefsPlaylists", Context.MODE_PRIVATE) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var savedPlaylistsMap by remember { mutableStateOf(loadPlaylistsFromPrefs(context, sharedPrefs)) }
    var currentPlaylistName by remember { mutableStateOf("Lista Principal") }
    var playlist by remember { mutableStateOf(savedPlaylistsMap[currentPlaylistName] ?: emptyList()) }

    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistNameInput by remember { mutableStateOf("") }
    var showSelectPlaylistMenu by remember { mutableStateOf(false) }

    var currentIndex by remember { mutableIntStateOf(-1) }

    var lrcLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    var currentLrcIndex by remember { mutableIntStateOf(-1) }

    var isPlaying by remember { mutableStateOf(false) }
    var isCarMode by remember { mutableStateOf(false) }
    var isShuffleMode by remember { mutableStateOf(false) }
    var repeatModeState by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }

    var miaubertoEmoji by remember { mutableStateOf("😴") }
    var miaubertoStatusText by remember { mutableStateOf("Miauberto está descansando...") }
    var clickCountBySpam by remember { mutableIntStateOf(0) }
    var gestureOverlayText by remember { mutableStateOf("") }

    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    
    // Configuración del Temporizador de Apagado (15m, 30m, 60m)
    var selectedTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepTimerText by remember { mutableStateOf("⏱️ Off") }
    var timerObj by remember { mutableStateOf<CountDownTimer?>(null) }

    var mediaController by remember { mutableStateOf<MediaController?>(null) }

    LaunchedEffect(isPlaying, lrcLines) {
        while (isPlaying && lrcLines.isNotEmpty()) {
            mediaController?.let { controller ->
                val pos = controller.currentPosition
                val activeIndex = lrcLines.indexOfLast { it.timeMs <= pos }
                if (activeIndex != currentLrcIndex && activeIndex >= 0) {
                    currentLrcIndex = activeIndex
                }
            }
            delay(250)
        }
    }

    DisposableEffect(context) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                controller.addListener(object : Player.Listener {
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

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        super.onMediaItemTransition(mediaItem, reason)
                        val index = controller.currentMediaItemIndex
                        if (index >= 0) {
                            currentIndex = index
                        }
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())

        onDispose {
            MediaController.releaseFuture(controllerFuture)
            timerObj?.cancel()
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
        selectedTimerMinutes = minutes
        
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
                mediaController?.pause()
                selectedTimerMinutes = 0
                sleepTimerText = "⏱️ Off"
                miaubertoEmoji = "😴💤"
                miaubertoStatusText = "Temporizador finalizado. Miauberto se fue a dormir."
            }
        }.start()
    }

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

            val newTracks = uris.map { extractAudioMetadata(context, it) }
            val updatedList = playlist + newTracks
            playlist = updatedList
            val updatedMap = savedPlaylistsMap.toMutableMap()
            updatedMap[currentPlaylistName] = updatedList
            savedPlaylistsMap = updatedMap
            savePlaylistsToPrefs(sharedPrefs, updatedMap)

            currentIndex = 0
            mediaController?.let { setFullPlaylistAndPlay(it, updatedList, 0) }
            clickCountBySpam = 0
            miaubertoStatusText = "¡Canciones agregadas a $currentPlaylistName!"
        }
    }

    val lrcPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            lrcLines = parseLrcFromUri(context, it)
            currentLrcIndex = -1
            miaubertoStatusText = "🎤 Letra Karaoke cargada"
        }
    }

    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text("Nueva Lista de Reproducción", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newPlaylistNameInput,
                    onValueChange = { newPlaylistNameInput = it },
                    label = { Text("Nombre de la lista") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistNameInput.isNotBlank()) {
                            val updatedMap = savedPlaylistsMap.toMutableMap()
                            if (!updatedMap.containsKey(newPlaylistNameInput)) {
                                updatedMap[newPlaylistNameInput] = emptyList()
                                savedPlaylistsMap = updatedMap
                                savePlaylistsToPrefs(sharedPrefs, updatedMap)
                                currentPlaylistName = newPlaylistNameInput
                                playlist = emptyList()
                            }
                            newPlaylistNameInput = ""
                            showNewPlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) { Text("Cancelar", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (isCarMode) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🚗 MODO COCHE", color = Color(0xFF38BDF8), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = { isCarMode = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                ) { Text("❌ Salir", fontSize = 14.sp) }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = miaubertoEmoji, fontSize = 64.sp)
                Spacer(modifier = Modifier.height(12.dp))
                val currentTrack = if (currentIndex in playlist.indices) playlist[currentIndex] else null
                Text(
                    text = currentTrack?.title ?: "Sin archivo",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                if (currentTrack != null) {
                    Text(
                        text = currentTrack.artist,
                        color = Color(0xFF94A3B8),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                Button(
                    onClick = { mediaController?.seekToPreviousMediaItem() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    enabled = playlist.isNotEmpty(),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { Text("⏮", fontSize = 36.sp, color = Color.White) }

                Button(
                    onClick = { mediaController?.let { if (it.isPlaying) it.pause() else it.play() } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                    modifier = Modifier.weight(1.2f).fillMaxHeight()
                ) { Text(if (isPlaying) "⏸" else "▶", fontSize = 42.sp, color = Color.White) }

                Button(
                    onClick = { mediaController?.seekToNextMediaItem() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    enabled = playlist.isNotEmpty(),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { Text("⏭", fontSize = 36.sp, color = Color.White) }
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
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFF020617), shape = RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                mediaController?.let { controller ->
                                    val width = size.width
                                    if (offset.x < width / 2) {
                                        controller.seekTo((controller.currentPosition - 10000).coerceAtLeast(0))
                                        gestureOverlayText = "⏪ -10s"
                                    } else {
                                        controller.seekTo((controller.currentPosition + 10000).coerceAtMost(controller.duration))
                                        gestureOverlayText = "⏩ +10s"
                                    }
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
                            player = mediaController
                            useController = true
                        }
                    },
                    update = { view -> view.player = mediaController },
                    modifier = Modifier.fillMaxSize()
                )

                if (gestureOverlayText.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            text = gestureOverlayText,
                            color = Color(0xFF38BDF8),
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

            // FILA DE CONTROLES
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = {
                            isShuffleMode = !isShuffleMode
                            mediaController?.shuffleModeEnabled = isShuffleMode
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isShuffleMode) Color(0xFF0EA5E9) else Color(0xFF334155)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("🔀", fontSize = 12.sp, color = Color.White) }

                    Button(
                        onClick = {
                            repeatModeState = when (repeatModeState) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            mediaController?.repeatMode = repeatModeState
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (repeatModeState != Player.REPEAT_MODE_OFF) Color(0xFF0EA5E9) else Color(0xFF334155)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = when (repeatModeState) {
                                Player.REPEAT_MODE_ALL -> "🔁 Todo"
                                Player.REPEAT_MODE_ONE -> "🔂 Una"
                                else -> "🔁 Off"
                            },
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = {
                            currentSpeed = when (currentSpeed) {
                                0.5f -> 1.0f
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                else -> 0.5f
                            }
                            mediaController?.playbackParameters = PlaybackParameters(currentSpeed)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("${currentSpeed}x", fontSize = 11.sp, color = Color.White) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { isCarMode = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("🚗", fontSize = 11.sp, color = Color.White) }

                    Button(
                        onClick = { lrcPickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("🎤", fontSize = 11.sp, color = Color.White) }

                    // BOTÓN CORREGIDO: Rotación cíclica de Sleep Timer (0m -> 15m -> 30m -> 60m -> 0m)
                    Button(
                        onClick = {
                            val nextMins = when (selectedTimerMinutes) {
                                0 -> 15
                                15 -> 30
                                30 -> 60
                                else -> 0
                            }
                            startSleepTimer(nextMins)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTimerMinutes > 0) Color(0xFF0EA5E9) else Color(0xFF334155)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text(sleepTimerText, fontSize = 11.sp, color = Color.White) }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { showSelectPlaylistMenu = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📋 $currentPlaylistName ▾", fontSize = 12.sp, color = Color.White, maxLines = 1)
                    }

                    DropdownMenu(
                        expanded = showSelectPlaylistMenu,
                        onDismissRequest = { showSelectPlaylistMenu = false }
                    ) {
                        savedPlaylistsMap.keys.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    currentPlaylistName = name
                                    playlist = savedPlaylistsMap[name] ?: emptyList()
                                    showSelectPlaylistMenu = false
                                    if (playlist.isNotEmpty()) {
                                        mediaController?.let { setFullPlaylistAndPlay(it, playlist, 0) }
                                    }
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = { showNewPlaylistDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) { Text("➕ Nueva Lista", fontSize = 12.sp, color = Color.White) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("audio/*", "video/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                    modifier = Modifier.weight(1f)
                ) { Text("📁 + Añadir Audio", color = Color.White) }

                Button(
                    onClick = {
                        mediaController?.seekToPreviousMediaItem()
                        triggerSpamReaction()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    enabled = playlist.isNotEmpty()
                ) { Text("⏮", color = Color.White) }

                Button(
                    onClick = {
                        mediaController?.seekToNextMediaItem()
                        triggerSpamReaction()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    enabled = playlist.isNotEmpty()
                ) { Text("⏭", color = Color.White) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                if (lrcLines.isNotEmpty()) {
                    val listState = rememberLazyListState()
                    LaunchedEffect(currentLrcIndex) {
                        if (currentLrcIndex >= 0) {
                            listState.animateScrollToItem(currentLrcIndex)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(lrcLines) { index, line ->
                            val isActive = index == currentLrcIndex
                            Text(
                                text = line.text,
                                color = if (isActive) Color(0xFF38BDF8) else Color(0xFF64748B),
                                fontSize = if (isActive) 14.sp else 12.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "🎤 Toca '.LRC' para cargar la letra sincronizada.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    itemsIndexed(playlist) { index, track ->
                        val isSelected = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) Color(0xFF334155) else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = if (isSelected) Color(0xFF38BDF8) else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = track.artist,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            Button(
                                onClick = {
                                    currentIndex = index
                                    mediaController?.let { setFullPlaylistAndPlay(it, playlist, index) }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("▶", color = if (isSelected) Color(0xFF38BDF8) else Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Hecho por: Miauberto",
                color = Color(0xFF38BDF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun extractAudioMetadata(context: Context, uri: Uri): AudioTrackInfo {
    var title = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Pista sin título"
    var artist = "Artista desconocido"

    try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val extractedTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val extractedArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)

        if (!extractedTitle.isNullOrBlank()) title = extractedTitle
        if (!extractedArtist.isNullOrBlank()) artist = extractedArtist
        mmr.release()
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return AudioTrackInfo(uri, title, artist)
}

private fun parseLrcFromUri(context: Context, uri: Uri): List<LrcLine> {
    val list = mutableListOf<LrcLine>()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val regex = "\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)".toRegex()
                    val match = regex.find(line)
                    if (match != null) {
                        val min = match.groupValues[1].toLong()
                        val sec = match.groupValues[2].toLong()
                        val millisStr = match.groupValues[3]
                        val ms = if (millisStr.length == 2) millisStr.toLong() * 10 else millisStr.toLong()
                        val totalMs = (min * 60 * 1000) + (sec * 1000) + ms
                        val text = match.groupValues[4].trim()
                        if (text.isNotEmpty()) {
                            list.add(LrcLine(totalMs, text))
                        }
                    }
                    line = reader.readLine()
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list.sortedBy { it.timeMs }
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
            .background(Color(0xFF020617), shape = RoundedCornerShape(6.dp))
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
                    .background(Color(0xFF0EA5E9), shape = RoundedCornerShape(2.dp))
            )
        }
    }
}

private fun setFullPlaylistAndPlay(controller: MediaController, tracks: List<AudioTrackInfo>, startIndex: Int) {
    if (tracks.isEmpty()) return
    val mediaItems = tracks.map { track ->
        MediaItem.Builder()
            .setUri(track.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .build()
            )
            .build()
    }

    controller.setMediaItems(mediaItems, startIndex, 0L)
    controller.prepare()
    controller.play()
}

private fun savePlaylistsToPrefs(prefs: android.content.SharedPreferences, map: Map<String, List<AudioTrackInfo>>) {
    val jsonObject = JSONObject()
    map.forEach { (name, tracks) ->
        val jsonArray = JSONArray()
        tracks.forEach { track ->
            val item = JSONObject()
            item.put("uri", track.uri.toString())
            item.put("title", track.title)
            item.put("artist", track.artist)
            jsonArray.put(item)
        }
        jsonObject.put(name, jsonArray)
    }
    prefs.edit().putString("all_playlists_metadata_json", jsonObject.toString()).apply()
}

private fun loadPlaylistsFromPrefs(context: Context, prefs: android.content.SharedPreferences): Map<String, List<AudioTrackInfo>> {
    val jsonString = prefs.getString("all_playlists_metadata_json", null) ?: return mapOf("Lista Principal" to emptyList())
    val map = mutableMapOf<String, List<AudioTrackInfo>>()
    try {
        val jsonObject = JSONObject(jsonString)
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val array = jsonObject.getJSONArray(key)
            val list = mutableListOf<AudioTrackInfo>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val uri = Uri.parse(item.getString("uri"))
                val title = item.optString("title", "Pista")
                val artist = item.optString("artist", "Artista desconocido")
                list.add(AudioTrackInfo(uri, title, artist))
            }
            map[key] = list
        }
    } catch (e: Exception) {
        map["Lista Principal"] = emptyList()
    }
    return if (map.isEmpty()) mapOf("Lista Principal" to emptyList()) else map
}

@Composable
fun MiaubertoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B)
        ),
        content = content
    )
}
