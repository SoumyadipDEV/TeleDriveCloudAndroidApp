package com.example.ui

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.text.DecimalFormat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.VFile
import kotlinx.coroutines.delay

@Composable
fun FilePreviewDialog(
    file: VFile,
    streamingUrl: String?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = getFileIcon(file),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatSize(file.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Media Preview Frame
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val url = streamingUrl ?: ""
                    
                    when {
                        file.mimeType?.startsWith("image") == true -> {
                            ImagePreview(url = url)
                        }
                        file.mimeType?.startsWith("video") == true -> {
                            VideoPreview(url = url)
                        }
                        file.mimeType?.startsWith("audio") == true -> {
                            AudioPreview(url = url, title = file.name)
                        }
                        else -> {
                            DocumentPreview(file = file)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { /* Action: Download */ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download")
                    }
                    OutlinedButton(
                        onClick = { /* Action: Share Link */ },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Link")
                    }
                }
            }
        }
    }
}

@Composable
fun ImagePreview(url: String) {
    if (url.isNotBlank()) {
        AsyncImage(
            model = url,
            contentDescription = "Image Preview",
            modifier = Modifier.fillMaxSize()
        )
    } else {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
fun VideoPreview(url: String) {
    val context = LocalContext.current
    if (url.isNotBlank()) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse(url))
                    val controller = MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setOnPreparedListener { start() }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Streaming from Telegram...", color = Color.LightGray)
        }
    }
}

@Composable
fun AudioPreview(url: String, title: String) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    
    // Simulate audio playback visually
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                delay(100)
                currentProgress = (currentProgress + 0.01f).coerceAtMost(1f)
                if (currentProgress >= 1f) {
                    isPlaying = false
                }
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp).background(Color.DarkGray, CircleShape).padding(16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        Slider(
            value = currentProgress,
            onValueChange = { currentProgress = it },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        IconButton(
            onClick = { isPlaying = !isPlaying },
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun DocumentPreview(file: VFile) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Mock a gorgeous doc reader
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF1967D2))
            Text(
                text = "DOCUMENT PREVIEWER",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray)
        
        Text(
            text = """
                TELEDRIVE USER QUICK START GUIDE
                -------------------------------------------------
                
                Welcome to your ultimate decentralized cloud storage companion! TeleDrive safely anchors your folders, photos, videos, and documents directly onto Telegram's massive, high-speed storage cloud.
                
                Core Features:
                - Zero Storage Limits: Backed by Telegram's free/premium hosting limits.
                - Dynamic Chunking: Files larger than 15MB are instantly split, uploaded concurrently, and stitched during streaming/download.
                - Native Streamer: Play heavy MP4/MKV movies inline immediately without waiting for standard buffer delays.
                - Virtual File System (VFS): Seamlessly nested directories, custom rename, move, search, and bookmarks.
                
                To start, tap the '+ New' FAB on the bottom right corner, click 'Upload File', and select any asset from your device storage. It will be pushed securely to the cloud.
                
                Settings config:
                To manage raw cloud paths, set up your custom Telegram Bot Token in your settings profile dashboard.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.DarkGray,
            fontFamily = FontFamily.Monospace,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3
        )
    }
}

fun getFileIcon(file: VFile): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        file.isFolder -> Icons.Default.Folder
        file.mimeType?.startsWith("image") == true -> Icons.Default.Image
        file.mimeType?.startsWith("video") == true -> Icons.Default.Movie
        file.mimeType?.startsWith("audio") == true -> Icons.Default.MusicNote
        file.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
        else -> Icons.Default.Description
    }
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
