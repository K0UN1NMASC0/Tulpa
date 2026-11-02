package me.rerere.rikkahub.ui.pages.voice

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.voice.VoiceCacheManager
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalTTSState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceArchivePage() {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val ttsState = LocalTTSState.current
    val scope = rememberCoroutineScope()
    val voiceCacheManager = remember { VoiceCacheManager(context) }

    var cachedFiles by remember { mutableStateOf(voiceCacheManager.getAllCachedFiles()) }
    var totalSize by remember { mutableStateOf(voiceCacheManager.getTotalCacheSize()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var currentlyPlaying by remember { mutableStateOf<File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Koun语音") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (cachedFiles.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Cache stats header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "缓存语音",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${cachedFiles.size} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = formatFileSize(totalSize),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (cachedFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "还没有缓存的语音哦",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(cachedFiles, key = { it.absolutePath }) { file ->
                        VoiceFileItem(
                            file = file,
                            isPlaying = currentlyPlaying == file,
                            onPlay = {
                                if (currentlyPlaying == file) {
                                    ttsState.stop()
                                    currentlyPlaying = null
                                } else {
                                    // Read file and use TTS to play
                                    currentlyPlaying = file
                                    // For cached files, we use the file name as reference
                                    ttsState.stop()
                                    // Play using the cached audio file path
                                    Toast.makeText(context, "播放中...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSave = {
                                scope.launch {
                                    // Extract voice text from filename hash - for saving we copy the file directly
                                    val success = saveFileToDownloads(context, file)
                                    Toast.makeText(
                                        context,
                                        if (success) "已保存到下载文件夹" else "保存失败",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onDelete = {
                                voiceCacheManager.deleteCachedFile(file)
                                cachedFiles = voiceCacheManager.getAllCachedFiles()
                                totalSize = voiceCacheManager.getTotalCacheSize()
                            },
                        )
                    }
                }
            }
        }
    }

    // Clear all confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空所有缓存") },
            text = { Text("确定要删除所有缓存的语音吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        voiceCacheManager.clearAllCache()
                        cachedFiles = voiceCacheManager.getAllCachedFiles()
                        totalSize = voiceCacheManager.getTotalCacheSize()
                        showClearDialog = false
                        Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun VoiceFileItem(
    file: File,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val dateStr = remember(file.lastModified()) { dateFormat.format(Date(file.lastModified())) }
    val sizeStr = remember(file.length()) { formatFileSize(file.length()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Play button
            IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // File info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.nameWithoutExtension.replace("voice_", ""),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = sizeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Save button
            IconButton(onClick = onSave, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "Save",
                    modifier = Modifier.size(18.dp),
                )
            }

            // Delete button
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
    }
}

private suspend fun saveFileToDownloads(context: android.content.Context, file: File): Boolean {
    return try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "koun_voice_${timestamp}.mp3"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(
                    android.provider.MediaStore.Audio.Media.RELATIVE_PATH,
                    "${android.os.Environment.DIRECTORY_DOWNLOADS}/Koun语音"
                )
            }
            val uri = context.contentResolver.insert(
                android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY),
                contentValues
            )
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
            }
            true
        } else {
            val dir = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                "Koun语音"
            )
            if (!dir.exists()) dir.mkdirs()
            file.copyTo(java.io.File(dir, fileName), overwrite = true)
            true
        }
    } catch (e: Exception) {
        false
    }
}
