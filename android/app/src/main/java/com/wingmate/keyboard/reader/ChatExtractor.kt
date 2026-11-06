package com.wingmate.keyboard.reader

import com.wingmate.keyboard.data.ChatMessage
import com.wingmate.keyboard.data.Sender

/** A piece of visible text from the screen, with its horizontal/vertical position in pixels. */
data class TextNode(
    val text: String,
    val left: Int,
    val right: Int,
    val top: Int,
    val bottom: Int,
)

/**
 * Turns visible text nodes from a chat screen into an ordered conversation.
 *
 * Pure Kotlin (no Android types) so it can be unit tested.
 *
 * Heuristic used by almost every chat UI: bubbles from the other person hug the left edge,
 * the user's own bubbles hug the right edge. Timestamps, read receipts and labels are dropped.
 */
object ChatExtractor {
    private val NOISE = listOf(
        Regex("""^\d{1,2}:\d{2}(\s?[AaPp]\.?[Mm]\.?)?$"""),                  // 9:41, 9:41 PM
        Regex("""^(today|yesterday|now|just now)$""", RegexOption.IGNORE_CASE),
        // Receipts: "Seen", "Read 9:41", "Delivered at 10:02" (but not "seen any good movies?")
        Regex("""^(seen|read|delivered|sent)(\s(at\s)?\d.*)?$""", RegexOption.IGNORE_CASE),
        Regex("""^(sending|typing)(…|\.\.\.)?$""", RegexOption.IGNORE_CASE),
        // Day separators: "Monday", "Sat, 9:41" (but not "sunday brunch?")
        Regex("""^(mon|tue|wed|thu|fri|sat|sun)[a-z]*,?(\s\d.*)?$""", RegexOption.IGNORE_CASE),
        Regex("""^\d{1,2}/\d{1,2}(/\d{2,4})?$"""),
        Regex("""^(you matched.*|it's a match.*|new match.*)$""", RegexOption.IGNORE_CASE),
    )

    fun isNoise(text: String): Boolean {
        val t = text.trim()
        return t.isEmpty() || t.length > 1000 || NOISE.any { it.matches(t) }
    }

    /**
     * @param nodes text nodes inside the message list (input box and toolbars already excluded).
     * @param screenWidth width of the chat window in pixels.
     * @param maxMessages keep only the most recent messages.
     */
    fun extract(nodes: List<TextNode>, screenWidth: Int, maxMessages: Int = 20): List<ChatMessage> {
        if (screenWidth <= 0) return emptyList()
        val result = mutableListOf<ChatMessage>()

        nodes
            .filter { !isNoise(it.text) }
            .sortedWith(compareBy<TextNode> { it.top }.thenBy { it.left })
            .forEach { node ->
                val sender = senderOf(node, screenWidth)
                val text = node.text.trim()
                val last = result.lastOrNull()
                // Skip exact duplicates (some apps expose the same text on a bubble and its child).
                if (last != null && last.from == sender && last.text == text) return@forEach
                result += ChatMessage(sender, text)
            }
        return result.takeLast(maxMessages)
    }

    internal fun senderOf(node: TextNode, screenWidth: Int): Sender {
        val leftGap = node.left
        val rightGap = screenWidth - node.right
        // Bubble pushed toward the right edge (bigger gap on the left) → it's mine.
        return if (rightGap < leftGap) Sender.ME else Sender.THEM
    }
}
