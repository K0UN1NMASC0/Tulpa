package me.rerere.rikkahub.ui.pages.voice

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Download04
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Stop
import me.rerere.rikkahub.data.voice.VoiceCacheManager
import me.rerere.rikkahub.data.voice.VoiceFilePlayer
import me.rerere.rikkahub.data.voice.VoiceRecord
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VoiceArchivePage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val voiceCacheManager = remember { VoiceCacheManager(context) }

    var records by remember { mutableStateOf(voiceCacheManager.getAllRecords()) }
    var totalSize by remember { mutableStateOf(voiceCacheManager.getTotalCacheSize()) }
    var onlyFavorites by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var playingHash by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            VoiceFilePlayer.stop()
        }
    }

    val displayedRecords = remember(records, onlyFavorites) {
        if (onlyFavorites) records.filter { it.isFavorite } else records
    }

    fun refresh() {
        records = voiceCacheManager.getAllRecords()
        totalSize = voiceCacheManager.getTotalCacheSize()
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Koun语音") },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    if (records.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(HugeIcons.Delete01, contentDescription = "清理缓存")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CustomColors.cardColorsOnSurfaceContainer.containerColor,
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
                            text = "已缓存的语音",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "共 ${records.size} 条 · 收藏 ${records.count { it.isFavorite }} 条 · ${formatFileSize(totalSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !onlyFavorites,
                            onClick = { onlyFavorites = false },
                            label = { Text("全部") }
                        )
                        FilterChip(
                            selected = onlyFavorites,
                            onClick = { onlyFavorites = true },
                            label = { Text("收藏") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (displayedRecords.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (onlyFavorites) "还没有收藏的语音哦" else "还没有缓存的语音哦",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(displayedRecords, key = { it.hash }) { record ->
                        VoiceRecordCard(
                            record = record,
                            isPlaying = playingHash == record.hash,
                            onPlay = {
                                val file = voiceCacheManager.getAudioFile(record.hash)
                                if (file.exists()) {
                                    VoiceFilePlayer.playOrStop(context, record.hash, file) { hash, playing ->
                                        playingHash = if (playing) hash else null
                                    }
                                } else {
                                    Toast.makeText(context, "音频文件不存在", Toast.LENGTH_SHORT).show()
                                    refresh()
                                }
                            },
                            onToggleFavorite = {
                                voiceCacheManager.toggleFavoriteByHash(record.hash)
                                refresh()
                            },
                            onSave = {
                                scope.launch {
                                    val file = voiceCacheManager.getAudioFile(record.hash)
                                    val success = voiceCacheManager.saveFileToDownloads(file)
                                    Toast.makeText(
                                        context,
                                        if (success) "已保存到 Download/Koun语音" else "保存失败",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onDelete = {
                                if (playingHash == record.hash) {
                                    VoiceFilePlayer.stop()
                                    playingHash = null
                                }
                                voiceCacheManager.deleteRecord(record.hash)
                                refresh()
                            },
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清理语音缓存") },
            text = { Text("要清空未收藏的缓存语音，还是清空全部语音？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        VoiceFilePlayer.stop()
                        playingHash = null
                        voiceCacheManager.clearAllCache(keepFavorites = true)
                        refresh()
                        showClearDialog = false
                        Toast.makeText(context, "已清理未收藏的缓存", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("仅清理未收藏")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            VoiceFilePlayer.stop()
                            playingHash = null
                            voiceCacheManager.clearAllCache(keepFavorites = false)
                            refresh()
                            showClearDialog = false
                            Toast.makeText(context, "已清空全部缓存", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("全部清空", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("取消")
                    }
                }
            },
        )
    }
}

@Composable
private fun VoiceRecordCard(
    record: VoiceRecord,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val dateStr = remember(record.timestamp) { dateFormat.format(Date(record.timestamp)) }
    val sizeStr = remember(record.fileSize) { formatFileSize(record.fileSize) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onPlay() },
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) HugeIcons.Stop else HugeIcons.Play,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$dateStr · $sizeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = if (record.isFavorite) "已收藏" else "收藏",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (record.isFavorite) FontWeight.Bold else FontWeight.Normal,
                color = if (record.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleFavorite() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )

            Text(
                text = "保存",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSave() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Delete01,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> String.format(Locale.getDefault(), "%.1fMB", bytes / (1024.0 * 1024.0))
    }
}
