package com.sanlin.mkeyboard.input

import android.view.inputmethod.InputConnection
import com.sanlin.mkeyboard.config.KeyboardConfig
import com.sanlin.mkeyboard.unicode.MyanmarUnicode
import com.sanlin.mkeyboard.util.DeleteHandler

/**
 * Input handler for Eastern Pwo Karen text.
 * Handles E-vowel reordering, Karen-specific autocorrections, and smart delete.
 */
class KarenInputHandler : InputHandler {

    private var swapConsonant = false
    private var swapMedial = false

    override fun handleInput(primaryCode: Int, ic: InputConnection): String {
        var charBeforeCursor = ic.getTextBeforeCursor(1, 0)
        var charCodeBeforeCursor = 0

        if (charBeforeCursor == null) {
            charBeforeCursor = ""
        }

        if (charBeforeCursor.isNotEmpty()) {
            charCodeBeforeCursor = charBeforeCursor[0].code
        }

        // U vowel + Asat reorder
        if ((charCodeBeforeCursor == MyanmarUnicode.U_VOWEL) && (primaryCode == MyanmarUnicode.ASAT)) {
            ic.deleteSurroundingText(1, 0)
            val temp = charArrayOf(MyanmarUnicode.ASAT.toChar(), MyanmarUnicode.U_VOWEL.toChar())
            return String(temp)
        }

        // GHA + HA_MEDIAL = MON_BBA
        if ((charCodeBeforeCursor == MyanmarUnicode.GHA) && (primaryCode == MyanmarUnicode.HA_MEDIAL)) {
            ic.deleteSurroundingText(1, 0)
            return MyanmarUnicode.MON_BBA.toChar().toString()
        }

        // HA + HA_MEDIAL = MON_JHA
        if ((charCodeBeforeCursor == MyanmarUnicode.HA) && (primaryCode == MyanmarUnicode.HA_MEDIAL)) {
            ic.deleteSurroundingText(1, 0)
            return MyanmarUnicode.MON_JHA.toChar().toString()
        }

        if (KeyboardConfig.isPrimeBookOn()) {
            return primeInput(primaryCode, ic)
        }

        // Handle consonant medial (negative key codes)
        if (primaryCode < 0) {
            val temp = charArrayOf(MyanmarUnicode.VIRAMA.toChar(), (primaryCode * (-1)).toChar())
            return String(temp)
        }

        return primaryCode.toChar().toString()
    }

    private fun primeInput(primaryCode: Int, ic: InputConnection): String {
        var charBeforeCursor = ic.getTextBeforeCursor(1, 0)
        val charCodeBeforeCursor: Int

        if (charBeforeCursor == null) {
            charBeforeCursor = ""
        }

        if (charBeforeCursor.isNotEmpty()) {
            charCodeBeforeCursor = charBeforeCursor[0].code
        } else {
            // First character, no need to reorder
            swapConsonant = false
            swapMedial = false
            if (primaryCode < 0) {
                val temp = charArrayOf(MyanmarUnicode.VIRAMA.toChar(), (primaryCode * (-1)).toChar())
                return String(temp)
            }
            return primaryCode.toChar().toString()
        }

        // E vowel handling with ZWSP
        if (primaryCode == MyanmarUnicode.E_VOWEL) {
            if (isConsonant(charCodeBeforeCursor) || isSymMedial(charCodeBeforeCursor)) {
                val reorderChars = charArrayOf(MyanmarUnicode.ZWSP.toChar(), MyanmarUnicode.E_VOWEL.toChar())
                val reorderString = String(reorderChars)
                swapConsonant = false
                swapMedial = false
                return reorderString
            }
        }

        if (charCodeBeforeCursor == MyanmarUnicode.E_VOWEL) {
            // HA_MEDIAL + ASAT reorder after E vowel
            if (primaryCode == MyanmarUnicode.ASAT) {
                var secPrev = 0
                val getText = ic.getTextBeforeCursor(2, 0)
                if (getText != null && getText.length == 2) {
                    secPrev = getText[0].code
                }
                if (secPrev == MyanmarUnicode.HA_MEDIAL) {
                    ic.deleteSurroundingText(2, 0)
                    val reorderChars = charArrayOf(
                        MyanmarUnicode.HA_MEDIAL.toChar(), MyanmarUnicode.ASAT.toChar(),
                        MyanmarUnicode.E_VOWEL.toChar()
                    )
                    val reorderString = String(reorderChars)
                    swapConsonant = true
                    swapMedial = true
                    return reorderString
                }
            }

            // Consonant after E vowel
            if (isConsonant(primaryCode) && (!swapConsonant)) {
                swapConsonant = true
                swapMedial = false
                return reorderEVowel(primaryCode, ic)
            }

            // Medial after consonant after E vowel
            if ((!swapMedial) && (swapConsonant)) {
                if (isSymMedial(primaryCode)) {
                    swapMedial = true
                    return reorderEVowel(primaryCode, ic)
                }
                // Consonant medial (negative key codes)
                if (primaryCode < 0) {
                    swapMedial = true
                    return reorderEVowelConMedial(primaryCode, ic)
                }
            }
        }

        swapConsonant = false
        swapMedial = false

        if (primaryCode < 0) {
            val temp = charArrayOf(MyanmarUnicode.VIRAMA.toChar(), (primaryCode * (-1)).toChar())
            return String(temp)
        }

        return primaryCode.toChar().toString()
    }

