package com.sanlin.mkeyboard.keyboard.model

import android.content.Context
import androidx.core.content.ContextCompat
import com.sanlin.mkeyboard.R
import com.sanlin.mkeyboard.config.KeyboardConfig
import com.sanlin.mkeyboard.keyboard.KeyboardHeightCalculator
import com.sanlin.mkeyboard.keyboard.data.FlickKeyboardParser

/**
 * Model class representing the complete flick keyboard layout.
 *
 * Structure:
 * - 4 rows x 5 columns layout (all keys equal size):
 *   - Column 0: Left side keys (row 2: ?123, row 3: globe)
 *   - Columns 1-3: Main flick keys (12 keys, loaded from XML)
 *   - Column 4: Right side keys (row 0: del, row 1: space, row 2: enter)
 *
 * @param context The context to access resources
 * @param layoutResId XML resource ID for the flick keyboard layout (default: Myanmar)
 */
class FlickKeyboard(
    private val context: Context,
    private val layoutResId: Int = R.xml.my_flick
) {

    /** The main flick keys (center 3 columns, 12 keys). */
    val flickKeys: List<FlickKey>

    /** Left side normal keys (column 0, 4 keys). */
    val leftSideKeys: MutableList<Key> = mutableListOf()

    /** Right side normal keys (column 4, 4 keys). */
    val rightSideKeys: MutableList<Key> = mutableListOf()

    /** Whether the keyboard is in shifted state. */
    var isShifted: Boolean = false

    /** Shifted keys (3x4 grid of extra characters, tap only). */
    val shiftedKeys: MutableList<Key> = mutableListOf()

    /** Total width of the keyboard. */
    val totalWidth: Int

    /** Total height of the keyboard. */
    val totalHeight: Int

    /** Width of keys. */
    val keyWidth: Int

    /** Height of keys (reduced to ~80% of width). */
    val keyHeight: Int

    /** Width/Height of main flick keys (for compatibility). */
    val flickKeySize: Int
        get() = keyWidth

    /** Width of side keys. */
    val sideKeyWidth: Int
        get() = keyWidth

    /** Number of main flick columns. */
    val mainColumns = 3

    /** Number of rows of flick keys. */
    val flickRows = 4

    /** Vertical gap between rows. */
    private val verticalGap: Int

    /** Horizontal gap between keys. */
    private val horizontalGap: Int

    /** X offset for one-handed mode (0 for full/left, positive for right). */
    val offsetX: Int

    /** The width of the key layout area (75% of screen in compact mode, full otherwise). */
    val layoutWidth: Int

    /** The full screen width (view always fills screen). */
    private val screenWidth: Int

    init {
        val displayMetrics = context.resources.displayMetrics
        screenWidth = displayMetrics.widthPixels

        // Determine compact mode from config
        val handMode = KeyboardConfig.getEffectiveFlickHandMode()
        val isCompact = handMode != "full"
        val compactRatio = KeyboardConfig.getFlickCompactSize() / 100f
        layoutWidth = if (isCompact) (screenWidth * compactRatio).toInt() else screenWidth
        offsetX = when (handMode) {
            "right" -> screenWidth - layoutWidth
            else -> 0  // "full" or "left"
        }

        // View still fills the screen; totalWidth is the layout area for key sizing
        totalWidth = screenWidth

        // Gap is 1% of layout width (same for horizontal and vertical)
        val gap = (layoutWidth * 0.01f).toInt().coerceAtLeast(4)
        verticalGap = gap
        horizontalGap = gap

        // Calculate key width for 5 columns within the layout area
        // layoutWidth = 5*keyWidth + 4*gap
        keyWidth = (layoutWidth - (4 * gap)) / 5

        // Key height is ~80% of width (reduced by 20%)
        keyHeight = (keyWidth * 0.80f).toInt()

        // Total height - proportional to the layout width (compact keys = shorter keyboard)
        totalHeight = KeyboardHeightCalculator.getKeyboardHeightForWidth(layoutWidth)

        // Load the main flick keys from XML layout
        flickKeys = FlickKeyboardParser.parse(context, layoutResId)

        // Calculate positions for all keys
        calculateFlickKeyPositions()

        // Create side keys
        createSideKeys()

        // Create shifted keys
        createShiftedKeys()
    }

    /**
     * Calculate and set the x, y, width, height for main flick keys.
     * Layout: [side][gap][main][gap][main][gap][main][gap][side]
     */
    private fun calculateFlickKeyPositions() {
        // Starting X for main keys (column 1, after left side key + gap)
        val mainStartX = offsetX + keyWidth + horizontalGap

        // Calculate main flick keys (columns 1-3) with gaps between them
        var keyIndex = 0
        for (row in 0 until flickRows) {
            for (col in 0 until mainColumns) {
                if (keyIndex < flickKeys.size) {
                    val key = flickKeys[keyIndex]
                    // Each main key is offset by (keyWidth + gap) * col
                    key.x = mainStartX + (col * (keyWidth + horizontalGap))
                    key.y = row * (keyHeight + verticalGap)
                    key.width = keyWidth
                    key.height = keyHeight
                    keyIndex++
                }
            }
        }
    }

    /**
     * Create side keys (normal keys, not flick keys).
     * Left side: row 2 = ?123, row 3 = globe
     * Right side: row 0 = del, row 1 = space, row 2 = enter
     */
    private fun createSideKeys() {
        // Left side key X position
        val leftStartX = offsetX

        // Right side key X position (column 4)
        val rightStartX = offsetX + (keyWidth + horizontalGap) * 4

        // Load icons
        val shiftIcon = ContextCompat.getDrawable(context, R.drawable.sym_keyboard_shift)
        val deleteIcon = ContextCompat.getDrawable(context, R.drawable.sym_keyboard_delete)
        val returnIcon = ContextCompat.getDrawable(context, R.drawable.sym_keyboard_return)
        val languageSwitchIcon = ContextCompat.getDrawable(context, R.drawable.sym_keyboard_language_switch)
        val spaceIcon = ContextCompat.getDrawable(context, R.drawable.sym_keyboard_space)
        val emojiIcon = ContextCompat.getDrawable(context, R.drawable.sym_keyboard_emoji)

        for (row in 0 until flickRows) {
            val rowY = row * (keyHeight + verticalGap)

            // Left side key (column 0)
            val leftKey = when (row) {
                1 -> Key(  // Row 1: Mode change (?123)
                    codes = intArrayOf(Key.KEYCODE_MODE_CHANGE),
                    label = "?123",
                    x = leftStartX,
                    y = rowY,
                    width = keyWidth,
                    height = keyHeight,
                    repeatable = false,
                    modifier = true  // Mark as special/function key
                )
                2 -> Key(  // Row 2: Emoji keyboard
                    codes = intArrayOf(Key.KEYCODE_EMOJI),
                    icon = emojiIcon,
                    x = leftStartX,
                    y = rowY,
                    width = keyWidth,
                    height = keyHeight,
                    repeatable = false,
                    modifier = true
                )
                3 -> Key(  // Row 3: Globe for subtype switch
                    codes = intArrayOf(Key.KEYCODE_SWITCH_KEYBOARD),
                    icon = languageSwitchIcon,
                    x = leftStartX,
                    y = rowY,
                    width = keyWidth,
                    height = keyHeight,
                    repeatable = false,
                    modifier = true
                )
                else -> Key(  // Row 0: Shift key
                    codes = intArrayOf(Key.KEYCODE_SHIFT),
                    icon = shiftIcon,
                    x = leftStartX,
                    y = rowY,
                    width = keyWidth,
                    height = keyHeight,
                    repeatable = false,
                    modifier = true
                )
            }
            leftSideKeys.add(leftKey)

            // Right side key (column 4)
            val rightKey = when (row) {
                0 -> Key(  // Row 0: Delete
                    codes = intArrayOf(Key.KEYCODE_DELETE),
                    icon = deleteIcon,
                    x = rightStartX,
                    y = rowY,
                    width = keyWidth,
                    height = keyHeight,
                    repeatable = true,
                    modifier = true
                )
                1 -> Key(  // Row 1: Space
                    codes = intArrayOf(32),  // ASCII space
                    icon = spaceIcon,
                    x = rightStartX,
                    y = rowY,
                    width = keyWidth,
                    height = keyHeight,
                    repeatable = false,
                    modifier = true
                )
                2 -> Key(  // Row 2: Enter
                    codes = intArrayOf(Key.KEYCODE_DONE),
                    icon = returnIcon,
                    x = rightStartX,
                    y = rowY,
                    width = keyWidth,
                    height = keyHeight,
                    repeatable = false,
                    modifier = true
                )
                3 -> Key(  // Row 3: Punctuation (၊/။) - single tap: ၊, double tap: ။
                    codes = intArrayOf(Key.KEYCODE_PUNCTUATION),
                    label = "။ ၊",  // Show both characters with space
                    x = rightStartX,
                    y = rowY,
                    width = keyWidth,
                    height = keyHeight,
                    repeatable = false,
                    modifier = true,
                    popupCharacters = "၊။"  // For popup preview
                )
                else -> Key(  // Other rows: placeholder
                    codes = intArrayOf(0),
                    label = "",
                    x = rightStartX,
                    y = rowY,
                    width = keyWidth,
                    height = keyHeight,
                    repeatable = false
                )
            }
            rightSideKeys.add(rightKey)
        }
    }

    /**
     * Get the key (flick or side) at the given coordinates.
     *
     * @return Either a FlickKey or Key, or null if no key at position
     */
    fun getKeyAt(x: Int, y: Int): Any? {
        // Check flick keys first
        getFlickKeyAt(x, y)?.let { return it }

        // Check side keys (normal keys)
        return getSideKeyAt(x, y)
    }

    /**
     * Get the flick key at the given coordinates.
     * Only checks main flick keys (center 3 columns).
     */
    fun getFlickKeyAt(x: Int, y: Int): FlickKey? {
        for (key in flickKeys) {
            if (key.isInside(x, y)) {
                return key
            }
        }
        return null
    }

    /**
     * Get the side key (normal key) at the given coordinates.
     */
    fun getSideKeyAt(x: Int, y: Int): Key? {
        for (key in leftSideKeys) {
            if (key.isInside(x, y)) {
                return key
            }
        }
        for (key in rightSideKeys) {
            if (key.isInside(x, y)) {
                return key
            }
        }
        return null
    }

    /**
     * Get all side keys (left + right) for rendering.
     */
    fun getAllSideKeys(): List<Key> {
        return leftSideKeys + rightSideKeys
    }

    /**
     * Create shifted keys (3x4 grid of extra characters).
     * These replace the main flick keys when shift is active.
     */
    private fun createShiftedKeys() {
        val shiftedLabels = arrayOf(
            "၎င်း", "င်္", "ဏ္ဍ",
            "ဪ", "ဋ္ဌ", "ဏ္ဌ",
            "+", "×", ".",
            "-", "÷", ","
        )

        val mainStartX = offsetX + keyWidth + horizontalGap

        var index = 0
        for (row in 0 until flickRows) {
            for (col in 0 until mainColumns) {
                val label = shiftedLabels[index]
                val key = Key(
                    codes = intArrayOf(0),  // We use text field instead
                    label = label,
                    text = label,
                    x = mainStartX + (col * (keyWidth + horizontalGap)),
                    y = row * (keyHeight + verticalGap),
                    width = keyWidth,
                    height = keyHeight,
                    repeatable = false
                )
                shiftedKeys.add(key)
                index++
            }
        }
    }

    /**
     * Get the shifted key at the given coordinates.
     */
    fun getShiftedKeyAt(x: Int, y: Int): Key? {
        for (key in shiftedKeys) {
            if (key.isInside(x, y)) {
                return key
            }
        }
        return null
    }

    /**
     * Get the height of the flick key area (without padding).
     */
    fun getFlickAreaHeight(): Int {
        return flickRows * (keyHeight + verticalGap)
    }

    /**
     * Toggle shift state.
     */
    fun toggleShift() {
        isShifted = !isShifted
    }

    /**
     * Get total width for layout.
     */
    fun getWidth(): Int = totalWidth

    /**
     * Get total height for layout.
     */
    fun getHeight(): Int = totalHeight
}
