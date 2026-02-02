package com.sanlin.mkeyboard.input

import android.view.inputmethod.InputConnection
import com.sanlin.mkeyboard.util.DeleteHandler

/**
 * Input handler for English text.
 * This is a pass-through handler that doesn't perform any special processing.
 */
class EnglishInputHandler : InputHandler {

    override fun handleInput(primaryCode: Int, ic: InputConnection): String {
        return primaryCode.toChar().toString()
    }

    override fun handleDelete(ic: InputConnection, isEndOfText: Boolean) {
        DeleteHandler.deleteChar(ic)
    }

    override fun reset() {
        // No state to reset
    }
}
