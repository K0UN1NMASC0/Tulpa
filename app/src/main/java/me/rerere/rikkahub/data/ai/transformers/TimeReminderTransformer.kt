package me.rerere.rikkahub.data.ai.transformers

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.toJavaInstant

private const val TIME_GAP_THRESHOLD_SECONDS = 7200L

object TimeReminderTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.assistant.enableTimeReminder) return messages
        return applyTimeReminder(messages)
    }
}

internal fun applyTimeReminder(messages: List<UIMessage>): List<UIMessage> {
    val result = mutableListOf<UIMessage>()
    val tz = TimeZone.currentSystemDefault()

    var firstUserFound = false
    for (i in messages.indices) {
        val current = messages[i]
        if (current.role == MessageRole.USER) {
            val currInstant = current.createdAt.toInstant(tz)
            if (!firstUserFound) {
                firstUserFound = true
                result.add(buildTimeReminderMessage(null, currInstant))
            } else {
                val prevUserInstant = findPreviousUserInstant(messages, i, tz)
                if (prevUserInstant != null) {
                    val gapSeconds = (currInstant - prevUserInstant).inWholeSeconds
                    val crossedDate = isDifferentLocalDate(prevUserInstant, currInstant, tz)
                    if (gapSeconds > TIME_GAP_THRESHOLD_SECONDS || crossedDate) {
                        result.add(buildTimeReminderMessage(gapSeconds, currInstant))
                    }
                }
            }
        }
        result.add(current)
    }

    return result
}

private fun findPreviousUserInstant(
    messages: List<UIMessage>,
    currentIndex: Int,
    tz: TimeZone,
): Instant? {
    for (j in (currentIndex - 1) downTo 0) {
        if (messages[j].role == MessageRole.USER) {
            return messages[j].createdAt.toInstant(tz)
        }
    }
    return null
}

private fun isDifferentLocalDate(a: Instant, b: Instant, tz: TimeZone): Boolean {
    return a.toLocalDateTime(tz).date != b.toLocalDateTime(tz).date
}

private val TIME_REMINDER_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun buildTimeReminderMessage(gapSeconds: Long?, instant: Instant): UIMessage {
    val javaInstant = instant.toJavaInstant()
    val zoneId = ZoneId.systemDefault()
    val localDateTime = javaInstant.atZone(zoneId).toLocalDateTime()
    val timeStr = TIME_REMINDER_FORMATTER.format(localDateTime)
    val content = if (gapSeconds != null) {
        val gapText = formatGap(gapSeconds)
        "<time_reminder>\u7cfb\u7edf\u6ce8\u5165\u00b7\u4ec5AI\u53ef\u89c1 | \u5f53\u524d\u65f6\u95f4: $timeStr | \u8ddd\u7528\u6237\u4e0a\u6761\u6d88\u606f: $gapText</time_reminder>"
    } else {
        "<time_reminder>\u7cfb\u7edf\u6ce8\u5165\u00b7\u4ec5AI\u53ef\u89c1 | \u5f53\u524d\u65f6\u95f4: $timeStr</time_reminder>"
    }
    return UIMessage(
        role = MessageRole.SYSTEM,
        parts = listOf(UIMessagePart.Text(content)),
    )
}

private fun formatGap(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}\u79d2"
        seconds < 3600 -> "${seconds / 60}\u5206\u949f"
        seconds < 86400 -> {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            if (m == 0L) "${h}\u5c0f\u65f6" else "${h}\u5c0f\u65f6${m}\u5206\u949f"
        }
        else -> {
            val d = seconds / 86400
            val h = (seconds % 86400) / 3600
            if (h == 0L) "${d}\u5929" else "${d}\u5929${h}\u5c0f\u65f6"
        }
    }
}
