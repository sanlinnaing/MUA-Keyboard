package com.sanlin.mkeyboard

import android.content.Context
import android.view.inputmethod.InputConnection

class MonKeyboard : MyKeyboard {
    private val DOT_BELOW = 0x1037
    private val ASAT = 0x103a
    private val NYA = 0x1009
    private val UU = 0x1025
    private val II_VOWEL = 0x102e
    private val AA_VOWEL = 0x102c
    private val SS = 0x1005
    private val YA_MEDIAL = 0x103b
    private val E_VOWEL = 0x1031
    private val VIRAMA = 0x1039
    private var stackPointer = 0
    private val stack = IntArray(3)
    private var swapConsonant = false
    private var medialCount: Short = 0
    private var swapMedial = false
    private var swapMonMedial = false
    private var EVOWEL_VIRAMA = false
    private val medialStack = IntArray(3)

    constructor(context: Context?, xmlLayoutResId: Int) : super(context, xmlLayoutResId)

    constructor(
        context: Context?, layoutTemplateResId: Int,
        characters: CharSequence?, columns: Int, horizontalPadding: Int
    ) : super(
        context, layoutTemplateResId, characters, columns,
        horizontalPadding
    )

    fun handleMonInput(primaryCode: Int, ic: InputConnection): String {
        var charBeforeCursor = ic.getTextBeforeCursor(1, 0)
        val charCodeBeforeCursor: Int
        // if getTextBeforeCursor return null, issues on version 1.1
        if (charBeforeCursor == null) {
            charBeforeCursor = ""
        }
        if (charBeforeCursor.length > 0) charCodeBeforeCursor = charBeforeCursor.get(0).code
        else {
            swapConsonant = false
            medialCount = 0
            swapMedial = false
            swapMonMedial = false
            EVOWEL_VIRAMA = false
            return primaryCode.toChar().toString() // else it is the first
        } // character no need to

        // reorder
        // tha + ra_medial = aw vowel autocorrect
        if ((charCodeBeforeCursor == 0x101e) && (primaryCode == 0x103c)) {
            ic.deleteSurroundingText(1, 0)
            return 0x1029.toChar().toString()
        }
        // dot_above + au vowel = au vowel + dot_above autocorrect
        if ((charCodeBeforeCursor == 0x1036) && (primaryCode == 0x102f)) {
            val temp = charArrayOf(0x102f.toChar(), 0x1036.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }
        // ss + ya_medial = za myint zwe autocorrect
        if ((charCodeBeforeCursor == SS) && (primaryCode == YA_MEDIAL)) {
            ic.deleteSurroundingText(1, 0)
            return 0x1008.toChar().toString()
        }
        // uu + aa_vowel = NYA + aa_vowel autocorrect
        if ((charCodeBeforeCursor == UU) && (primaryCode == AA_VOWEL)) {
            val temp = charArrayOf(NYA.toChar(), AA_VOWEL.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }
        // uu_vowel+ii_vowel = u autocorrect
        if ((charCodeBeforeCursor == UU) && (primaryCode == II_VOWEL)) {
            ic.deleteSurroundingText(1, 0)
            return 0x1026.toChar().toString() // U
        }
        // uu_vowel+asat autocorrect
        if ((charCodeBeforeCursor == UU) && (primaryCode == ASAT)) {
            val temp = charArrayOf(NYA.toChar(), ASAT.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }
        // asat + dot below to reorder dot below + asat
        if ((charCodeBeforeCursor == ASAT) && (primaryCode == DOT_BELOW)) {
            val temp = charArrayOf(DOT_BELOW.toChar(), ASAT.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }
        if (MyConfig.isPrimeBookOn()) {
            return handleMonPrimeBook(primaryCode, ic, charCodeBeforeCursor)
        }
        return primaryCode.toChar().toString()
    }

    private fun handleMonPrimeBook(
        primaryCode: Int, ic: InputConnection,
        charCodeBeforeCursor: Int
    ): String {
        // E vowel + cons + virama + cons
        if ((primaryCode == VIRAMA) and (swapConsonant)) {
            swapConsonant = false
            EVOWEL_VIRAMA = true
            return primaryCode.toChar().toString()
        }

        // if e_vowel renew checking flag if
        if (primaryCode == E_VOWEL) {
            var outText = primaryCode.toChar().toString()
            if (isConsonant(charCodeBeforeCursor)) {
                val temp = charArrayOf(0x200b.toChar(), primaryCode.toChar()) // ZWSP added
                outText = String(temp)
            }
            swapConsonant = false
            medialCount = 0
            swapMedial = false
            swapMonMedial = false
            EVOWEL_VIRAMA = false
            return outText
        }

        if (EVOWEL_VIRAMA) {
            if (isConsonant(primaryCode)) {
                swapConsonant = true
                ic.deleteSurroundingText(2, 0)
                val reorderChars = charArrayOf(
                    VIRAMA.toChar(), primaryCode.toChar(),
                    E_VOWEL.toChar()
                )
                val reorderString = String(reorderChars)
                EVOWEL_VIRAMA = false
                return reorderString
            } else {
                EVOWEL_VIRAMA = false
            }
        }
        if (isOthers(primaryCode)) {
            swapConsonant = false
            medialCount = 0
            swapMedial = false
            swapMonMedial = false
            EVOWEL_VIRAMA = false
            return primaryCode.toChar().toString()
        }
        // if no previous E_vowel, no need to check Reorder.
        if (charCodeBeforeCursor != E_VOWEL) {
            return primaryCode.toChar().toString()
        }
        // Next other instructions will run charCodeBeforeCursor == E_VOWEL.
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
            // delete e_vowel and create Type character + e_vowel
            // (reordering)

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

    fun handleMonDelete(ic: InputConnection) {
        if (MyIME.isEndofText(ic)) {
            handleSingleDelete(ic)
        } else {
            handelMonWordDelete(ic)
        }
    }

    private fun handelMonWordDelete(ic: InputConnection) {
        var i = 1
        var getText = ic.getTextBeforeCursor(1, 0)
        // null error fixed on issue of version 1.1
        if ((getText == null) || (getText.length <= 0)) {
            return  // fixed on issue of version 1.2, cause=(getText is null) solution=(if getText is null, return)
        }
        // for Emotion delete
        if (Character.isLowSurrogate(getText[0])
            || Character.isHighSurrogate(getText[0])
        ) {
            ic.deleteSurroundingText(2, 0)
            return
        }
        var current: Int
        var beforeLength = 0
        var currentLength = 1

        current = getText[0].code
        // word or separator
        while (!(isConsonant(current) || MyIME.isWordSeparator(current))
            && (beforeLength != currentLength)
        ) {
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
            if (virama == VIRAMA) {
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
        // if getTextBeforeCursor return null, issues on version 1.1
        if (getText == null) {
            getText = ""
        }
        if (getText.length > 0) {
            firstChar = getText.get(0).code
            if (firstChar == E_VOWEL) {
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
                } else if (isMynMedial(secPrev)) { // Myn Medial + E_VOWEL
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
                        // nul point exception cause medialCount = -1
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
                    if (thirdChar == VIRAMA) {
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
                if (secPrev == E_VOWEL) swapConsonant = true
                // ic.deleteSurroundingText(1, 0);
                MyIME.deleteHandle(ic)
            }
        } else {
            // It is the start of input text box
            ic.deleteSurroundingText(1, 0)
        }
        stackPointer = 0
        val logText = StringBuilder()
        for (k in 0 until medialCount) {
            logText.append(medialStack[k].toChar().toString()).append(" | ")
        }
    }

    private fun deleteCharBeforeEvowel(ic: InputConnection) {
        ic.deleteSurroundingText(2, 0)
        ic.commitText(E_VOWEL.toChar().toString(), 1)
    }

    private fun deleteTwoCharBeforeEvowel(ic: InputConnection) {
        ic.deleteSurroundingText(3, 0)
        ic.commitText(E_VOWEL.toChar().toString(), 1)
    }

    private fun getFlagMedial(ic: InputConnection) {
        var getText = ic.getTextBeforeCursor(2, 0)
        var beforeLength = 0
        var currentLength = 1
        var i = 2
        if (getText == null) {
            getText = ""
        }
        var current = getText.get(0).code
        // checking medial and store medial to stack orderly
        // till to Consonant or word separator or till at the start of input box
        while (!(isConsonant(current) || MyIME.isWordSeparator(current))
            && (beforeLength != currentLength) && (isMynMedial(current))
        ) {
            medialCount++
            pushMedialStack(current) //
            swapMedial = true
            swapConsonant = true
            i++
            beforeLength = currentLength
            getText = ic.getTextBeforeCursor(i, 0)
            currentLength = getText!!.length // set current length of new
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
        val reorderChars = charArrayOf(primaryCode.toChar(), E_VOWEL.toChar())
        return String(reorderChars)
    }

    private fun isValidMedial(primaryCode: Int): Boolean {
        return if (!swapConsonant)  // if no previous consonant, it is invalid
            false
        else if (!swapMedial)  // if no previous medial, no need to check it is
        // valid
            true
        else if (medialCount > 2)  // only 3 times of medial;
            false
        else if (medialStack[medialCount - 1] == 0x103e)  // if previous medial is
        // Ha medial, no other
        // medial followed
            false
        else if ((medialStack[medialCount - 1] == 0x103d)
            && (primaryCode == 4158)
        )  // if previous medial is Wa medial, only Ha medial will followed, no
        // other medial followed
            true
        else !(((medialStack[medialCount - 1] == 0x103b) && (primaryCode == 0x103c)) // if previous medial Ya medial and then Ra medial followed
                || ((medialStack[medialCount - 1] == 0x103c) && (primaryCode == 0x103b)) // if previous medial is Ra medial and then Ya medial followed
                || ((medialStack[medialCount - 1] == 0x103b) && (primaryCode == 0x103b)) // if previous medial is Ya medial and then Ya medial followed
                || ((medialStack[medialCount - 1] == 0x103c) && (primaryCode == 0x103c)))
        // if previous medial is Ra medial and then Ra medial followed
        // if All condition is passed, medial is valid :D Bravo
    }

    private fun isOthers(primaryCode: Int): Boolean {
        when (primaryCode) {
            0x102b, 0x102c, 0x1037, 0x1038 -> return true
        }
        return false
    }

    private fun isConsonant(primaryCode: Int): Boolean {
        return (primaryCode > 4095) && (primaryCode < 4130)
                || ((primaryCode > 4185) && (primaryCode < 4190))
    }

    private fun isMynMedial(primaryCode: Int): Boolean {
        return (primaryCode > 0x103a) && (primaryCode < 0x103f)
    }

    private fun isMonMedial(primaryCode: Int): Boolean {
        return (primaryCode > 0x103a) && (primaryCode < 0x1061)
    }

    fun handleMonMoneySym(ic: InputConnection) {
        val temp = charArrayOf(
            0x1012.toChar(),
            0x1039.toChar(),
            0x1000.toChar(),
            0x1031.toChar(),
            0x101d.toChar(),
            0x103a.toChar()
        )
        ic.commitText(String(temp), 1)
    }
}
