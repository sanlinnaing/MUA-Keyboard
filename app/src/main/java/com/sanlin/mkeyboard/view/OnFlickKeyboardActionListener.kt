package com.sanlin.mkeyboard.view

import com.sanlin.mkeyboard.keyboard.model.FlickCharacter
import com.sanlin.mkeyboard.keyboard.model.FlickDirection
import com.sanlin.mkeyboard.keyboard.model.FlickKey
import com.sanlin.mkeyboard.keyboard.model.Key

/**
 * Listener interface for flick keyboard actions.
 *
 * This interface is used by FlickKeyboardView to communicate user interactions
 * to the IME service.
 */
interface OnFlickKeyboardActionListener {

    /**
     * Called when a flick key produces a character.
     *
     * @param character The FlickCharacter that was selected
     * @param direction The flick direction that was used
     * @param key The FlickKey that was activated
     */
    fun onFlickCharacter(character: FlickCharacter, direction: FlickDirection, key: FlickKey)

    /**
     * Called when a special key (space, delete, enter, mode) is pressed.
     *
     * @param primaryCode The key code of the special key
     * @param key The Key that was pressed
     */
    fun onSpecialKey(primaryCode: Int, key: Key)

    /**
     * Called when a special key starts repeating (e.g., delete held down).
     *
     * @param primaryCode The key code of the repeating key
     * @param key The Key that is repeating
     */
    fun onSpecialKeyRepeat(primaryCode: Int, key: Key)

    /**
     * Called when a key is first pressed down.
     *
     * @param key Either a FlickKey or Key that was pressed
     */
    fun onKeyDown(key: Any)

    /**
     * Called when a key is released.
     *
     * @param key Either a FlickKey or Key that was released
     */
    fun onKeyUp(key: Any)

    /**
     * Called when the user swipes left on the keyboard.
     */
    fun onSwipeLeft()

    /**
     * Called when the user swipes right on the keyboard.
     */
    fun onSwipeRight()

    /**
     * Called when the space key is long-pressed.
     * Typically used to show the input method picker.
     */
    fun onSpaceLongPress()

    /**
     * Called when the punctuation key is single-tapped.
     * Should output ။ (Section, U+104B).
     */
    fun onPunctuationSingleTap()

    /**
     * Called when the punctuation key is double-tapped.
     * Should output ၊ (Little Section, U+104A) directly.
     * Note: The single tap ။ was not yet inserted (it was pending), so no deletion needed.
     */
    fun onPunctuationDoubleTap()

    /**
     * Called when a flick key is long-pressed.
     * Used for alternate characters (e.g., ာ long-press -> ါ).
     *
     * @param key The FlickKey that was long-pressed
     * @param alternateCode The Unicode code point for the alternate character
     */
    fun onFlickKeyLongPress(key: FlickKey, alternateCode: Int)

    /**
     * Called when a shifted key is tapped in the flick keyboard.
     * The keyboard should commit the text and unshift.
     *
     * @param text The text string to commit
     */
    fun onFlickShiftedKey(text: String)
}
