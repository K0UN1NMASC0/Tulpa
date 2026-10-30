package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.toJavaInstant

object TimeReminderTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return applyTimeReminder(messages)
    }
}

/** Adds private system-message anchors at local hour boundaries in the stored history. */
internal fun applyTimeReminder(messages: List<UIMessage>): List<UIMessage> {
    if (messages.isEmpty()) return messages

    val result = mutableListOf<UIMessage>()
    val zoneId = ZoneId.systemDefault()
    var previousInstant: java.time.Instant? = null

    messages.forEach { current ->
        val instant = current.createdAt.toInstant(TimeZone.currentSystemDefault()).toJavaInstant()
        val previous = previousInstant

        if (previous == null) {
            result.add(buildTimeReminderMessage(instant))
        } else if (instant.isAfter(previous)) {
            var boundary = previous.atZone(zoneId)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .plusHours(1)
            while (!boundary.toInstant().isAfter(instant)) {
                result.add(buildTimeReminderMessage(boundary.toInstant()))
                boundary = boundary.plusHours(1)
            }
        }

        result.add(current)
        if (previous == null || instant.isAfter(previous)) previousInstant = instant
    }

    return result
}

private val TIME_ANCHOR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun buildTimeReminderMessage(instant: java.time.Instant): UIMessage {
    val localDateTime = instant.atZone(ZoneId.systemDefault())
    val timeStr = TIME_ANCHOR_FORMATTER.format(localDateTime)
    val content = "<time_reminder>系统注入·仅AI可见·历史时间锚点 | $timeStr | 24小时制 | 不显示在聊天气泡</time_reminder>"
    return UIMessage(
        role = MessageRole.SYSTEM,
        parts = listOf(UIMessagePart.Text(content)),
    )
}
