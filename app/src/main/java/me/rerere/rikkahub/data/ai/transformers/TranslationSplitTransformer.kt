package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 翻译分离 Transformer
 *
 * 从 AI 回复中提取「中文翻译」部分，将其移入 UIMessage.translation 字段。
 * - 显示时：气泡内上方日语原文，下方翻译（利用已有的 translation 渲染）
 * - 上下文：只保留日语原文，翻译不参与 API 请求，节省 token
 *
 * 支持的格式：
 *   「中文翻译内容」  （全角方括号包裹的中文行）
 *
 * 仅在 Assistant.enableTranslationSplit = true 时生效。
 */
object TranslationSplitTransformer : OutputMessageTransformer {

    // 匹配「...」包裹的整行（中文翻译行）
    // 注意：使用非贪婪匹配，支持多行场景
    private val TRANSLATION_LINE_REGEX = Regex("""「([^」]+)」""")

    // 用于判断内容是否包含中文字符
    private val CHINESE_CHAR_REGEX = Regex("""[\u4e00-\u9fff]""")

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.assistant.enableTranslationSplit) return messages

        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) return@map message
            // 已经有 translation 的不重复处理
            if (message.translation != null) return@map message

            splitTranslation(message)
        }
    }

    private fun splitTranslation(message: UIMessage): UIMessage {
        val textParts = message.parts.filterIsInstance<UIMessagePart.Text>()
        if (textParts.isEmpty()) return message

        val fullText = textParts.joinToString("") { it.text }

        // 找出所有「翻译」行
        val matches = TRANSLATION_LINE_REGEX.findAll(fullText).toList()
        if (matches.isEmpty()) return message

        // 过滤出确实包含中文的「」内容作为翻译
        val translationMatches = matches.filter { match ->
            val content = match.groupValues[1]
            CHINESE_CHAR_REGEX.containsMatchIn(content)
        }

        if (translationMatches.isEmpty()) return message

        // 提取翻译内容（不带「」）
        val translationText = translationMatches.joinToString("\n") { it.groupValues[1] }

        // 从原文中移除翻译行（包括「」），保留日语原文
        var cleanedText = fullText
        for (match in translationMatches.reversed()) {
            // 移除匹配到的「翻译」以及前后可能的换行
            val start = match.range.first
            val end = match.range.last + 1

            // 检查前面是否有换行需要一起清理
            val removeStart = if (start > 0 && cleanedText[start - 1] == '\n') start - 1 else start
            cleanedText = cleanedText.removeRange(removeStart, end)
        }

        // 清理多余空行
        cleanedText = cleanedText.replace(Regex("""\n{3,}"""), "\n\n").trim()

        if (cleanedText.isBlank()) return message

        // 重建 parts：替换第一个 Text part，保留其他非文本 parts
        val newParts = message.parts.toMutableList()
        val firstTextIdx = newParts.indexOfFirst { it is UIMessagePart.Text }
        if (firstTextIdx >= 0) {
            // 移除所有 Text parts
            newParts.removeAll { it is UIMessagePart.Text }
            // 在原位置插入清理后的文本
            newParts.add(firstTextIdx, UIMessagePart.Text(cleanedText))
        }

        return message.copy(
            parts = newParts,
            translation = translationText
        )
    }
}
