package com.wingmate.keyboard.reader

import com.wingmate.keyboard.data.ChatMessage
import com.wingmate.keyboard.data.Sender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExtractorTest {
    private val width = 1080

    private fun them(text: String, top: Int) = TextNode(text, 40, 600, top, top + 80)
    private fun me(text: String, top: Int) = TextNode(text, 480, 1040, top, top + 80)

    @Test
    fun `attributes bubbles by side and orders them top to bottom`() {
        val nodes = listOf(
            me("climbing and burning pasta", 300),
            them("haha what do you do for fun", 100),
            them("lmao same on the pasta", 500),
        )
        assertEquals(
            listOf(
                ChatMessage(Sender.THEM, "haha what do you do for fun"),
                ChatMessage(Sender.ME, "climbing and burning pasta"),
                ChatMessage(Sender.THEM, "lmao same on the pasta"),
            ),
            ChatExtractor.extract(nodes, width),
        )
    }

    @Test
    fun `drops timestamps and receipts`() {
        val nodes = listOf(
            TextNode("9:41 PM", 480, 600, 50, 80),
            them("hey!", 100),
            TextNode("Seen", 900, 1040, 200, 230),
            TextNode("Today", 480, 600, 20, 40),
        )
        assertEquals(listOf(ChatMessage(Sender.THEM, "hey!")), ChatExtractor.extract(nodes, width))
    }

    @Test
    fun `keeps only the most recent messages`() {
        val nodes = (0 until 30).map { them("msg $it", it * 100) }
        val out = ChatExtractor.extract(nodes, width, maxMessages = 5)
        assertEquals(5, out.size)
        assertEquals("msg 29", out.last().text)
    }

    @Test
    fun `collapses duplicated nodes`() {
        val out = ChatExtractor.extract(listOf(them("hi", 100), them("hi", 100)), width)
        assertEquals(1, out.size)
    }

    @Test
    fun `noise detection`() {
        assertTrue(ChatExtractor.isNoise("12:05"))
        assertTrue(ChatExtractor.isNoise("Delivered"))
        assertTrue(ChatExtractor.isNoise("   "))
        assertTrue(ChatExtractor.isNoise("Read 9:41"))
        assertTrue(ChatExtractor.isNoise("Saturday"))
        assertEquals(false, ChatExtractor.isNoise("seen any good movies lately?"))
        assertEquals(false, ChatExtractor.isNoise("sunday brunch?"))
        assertEquals(false, ChatExtractor.isNoise("read anything good lately"))
    }
}
