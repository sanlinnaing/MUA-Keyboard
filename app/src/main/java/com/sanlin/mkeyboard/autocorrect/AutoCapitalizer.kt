package com.sanlin.mkeyboard.autocorrect

import android.view.inputmethod.InputConnection

/**
 * Handles auto-capitalization for English text input.
 *
 * Rules:
 * - Capitalize after sentence punctuation (. ! ?) followed by space
 * - Capitalize at the start of text field
 * - Fix standalone "i" to "I"
 */
class AutoCapitalizer {

    companion object {
        private val SENTENCE_ENDINGS = charArrayOf('.', '!', '?')
    }

    /**
     * Check if the next character should be capitalized.
     *
     * @param ic the InputConnection to check text before cursor
     * @return true if next character should be capitalized
     */
    fun shouldCapitalize(ic: InputConnection?): Boolean {
        if (ic == null) return false

        val textBefore = ic.getTextBeforeCursor(3, 0)?.toString() ?: ""

        // Start of text field
        if (textBefore.isEmpty()) return true

        // After sentence ending + space
        if (textBefore.length >= 2) {
            val lastTwo = textBefore.takeLast(2)
            if (lastTwo.length == 2 &&
                lastTwo[0] in SENTENCE_ENDINGS &&
                lastTwo[1] == ' ') {
                return true
            }
        }

        // After newline
        if (textBefore.lastOrNull() == '\n') return true

        return false
    }

    /**
     * Check if we just typed " i " and should correct to " I ".
     * This should be called after committing text.
     *
     * @param ic the InputConnection
     * @param justTyped the character just typed
     * @return true if correction was applied
     */
    fun fixStandaloneI(ic: InputConnection?, justTyped: Char): Boolean {
        if (ic == null) return false

        // Only trigger on space after 'i'
        if (justTyped != ' ') return false

        val textBefore = ic.getTextBeforeCursor(3, 0)?.toString() ?: ""

        // Check for " i " pattern (space + i + space just typed)
        // textBefore includes the space we just typed
        if (textBefore.length >= 3) {
            val pattern = textBefore.takeLast(3)
            if (pattern == " i ") {
                // Delete " i " and replace with " I "
                ic.deleteSurroundingText(3, 0)
                ic.commitText(" I ", 1)
                return true
            }
        } else if (textBefore == "i ") {
            // At start of text: "i " -> "I "
            ic.deleteSurroundingText(2, 0)
            ic.commitText("I ", 1)
            return true
        }

        return false
    }

    /**
     * Capitalize a character if needed based on context.
     *
     * @param code the character code
     * @param ic the InputConnection
     * @return the (possibly capitalized) character code
     */
    fun capitalizeIfNeeded(code: Int, ic: InputConnection?): Int {
        if (!Character.isLetter(code)) return code
        if (shouldCapitalize(ic)) {
            return Character.toUpperCase(code)
        }
        return code
    }
}
