package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeReminderTransformerTest {
    private val zoneId = ZoneId.systemDefault()

    private fun userMessage(text: String, createdAt: LocalDateTime) = UIMessage(
        role = MessageRole.USER,
        parts = listOf(UIMessagePart.Text(text)),
        createdAt = createdAt,
    )

    private fun assistantMessage(text: String, createdAt: LocalDateTime) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(text)),
        createdAt = createdAt,
    )

    private fun getMessageText(msg: UIMessage): String =
        msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    private fun anchorTexts(messages: List<UIMessage>): List<String> =
        messages.filter { it.role == MessageRole.SYSTEM }
            .map(::getMessageText)

    @Test
    fun `first history message gets private anchor without changing bubble`() {
        val original = userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 12, 34))
        val result = applyTimeReminder(listOf(original))

        assertEquals(2, result.size)
        assertEquals(MessageRole.SYSTEM, result.first().role)
        assertTrue(getMessageText(result.first()).contains("2026-02-22 10:12"))
        assertTrue(getMessageText(result.first()).contains("24小时制"))
        assertFalse(getMessageText(result.first()).contains("上午"))
        assertEquals(original, result.last())
    }

    @Test
    fun `same hour does not add redundant hourly anchors`() {
        val messages = listOf(
            userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)),
            assistantMessage("Hi", LocalDateTime(2026, 2, 22, 10, 2, 0)),
            userMessage("World", LocalDateTime(2026, 2, 22, 10, 59, 59)),
        )

        val result = applyTimeReminder(messages)
        assertEquals(1, anchorTexts(result).size)
        assertEquals(messages, result.filter { it.role != MessageRole.SYSTEM })
    }

    @Test
    fun `crossing one hour inserts anchor at the exact hour`() {
        val messages = listOf(
            userMessage("Before", LocalDateTime(2026, 2, 22, 10, 59, 59)),
            userMessage("After", LocalDateTime(2026, 2, 22, 11, 0, 0)),
        )

        val result = applyTimeReminder(messages)
        val anchors = anchorTexts(result)
        assertEquals(2, anchors.size)
        assertTrue(anchors[1].contains("2026-02-22 11:00"))
        assertEquals("Before", getMessageText(result[1]))
        assertEquals("After", getMessageText(result[3]))
    }

    @Test
    fun `long gap inserts every crossed hour including day change`() {
        val messages = listOf(
            userMessage("Yesterday", LocalDateTime(2026, 2, 22, 23, 15, 0)),
            userMessage("Today", LocalDateTime(2026, 2, 23, 2, 20, 0)),
        )

        val result = applyTimeReminder(messages)
        val anchors = anchorTexts(result)
        assertEquals(5, anchors.size)
        assertTrue(anchors[1].contains("2026-02-23 00:00"))
        assertTrue(anchors[2].contains("2026-02-23 01:00"))
        assertTrue(anchors[3].contains("2026-02-23 02:00"))
        assertEquals("Today", getMessageText(result.last()))
    }

    @Test
    fun `assistant messages also preserve the hour timeline`() {
        val messages = listOf(
            userMessage("Q1", LocalDateTime(2026, 2, 22, 10, 0, 0)),
            assistantMessage("A1", LocalDateTime(2026, 2, 22, 12, 10, 0)),
            userMessage("Q2", LocalDateTime(2026, 2, 22, 12, 20, 0)),
        )

        val result = applyTimeReminder(messages)
        val anchors = anchorTexts(result)
        assertEquals(3, anchors.size)
        assertTrue(anchors[1].contains("2026-02-22 11:00"))
        assertTrue(anchors[2].contains("2026-02-22 12:00"))
        assertEquals(messages, result.filter { it.role != MessageRole.SYSTEM })
    }

    @Test
    fun `empty messages should return empty`() {
        assertTrue(applyTimeReminder(emptyList()).isEmpty())
    }
}
