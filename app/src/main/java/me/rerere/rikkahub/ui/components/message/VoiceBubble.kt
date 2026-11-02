package me.rerere.rikkahub.ui.components.message

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.voice.VoiceCacheManager
import me.rerere.rikkahub.ui.context.LocalTTSState

/**
 * A compact voice playback bubble rendered below the main chat bubble.
 * Shows the voice text, a play button, and a save-to-downloads button.
 */
@Composable
fun VoiceBubble(
    voiceText: String,
    voiceCacheManager: VoiceCacheManager,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ttsState = LocalTTSState.current
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }

    // Strip audio tags for display, keep them for TTS
    val displayText = voiceText
        .replace(Regex("""\[.*?]"""), "")
        .trim()
        .take(60)
        .let { if (it.length >= 60) "$it…" else it }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Play / Stop button
            IconButton(
                onClick = {
                    if (isPlaying) {
                        ttsState.stop()
                        isPlaying = false
                    } else {
                        ttsState.speak(voiceText)
                        isPlaying = true
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            // Voice text preview
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Save to Downloads button
            IconButton(
                onClick = {
                    scope.launch {
                        val success = voiceCacheManager.saveToDownloads(context, voiceText)
                        Toast.makeText(
                            context,
                            if (success) "已保存到下载文件夹" else "保存失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Save",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
