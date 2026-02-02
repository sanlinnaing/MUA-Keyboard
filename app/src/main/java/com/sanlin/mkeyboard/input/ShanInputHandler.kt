package com.sanlin.mkeyboard.input

import android.view.inputmethod.InputConnection
import com.sanlin.mkeyboard.config.KeyboardConfig
import com.sanlin.mkeyboard.unicode.MyanmarUnicode
import com.sanlin.mkeyboard.util.DeleteHandler

/**
 * Input handler for Shan text.
 * Handles E-vowel reordering, Shan-specific autocorrections, and smart delete.
 */
class ShanInputHandler(
    private val shanConsonants: String = ""
) : InputHandler {

    private var swapConsonant = false
    private var swapMedial = false

    override fun handleInput(primaryCode: Int, ic: InputConnection): String {
        var charBeforeCursor = ic.getTextBeforeCursor(1, 0)

        // E vowel handling (both Myanmar E and Shan E)
        if ((primaryCode == MyanmarUnicode.E_VOWEL || primaryCode == MyanmarUnicode.SHAN_E)
            && KeyboardConfig.isPrimeBookOn()
        ) {
            val temp = charArrayOf(MyanmarUnicode.ZWSP.toChar(), primaryCode.toChar())
            val outText = String(temp)
            swapConsonant = false
            swapMedial = false
            return outText
        }

        if (charBeforeCursor == null) {
            charBeforeCursor = ""
        }

        var charCodeBeforeCursor: Int? = null
        if (charBeforeCursor.isNotEmpty()) {
            charCodeBeforeCursor = charBeforeCursor[0].code
        } else {
            return primaryCode.toChar().toString()
        }

        // Asat + Shan Medial Wa reorder
        if (charCodeBeforeCursor == MyanmarUnicode.ASAT && primaryCode == MyanmarUnicode.SHAN_MEDIAL_WA) {
            val temp = charArrayOf(MyanmarUnicode.SHAN_MEDIAL_WA.toChar(), MyanmarUnicode.ASAT.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }

        // Shan tone + vowel reorder
        if (charCodeBeforeCursor == MyanmarUnicode.SHAN_TONE_2 && primaryCode == MyanmarUnicode.SHAN_COUNCIL_TONE_2) {
            val temp = charArrayOf(MyanmarUnicode.SHAN_COUNCIL_TONE_2.toChar(), MyanmarUnicode.SHAN_TONE_2.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }

        // UU vowel + I vowel reorder
        if (charCodeBeforeCursor == MyanmarUnicode.UU_VOWEL && primaryCode == MyanmarUnicode.I_VOWEL) {
            val temp = charArrayOf(MyanmarUnicode.I_VOWEL.toChar(), MyanmarUnicode.UU_VOWEL.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }

        // U vowel + I vowel reorder
        if (charCodeBeforeCursor == MyanmarUnicode.U_VOWEL && primaryCode == MyanmarUnicode.I_VOWEL) {
            val temp = charArrayOf(MyanmarUnicode.I_VOWEL.toChar(), MyanmarUnicode.U_VOWEL.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }

        if (KeyboardConfig.isPrimeBookOn()) {
            return handleShanTyping(primaryCode, ic)
        }

        return primaryCode.toChar().toString()
    }

    private fun handleShanTyping(primaryCode: Int, ic: InputConnection): String {
        var charBeforeCursor = ic.getTextBeforeCursor(1, 0)

        if (charBeforeCursor == null) {
            charBeforeCursor = ""
        }

        var charCodeBeforeCursor: Int? = null

        if (isOthers(primaryCode)) {
            swapConsonant = false
            swapMedial = false
            return primaryCode.toChar().toString()
        }

        if (charBeforeCursor.isNotEmpty()) {
            charCodeBeforeCursor = charBeforeCursor[0].code
        } else {
            swapConsonant = false
            swapMedial = false
            return primaryCode.toChar().toString()
        }

        // Check for E vowel (Myanmar or Shan)
        if ((charCodeBeforeCursor == MyanmarUnicode.E_VOWEL || charCodeBeforeCursor == MyanmarUnicode.SHAN_E)) {
            if (isConsonant(primaryCode)) {
                if (!swapConsonant) {
                    swapConsonant = true
                    return reorder(primaryCode, charCodeBeforeCursor, ic)
                } else {
                    swapConsonant = false
                    swapMedial = false
                    return primaryCode.toChar().toString()
                }
            }
            if (primaryCode == MyanmarUnicode.ASAT) {
                if (swapConsonant) {
                    swapConsonant = false
                    return reorder(primaryCode, charCodeBeforeCursor, ic)
                }
            }
            if (isMedial(primaryCode)) {
                if (!swapMedial && swapConsonant) {
                    swapConsonant = false
                    swapMedial = true
                    return reorder(primaryCode, charCodeBeforeCursor, ic)
                }
            }
        }

        return primaryCode.toChar().toString()
    }

    private fun isOthers(primaryCode: Int): Boolean {
        return isConsonant(primaryCode) && isMedial(primaryCode)
    }

    private fun isMedial(primaryCode: Int): Boolean {
        // medial Ya, Ra
        return primaryCode == MyanmarUnicode.YA_MEDIAL || primaryCode == MyanmarUnicode.RA_MEDIAL
    }

    private fun reorder(
        primaryCode: Int, charCodeBeforeCursor: Int,
        ic: InputConnection
    ): String {
        ic.deleteSurroundingText(1, 0)
        val reorderChars = charArrayOf(primaryCode.toChar(), charCodeBeforeCursor.toChar())
        return String(reorderChars)
    }

    override fun handleDelete(ic: InputConnection, isEndOfText: Boolean) {
        if (isEndOfText) {
            handleSingleDelete(ic)
        } else {
            DeleteHandler.deleteChar(ic)
        }
    }

    private fun handleSingleDelete(ic: InputConnection) {
        var getText = ic.getTextBeforeCursor(1, 0)

        if (getText == null) {
            getText = ""
        }

        val firstChar: Int
        val secPrev: Int

        if (getText.isNotEmpty()) {
            firstChar = getText[0].code
            if (firstChar == MyanmarUnicode.E_VOWEL || firstChar == MyanmarUnicode.SHAN_E) {
                // Need to initialize FLAG
                swapConsonant = false
                swapMedial = false
                getText = ic.getTextBeforeCursor(2, 0)
                secPrev = getText!![0].code
                if (isMedial(secPrev)) {
                    swapConsonant = true
                    swapMedial = false
                    deleteCharBefore(firstChar, ic)
                } else if (isConsonant(secPrev)) {
                    swapMedial = false
                    swapConsonant = false
                    deleteCharBefore(firstChar, ic)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            } else {
                getText = ic.getTextBeforeCursor(2, 0)
                secPrev = getText!![0].code
                val getThirdText = ic.getTextBeforeCursor(3, 0)
                var thirdChar = 0
                if (getThirdText != null && getThirdText.length == 3) thirdChar = getThirdText[0].code

                if (secPrev == MyanmarUnicode.E_VOWEL || secPrev == MyanmarUnicode.SHAN_E) {
                    swapConsonant = thirdChar != MyanmarUnicode.ZWSP
                }
                DeleteHandler.deleteChar(ic)
            }
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun deleteCharBefore(firstChar: Int, ic: InputConnection) {
        ic.deleteSurroundingText(2, 0)
        ic.commitText(firstChar.toChar().toString(), 1)
    }

    private fun isConsonant(primaryCode: Int): Boolean {
        return shanConsonants.contains(primaryCode.toChar().toString())
    }

    /**
     * Generate Shan vowel combination.
     */
    fun shanVowel1(): String {
        val outText = charArrayOf(0x10A2.toChar(), MyanmarUnicode.ASAT.toChar())
        return String(outText)
    }

    override fun handleMoneySym(ic: InputConnection) {
        val temp = charArrayOf(
            MyanmarUnicode.PA.toChar(),
            MyanmarUnicode.YA_MEDIAL.toChar(),
            MyanmarUnicode.SHAN_VOWEL_O.toChar(),
            MyanmarUnicode.VISARGA.toChar()
        )
        ic.commitText(String(temp), 1)
    }

    override fun reset() {
        swapConsonant = false
        swapMedial = false
    }
}
