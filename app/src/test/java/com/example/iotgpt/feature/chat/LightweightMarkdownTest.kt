package com.example.iotgpt.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightweightMarkdownTest {

    @Test
    fun parseInline_boldText_splitIntoTextAndBold() {
        val result = LightweightMarkdown.parseInline("hello **world**!")

        assertEquals(
            listOf(
                MarkdownInline.Text("hello "),
                MarkdownInline.Bold("world"),
                MarkdownInline.Text("!")
            ),
            result
        )
    }

    @Test
    fun parse_bulletLines_groupedAsBulletList() {
        val blocks = LightweightMarkdown.parse("- **one**\n- two")

        assertEquals(1, blocks.size)
        val list = blocks.first() as MarkdownBlock.BulletList
        assertEquals(2, list.items.size)
        assertEquals(MarkdownInline.Bold("one"), list.items.first().first())
        assertEquals(MarkdownInline.Text("two"), list.items[1].first())
    }

    @Test
    fun parse_numberedLines_keepProvidedNumbers() {
        val blocks = LightweightMarkdown.parse("3. first\n4. second")

        val list = blocks.first() as MarkdownBlock.NumberedList
        assertEquals(3, list.items.first().number)
        assertEquals(4, list.items[1].number)
    }

    @Test
    fun parse_codeFence_preservesLanguageAndCode() {
        val blocks = LightweightMarkdown.parse("before\n```kotlin\nval x = 1\n```\nafter")

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        val code = blocks[1] as MarkdownBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
        assertTrue(blocks[2] is MarkdownBlock.Paragraph)
    }

    @Test
    fun parse_unclosedCodeFence_keepsStreamingPartialCodeVisible() {
        val blocks = LightweightMarkdown.parse("```kotlin\nval x =")

        assertEquals(1, blocks.size)
        val code = blocks.first() as MarkdownBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x =", code.code)
    }
}
