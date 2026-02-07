package com.sanlin.mkeyboard.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.sanlin.mkeyboard.R
import com.sanlin.mkeyboard.config.KeyboardConfig
import com.sanlin.mkeyboard.suggestion.Suggestion

/**
 * A horizontal scrollable bar that displays word suggestions.
 * Suggestions are shown as chips that can be tapped to insert the word.
 */
class SuggestionBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val chipContainer: LinearLayout
    private var onSuggestionClickListener: ((String) -> Unit)? = null

    // Dimensions
    private val barHeight: Int
    private val chipPaddingHorizontal: Int
    private val chipPaddingVertical: Int
    private val chipMargin: Int
    private val chipTextSize: Float
    private val barPaddingHorizontal: Int

    init {
        // Calculate dimensions based on density
        // Myanmar characters are taller than English, so we need more height
        val density = resources.displayMetrics.density
        barHeight = (48 * density).toInt()  // Increased for Myanmar text
        chipPaddingHorizontal = (12 * density).toInt()  // Reduced horizontal padding
        chipPaddingVertical = (4 * density).toInt()  // Reduced vertical padding
        chipMargin = (4 * density).toInt()  // Reduced margin
        chipTextSize = 16f
        barPaddingHorizontal = (8 * density).toInt()

        // Configure scroll view
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        overScrollMode = OVER_SCROLL_NEVER

        // Create container for chips
        chipContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(barPaddingHorizontal, 0, barPaddingHorizontal, 0)
        }

        addView(chipContainer, LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT
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
        chipContainer.removeAllViews()

        // Bar is always visible, just empty when no suggestions
        val isLight = isLightTheme(KeyboardConfig.getCurrentTheme())

        for (suggestion in suggestions) {
            val chip = createChip(suggestion.word, isLight)
            chipContainer.addView(chip)
        }

        // Center chips when fewer than 4 suggestions
        if (suggestions.size < 4) {
            chipContainer.gravity = Gravity.CENTER
            isFillViewport = true
        } else {
            chipContainer.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            isFillViewport = false
        }

        // Scroll to start
        scrollTo(0, 0)
    }

    /**
     * Clear all suggestions.
     */
    fun clearSuggestions() {
        chipContainer.removeAllViews()
        // Bar stays visible, just empty - keep centered
        chipContainer.gravity = Gravity.CENTER
        isFillViewport = true
    }

    /**
     * Set the listener for suggestion clicks.
     * @param listener callback that receives the selected word
     */
    fun setOnSuggestionClickListener(listener: (String) -> Unit) {
        onSuggestionClickListener = listener
    }

    private fun createChip(word: String, isLight: Boolean): TextView {
        return TextView(context).apply {
            text = word
            setTextSize(TypedValue.COMPLEX_UNIT_SP, chipTextSize)
            setTypeface(typeface, Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(chipPaddingHorizontal, chipPaddingVertical, chipPaddingHorizontal, chipPaddingVertical)
            applyChipTheme(this, isLight)

            // Ensure Myanmar text has enough line height
            includeFontPadding = true

            // Set layout params with margins
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(chipMargin, chipMargin, chipMargin, chipMargin)
                gravity = Gravity.CENTER_VERTICAL
            }

            // Make clickable
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