    override fun handleDelete(ic: InputConnection, isEndOfText: Boolean) {
        if (isEndOfText) {
            handleSingleDelete(ic)
        } else {
            handleWordDelete(ic)
        }
    }

    private fun handleWordDelete(ic: InputConnection) {
        DeleteHandler.deleteChar(ic)
    }

    private fun handleSingleDelete(ic: InputConnection) {
        var getText = ic.getTextBeforeCursor(1, 0)
        var secPrev = 0
        var thirdPrevChar = 0

        if (getText == null || getText.isEmpty()) {
            ic.deleteSurroundingText(1, 0)
            return
        }

        val firstChar = getText[0].code
        getText = ic.getTextBeforeCursor(2, 0)
        if (getText != null && getText.length == 2) {
            secPrev = getText[0].code
        }
        getText = ic.getTextBeforeCursor(3, 0)
        if (getText != null && getText.length == 3) {
            thirdPrevChar = getText[0].code
        }

        if (firstChar == MyanmarUnicode.E_VOWEL) {
            when {
                (secPrev == MyanmarUnicode.ASAT) && thirdPrevChar == MyanmarUnicode.HA_MEDIAL -> {
                    deleteCharBeforeEVowel(ic)
                    swapConsonant = true
                    swapMedial = true
                }
                thirdPrevChar == MyanmarUnicode.VIRAMA -> {
                    delete2CharBeforeEVowel(ic)
                    swapConsonant = true
                    swapMedial = false
                }
                isSymMedial(secPrev) && isConsonant(thirdPrevChar) -> {
                    deleteCharBeforeEVowel(ic)
                    swapConsonant = true
                    swapMedial = false
                }
                isConsonant(secPrev) -> {
                    deleteCharBeforeEVowel(ic)
                    swapConsonant = false
                    swapMedial = false
                }
                else -> {
                    if (secPrev == MyanmarUnicode.ZWSP) ic.deleteSurroundingText(2, 0)
                    else DeleteHandler.deleteChar(ic)
                    swapConsonant = false
                    swapMedial = false
                }
            }
            return
        }

        if (secPrev == MyanmarUnicode.VIRAMA) {
            ic.deleteSurroundingText(2, 0)
            if (thirdPrevChar == MyanmarUnicode.E_VOWEL) {
                swapConsonant = true
            }
            return
        }

        DeleteHandler.deleteChar(ic)
        if (secPrev == MyanmarUnicode.E_VOWEL) {
            if (isConsonant(thirdPrevChar)) {
                swapConsonant = true
                swapMedial = false
            }
            if (isSymMedial(thirdPrevChar)) {
                swapConsonant = true
                swapMedial = true
            }
        }
    }

    private fun delete2CharBeforeEVowel(ic: InputConnection) {
        ic.deleteSurroundingText(3, 0)
        ic.commitText(MyanmarUnicode.E_VOWEL.toChar().toString(), 1)
    }

    private fun deleteCharBeforeEVowel(ic: InputConnection) {
        ic.deleteSurroundingText(2, 0)
        ic.commitText(MyanmarUnicode.E_VOWEL.toChar().toString(), 1)
    }

    private fun reorderEVowel(primaryCode: Int, ic: InputConnection): String {
        ic.deleteSurroundingText(1, 0)
        val reorderChars = charArrayOf(primaryCode.toChar(), MyanmarUnicode.E_VOWEL.toChar())
        return String(reorderChars)
    }

    private fun reorderEVowelConMedial(primaryCode: Int, ic: InputConnection): String {
        ic.deleteSurroundingText(1, 0)
        val reorderChars = charArrayOf(
            MyanmarUnicode.VIRAMA.toChar(), (primaryCode * (-1)).toChar(),
            MyanmarUnicode.E_VOWEL.toChar()
        )
        return String(reorderChars)
    }

    private fun isConsonant(code: Int): Boolean {
        return ((code >= MyanmarUnicode.KA) && (code <= MyanmarUnicode.A)) ||
                (code == MyanmarUnicode.MON_BBA) ||
                (code == MyanmarUnicode.MON_JHA) ||
                (code == MyanmarUnicode.KAREN_SHA) ||
                (code == MyanmarUnicode.MON_NGA)
    }

    private fun isSymMedial(code: Int): Boolean {
        return ((code >= MyanmarUnicode.YA_MEDIAL) && (code <= MyanmarUnicode.HA_MEDIAL)) ||
                ((code >= MyanmarUnicode.KAREN_MEDIAL) && (code <= MyanmarUnicode.KAREN_MEDIAL_3))
    }

    override fun reset() {
        swapConsonant = false
        swapMedial = false
    }
}
