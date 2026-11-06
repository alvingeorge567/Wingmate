package com.wingmate.keyboard.reader

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.wingmate.keyboard.data.ChatMessage
import com.wingmate.keyboard.data.Settings

sealed interface CaptureResult {
    data class Success(val packageName: String, val messages: List<ChatMessage>) : CaptureResult
    /** The accessibility service is not turned on. */
    data object NotEnabled : CaptureResult
    /** The current app isn't on the user's allowlist, so we don't look. */
    data class NotAllowed(val packageName: String) : CaptureResult
    /** We looked, but found no messages. */
    data object Empty : CaptureResult
}

/**
 * Reads the chat visible on screen, only when the keyboard asks ([capture]).
 * It does nothing with accessibility events; nothing is stored or sent anywhere by this class.
 */
class ChatReaderService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun capture(): CaptureResult {
        val appWindow = findChatWindow() ?: return CaptureResult.Empty
        val root = appWindow.root ?: return CaptureResult.Empty
        val pkg = root.packageName?.toString().orEmpty()

        val allowed = Settings(this).allowedApps + packageName // our own test chat is always allowed
        if (pkg !in allowed) return CaptureResult.NotAllowed(pkg)

        val windowBounds = Rect().also { appWindow.getBoundsInScreen(it) }
        val list = findMessageList(root) ?: root

        val nodes = mutableListOf<TextNode>()
        collectText(list, nodes, depth = 0)

        // Bubble positions are relative to the window, not the whole screen (split screen etc.).
        val relative = nodes.map {
            it.copy(left = it.left - windowBounds.left, right = it.right - windowBounds.left)
        }
        val messages = ChatExtractor.extract(relative, windowBounds.width())
        return if (messages.isEmpty()) CaptureResult.Empty else CaptureResult.Success(pkg, messages)
    }

    /** The focused (or top-most) normal application window, i.e. the chat app behind the keyboard. */
    private fun findChatWindow(): AccessibilityWindowInfo? {
        val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        return apps.firstOrNull { it.isFocused } ?: apps.firstOrNull { it.isActive } ?: apps.firstOrNull()
    }

    /** The biggest scrollable container is almost always the message list. */
    private fun findMessageList(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        val rect = Rect()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_DEPTH) return
            if (node.isScrollable && node.isVisibleToUser) {
                node.getBoundsInScreen(rect)
                val area = rect.width() * rect.height()
                if (area > bestArea) {
                    bestArea = area
                    best = node
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return best
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<TextNode>, depth: Int) {
        if (depth > MAX_DEPTH || out.size > MAX_NODES) return
        if (!node.isVisibleToUser || node.isEditable) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            val r = Rect().also { node.getBoundsInScreen(it) }
            out += TextNode(text, r.left, r.right, r.top, r.bottom)
            return // the text of a node already includes its children's text for TextViews
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectText(it, out, depth + 1) }
    }

    companion object {
        private const val MAX_DEPTH = 40
        private const val MAX_NODES = 400

        @Volatile
        private var instance: ChatReaderService? = null

        val isRunning: Boolean get() = instance != null

        /** Called by the keyboard on the main thread when the user taps Suggest. */
        fun captureConversation(): CaptureResult =
            instance?.runCatching { capture() }?.getOrDefault(CaptureResult.Empty)
                ?: CaptureResult.NotEnabled
    }
}
