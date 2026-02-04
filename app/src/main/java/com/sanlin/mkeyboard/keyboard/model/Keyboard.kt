package com.sanlin.mkeyboard.keyboard.model

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import com.sanlin.mkeyboard.config.KeyboardConfig
import com.sanlin.mkeyboard.keyboard.KeyboardHeightCalculator
import com.sanlin.mkeyboard.keyboard.parser.KeyboardXmlParser

/**
 * Keyboard model class representing a complete keyboard layout.
 * Simplified from AOSP Keyboard for MUA-Keyboard needs.
 *
 * This class loads keyboard layouts from XML resources and provides
 * the data model for the keyboard view to render.
 */
open class Keyboard(
    private val context: Context,
    xmlLayoutResId: Int,
    modeId: Int = 0
) {
    /** List of all rows in this keyboard. */
    val rows: MutableList<Row> = mutableListOf()

    /** Flattened list of all keys for easy access. */
    val keys: MutableList<Key> = mutableListOf()

    /** Total width of the keyboard (device screen width). */
    var totalWidth: Int = 0
        private set

    /** Total height of the keyboard. */
    var totalHeight: Int = 0
        private set

    /** Default width of a key. */
    var defaultKeyWidth: Int = 0
        private set

    /** Default height of a key. */
    var defaultKeyHeight: Int = 0
        private set

    /** Default horizontal gap between keys. */
    var defaultHorizontalGap: Int = 0
        private set

    /** Default vertical gap between rows. */
    var defaultVerticalGap: Int = 0
        private set

    /** Whether the keyboard is in shifted state. */
    var isShifted: Boolean = false
        set(value) {
            field = value
            // Update shift key state
            for (key in keys) {
                if (key.codes.isNotEmpty() && key.codes[0] == Key.KEYCODE_SHIFT) {
                    key.on = value
                }
            }
        }

    /** Reference to the space bar key for updating labels. */
    private var spaceKey: Key? = null

    /** Display metrics for percentage and dp-to-pixel calculations. */
    private val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    private val displayWidth: Int
    private val displayHeight: Int

    init {
        displayWidth = displayMetrics.widthPixels
        displayHeight = displayMetrics.heightPixels
        totalWidth = displayWidth

        // Parse the keyboard XML
        KeyboardXmlParser.parse(context, xmlLayoutResId, this)
    }

    /**
     * Set dimensions for the keyboard from the XML parser.
     */
    internal fun setDimensions(
        keyWidth: Int,
        keyHeight: Int,
        horizontalGap: Int,
        verticalGap: Int
    ) {
        defaultKeyWidth = keyWidth
        defaultKeyHeight = keyHeight
        defaultHorizontalGap = horizontalGap
        defaultVerticalGap = verticalGap
    }

    /**
     * Add a row to the keyboard.
     */
    internal fun addRow(row: Row) {
        rows.add(row)
        keys.addAll(row.keys)
    }

    /**
     * Called when parsing is complete to finalize keyboard dimensions.
     */
    internal fun finalizeParsing() {
        // Calculate the standard keyboard height (same as flick keyboard)
        val standardHeight = KeyboardHeightCalculator.getStandardKeyboardHeight(context)

        // Calculate total key height (without gaps)
        var totalKeyHeight = 0
        for (row in rows) {
            if (row.keys.isNotEmpty()) {
                totalKeyHeight += row.defaultHeight
            }
        }

        // Calculate adjusted vertical gap to match standard height
        val rowCount = rows.count { it.keys.isNotEmpty() }
        val gapCount = rowCount - 1
        val adjustedVerticalGap = if (gapCount > 0) {
            val requiredGapSpace = standardHeight - totalKeyHeight
            (requiredGapSpace / gapCount).coerceAtLeast(0)
        } else {
            defaultVerticalGap
        }

        // Apply adjusted vertical gap to all rows
        for (row in rows) {
            row.verticalGap = adjustedVerticalGap
        }

        // Calculate total height and fix key positions with adjusted gaps
        var currentY = 0
        for (row in rows) {
            for (key in row.keys) {
                key.y = currentY
            }
            if (row.keys.isNotEmpty()) {
                // Extend the last key in each row to fill the remaining width
                val lastKey = row.keys.last()
                val currentEndX = lastKey.x + lastKey.width
                if (currentEndX < totalWidth) {
                    lastKey.width = totalWidth - lastKey.x
                }
                currentY += row.defaultHeight + row.verticalGap
            }
        }
        totalHeight = currentY

        // Find and store reference to space key
        for (key in keys) {
            if (key.codes.isNotEmpty() && key.codes[0] == 32) { // Space key code
                spaceKey = key
                break
            }
        }
    }

    /**
     * Set the label and icon for the space bar to display the current subtype name.
     */
    fun setSpaceBarSubtypeName(subtypeName: String?, drawable: Drawable?) {
        spaceKey?.let { key ->
            key.label = subtypeName
            key.icon = drawable
        }
    }

    /**
     * Get the key at the given position, or the nearest key within threshold.
     *
     * @param x Touch X coordinate
     * @param y Touch Y coordinate
     * @param useProximity If true, find nearest key when touch is between keys (default: true)
     * @return The key at the position, or nearest key within threshold, or null
     */
    fun getKeyAt(x: Int, y: Int, useProximity: Boolean = true): Key? {
        // First, try exact match
        for (key in keys) {
            if (key.isInside(x, y)) {
                return key
            }
        }

        // If no exact match and proximity enabled, find nearest key
        if (useProximity && KeyboardConfig.isProximityEnabled()) {
            val thresholdPx = KeyboardConfig.getProximityThresholdDp() * displayMetrics.density
            return getNearestKey(x, y, thresholdPx)
        }

        return null
    }

    /**
     * Find the nearest key within the given threshold distance.
     *
     * @param x Touch X coordinate
     * @param y Touch Y coordinate
     * @param threshold Maximum distance in pixels to consider a key
     * @return The nearest key within threshold, or null if none found
     */
    private fun getNearestKey(x: Int, y: Int, threshold: Float): Key? {
        var nearestKey: Key? = null
        var minDistance = threshold

        for (key in keys) {
            val distance = key.distanceToEdge(x, y)
            if (distance < minDistance) {
                minDistance = distance
                nearestKey = key
            }
        }

        return nearestKey
    }

    /**
     * Get the primary code for a key.
     */
    fun getPrimaryCode(key: Key): Int {
        return if (key.codes.isNotEmpty()) key.codes[0] else 0
    }

    /**
     * Get the width of the keyboard.
     */
    fun getWidth(): Int = totalWidth

    /**
     * Get the height of the keyboard.
     */
    fun getHeight(): Int = totalHeight

    companion object {
        // Key codes matching AOSP Keyboard.KEYCODE_* constants for backwards compatibility
        const val KEYCODE_SHIFT = Key.KEYCODE_SHIFT
        const val KEYCODE_MODE_CHANGE = Key.KEYCODE_MODE_CHANGE
        const val KEYCODE_CANCEL = Key.KEYCODE_CANCEL
        const val KEYCODE_DONE = Key.KEYCODE_DONE
        const val KEYCODE_DELETE = Key.KEYCODE_DELETE
        const val KEYCODE_ALT = Key.KEYCODE_ALT
    }
}
