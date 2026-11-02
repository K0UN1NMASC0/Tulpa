package me.rerere.rikkahub.data.voice

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.utils.VoiceBlockUtils
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "VoiceCacheManager"

@Serializable
data class VoiceRecord(
    val hash: String,
    val text: String,
    val displayText: String,
    val timestamp: Long,
    val isFavorite: Boolean = false,
    val fileSize: Long = 0L,
)

class VoiceCacheManager(
    private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val cacheDir: File
        get() {
            val dir = File(context.filesDir, "koun_voice_cache")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val indexFile: File
        get() = File(cacheDir, "voice_index.json")

    private fun loadRecords(): MutableList<VoiceRecord> {
        return try {
            if (indexFile.exists()) {
                json.decodeFromString<List<VoiceRecord>>(indexFile.readText()).toMutableList()
            } else {
                mutableListOf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load voice index", e)
            mutableListOf()
        }
    }

    private fun saveRecords(records: List<VoiceRecord>) {
        try {
            indexFile.writeText(json.encodeToString(records))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save voice index", e)
        }
    }

    fun getAudioFile(hash: String): File {
        return File(cacheDir, "voice_${hash}.mp3")
    }

    fun getCachedFile(voiceText: String): File? {
        val hash = VoiceBlockUtils.contentHash(voiceText)
        val file = getAudioFile(hash)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun isFavorite(voiceText: String): Boolean {
        val hash = VoiceBlockUtils.contentHash(voiceText)
        return loadRecords().firstOrNull { it.hash == hash }?.isFavorite == true
    }

    fun toggleFavorite(voiceText: String): Boolean {
        val hash = VoiceBlockUtils.contentHash(voiceText)
        val display = voiceText.replace(Regex("""\[.*?]"""), "").trim()
        val records = loadRecords()
        val idx = records.indexOfFirst { it.hash == hash }
        val newFav: Boolean
        if (idx >= 0) {
            newFav = !records[idx].isFavorite
            records[idx] = records[idx].copy(isFavorite = newFav)
        } else {
            newFav = true
            val file = getAudioFile(hash)
            records.add(
                0,
                VoiceRecord(
                    hash = hash,
                    text = voiceText,
                    displayText = display.ifEmpty { voiceText },
                    timestamp = System.currentTimeMillis(),
                    isFavorite = true,
                    fileSize = if (file.exists()) file.length() else 0L
                )
            )
        }
        saveRecords(records)
        return newFav
    }

    fun toggleFavoriteByHash(hash: String): Boolean {
        val records = loadRecords()
        val idx = records.indexOfFirst { it.hash == hash }
        if (idx < 0) return false
        val newFav = !records[idx].isFavorite
        records[idx] = records[idx].copy(isFavorite = newFav)
        saveRecords(records)
        return newFav
    }

    fun cacheAudio(voiceText: String, audioData: ByteArray): File {
        val hash = VoiceBlockUtils.contentHash(voiceText)
        val file = getAudioFile(hash)
        file.writeBytes(audioData)

        val display = voiceText.replace(Regex("""\[.*?]"""), "").trim()
        val records = loadRecords()
        val idx = records.indexOfFirst { it.hash == hash }
        if (idx >= 0) {
            records[idx] = records[idx].copy(
                text = voiceText,
                displayText = display.ifEmpty { voiceText },
                fileSize = file.length()
            )
        } else {
            records.add(
                0,
                VoiceRecord(
                    hash = hash,
                    text = voiceText,
                    displayText = display.ifEmpty { voiceText },
                    timestamp = System.currentTimeMillis(),
                    isFavorite = false,
                    fileSize = file.length()
                )
            )
        }
        saveRecords(records)
        return file
    }

    /**
     * Get cached MP3 file or synthesize via TTSManager and cache it.
     * Does NOT strip square brackets so ElevenLabs v3 audio tags work properly!
     */
    suspend fun getOrSynthesizeAudio(
        voiceText: String,
        settings: Settings,
        ttsManager: TTSManager,
    ): File = withContext(Dispatchers.IO) {
        getCachedFile(voiceText)?.let { return@withContext it }

        val provider = settings.getSelectedTTSProvider()
            ?: throw IllegalStateException("未选择TTS语音服务")

        val chunks = ttsManager.generateSpeech(provider, TTSRequest(text = voiceText)).toList()
        val out = ByteArrayOutputStream()
        chunks.forEach { out.write(it.data) }
        val bytes = out.toByteArray()
        if (bytes.isEmpty()) {
            throw IllegalStateException("生成的语音数据为空")
        }
        cacheAudio(voiceText, bytes)
    }

    suspend fun saveToDownloads(
        voiceText: String,
        settings: Settings? = null,
        ttsManager: TTSManager? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val cachedFile = getCachedFile(voiceText) ?: run {
            if (settings != null && ttsManager != null) {
                runCatching { getOrSynthesizeAudio(voiceText, settings, ttsManager) }.getOrNull()
            } else null
        } ?: return@withContext false

        saveFileToDownloads(cachedFile)
    }

    suspend fun saveFileToDownloads(cachedFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!cachedFile.exists() || cachedFile.length() == 0L) return@withContext false
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "koun_voice_${timestamp}.mp3"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Koun语音")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    contentValues
                )
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        cachedFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                } ?: return@withContext false
            } else {
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Koun语音"
                )
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, fileName)
                cachedFile.copyTo(targetFile, overwrite = true)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save voice to Downloads", e)
            false
        }
    }

    fun getAllRecords(): List<VoiceRecord> {
        val records = loadRecords()
        val valid = records.filter {
            val f = getAudioFile(it.hash)
            f.exists() && f.length() > 0
        }.map {
            val f = getAudioFile(it.hash)
            it.copy(fileSize = f.length())
        }
        if (valid.size != records.size) {
            saveRecords(valid)
        }
        return valid.sortedByDescending { it.timestamp }
    }

    fun getTotalCacheSize(): Long {
        return getAllRecords().sumOf { it.fileSize }
    }

    fun deleteRecord(hash: String): Boolean {
        val file = getAudioFile(hash)
        val deleted = if (file.exists()) file.delete() else true
        val records = loadRecords().filterNot { it.hash == hash }
        saveRecords(records)
        return deleted
    }

    fun clearAllCache(keepFavorites: Boolean = true): Int {
        val records = loadRecords()
        var deleted = 0
        val remaining = mutableListOf<VoiceRecord>()
        records.forEach { record ->
            if (keepFavorites && record.isFavorite) {
                remaining.add(record)
            } else {
                val f = getAudioFile(record.hash)
                if (!f.exists() || f.delete()) {
                    deleted++
                }
            }
        }
        saveRecords(remaining)
        return deleted
    }
}

/**
 * Singleton local audio player for cached Koun voice files.
 */
object VoiceFilePlayer {
    private var exoPlayer: ExoPlayer? = null
    private var currentHash: String? = null
    private var onStateChanged: ((String?, Boolean) -> Unit)? = null

    fun playOrStop(
        context: Context,
        hash: String,
        file: File,
        onUpdate: (playingHash: String?, isPlaying: Boolean) -> Unit
    ) {
        if (currentHash == hash && exoPlayer?.isPlaying == true) {
            stop()
            onUpdate(null, false)
            return
        }
        stop()
        val player = ExoPlayer.Builder(context.applicationContext).build()
        exoPlayer = player
        currentHash = hash
        onStateChanged = onUpdate

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    val h = currentHash
                    currentHash = null
                    onStateChanged?.invoke(h, false)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onStateChanged?.invoke(currentHash, isPlaying)
            }
        })

        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
        player.play()
        onUpdate(hash, true)
    }

    fun stop() {
        try {
            exoPlayer?.stop()
            exoPlayer?.release()
        } catch (_: Exception) {
        }
        val prev = currentHash
        exoPlayer = null
        currentHash = null
        onStateChanged?.invoke(prev, false)
    }
}
