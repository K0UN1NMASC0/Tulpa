package me.rerere.rikkahub.ui.components.message

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * A compact pill-shaped voice playback bubble rendered below the main chat bubble.
 * Matches user's custom design: [Play] (显示语音的文本)    收藏  保存
 */
@Composable
fun VoiceBubble(
    voiceText: String,
    voiceCacheManager: VoiceCacheManager,
    modifier: Modifier = Modifier,
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
            .clip(RoundedCornerShape(50))
            .clickable { handlePlay() }
            .animateContentSize(),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = settings.displaySetting.bubbleOpacity),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) HugeIcons.Stop else HugeIcons.Play,
                    contentDescription = if (isPlaying) "停止" else "播放",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = if (isFavorite) "已收藏" else "收藏",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isFavorite) FontWeight.Bold else FontWeight.Normal,
                color = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        scope.launch {
                            // If not yet synthesized, synthesize first so it's in the archive
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
