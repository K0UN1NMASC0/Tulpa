package me.rerere.rikkahub.utils

/**
 * Utility functions for parsing <voice>...</voice> blocks from AI messages.
 * Follows the same pattern as stickerPattern in ChatMessage.kt.
 */
object VoiceBlockUtils {
    private val voicePattern = Regex("""<voice>([\s\S]*?)</voice>""")

    /**
     * Extract the voice block content from a message text.
     * Returns null if no voice block is found.
     */
    fun extractVoiceContent(text: String): String? {
        return voicePattern.find(text)?.groupValues?.get(1)?.trim()
    }

    /**
     * Check if the message contains a voice block.
     */
    fun hasVoiceBlock(text: String): Boolean {
        return voicePattern.containsMatchIn(text)
    }

    /**
     * Strip the voice block from the message text, returning the display text.
     */
    fun stripVoiceBlock(text: String): String {
        return voicePattern.replace(text, "").trim()
    }

    /**
     * Generate a simple hash for cache key purposes.
     */
    fun contentHash(text: String): String {
        return text.hashCode().toUInt().toString(16)
    }
}
