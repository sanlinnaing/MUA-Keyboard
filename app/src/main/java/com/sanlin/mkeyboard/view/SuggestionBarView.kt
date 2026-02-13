package com.sanlin.mkeyboard.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spannable
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.util.Log
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.sanlin.mkeyboard.R
import com.sanlin.mkeyboard.config.KeyboardConfig
import com.sanlin.mkeyboard.suggestion.Suggestion

/**
 * A horizontal scrollable bar that displays word suggestions.
 * Suggestions are shown as chips that can be tapped to insert the word.
 * Clipboard icon is shown on the left when clipboard has content.
 */
class SuggestionBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val clipboardButton: TextView
    private val scrollView: HorizontalScrollView
    private val chipContainer: LinearLayout
    private var onSuggestionClickListener: ((String) -> Unit)? = null
    private var onPasteClickListener: (() -> Unit)? = null
    private var clipboardText: String? = null
    private var isInitialState = true  // True until user types something
    var isPasted = false  // True after user pastes from clipboard chip

    // Dimensions
    private val barHeight: Int
    private val chipPaddingHorizontal: Int
    private val chipPaddingVertical: Int
    private val chipMargin: Int
    private val chipTextSize: Float
    private val barPaddingHorizontal: Int

    init {
        // Calculate dimensions based on density
        val density = resources.displayMetrics.density
        barHeight = (48 * density).toInt()
        chipPaddingHorizontal = (12 * density).toInt()
        chipPaddingVertical = (4 * density).toInt()
        chipMargin = (4 * density).toInt()
        chipTextSize = 16f
        barPaddingHorizontal = (8 * density).toInt()

        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        // Create clipboard button (left side, initially hidden)
        clipboardButton = TextView(context).apply {
            text = "\uD83D\uDCCB"  // Clipboard emoji
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setPadding(chipPaddingHorizontal, chipPaddingVertical, chipPaddingHorizontal, chipPaddingVertical)
            visibility = View.GONE

            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(chipMargin, 0, 0, 0)
                gravity = Gravity.CENTER_VERTICAL
            }

            isClickable = true
            isFocusable = true

            setOnClickListener {
                onPasteClickListener?.invoke()
            }
        }
        addView(clipboardButton)

        // Create scroll view for suggestions
        scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = OVER_SCROLL_NEVER
        }

        // Create container for chips inside scroll view
        chipContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(barPaddingHorizontal, 0, barPaddingHorizontal, 0)
        }

        scrollView.addView(chipContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        addView(scrollView, LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            1f  // Take remaining space
        ))

        // Apply theme
        applyTheme()
    }

    /**
     * Apply the current keyboard theme to the suggestion bar.
     */
    fun applyTheme() {
        val theme = KeyboardConfig.getCurrentTheme()
        val isLightTheme = isLightTheme(theme)

        setBackgroundColor(getBarBackgroundColor(theme))

        // Update clipboard button theme
        clipboardButton.setBackgroundResource(getChipBackgroundRes(isLightTheme))

        // Update existing chips
        for (i in 0 until chipContainer.childCount) {
            val chip = chipContainer.getChildAt(i) as? TextView
            chip?.let { applyChipTheme(it, isLightTheme) }
        }
    }

    private fun isLightTheme(theme: Int): Boolean {
        return when (theme) {
            6 -> true  // Light theme
            1 -> !isSystemInDarkMode()  // Default follows system
            else -> false
        }
    }

    private fun isSystemInDarkMode(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun getBarBackgroundColor(theme: Int): Int {
        return when (theme) {
            6 -> Color.parseColor("#D8D8DE")  // Light - slightly darker than keyboard
            5 -> Color.parseColor("#231E12")  // Golden Yellow
            4 -> Color.parseColor("#151724")  // Blue Gray
            3 -> Color.parseColor("#152415")  // Green
            2 -> Color.parseColor("#0A0A0A")  // Dark
            1 -> {
                if (isSystemInDarkMode()) {
                    Color.parseColor("#151515")
                } else {
                    Color.parseColor("#D8D8DE")
                }
            }
            else -> Color.parseColor("#151515")
        }
    }

    private fun getChipBackgroundRes(isLight: Boolean): Int {
        return if (isLight) {
            R.drawable.suggestion_chip_bg_light
        } else {
            R.drawable.suggestion_chip_bg
        }
    }

    private fun getChipTextColor(isLight: Boolean): Int {
        return if (isLight) {
            Color.parseColor("#1A1A1A")
        } else {
            Color.parseColor("#E0E0E0")
        }
    }

    private fun applyChipTheme(chip: TextView, isLight: Boolean) {
        chip.setBackgroundResource(getChipBackgroundRes(isLight))
        chip.setTextColor(getChipTextColor(isLight))
    }

    /**
     * Set the suggestions to display.
     * @param suggestions list of suggestions
     */
    fun setSuggestions(suggestions: List<Suggestion>) {
        Log.d("SuggestionBarView", "setSuggestions: ${suggestions.size} suggestions")
        chipContainer.removeAllViews()

        // User has started typing if we're getting suggestions
        if (suggestions.isNotEmpty()) {
            isInitialState = false
        }

        // Update clipboard button visibility
        updateClipboardButton()

        if (suggestions.isEmpty()) {
            // Show centered paste chip only in initial state
            if (isInitialState) {
                showInitialClipboardChip()
            }
            return
        }

        val isLight = isLightTheme(KeyboardConfig.getCurrentTheme())

        // Add chips with flexible spacers for centering
        for ((index, suggestion) in suggestions.withIndex()) {
            if (index == 0) {
                chipContainer.addView(createFlexSpacer())
            }

            val chip = createChip(suggestion, isLight)
            chipContainer.addView(chip)

            chipContainer.addView(createFlexSpacer())
        }

        scrollView.isFillViewport = true
        chipContainer.gravity = Gravity.CENTER_VERTICAL
        scrollView.scrollTo(0, 0)
    }

    /**
     * Set the clipboard text.
     */
    fun setClipboardText(text: String?) {
        clipboardText = text?.trim()?.takeIf { it.isNotEmpty() }
        updateClipboardButton()
        // Refresh content preview if in initial state and not yet pasted
        if (isInitialState && !isPasted) {
            chipContainer.removeAllViews()
            showInitialClipboardChip()
        }
    }

    /**
     * Update clipboard button visibility.
     */
    private fun updateClipboardButton() {
        val hasClipboard = !clipboardText.isNullOrEmpty()
        // Show clipboard icon when: has content AND (already pasted OR user started typing)
        clipboardButton.visibility = if (hasClipboard && (isPasted || !isInitialState)) View.VISIBLE else View.GONE
    }

    /**
     * Show centered paste chip with clipboard preview (initial state, before paste).
     */
    private fun showInitialClipboardChip() {
        val text = clipboardText
        if (text.isNullOrEmpty()) return
        if (isPasted) return  // Already pasted, just show icon

        val isLight = isLightTheme(KeyboardConfig.getCurrentTheme())

        chipContainer.addView(createFlexSpacer())

        val pasteChip = createPasteChip(text, isLight)
        chipContainer.addView(pasteChip)

        chipContainer.addView(createFlexSpacer())

        scrollView.isFillViewport = true
        chipContainer.gravity = Gravity.CENTER_VERTICAL
    }

    /**
     * Mark paste as done. Hides the content preview chip and shows just the icon.
     */
    fun markPasted() {
        isPasted = true
        chipContainer.removeAllViews()
        updateClipboardButton()
    }

    /**
     * Set listener for paste button clicks.
     */
    fun setOnPasteClickListener(listener: () -> Unit) {
        onPasteClickListener = listener
    }

    /**
     * Create a paste chip showing clipboard preview.
     */
    private fun createPasteChip(clipText: String, isLight: Boolean): TextView {
        val density = resources.displayMetrics.density
        val chipMaxWidth = (200 * density).toInt()

        return TextView(context).apply {
            val displayText = if (clipText.length > 30) {
                clipText.take(30) + "…"
            } else {
                clipText
            }
            text = "\uD83D\uDCCB $displayText"

            setTextSize(TypedValue.COMPLEX_UNIT_SP, chipTextSize - 1)
            setTypeface(typeface, Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(chipPaddingHorizontal, chipPaddingVertical, chipPaddingHorizontal, chipPaddingVertical)

            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setMaxWidth(chipMaxWidth)

            applyChipTheme(this, isLight)
            alpha = 0.85f

            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(chipMargin, chipMargin, chipMargin, chipMargin)
                gravity = Gravity.CENTER_VERTICAL
            }

            isClickable = true
            isFocusable = true

            setOnClickListener {
                onPasteClickListener?.invoke()
            }
        }
    }

    /**
     * Create a flexible spacer view.
     */
    private fun createFlexSpacer(): View {
        return View(context).apply {
            layoutParams = LayoutParams(0, 1, 1f)
        }
    }

    /**
     * Clear all suggestions.
     */
    fun clearSuggestions() {
        chipContainer.removeAllViews()
        updateClipboardButton()
        if (isInitialState) {
            showInitialClipboardChip()
        }
        scrollView.isFillViewport = true
    }

    /**
     * Reset to initial state (call when input field changes).
     */
    fun resetState() {
        isInitialState = true
        // isPasted is NOT reset here — it persists until clipboard content changes
        chipContainer.removeAllViews()
        updateClipboardButton()
        showInitialClipboardChip()
    }

    /**
     * Set the listener for suggestion clicks.
     */
    fun setOnSuggestionClickListener(listener: (String) -> Unit) {
        onSuggestionClickListener = listener
    }

    private fun createChip(suggestion: Suggestion, isLight: Boolean): TextView {
        val word = suggestion.word
        return TextView(context).apply {
            // Add colored dot indicator for boosted/user-dict suggestions
            if (suggestion.fromUserDict || suggestion.boosted) {
                val indicator = if (suggestion.fromUserDict) " \u25C6" else " \u25CF"  // ◆ or ●
                val spannable = SpannableString("$word$indicator")
                val indicatorColor = if (suggestion.fromUserDict) {
                    Color.parseColor("#4CAF50")  // Green for user dict
                } else {
                    Color.parseColor("#42A5F5")  // Blue for personalized
                }
                spannable.setSpan(
                    ForegroundColorSpan(indicatorColor),
                    word.length,
                    spannable.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                text = spannable
            } else {
                text = word
            }

            setTextSize(TypedValue.COMPLEX_UNIT_SP, chipTextSize)
            setTypeface(typeface, Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(chipPaddingHorizontal, chipPaddingVertical, chipPaddingHorizontal, chipPaddingVertical)
            applyChipTheme(this, isLight)

            includeFontPadding = true

            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(chipMargin, chipMargin, chipMargin, chipMargin)
                gravity = Gravity.CENTER_VERTICAL
            }

            isClickable = true
            isFocusable = true

            setOnClickListener {
                onSuggestionClickListener?.invoke(word)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightSpec = MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, heightSpec)
    }
}
