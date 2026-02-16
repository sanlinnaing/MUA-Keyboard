package com.sanlin.mkeyboard.keyboard

import android.content.Context

/**
 * Utility class to calculate consistent keyboard height across different keyboard types.
 *
 * This ensures that the flick keyboard and normal QWERTY keyboard have the same total height,
 * providing a consistent visual experience when switching between them.
 */
object KeyboardHeightCalculator {

    /**
     * Calculate the standard keyboard height based on the flick keyboard formula.
     * This height is used as the target for all keyboard types.
     *
     * Flick keyboard formula:
     * - 5 columns with gaps: keyWidth = (totalWidth - 4 * gap) / 5
     * - Key height is 80% of width: keyHeight = keyWidth * 0.80
     * - 4 rows with 3 gaps: totalHeight = (keyHeight * 4) + (gap * 3)
     *
     * @param context Context to access display metrics
     * @return The standard keyboard height in pixels
     */
    @JvmStatic
    fun getStandardKeyboardHeight(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        return getKeyboardHeightForWidth(displayMetrics.widthPixels)
    }

    /**
     * Calculate keyboard height for a given layout width.
     * Used by flick keyboard in compact (one-handed) mode.
     */
    @JvmStatic
    fun getKeyboardHeightForWidth(totalWidth: Int): Int {
        // Gap is 1% of layout width (same as flick keyboard)
        val gap = (totalWidth * 0.01f).toInt().coerceAtLeast(4)

        // Key width for 5 columns: totalWidth = 5*keyWidth + 4*gap
        val keyWidth = (totalWidth - (4 * gap)) / 5

        // Key height is 80% of width
        val keyHeight = (keyWidth * 0.80f).toInt()

        // Total height for 4 rows with 3 gaps
        val flickRows = 4
        return (keyHeight * flickRows) + (gap * (flickRows - 1))
    }

    /**
     * Calculate the vertical gap needed to make the normal keyboard match the standard height.
     *
     * @param context Context to access display metrics
     * @param rowCount Number of rows in the keyboard
     * @param keyHeight Height of each key in pixels
     * @param existingVerticalGap Current vertical gap between rows
     * @return The adjusted vertical gap to achieve standard height
     */
    @JvmStatic
    fun calculateAdjustedVerticalGap(
        context: Context,
        rowCount: Int,
        keyHeight: Int,
        existingVerticalGap: Int
    ): Int {
        val standardHeight = getStandardKeyboardHeight(context)

        // Calculate current height without gap adjustment
        // currentHeight = rowCount * keyHeight + (rowCount - 1) * existingVerticalGap
        val totalKeyHeight = rowCount * keyHeight

        // Calculate required gap space
        // standardHeight = totalKeyHeight + (rowCount - 1) * newGap
        // newGap = (standardHeight - totalKeyHeight) / (rowCount - 1)
        val gapCount = rowCount - 1
        if (gapCount <= 0) return existingVerticalGap

        val requiredGapSpace = standardHeight - totalKeyHeight
        val newGap = requiredGapSpace / gapCount

        // Return the larger of calculated gap or existing gap (don't reduce gaps)
        return newGap.coerceAtLeast(existingVerticalGap)
    }
}
