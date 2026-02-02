package com.sanlin.mkeyboard.view

/**
 * Listener interface for keyboard actions.
 * This interface matches the AOSP KeyboardView.OnKeyboardActionListener for compatibility.
 */
interface OnKeyboardActionListener {
    /**
     * Called when a key is pressed.
     * @param primaryCode The primary code of the key.
     */
    fun onPress(primaryCode: Int)

    /**
     * Called when a key is released.
     * @param primaryCode The primary code of the key.
     */
    fun onRelease(primaryCode: Int)

    /**
     * Called when a key is tapped.
     * @param primaryCode The primary code of the key.
     * @param keyCodes All the codes associated with the key (for keys with alternate characters).
     * @param isRepeat True if this is a repeated key press (long press repeat), false for first press.
     */
    fun onKey(primaryCode: Int, keyCodes: IntArray?, isRepeat: Boolean = false)

    /**
     * Called when text should be committed.
     * @param text The text to commit.
     */
    fun onText(text: CharSequence?)

    /**
     * Called when the user swipes left.
     */
    fun swipeLeft()

    /**
     * Called when the user swipes right.
     */
    fun swipeRight()

    /**
     * Called when the user swipes down.
     */
    fun swipeDown()

    /**
     * Called when the user swipes up.
     */
    fun swipeUp()

    /**
     * Called when the space key is long pressed.
     * Used to show the input method picker.
     */
    fun onSpaceLongPress() {}
}
