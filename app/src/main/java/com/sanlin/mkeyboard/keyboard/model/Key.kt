package com.sanlin.mkeyboard.keyboard.model

import android.graphics.drawable.Drawable
import kotlin.math.sqrt

/**
 * Key data class representing a single key on the keyboard.
 * Simplified from AOSP Keyboard.Key for MUA-Keyboard needs.
 */
data class Key(
    /** All the key codes (unicode or custom) that this key outputs. */
    var codes: IntArray = intArrayOf(0),

    /** Label to display on the key. */
    var label: CharSequence? = null,

    /** Icon to display on the key instead of a label. */
    var icon: Drawable? = null,

    /** Icon to display when the key is pressed. */
    var iconPreview: Drawable? = null,

    /** Width of the key in pixels. */
    var width: Int = 0,

    /** Height of the key in pixels. */
    var height: Int = 0,

    /** The horizontal gap before this key. */
    var gap: Int = 0,

    /** X coordinate of the key in the keyboard layout. */
    var x: Int = 0,

    /** Y coordinate of the key in the keyboard layout. */
    var y: Int = 0,

    /** Whether this is a sticky/toggle key like Caps Lock. */
    var sticky: Boolean = false,

    /** Whether this is a modifier key like Shift. */
    var modifier: Boolean = false,

    /** Whether the key is on (toggled). */
    var on: Boolean = false,

    /** Whether the key is currently pressed. */
    var pressed: Boolean = false,

    /** Whether this key is repeatable (like backspace). */
    var repeatable: Boolean = false,

    /** Text to output when this key is pressed (for multiple characters). */
    var text: CharSequence? = null,

    /** Popup characters for long-press popup. */
    var popupCharacters: CharSequence? = null,

    /** Resource ID of the popup keyboard layout. */
    var popupResId: Int = 0,

    /** Edge flags for the key (left, right, top, bottom). */
    var edgeFlags: Int = 0
) {
    companion object {
        /** Edge flag indicating key is on the left edge. */
        const val EDGE_LEFT = 0x01
        /** Edge flag indicating key is on the right edge. */
        const val EDGE_RIGHT = 0x02
        /** Edge flag indicating key is on the top edge. */
        const val EDGE_TOP = 0x04
        /** Edge flag indicating key is on the bottom edge. */
        const val EDGE_BOTTOM = 0x08

        // Standard key codes matching AOSP Keyboard.KEYCODE_* constants
        const val KEYCODE_SHIFT = -1
        const val KEYCODE_MODE_CHANGE = -2
        const val KEYCODE_CANCEL = -3
        const val KEYCODE_DONE = -4
        const val KEYCODE_DELETE = -5
        const val KEYCODE_ALT = -6

        // Custom key codes for MUA Keyboard
        const val KEYCODE_SWITCH_KEYBOARD = -101
        const val KEYCODE_EMOJI = -202
        const val KEYCODE_BACK_FROM_EMOJI = -3
        const val KEYCODE_MYANMAR_DELETE = 300000
        const val KEYCODE_MYANMAR_MONEY = 300001
        const val KEYCODE_SHAN_VOWEL = 42264154
        const val KEYCODE_PUNCTUATION = -301  // Myanmar punctuation key (၊/။)
    }

    /**
     * Check if the key is within the given rectangle.
     */
    fun isInside(touchX: Int, touchY: Int): Boolean {
        return touchX >= x && touchX < x + width &&
                touchY >= y && touchY < y + height
    }

    /**
     * Calculate the minimum distance from a touch point to this key's edges.
     * Returns 0 if the point is inside the key.
     *
     * @param touchX Touch X coordinate
     * @param touchY Touch Y coordinate
     * @return Distance in pixels to the nearest edge, or 0 if inside
     */
    fun distanceToEdge(touchX: Int, touchY: Int): Float {
        // If inside the key, distance is 0
        if (isInside(touchX, touchY)) return 0f

        // Calculate horizontal distance to nearest edge
        val dx = when {
            touchX < x -> x - touchX
            touchX > x + width -> touchX - (x + width)
            else -> 0
        }

        // Calculate vertical distance to nearest edge
        val dy = when {
            touchY < y -> y - touchY
            touchY > y + height -> touchY - (y + height)
            else -> 0
        }

        // Euclidean distance to nearest edge
        return sqrt((dx * dx + dy * dy).toFloat())
    }

    /**
     * Get the current drawable state for key rendering.
     */
    fun getCurrentDrawableState(): IntArray {
        return when {
            on -> if (pressed) KEY_STATE_PRESSED_ON else KEY_STATE_NORMAL_ON
            sticky -> if (pressed) KEY_STATE_PRESSED_OFF else KEY_STATE_NORMAL_OFF
            modifier -> if (pressed) KEY_STATE_FUNCTION_PRESSED else KEY_STATE_FUNCTION
            pressed -> KEY_STATE_PRESSED
            else -> KEY_STATE_NORMAL
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Key
        if (!codes.contentEquals(other.codes)) return false
        if (label != other.label) return false
        if (x != other.x) return false
        if (y != other.y) return false
        return true
    }

    override fun hashCode(): Int {
        var result = codes.contentHashCode()
        result = 31 * result + (label?.hashCode() ?: 0)
        result = 31 * result + x
        result = 31 * result + y
        return result
    }
}

// Drawable state constants
private val KEY_STATE_NORMAL_ON = intArrayOf(
    android.R.attr.state_checkable,
    android.R.attr.state_checked
)

private val KEY_STATE_PRESSED_ON = intArrayOf(
    android.R.attr.state_pressed,
    android.R.attr.state_checkable,
    android.R.attr.state_checked
)

private val KEY_STATE_NORMAL_OFF = intArrayOf(
    android.R.attr.state_checkable
)

private val KEY_STATE_PRESSED_OFF = intArrayOf(
    android.R.attr.state_pressed,
    android.R.attr.state_checkable
)

private val KEY_STATE_FUNCTION = intArrayOf(
    android.R.attr.state_single
)

private val KEY_STATE_FUNCTION_PRESSED = intArrayOf(
    android.R.attr.state_pressed,
    android.R.attr.state_single
)

private val KEY_STATE_NORMAL = intArrayOf()

private val KEY_STATE_PRESSED = intArrayOf(
    android.R.attr.state_pressed
)
