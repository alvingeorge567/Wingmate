package com.wingmate.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.wingmate.keyboard.data.Tone
import com.wingmate.keyboard.net.Suggestion

/**
 * The whole keyboard surface, built in code (no XML) to keep the IME light:
 *
 *  [✨ Suggest] [Mix] [😜 Playful] [😏 Flirty] … [📋 Use copied]   ← toolbar
 *  "They seem curious and relaxed."                                 ← status / mood hint
 *  ┌ suggestion card ┐ ┌ suggestion card ┐ …                         ← only after Suggest
 *  q w e r t y u i o p / a s d … / ⇧ z x … ⌫ / 123 🌐 space . ⏎        ← keys
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onSwitchKeyboard(showPicker: Boolean)
        fun onSuggest()
        fun onToneSelected(tone: Tone)
        fun onUseClipboard()
        fun onSuggestionTap(suggestion: Suggestion)
        fun onSuggestionLongPress(suggestion: Suggestion)
    }

    private object Colors {
        val BG = Color.parseColor("#1E1B24")
        val KEY = Color.parseColor("#3A3542")
        val KEY_SPECIAL = Color.parseColor("#2C2833")
        val TEXT = Color.parseColor("#F4F1F8")
        val MUTED = Color.parseColor("#B7AFC4")
        val ACCENT = Color.parseColor("#FF5C8A")
        val CARD = Color.parseColor("#2E2937")
    }

    private val toolbar = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val toneChips = mutableMapOf<Tone, TextView>()
    private val clipboardChip: TextView
    private val status = TextView(context)
    private val progress = ProgressBar(context)
    private val cards = LinearLayout(context).apply { orientation = VERTICAL }
    private val keys = LinearLayout(context).apply { orientation = VERTICAL }

    private var shifted = true
    private var capsLock = false
    private var symbols = false
    private var enterLabel = "⏎"

    init {
        orientation = VERTICAL
        setBackgroundColor(Colors.BG)
        setPadding(dp(4), dp(6), dp(4), dp(6))

        toolbar.addView(chip("✨ Suggest", accent = true) { listener.onSuggest() })
        Tone.entries.forEach { tone ->
            val c = chip(tone.label) { listener.onToneSelected(tone) }
            toneChips[tone] = c
            toolbar.addView(c)
        }
        clipboardChip = chip("📋 Use copied") { listener.onUseClipboard() }.also {
            it.visibility = GONE
            toolbar.addView(it)
        }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(toolbar)
        })

        val statusRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(2))
        }
        progress.isIndeterminate = true
        progress.visibility = GONE
        statusRow.addView(progress, LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) })
        status.setTextColor(Colors.MUTED)
        status.textSize = 12f
        status.maxLines = 2
        status.ellipsize = TextUtils.TruncateAt.END
        statusRow.addView(status, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        statusRow.visibility = GONE
        addView(statusRow)

        addView(cards)
        addView(keys)
        renderKeys()
    }

    // ---- public API used by the service ----

    fun setTone(tone: Tone) {
        toneChips.forEach { (t, view) -> styleChip(view, selected = t == tone) }
    }

    fun setClipboardAvailable(available: Boolean) {
        clipboardChip.visibility = if (available) VISIBLE else GONE
    }

    fun setEnterLabel(label: String) {
        enterLabel = label
        renderKeys()
    }

    fun showLoading(message: String) {
        cards.removeAllViews()
        progress.visibility = VISIBLE
        showStatus(message)
    }

    fun showStatus(message: String?) {
        val row = status.parent as View
        if (message.isNullOrBlank()) {
            row.visibility = GONE
            progress.visibility = GONE
        } else {
            status.text = message
            row.visibility = VISIBLE
        }
    }

    fun showSuggestions(read: String, suggestions: List<Suggestion>) {
        progress.visibility = GONE
        showStatus(read.ifBlank { null })
        cards.removeAllViews()
        suggestions.forEach { s -> cards.addView(card(s)) }
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.END }
        actions.addView(chip("↻ More") { listener.onSuggest() })
        actions.addView(chip("✕ Hide") { clearSuggestions() })
        cards.addView(actions)
    }

    fun showError(message: String) {
        progress.visibility = GONE
        cards.removeAllViews()
        showStatus("⚠️ $message")
    }

    fun clearSuggestions() {
        cards.removeAllViews()
        showStatus(null)
    }

    /** After a word is committed, drop out of single-shift (but keep caps lock). */
    fun onCharTyped() {
        if (shifted && !capsLock) {
            shifted = false
            renderKeys()
        }
    }

    fun setAutoShift(shift: Boolean) {
        if (capsLock || symbols || shifted == shift) return
        shifted = shift
        renderKeys()
    }

    // ---- building blocks ----

    private fun card(s: Suggestion): View {
        val tv = TextView(context).apply {
            text = s.text
            setTextColor(Colors.TEXT)
            textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Colors.CARD, dp(12).toFloat())
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                listener.onSuggestionTap(s)
            }
            setOnLongClickListener {
                listener.onSuggestionLongPress(s)
                true
            }
        }
        val label = TextView(context).apply {
            text = Tone.fromWire(s.tone).label
            setTextColor(Colors.MUTED)
            textSize = 10f
            setPadding(dp(14), 0, 0, dp(2))
        }
        return LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, dp(3), 0, dp(3))
            addView(label)
            addView(tv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun chip(text: String, accent: Boolean = false, onClick: () -> Unit) =
        TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(Colors.TEXT)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(if (accent) Colors.ACCENT else Colors.KEY_SPECIAL, dp(16).toFloat())
            if (accent) setTypeface(typeface, Typeface.BOLD)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { onClick() }
        }

    private fun styleChip(view: TextView, selected: Boolean) {
        view.background = rounded(if (selected) Colors.KEY else Colors.KEY_SPECIAL, dp(16).toFloat()).apply {
            if (selected) setStroke(dp(1), Colors.ACCENT)
        }
    }

    private fun renderKeys() {
        keys.removeAllViews()
        val rows: List<List<String>> = if (symbols) {
            listOf(
                "1234567890".map { it.toString() },
                listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
                listOf("ABC_SHIFT", "*", "\"", "'", ":", ";", "!", "?", "DEL"),
                listOf("ABC", "GLOBE", ",", "SPACE", ".", "ENTER"),
            )
        } else {
            listOf(
                "qwertyuiop".map { it.toString() },
                "asdfghjkl".map { it.toString() },
                listOf("SHIFT") + "zxcvbnm".map { it.toString() } + listOf("DEL"),
                listOf("123", "GLOBE", ",", "SPACE", ".", "ENTER"),
            )
        }
        rows.forEachIndexed { index, row ->
            val line = LinearLayout(context).apply {
                orientation = HORIZONTAL
                // Center the shorter middle row like a real keyboard.
                if (!symbols && index == 1) setPadding(dp(16), 0, dp(16), 0)
            }
            row.forEach { line.addView(key(it)) }
            keys.addView(line, LayoutParams(LayoutParams.MATCH_PARENT, dp(50)))
        }
    }

    private fun key(code: String): View {
        val (label, weight, special) = when (code) {
            "SHIFT" -> Triple(if (capsLock) "⇪" else "⇧", 1.5f, true)
            "ABC_SHIFT" -> Triple("", 1.5f, true)
            "DEL" -> Triple("⌫", 1.5f, true)
            "123" -> Triple("123", 1.5f, true)
            "ABC" -> Triple("ABC", 1.5f, true)
            "GLOBE" -> Triple("🌐", 1f, true)
            "SPACE" -> Triple("space", 4f, false)
            "ENTER" -> Triple(enterLabel, 1.5f, true)
            else -> Triple(if (shifted && !symbols) code.uppercase() else code, 1f, false)
        }
        val view = TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Colors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (label.length > 1) 14f else 20f)
            background = rounded(
                when {
                    code == "SHIFT" && shifted -> Colors.MUTED
                    special -> Colors.KEY_SPECIAL
                    else -> Colors.KEY
                },
                dp(6).toFloat(),
            )
        }
        val lp = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
        view.layoutParams = lp

        when (code) {
            "DEL" -> attachRepeat(view) { listener.onDelete() }
            "SHIFT" -> {
                view.setOnClickListener {
                    if (capsLock) { capsLock = false; shifted = false } else shifted = !shifted
                    renderKeys()
                }
                view.setOnLongClickListener { capsLock = true; shifted = true; renderKeys(); true }
            }
            "123" -> view.setOnClickListener { symbols = true; renderKeys() }
            "ABC" -> view.setOnClickListener { symbols = false; renderKeys() }
            "ABC_SHIFT" -> view.isEnabled = false
            "GLOBE" -> {
                view.setOnClickListener { listener.onSwitchKeyboard(showPicker = false) }
                view.setOnLongClickListener { listener.onSwitchKeyboard(showPicker = true); true }
            }
            "SPACE" -> view.setOnClickListener { tap(view); listener.onText(" ") }
            "ENTER" -> view.setOnClickListener { tap(view); listener.onEnter() }
            else -> view.setOnClickListener {
                tap(view)
                listener.onText(if (shifted && !symbols) code.uppercase() else code)
                onCharTyped()
            }
        }
        return view
    }

    private fun tap(v: View) = v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    /** Hold-to-repeat (used for backspace). */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachRepeat(view: View, action: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        val repeater = object : Runnable {
            override fun run() {
                action()
                handler.postDelayed(this, 60)
            }
        }
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tap(v)
                    action()
                    handler.postDelayed(repeater, 400)
                    v.isPressed = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(repeater)
                    v.isPressed = false
                }
            }
            true
        }
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
