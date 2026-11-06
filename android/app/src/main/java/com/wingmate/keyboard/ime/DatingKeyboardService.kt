package com.wingmate.keyboard.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.wingmate.keyboard.data.ChatMessage
import com.wingmate.keyboard.data.Sender
import com.wingmate.keyboard.data.Settings
import com.wingmate.keyboard.data.Tone
import com.wingmate.keyboard.net.Suggestion
import com.wingmate.keyboard.net.SuggestionClient
import com.wingmate.keyboard.net.SuggestionException
import com.wingmate.keyboard.reader.CaptureResult
import com.wingmate.keyboard.reader.ChatReaderService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DatingKeyboardService : InputMethodService(), KeyboardView.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var request: Job? = null
    private lateinit var settings: Settings
    private lateinit var client: SuggestionClient
    private var keyboard: KeyboardView? = null

    /** Tone picked on the keyboard for this session; starts from the saved default. */
    private var tone: Tone = Tone.ANY

    /** Set when the user taps "Use copied": suggestions are based on that text only. */
    private var useClipboardOnce = false

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        client = SuggestionClient({ settings.backendUrl }, { settings.appToken })
    }

    override fun onCreateInputView(): View =
        KeyboardView(this, this).also { keyboard = it }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        tone = settings.tone
        keyboard?.apply {
            setTone(tone)
            setClipboardAvailable(clipboardText() != null)
            setEnterLabel(enterLabelFor(info))
            if (!restarting) clearSuggestions()
            setAutoShift(textBeforeCursor().isBlank())
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        request?.cancel()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ---- suggestions ----

    override fun onSuggest() {
        if (!settings.consentGiven) {
            keyboard?.showError("Open the Wingmate app to finish setup first.")
            return
        }
        val conversation = gatherConversation() ?: return
        val draft = textBeforeCursor()

        request?.cancel()
        keyboard?.showLoading("Thinking of replies…")
        request = scope.launch {
            try {
                val res = client.suggest(conversation, draft, tone, settings.aboutMe)
                keyboard?.showSuggestions(res.read, res.suggestions)
            } catch (e: SuggestionException) {
                keyboard?.showError(e.message ?: "Something went wrong.")
            }
        }
    }

    /** Screen reader first, clipboard as fallback. Shows a hint and returns null if neither works. */
    private fun gatherConversation(): List<ChatMessage>? {
        val clip = clipboardText()
        if (useClipboardOnce && clip != null) {
            useClipboardOnce = false
            return listOf(ChatMessage(Sender.THEM, clip))
        }
        return when (val result = ChatReaderService.captureConversation()) {
            is CaptureResult.Success -> result.messages
            else -> {
                if (clip != null) return listOf(ChatMessage(Sender.THEM, clip))
                keyboard?.showError(
                    when (result) {
                        CaptureResult.NotEnabled ->
                            "Turn on the chat reader in the Wingmate app, or copy their message and try again."
                        is CaptureResult.NotAllowed ->
                            "Reading isn't enabled for this app. Add it in Wingmate settings, or copy their message."
                        else -> "Couldn't find messages on screen. Copy their message and try again."
                    },
                )
                null
            }
        }
    }

    override fun onToneSelected(tone: Tone) {
        this.tone = tone
        keyboard?.setTone(tone)
    }

    override fun onUseClipboard() {
        useClipboardOnce = true
        onSuggest()
    }

    /** Tap: replace whatever is typed with the suggestion, ready to send. */
    override fun onSuggestionTap(suggestion: Suggestion) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(MAX_FIELD, 0)?.length ?: 0
        val after = ic.getTextAfterCursor(MAX_FIELD, 0)?.length ?: 0
        ic.beginBatchEdit()
        ic.deleteSurroundingText(before, after)
        ic.commitText(suggestion.text, 1)
        ic.endBatchEdit()
        keyboard?.clearSuggestions()
    }

    /** Long-press: copy to clipboard so it can be pasted or tweaked elsewhere. */
    override fun onSuggestionLongPress(suggestion: Suggestion) {
        getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Wingmate suggestion", suggestion.text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    // ---- typing ----

    override fun onText(text: String) {
        currentInputConnection?.commitText(text, 1)
        if (text == " " || text == ".") keyboard?.setAutoShift(textBeforeCursor().trimEnd().endsWith('.'))
    }

    override fun onDelete() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingTextInCodePoints(1, 0)
        keyboard?.setAutoShift(textBeforeCursor().isBlank())
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    override fun onSwitchKeyboard(showPicker: Boolean) {
        if (!showPicker && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (switchToNextInputMethod(false)) return
        }
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    // ---- helpers ----

    private fun textBeforeCursor(): String =
        currentInputConnection?.getTextBeforeCursor(MAX_FIELD, 0)?.toString().orEmpty()

    private fun clipboardText(): String? {
        val cm = getSystemService(ClipboardManager::class.java) ?: return null
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        return text?.trim()?.takeIf { it.isNotEmpty() && it.length <= 1000 }
    }

    private fun enterLabelFor(info: EditorInfo): String =
        when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> "⏎"
        }

    private companion object {
        const val MAX_FIELD = 1000
    }
}
