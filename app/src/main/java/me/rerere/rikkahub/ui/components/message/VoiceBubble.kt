package me.rerere.rikkahub.ui.components.message

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Stop
import me.rerere.rikkahub.data.voice.VoiceCacheManager
import me.rerere.rikkahub.data.voice.VoiceFilePlayer
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.VoiceBlockUtils
import me.rerere.tts.provider.TTSManager
import org.koin.compose.koinInject

/**
 * A voice playback bubble rendered below the main chat bubble.
 * Uses the same bubble shape/color/border/textColor as the AI chat bubble.
 */
@Composable
fun VoiceBubble(
    voiceText: String,
    voiceCacheManager: VoiceCacheManager,
    modifier: Modifier = Modifier,
    bubbleShape: RoundedCornerShape = RoundedCornerShape(16.dp),
    bubbleColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    bubbleBorder: BorderStroke? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val ttsManager = koinInject<TTSManager>()
    val scope = rememberCoroutineScope()

    val hash = remember(voiceText) { VoiceBlockUtils.contentHash(voiceText) }
    var isPlaying by remember(hash) { mutableStateOf(false) }
    var isLoading by remember(hash) { mutableStateOf(false) }
    var isFavorite by remember(hash) { mutableStateOf(voiceCacheManager.isFavorite(voiceText)) }

    // Strip audio tags for display, keep them for TTS synthesis
    val displayText = remember(voiceText) {
        voiceText
            .replace(Regex("""\[.*?]"""), "")
            .replace("\n", " ")
            .trim()
            .ifEmpty { voiceText.trim() }
    }

    val handlePlay = {
        if (isLoading) {
            // Do nothing while synthesizing
        } else if (isPlaying) {
            VoiceFilePlayer.stop()
            isPlaying = false
        } else {
            scope.launch {
                isLoading = true
                try {
                    val audioFile = voiceCacheManager.getOrSynthesizeAudio(
                        voiceText = voiceText,
                        settings = settings,
                        ttsManager = ttsManager,
                    )
                    isLoading = false
                    VoiceFilePlayer.playOrStop(context, hash, audioFile) { playingHash, playing ->
                        isPlaying = (playingHash == hash && playing)
                    }
                } catch (e: Exception) {
                    isLoading = false
                    Toast.makeText(
                        context,
                        "语音生成失败: ${e.message ?: "请检查TTS设置"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .padding(top = 6.dp)
            .clip(bubbleShape)
            .clickable { handlePlay() }
            .animateContentSize(),
        shape = bubbleShape,
        color = bubbleColor,
        border = bubbleBorder,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Voice text display - full text, uses AI text style for color consistency
            Text(
                text = displayText,
                style = textStyle,
                color = textColor,
            )

            // Controls row
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Play / Stop button
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = textColor,
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) HugeIcons.Stop else HugeIcons.Play,
                        contentDescription = if (isPlaying) "停止" else "播放",
                        tint = textColor,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { handlePlay() },
                    )
                }

                Text(
                    text = if (isPlaying) "播放中…" else "点击播放",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = if (isFavorite) "已收藏" else "收藏",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isFavorite) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            scope.launch {
                                if (voiceCacheManager.getCachedFile(voiceText) == null) {
                                    runCatching {
                                        voiceCacheManager.getOrSynthesizeAudio(voiceText, settings, ttsManager)
                                    }
                                }
                                isFavorite = voiceCacheManager.toggleFavorite(voiceText)
                                Toast.makeText(
                                    context,
                                    if (isFavorite) "已加入语音收藏♡" else "已取消收藏",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )

                Text(
                    text = "保存",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            scope.launch {
                                val success = voiceCacheManager.saveToDownloads(
                                    voiceText = voiceText,
                                    settings = settings,
                                    ttsManager = ttsManager,
                                )
                                Toast.makeText(
                                    context,
                                    if (success) "已保存到 Download/Koun语音" else "保存失败，请先播放或检查TTS配置",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
