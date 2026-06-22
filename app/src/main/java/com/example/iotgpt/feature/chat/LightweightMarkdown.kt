package com.example.iotgpt.feature.chat

/**
 * Small Markdown subset used by chat bubbles. It intentionally supports only
 * stable, low-risk constructs that fit the existing Compose message layout.
 */
internal object LightweightMarkdown {
    fun parse(content: String): List<MarkdownBlock> {
        if (content.isBlank()) return emptyList()

        val blocks = mutableListOf<MarkdownBlock>()
        val textBuffer = mutableListOf<String>()
        val codeBuffer = mutableListOf<String>()
        var inCodeBlock = false
        var codeLanguage = ""

        fun flushTextBuffer() {
            if (textBuffer.isNotEmpty()) {
                blocks += parseTextBlocks(textBuffer)
                textBuffer.clear()
            }
        }

        fun flushCodeBuffer() {
            blocks += MarkdownBlock.Code(
                language = codeLanguage.ifBlank { "代码" },
                code = codeBuffer.joinToString("\n").trimEnd()
            )
            codeBuffer.clear()
            codeLanguage = ""
        }

        content.lines().forEach { line ->
            if (line.trimStart().startsWith("```")) {
                if (inCodeBlock) {
                    flushCodeBuffer()
                    inCodeBlock = false
                } else {
                    flushTextBuffer()
                    inCodeBlock = true
                    codeLanguage = line.trim().removePrefix("```").trim()
                }
            } else if (inCodeBlock) {
                codeBuffer += line
            } else {
                textBuffer += line
            }
        }

        if (inCodeBlock) {
            flushCodeBuffer()
        }
        flushTextBuffer()

        return blocks
    }

    fun parseInline(text: String): List<MarkdownInline> {
        if (text.isEmpty()) return emptyList()

        val result = mutableListOf<MarkdownInline>()
        var cursor = 0
        while (cursor < text.length) {
            val start = text.indexOf("**", cursor)
            if (start < 0) {
                appendText(result, text.substring(cursor))
                break
            }

            val end = text.indexOf("**", start + 2)
            if (end < 0) {
                appendText(result, text.substring(cursor))
                break
            }

            appendText(result, text.substring(cursor, start))
            val boldText = text.substring(start + 2, end)
            if (boldText.isBlank()) {
                appendText(result, text.substring(start, end + 2))
            } else {
                result += MarkdownInline.Bold(boldText)
            }
            cursor = end + 2
        }
        return result
    }

    private fun parseTextBlocks(lines: List<String>): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<String>()
        val bulletItems = mutableListOf<List<MarkdownInline>>()
        val numberedItems = mutableListOf<MarkdownBlock.NumberedItem>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(parseInline(paragraph.joinToString("\n").trim()))
                paragraph.clear()
            }
        }

        fun flushBullets() {
            if (bulletItems.isNotEmpty()) {
                blocks += MarkdownBlock.BulletList(bulletItems.toList())
                bulletItems.clear()
            }
        }

        fun flushNumbered() {
            if (numberedItems.isNotEmpty()) {
                blocks += MarkdownBlock.NumberedList(numberedItems.toList())
                numberedItems.clear()
            }
        }

        lines.forEach { rawLine ->
            val line = rawLine.trimEnd()
            val trimmed = line.trim()
            val bullet = bulletRegex.matchEntire(line)
            val numbered = numberedRegex.matchEntire(line)

            when {
                trimmed.isBlank() -> {
                    flushParagraph()
                    flushBullets()
                    flushNumbered()
                }
                bullet != null -> {
                    flushParagraph()
                    flushNumbered()
                    bulletItems += parseInline(bullet.groupValues[1].trim())
                }
                numbered != null -> {
                    flushParagraph()
                    flushBullets()
                    numberedItems += MarkdownBlock.NumberedItem(
                        number = numbered.groupValues[1].toIntOrNull()
                            ?: (numberedItems.size + 1),
                        inlines = parseInline(numbered.groupValues[2].trim())
                    )
                }
                else -> {
                    flushBullets()
                    flushNumbered()
                    paragraph += line
                }
            }
        }

        flushParagraph()
        flushBullets()
        flushNumbered()

        return blocks
    }

    private fun appendText(target: MutableList<MarkdownInline>, value: String) {
        if (value.isNotEmpty()) {
            target += MarkdownInline.Text(value)
        }
    }

    private val bulletRegex = Regex("""\s*[-*]\s+(.+)""")
    private val numberedRegex = Regex("""\s*(\d+)[.)]\s+(.+)""")
}

internal sealed interface MarkdownBlock {
    data class Paragraph(val inlines: List<MarkdownInline>) : MarkdownBlock
    data class BulletList(val items: List<List<MarkdownInline>>) : MarkdownBlock
    data class NumberedList(val items: List<NumberedItem>) : MarkdownBlock
    data class Code(val language: String, val code: String) : MarkdownBlock

    data class NumberedItem(
        val number: Int,
        val inlines: List<MarkdownInline>
    )
}

internal sealed interface MarkdownInline {
    data class Text(val value: String) : MarkdownInline
    data class Bold(val value: String) : MarkdownInline
}
