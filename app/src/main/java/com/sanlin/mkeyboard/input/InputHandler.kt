package com.sanlin.mkeyboard.input

import android.view.inputmethod.InputConnection

/**
 * Interface for handling text input for different languages.
 * Each language/script has its own implementation that handles
 * character reordering, autocorrection, and other script-specific logic.
 */
interface InputHandler {

    /**
     * Handle character input and return the text to commit.
     *
     * @param primaryCode The primary code of the key pressed.
     * @param ic The input connection to the text field.
     * @return The text to commit to the input field.
     */
    fun handleInput(primaryCode: Int, ic: InputConnection): String

    /**
     * Handle delete operation.
     *
     * @param ic The input connection to the text field.
     * @param isEndOfText Whether cursor is at end of text.
     */
    fun handleDelete(ic: InputConnection, isEndOfText: Boolean)

    /**
     * Handle special money/currency symbol input.
     *
     * @param ic The input connection to the text field.
     */
    fun handleMoneySym(ic: InputConnection) {}

    /**
     * Reset internal state (call when switching keyboards or on certain events).
     */
    fun reset() {}

    /**
     * Check for double-tap and return alternate character code if applicable.
     *
     * @param primaryCode The primary code of the key pressed.
     * @param keyCodes All codes associated with the key.
     * @return The alternate code if double-tap detected, or -1 if not.
     */
    fun checkDoubleTap(primaryCode: Int, keyCodes: IntArray?): Int = -1

    /**
     * Check if E-vowel reordering was just performed (swapConsonant flag is true).
     * Used for proper handling of double-tap after E-vowel reordering.
     *
     * @return true if E-vowel reordering was done, false otherwise.
     */
    fun wasEVowelReordered(): Boolean = false

    /**
     * Prepare for double-tap input by resetting E-vowel state.
     * This should be called before processing double-tap alternate character.
     */
    fun prepareForDoubleTap() {}
}
