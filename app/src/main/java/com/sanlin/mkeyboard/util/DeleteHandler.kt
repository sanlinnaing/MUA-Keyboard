package com.sanlin.mkeyboard.util

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * Utility class for handling delete operations.
 * Provides smart delete functionality that handles surrogate pairs (emojis)
 * and other multi-codepoint characters correctly.
 */
object DeleteHandler {

    /**
     * Delete selected text or single character.
     * Always uses key event which handles both selections and single chars properly.
     *
     * @param ic The input connection to the text field.
     */
    @JvmStatic
    fun deleteChar(ic: InputConnection) {
        // Always use key event - it handles both selections and single characters
        // This is the most reliable method across all apps
        val eventTime = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(eventTime, eventTime,
            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0, 0,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE))
        ic.sendKeyEvent(KeyEvent(eventTime, eventTime,
            KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0, 0,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE))
    }

    /**
     * Delete character using deleteSurroundingText (for Myanmar smart delete).
     * This doesn't handle selections - use deleteChar for general delete.
     *
     * @param ic The input connection to the text field.
     */
    @JvmStatic
    fun deleteCharSimple(ic: InputConnection) {
        val ch = ic.getTextBeforeCursor(2, 0)
        if (ch != null && ch.length >= 2 && Character.isLowSurrogate(ch[1])) {
            // Surrogate pair (emoji)
            ic.deleteSurroundingText(2, 0)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    /**
     * Check if cursor is at end of text.
     *
     * @param ic The input connection to the text field.
     * @return true if cursor is at end of text, false otherwise.
     */
    @JvmStatic
    fun isEndOfText(ic: InputConnection): Boolean {
        val charAfterCursor = ic.getTextAfterCursor(1, 0)
        return charAfterCursor == null || charAfterCursor.isEmpty()
    }
}
