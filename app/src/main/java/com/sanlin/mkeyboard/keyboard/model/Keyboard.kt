package com.sanlin.mkeyboard.keyboard.model

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
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

    /** Pixel width of the split gap (0 if not split). View reads this for divider drawing. */
    var splitGapPx: Int = 0
        private set

    /** Scale factor applied to key heights (< 1.0 when keys shrunk in landscape). View uses this for font scaling. */
    var keyHeightScale: Float = 1f
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

        // Account for display cutout (e.g., punch-hole camera in landscape)
        val cutoutInset = getDisplayCutoutHorizontalInset(context)
        totalWidth = displayWidth - cutoutInset

        // Parse the keyboard XML with cutout-safe width
        KeyboardXmlParser.parse(context, xmlLayoutResId, this, totalWidth)

        // Apply split layout if enabled (check orientation for auto mode)
        val isLandscape = displayWidth > displayHeight
        if (KeyboardConfig.isSplitKeyboardEnabled(isLandscape)) {
            val gapPx = (totalWidth * KeyboardConfig.getSplitGapPercent() / 100.0).toInt()
            applySplitLayout(gapPx)
        }
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
        // Physical size constants (mm)
        val xdpi = displayMetrics.xdpi.takeIf { it > 0f } ?: 160f
        val ydpi = displayMetrics.ydpi.takeIf { it > 0f } ?: 160f
        val pxPerMmX = xdpi / 25.4f
        val pxPerMmY = ydpi / 25.4f

        // Ensure minimum horizontal gap (~0.5mm) between keys
        val minHGapPx = (0.5f * pxPerMmX).toInt().coerceAtLeast(1)
        for (row in rows) {
            if (row.keys.size <= 1) continue
            // Check if any adjacent keys have less than minimum gap
            var needsGapFix = false
            for (i in 1 until row.keys.size) {
                val gap = row.keys[i].x - (row.keys[i - 1].x + row.keys[i - 1].width)
                if (gap < minHGapPx) {
                    needsGapFix = true
                    break
                }
            }
            if (needsGapFix) {
                val totalGapNeeded = (row.keys.size - 1) * minHGapPx
                val availableForKeys = totalWidth - totalGapNeeded
                val totalOrigKeyWidth = row.keys.sumOf { it.width }
                if (totalOrigKeyWidth <= 0) continue
                var cursor = 0
                for ((i, key) in row.keys.withIndex()) {
                    key.x = cursor
                    key.width = (key.width.toLong() * availableForKeys / totalOrigKeyWidth).toInt()
                    cursor += key.width + (if (i < row.keys.size - 1) minHGapPx else 0)
                }
                // Extend last key to fill remaining width
                val lastKey = row.keys.last()
                lastKey.width = totalWidth - lastKey.x
            }
        }

        // Calculate the standard keyboard height (same as flick keyboard)
        val standardHeight = KeyboardHeightCalculator.getStandardKeyboardHeight(context)

        // Calculate total key height (without gaps)
        var totalKeyHeight = 0
        for (row in rows) {
            if (row.keys.isNotEmpty()) {
                totalKeyHeight += row.defaultHeight
            }
        }

        // Minimum vertical gap between rows (~0.5mm)
        val minVGapPx = (0.5f * pxPerMmY).toInt().coerceAtLeast(1)
        val maxVGapPx = (1f * pxPerMmY).toInt()
        val rowCount = rows.count { it.keys.isNotEmpty() }
        val gapCount = rowCount - 1

        // If keys alone exceed standard height, reserve gap space then scale keys to fit
        if (standardHeight < totalKeyHeight && totalKeyHeight > 0) {
            val reservedGapSpace = gapCount * minVGapPx
            val keySpace = (standardHeight - reservedGapSpace).coerceAtLeast(rowCount * 10)
            val scale = keySpace.toFloat() / totalKeyHeight
            keyHeightScale = scale
            for (row in rows) {
                if (row.keys.isNotEmpty()) {
                    val newHeight = (row.defaultHeight * scale).toInt()
                    row.defaultHeight = newHeight
                    for (key in row.keys) {
                        key.height = newHeight
                    }
                }
            }
            totalKeyHeight = keySpace
        }

        // Calculate adjusted vertical gap to match standard height
        val adjustedVerticalGap = if (gapCount > 0) {
            val requiredGapSpace = standardHeight - totalKeyHeight
            (requiredGapSpace / gapCount).coerceIn(minVGapPx, maxVGapPx)
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
     * Apply split layout: insert a center gap while preserving original key width ratios.
     * All keys shrink by the same uniform factor; the only extra space is the center split gap.
     * The space bar row is handled specially: space spans across both halves as a single key.
     */
    private fun applySplitLayout(gapPx: Int) {
        if (gapPx <= 0 || totalWidth <= 0) return

        // Gap is a percentage of the current screen width so the slider
        // has a visible effect in both portrait and landscape.
        splitGapPx = gapPx

        // Minimum horizontal gap between keys (~0.5mm physical)
        val xdpi = displayMetrics.xdpi.takeIf { it > 0f } ?: 160f
        val hGapPx = (0.5f * xdpi / 25.4f).toInt().coerceAtLeast(1)

        for (row in rows) {
            if (row.keys.isEmpty()) continue

            val spaceIndex = row.keys.indexOfFirst { it.codes.isNotEmpty() && it.codes[0] == 32 }

            if (spaceIndex >= 0) {
                // Space bar row: left keys | space (bridges gap) | right keys
                layoutSpaceBarRow(row, spaceIndex, splitGapPx, hGapPx)
            } else {
                // Normal row: left half | gap | right half
                layoutSplitRow(row, splitGapPx, hGapPx)
            }
        }
    }

    /**
     * Layout a row that contains the space bar.
     * Keys before space align to left half, keys after space align to right half,
     * and the space bar spans the middle (bridging the split gap).
     */
    private fun layoutSpaceBarRow(row: Row, spaceIndex: Int, gapPx: Int, hGapPx: Int) {
        val keyCount = row.keys.size
        val leftKeys = spaceIndex                    // keys before space
        val rightKeys = keyCount - spaceIndex - 1    // keys after space

        // Measure original widths
        var leftOrigWidth = 0
        for (i in 0 until leftKeys) leftOrigWidth += row.keys[i].width
        var rightOrigWidth = 0
        for (i in spaceIndex + 1 until keyCount) rightOrigWidth += row.keys[i].width
        val nonSpaceOrigWidth = leftOrigWidth + rightOrigWidth
        if (nonSpaceOrigWidth <= 0 && leftKeys + rightKeys > 0) return

        // Scale non-space keys proportionally to the available key space
        // (totalWidth minus the split gap), matching how layoutSplitRow works.
        val keysWidth = totalWidth - gapPx
        val scaleFactor = keysWidth.toDouble() / totalWidth

        // Scale non-space keys
        val leftKeySpace = (leftOrigWidth * scaleFactor).toInt()
        val rightKeySpace = (rightOrigWidth * scaleFactor).toInt()

        // Compute gap space within left and right groups
        val leftGaps = if (leftKeys > 1) (leftKeys - 1) * hGapPx else 0
        val rightGaps = if (rightKeys > 1) (rightKeys - 1) * hGapPx else 0

        // Position left keys
        var cursor = 0
        for (i in 0 until leftKeys) {
            val key = row.keys[i]
            key.x = cursor
            key.width = if (leftOrigWidth > 0)
                (key.width.toLong() * leftKeySpace / leftOrigWidth).toInt() else 0
            cursor += key.width + (if (i < leftKeys - 1) hGapPx else 0)
        }
        // Snap last left key
        if (leftKeys > 0) {
            val lastLeft = row.keys[leftKeys - 1]
            lastLeft.width = leftKeySpace + leftGaps - lastLeft.x
            cursor = lastLeft.x + lastLeft.width + hGapPx
        }

        // Position right keys from the right edge, working backwards to find start
        val rightTotalWidth = rightKeySpace + rightGaps
        val rightStart = totalWidth - rightTotalWidth
        var rCursor = rightStart
        if (rightOrigWidth > 0) {
            for (i in spaceIndex + 1 until keyCount) {
                val key = row.keys[i]
                key.x = rCursor
                key.width = (key.width.toLong() * rightKeySpace / rightOrigWidth).toInt()
                rCursor += key.width + (if (i < keyCount - 1) hGapPx else 0)
            }
            // Snap last right key to fill totalWidth
            row.keys.last().width = totalWidth - row.keys.last().x
        }

        // Space bar fills the middle (between left group and right group)
        val spaceKey = row.keys[spaceIndex]
        spaceKey.x = cursor
        spaceKey.width = (rightStart - hGapPx) - cursor
        if (spaceKey.width < 0) spaceKey.width = 0
    }

    /**
     * Layout a normal row (no space bar) with left half | split gap | right half.
     */
    private fun layoutSplitRow(row: Row, gapPx: Int, hGapPx: Int) {
        val keyCount = row.keys.size

        // Determine split index: B goes to right side
        val splitIndex = when {
            keyCount >= 10 -> 5   // 5 left, 5 right
            keyCount == 9 -> 5    // SHIFT Z X C V | B N M DEL
            else -> keyCount / 2
        }

        // Measure original pixel widths for each half
        val leftCount = splitIndex.coerceAtMost(keyCount)
        val rightCount = keyCount - leftCount
        var leftOrigWidth = 0
        for (i in 0 until leftCount) leftOrigWidth += row.keys[i].width
        var rightOrigWidth = 0
        for (i in splitIndex until keyCount) rightOrigWidth += row.keys[i].width
        val totalOrigWidth = leftOrigWidth + rightOrigWidth
        if (totalOrigWidth <= 0) return

        // Compute space for gaps within each half
        val leftGaps = if (leftCount > 1) (leftCount - 1) * hGapPx else 0
        val rightGaps = if (rightCount > 1) (rightCount - 1) * hGapPx else 0
        val availableForKeys = totalWidth - gapPx - leftGaps - rightGaps

        // Allocate key space proportionally to each half
        val leftKeySpace = (availableForKeys.toLong() * leftOrigWidth / totalOrigWidth).toInt()
        val rightKeySpace = availableForKeys - leftKeySpace
        val leftAvailable = leftKeySpace + leftGaps
        val rightAvailable = rightKeySpace + rightGaps

        // Scale and position left half with gaps
        var cursor = 0
        for (i in 0 until leftCount) {
            val key = row.keys[i]
            key.x = cursor
            key.width = (key.width.toLong() * leftKeySpace / leftOrigWidth).toInt()
            cursor += key.width + (if (i < leftCount - 1) hGapPx else 0)
        }
        // Snap last left key to exact boundary
        if (leftCount > 0) {
            row.keys[leftCount - 1].width = leftAvailable - row.keys[leftCount - 1].x
        }

        // Scale and position right half with gaps
        cursor = leftAvailable + gapPx
        if (rightOrigWidth > 0) {
            for (i in splitIndex until keyCount) {
                val key = row.keys[i]
                key.x = cursor
                key.width = (key.width.toLong() * rightKeySpace / rightOrigWidth).toInt()
                cursor += key.width + (if (i < keyCount - 1) hGapPx else 0)
            }
        }
        // Snap last right key to fill totalWidth
        if (splitIndex < keyCount) {
            row.keys.last().width = totalWidth - row.keys.last().x
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
        // Gap guard: if touch is inside the split gap zone, return null
        // Find the gap from the row matching the touch y-coordinate
        if (splitGapPx > 0) {
            for (row in rows) {
                if (row.keys.isEmpty()) continue
                val rowTop = row.keys[0].y
                val rowBottom = rowTop + row.keys[0].height
                if (y in rowTop until rowBottom) {
                    // Find gap: last key of left half's right edge to first key of right half's left edge
                    for (i in 0 until row.keys.size - 1) {
                        val rightEdge = row.keys[i].x + row.keys[i].width
                        val leftEdge = row.keys[i + 1].x
                        if (leftEdge - rightEdge >= splitGapPx / 2) {
                            if (x in rightEdge until leftEdge) return null
                            break
                        }
                    }
                    break
                }
            }
        }

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

        /**
         * Get the total horizontal inset caused by display cutouts (punch-hole camera, etc.).
         * Returns left + right cutout inset in pixels, or 0 if no cutout.
         */
        fun getDisplayCutoutHorizontalInset(context: Context): Int {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics = windowManager.currentWindowMetrics
                val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.displayCutout()
                )
                return insets.left + insets.right
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                val cutout = windowManager.defaultDisplay?.cutout
                if (cutout != null) {
                    return cutout.safeInsetLeft + cutout.safeInsetRight
                }
            }
            return 0
        }
    }
}
