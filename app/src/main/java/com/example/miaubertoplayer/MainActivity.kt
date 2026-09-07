package com.example.miaubertoplayer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
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
                    MiaubertoPlayerScreen()
                }
            }
        }
    }
}

@Composable
fun MiaubertoPlayerScreen() {
    val context = LocalContext.current
    var playlist by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var lyricsText by remember { mutableStateOf("Selecciona archivos multimedia para empezar.") }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            playlist = uris
            currentIndex = 0
            playMedia(exoPlayer, playlist[0])
            lyricsText = "Cargado: ${playlist[0].lastPathSegment ?: "Archivo multimedia"}"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(text = "😾", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "MIAUBERTO PLAYER",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.Black, shape = RoundedCornerShape(12.dp))
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
                .height(100.dp)
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
