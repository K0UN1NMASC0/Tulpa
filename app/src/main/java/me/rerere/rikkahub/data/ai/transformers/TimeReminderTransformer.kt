package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.time.DayOfWeek
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

private val WEEKDAY_MAP = mapOf(
    DayOfWeek.MONDAY to "星期一",
    DayOfWeek.TUESDAY to "星期二",
    DayOfWeek.WEDNESDAY to "星期三",
    DayOfWeek.THURSDAY to "星期四",
    DayOfWeek.FRIDAY to "星期五",
    DayOfWeek.SATURDAY to "星期六",
    DayOfWeek.SUNDAY to "星期日",
)

private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 每隔1小时在该小时的第一条消息文本开头注入时间锚点。
 * - 跨日(含首条): 完整 yyyy-MM-dd HH:mm (星期X)
 * - 同日跨小时:   仅 HH:mm
 * 标签直接 prepend 到消息的第一个 Text part，不额外插入 SYSTEM 消息。
 */
internal fun applyTimeReminder(messages: List<UIMessage>): List<UIMessage> {
    if (messages.isEmpty()) return messages

    val result = mutableListOf<UIMessage>()
    val zoneId = ZoneId.systemDefault()
    var lastInjectedHour = -1
    var lastInjectedDate = ""

    messages.forEach { current ->
        val instant = current.createdAt
            .toInstant(TimeZone.currentSystemDefault())
            .toJavaInstant()
        val zonedDt = instant.atZone(zoneId)
        val curHour = zonedDt.hour
        val curDate = DATE_FMT.format(zonedDt)

        val crossedDay = lastInjectedDate != curDate
        val crossedHour = lastInjectedHour != curHour

        if (crossedDay || crossedHour) {
            val tag = if (crossedDay) {
                val weekday = WEEKDAY_MAP[zonedDt.dayOfWeek] ?: ""
                "<time_reminder>系统注入·仅AI可见·历史时间锚点 | " +
                    "$curDate ${TIME_FMT.format(zonedDt)} ($weekday) | " +
                    "24小时制 | 不显示在聊天气泡</time_reminder>"
            } else {
                "<time_reminder>系统注入·仅AI可见·历史时间锚点 | " +
                    "${TIME_FMT.format(zonedDt)} | " +
                    "24小时制 | 不显示在聊天气泡</time_reminder>"
            }

            result.add(prependTimeTag(current, tag))
            lastInjectedHour = curHour
            lastInjectedDate = curDate
        } else {
            result.add(current)
        }
    }

    return result
}

/** Prepend a time-reminder tag to the first Text part of a message. */
private fun prependTimeTag(message: UIMessage, tag: String): UIMessage {
    val newParts = message.parts.toMutableList()
    val firstTextIdx = newParts.indexOfFirst { it is UIMessagePart.Text }
    if (firstTextIdx >= 0) {
        val original = newParts[firstTextIdx] as UIMessagePart.Text
        newParts[firstTextIdx] = UIMessagePart.Text("$tag\n${original.text}")
    } else {
        newParts.add(0, UIMessagePart.Text(tag))
    }
    return message.copy(parts = newParts)
}
