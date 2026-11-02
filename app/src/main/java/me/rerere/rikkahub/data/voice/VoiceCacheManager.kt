package me.rerere.rikkahub.data.voice

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.utils.VoiceBlockUtils
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "VoiceCacheManager"

/**
 * Manages voice audio caching and saving.
 * - Generated MP3s are cached in app-internal cache dir
 * - Users can explicitly save to Downloads/Koun语音/
 */
class VoiceCacheManager(
    private val context: Context,
) {
    private val cacheDir: File
        get() {
            val dir = File(context.cacheDir, "voice_cache")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /**
     * Get cached MP3 file for the given voice text content.
     * Returns null if not cached.
     */
    fun getCachedFile(voiceText: String): File? {
        val hash = VoiceBlockUtils.contentHash(voiceText)
        val file = File(cacheDir, "voice_${hash}.mp3")
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Save MP3 data to cache and return the file.
     */
    fun cacheAudio(voiceText: String, audioData: ByteArray): File {
        val hash = VoiceBlockUtils.contentHash(voiceText)
        val file = File(cacheDir, "voice_${hash}.mp3")
        file.writeBytes(audioData)
        Log.i(TAG, "Cached voice audio: ${file.absolutePath} (${audioData.size} bytes)")
        return file
    }

    /**
     * Save a cached voice file to the user-visible Downloads folder.
     */
    suspend fun saveToDownloads(context: Context, voiceText: String): Boolean = withContext(Dispatchers.IO) {
        val cachedFile = getCachedFile(voiceText) ?: return@withContext false
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "koun_voice_${timestamp}.mp3"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
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
                }
            } else {
                // Legacy storage for older Android
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Koun语音"
                )
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, fileName)
                cachedFile.copyTo(targetFile, overwrite = true)
            }
            Log.i(TAG, "Saved voice to Downloads: $fileName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save voice to Downloads", e)
            false
        }
    }

    /**
     * Get all cached voice files, sorted by last modified (newest first).
     */
    fun getAllCachedFiles(): List<File> {
        return cacheDir.listFiles()
            ?.filter { it.extension == "mp3" && it.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Get total cache size in bytes.
     */
    fun getTotalCacheSize(): Long {
        return getAllCachedFiles().sumOf { it.length() }
    }

    /**
     * Delete a specific cached file.
     */
    fun deleteCachedFile(file: File): Boolean {
        return file.delete()
    }

    /**
     * Clear all cached voice files.
     */
    fun clearAllCache(): Int {
        val files = getAllCachedFiles()
        var deleted = 0
        files.forEach { if (it.delete()) deleted++ }
        Log.i(TAG, "Cleared $deleted cached voice files")
        return deleted
    }
}
