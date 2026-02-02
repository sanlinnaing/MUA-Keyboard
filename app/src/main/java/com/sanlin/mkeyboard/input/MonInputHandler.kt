package com.sanlin.mkeyboard.input

import android.view.inputmethod.InputConnection
import com.sanlin.mkeyboard.config.KeyboardConfig
import com.sanlin.mkeyboard.unicode.MyanmarUnicode
import com.sanlin.mkeyboard.util.DeleteHandler

/**
 * Input handler for Mon text.
 * Handles E-vowel reordering, Mon-specific autocorrections, and smart delete.
 */
class MonInputHandler(
    private val wordSeparators: String = ""
) : InputHandler {

    private var stackPointer = 0
    private val stack = IntArray(3)
    private var swapConsonant = false
    private var medialCount: Short = 0
    private var swapMedial = false
    private var swapMonMedial = false
    private var evowelVirama = false
    private val medialStack = IntArray(3)

    override fun handleInput(primaryCode: Int, ic: InputConnection): String {
        var charBeforeCursor = ic.getTextBeforeCursor(1, 0)
        val charCodeBeforeCursor: Int

        if (charBeforeCursor == null) {
            charBeforeCursor = ""
        }

        if (charBeforeCursor.isNotEmpty()) {
            charCodeBeforeCursor = charBeforeCursor[0].code
        } else {
            swapConsonant = false
            medialCount = 0
            swapMedial = false
            swapMonMedial = false
            evowelVirama = false
            return primaryCode.toChar().toString()
        }

        // tha + ra_medial = aw vowel autocorrect
        if ((charCodeBeforeCursor == MyanmarUnicode.SA) && (primaryCode == MyanmarUnicode.RA_MEDIAL)) {
            ic.deleteSurroundingText(1, 0)
            return MyanmarUnicode.O.toChar().toString()
        }

        // dot_above + au vowel = au vowel + dot_above autocorrect
        if ((charCodeBeforeCursor == MyanmarUnicode.ANUSVARA) && (primaryCode == MyanmarUnicode.U_VOWEL)) {
            val temp = charArrayOf(MyanmarUnicode.U_VOWEL.toChar(), MyanmarUnicode.ANUSVARA.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }

        // ss + ya_medial = za myint zwe autocorrect
        if ((charCodeBeforeCursor == MyanmarUnicode.CA) && (primaryCode == MyanmarUnicode.YA_MEDIAL)) {
            ic.deleteSurroundingText(1, 0)
            return MyanmarUnicode.JHA.toChar().toString()
        }

        // uu + aa_vowel = NYA + aa_vowel autocorrect
        if ((charCodeBeforeCursor == MyanmarUnicode.U) && (primaryCode == MyanmarUnicode.AA)) {
            val temp = charArrayOf(MyanmarUnicode.NYA.toChar(), MyanmarUnicode.AA.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }

        // uu_vowel+ii_vowel = u autocorrect
        if ((charCodeBeforeCursor == MyanmarUnicode.U) && (primaryCode == MyanmarUnicode.II_VOWEL)) {
            ic.deleteSurroundingText(1, 0)
            return MyanmarUnicode.UU.toChar().toString()
        }

        // uu_vowel+asat autocorrect
        if ((charCodeBeforeCursor == MyanmarUnicode.U) && (primaryCode == MyanmarUnicode.ASAT)) {
            val temp = charArrayOf(MyanmarUnicode.NYA.toChar(), MyanmarUnicode.ASAT.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }

        // asat + dot below to reorder dot below + asat
        if ((charCodeBeforeCursor == MyanmarUnicode.ASAT) && (primaryCode == MyanmarUnicode.DOT_BELOW)) {
            val temp = charArrayOf(MyanmarUnicode.DOT_BELOW.toChar(), MyanmarUnicode.ASAT.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }

        if (KeyboardConfig.isPrimeBookOn()) {
            return handleMonPrimeBook(primaryCode, ic, charCodeBeforeCursor)
        }

        return primaryCode.toChar().toString()
    }

    private fun handleMonPrimeBook(
        primaryCode: Int, ic: InputConnection,
        charCodeBeforeCursor: Int
    ): String {
        // E vowel + cons + virama + cons
        if ((primaryCode == MyanmarUnicode.VIRAMA) and (swapConsonant)) {
            swapConsonant = false
            evowelVirama = true
            return primaryCode.toChar().toString()
        }

        // if e_vowel renew checking flag if
        if (primaryCode == MyanmarUnicode.E_VOWEL) {
            var outText = primaryCode.toChar().toString()
            if (isConsonant(charCodeBeforeCursor)) {
                val temp = charArrayOf(MyanmarUnicode.ZWSP.toChar(), primaryCode.toChar())
                outText = String(temp)
            }
            swapConsonant = false
            medialCount = 0
            swapMedial = false
            swapMonMedial = false
            evowelVirama = false
            return outText
        }

        if (evowelVirama) {
            if (isConsonant(primaryCode)) {
                swapConsonant = true
                ic.deleteSurroundingText(2, 0)
                val reorderChars = charArrayOf(
                    MyanmarUnicode.VIRAMA.toChar(), primaryCode.toChar(),
                    MyanmarUnicode.E_VOWEL.toChar()
                )
                val reorderString = String(reorderChars)
                evowelVirama = false
                return reorderString
            } else {
                evowelVirama = false
            }
        }

        if (isOthers(primaryCode)) {
            swapConsonant = false
            medialCount = 0
            swapMedial = false
            swapMonMedial = false
            evowelVirama = false
            return primaryCode.toChar().toString()
        }

        // if no previous E_vowel, no need to check Reorder.
        if (charCodeBeforeCursor != MyanmarUnicode.E_VOWEL) {
            return primaryCode.toChar().toString()
        }

        // if input character is consonant and consonant e_vowel swapped,
        // no need to reorder. con+vowel+con
        if (isConsonant(primaryCode) && (swapConsonant)) {
            swapConsonant = false
            swapMedial = false
            swapMonMedial = false
            medialCount = 0
            return primaryCode.toChar().toString()
        }

        if (isConsonant(primaryCode)) {
            if (!swapConsonant) {
                swapConsonant = true
                return reorderEVowel(primaryCode, ic)
            } else {
                swapConsonant = false
                return primaryCode.toChar().toString()
            }
        }

        if (isMynMedial(primaryCode)) {
            if (isValidMedial(primaryCode)) {
                medialStack[medialCount.toInt()] = primaryCode
                medialCount++
                swapMedial = true
                return reorderEVowel(primaryCode, ic)
            }
        }

        if (isMonMedial(primaryCode)) {
            if ((!swapMonMedial) && (!swapMedial)) {
                swapMonMedial = true
                return reorderEVowel(primaryCode, ic)
            }
            swapMonMedial = false
            return primaryCode.toChar().toString()
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
        var i = 1
        var getText = ic.getTextBeforeCursor(1, 0)

        if (getText == null || getText.isEmpty()) {
            return
        }

        // for Emotion delete
        if (Character.isLowSurrogate(getText[0]) || Character.isHighSurrogate(getText[0])) {
            ic.deleteSurroundingText(2, 0)
            return
        }

        var current: Int
        var beforeLength = 0
        var currentLength = 1

        current = getText[0].code
        while (!(isConsonant(current) || isWordSeparator(current)) && (beforeLength != currentLength)) {
            i++
            beforeLength = currentLength
            getText = ic.getTextBeforeCursor(i, 0)
            currentLength = getText!!.length
            current = getText[0].code
        }

        if (beforeLength == currentLength) {
            ic.deleteSurroundingText(1, 0)
        } else {
            getText = ic.getTextBeforeCursor(i + 1, 0)
            val virama = getText!![0].code
            if (virama == MyanmarUnicode.VIRAMA) {
                ic.deleteSurroundingText(i + 1, 0)
            } else {
                ic.deleteSurroundingText(i, 0)
            }
        }

        swapConsonant = false
        medialCount = 0
        swapMedial = false
    }

    private fun handleSingleDelete(ic: InputConnection) {
        var getText = ic.getTextBeforeCursor(1, 0)
        val firstChar: Int
        val secPrev: Int

        if (getText == null) {
            getText = ""
        }

        if (getText.isNotEmpty()) {
            firstChar = getText[0].code
            if (firstChar == MyanmarUnicode.E_VOWEL) {
                // Need to initialize FLAG
                swapConsonant = false
                swapMedial = false
                swapMonMedial = false
                medialCount = 0
                stackPointer = 0
                // 2nd previous character
                getText = ic.getTextBeforeCursor(2, 0)
                secPrev = getText!![0].code

                // Mon Medial + E_VOWEL
                if (isMonMedial(secPrev)) {
                    val getThirdText = ic.getTextBeforeCursor(3, 0)
                    var thirdChar = 0
                    if (getThirdText!!.length == 3) {
                        thirdChar = getThirdText[0].code
                        if (isConsonant(thirdChar)) {
                            deleteCharBeforeEvowel(ic)
                            swapMonMedial = false
                            swapConsonant = true
                        }
                    }
                } else if (isMynMedial(secPrev)) {
                    getFlagMedial(ic)
                    if (swapConsonant) {
                        deleteCharBeforeEvowel(ic)
                        medialCount--

                        stackPointer--
                        if (medialCount <= 0) {
                            swapMedial = false
                        }
                        for (j in 0 until medialCount) {
                            medialStack[j] = stack[stackPointer]
                            stackPointer--
                        }
                        if (medialCount < 0) {
                            medialCount = 0
                        }
                    } else {
                        ic.deleteSurroundingText(1, 0)
                    }
                } else if (isConsonant(secPrev)) {
                    val getThirdText = ic.getTextBeforeCursor(3, 0)
                    var thirdChar = 0
                    if (getThirdText!!.length == 3) thirdChar = getThirdText[0].code
                    if (thirdChar == MyanmarUnicode.VIRAMA) {
                        deleteTwoCharBeforeEvowel(ic)
                    } else {
                        deleteCharBeforeEvowel(ic)
                    }
                    swapConsonant = false
                    swapMedial = false
                    medialCount = 0
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            } else {
                // If not E_Vowel
                getText = ic.getTextBeforeCursor(2, 0)
                secPrev = getText!![0].code
                if (secPrev == MyanmarUnicode.E_VOWEL) swapConsonant = true
                DeleteHandler.deleteChar(ic)
            }
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        stackPointer = 0
    }

    private fun deleteCharBeforeEvowel(ic: InputConnection) {
        ic.deleteSurroundingText(2, 0)
        ic.commitText(MyanmarUnicode.E_VOWEL.toChar().toString(), 1)
    }

    private fun deleteTwoCharBeforeEvowel(ic: InputConnection) {
        ic.deleteSurroundingText(3, 0)
        ic.commitText(MyanmarUnicode.E_VOWEL.toChar().toString(), 1)
    }

    private fun getFlagMedial(ic: InputConnection) {
        var getText = ic.getTextBeforeCursor(2, 0)
        var beforeLength = 0
        var currentLength = 1
        var i = 2
        if (getText == null) {
            getText = ""
        }
        var current = getText[0].code

        while (!(isConsonant(current) || isWordSeparator(current))
            && (beforeLength != currentLength) && (isMynMedial(current))
        ) {
            medialCount++
            pushMedialStack(current)
            swapMedial = true
            swapConsonant = true
            i++
            beforeLength = currentLength
            getText = ic.getTextBeforeCursor(i, 0)
            currentLength = getText!!.length
            current = getText[0].code
        }

        if (isConsonant(current)) {
            return
        }

        if ((!isMynMedial(current)) && (!isConsonant(current))) {
            swapMedial = false
            swapConsonant = false
            medialCount = 0
            stackPointer = 0
            return
        }

        if (beforeLength == currentLength) {
            swapMedial = false
            swapConsonant = false
            medialCount = 0
            stackPointer = 0
        }
    }

    private fun pushMedialStack(current: Int) {
        stack[stackPointer] = current
        stackPointer++
    }

    private fun reorderEVowel(primaryCode: Int, ic: InputConnection): String {
        ic.deleteSurroundingText(1, 0)
        val reorderChars = charArrayOf(primaryCode.toChar(), MyanmarUnicode.E_VOWEL.toChar())
        return String(reorderChars)
    }

    private fun isValidMedial(primaryCode: Int): Boolean {
        return when {
            !swapConsonant -> false
            !swapMedial -> true
            medialCount > 2 -> false
            medialStack[medialCount - 1] == MyanmarUnicode.HA_MEDIAL -> false
            (medialStack[medialCount - 1] == MyanmarUnicode.WA_MEDIAL) && (primaryCode == MyanmarUnicode.HA_MEDIAL) -> true
            else -> !(((medialStack[medialCount - 1] == MyanmarUnicode.YA_MEDIAL) && (primaryCode == MyanmarUnicode.RA_MEDIAL))
                    || ((medialStack[medialCount - 1] == MyanmarUnicode.RA_MEDIAL) && (primaryCode == MyanmarUnicode.YA_MEDIAL))
                    || ((medialStack[medialCount - 1] == MyanmarUnicode.YA_MEDIAL) && (primaryCode == MyanmarUnicode.YA_MEDIAL))
                    || ((medialStack[medialCount - 1] == MyanmarUnicode.RA_MEDIAL) && (primaryCode == MyanmarUnicode.RA_MEDIAL)))
        }
    }

    private fun isOthers(primaryCode: Int): Boolean {
        return when (primaryCode) {
            MyanmarUnicode.TALL_AA, MyanmarUnicode.AA, MyanmarUnicode.DOT_BELOW, MyanmarUnicode.VISARGA -> true
            else -> false
        }
    }

    private fun isConsonant(primaryCode: Int): Boolean {
        return (primaryCode > 4095) && (primaryCode < 4130) ||
                ((primaryCode > 4185) && (primaryCode < 4190))
    }

    private fun isMynMedial(primaryCode: Int): Boolean {
        return (primaryCode > MyanmarUnicode.ASAT) && (primaryCode < 0x103f)
    }

    private fun isMonMedial(primaryCode: Int): Boolean {
        return (primaryCode > MyanmarUnicode.ASAT) && (primaryCode < 0x1061)
    }

    private fun isWordSeparator(code: Int): Boolean {
        return wordSeparators.contains(code.toChar().toString())
    }

    override fun handleMoneySym(ic: InputConnection) {
        val temp = charArrayOf(
            MyanmarUnicode.DA.toChar(),
            MyanmarUnicode.VIRAMA.toChar(),
            MyanmarUnicode.KA.toChar(),
            MyanmarUnicode.E_VOWEL.toChar(),
            MyanmarUnicode.WA.toChar(),
            MyanmarUnicode.ASAT.toChar()
        )
        ic.commitText(String(temp), 1)
    }

    override fun reset() {
        swapConsonant = false
        medialCount = 0
        swapMedial = false
        swapMonMedial = false
        evowelVirama = false
        stackPointer = 0
    }
}
