package com.sanlin.mkeyboard

import android.content.Context
import android.view.inputmethod.InputConnection

class BamarKeyboard(context: Context?, xmlLayoutResId: Int) :
    MyKeyboard(context, xmlLayoutResId) {
    private var stackPointer = 0
    private val stack = IntArray(3)

    private var swapConsonant = false
    private var medialCount: Short = 0
    private var swapMedial = false
    private var hasZWSP = false
    private var evowel_virama = false
    private val medialStack = IntArray(3)

    fun handelMyanmarInputText(primaryCode: Int, ic: InputConnection): String {
        // if e_vowel renew checking flag if
        if (primaryCode == 0x1031 && MyConfig.isPrimeBookOn()) {
            var outText = primaryCode.toChar().toString()
            // if (isConsonant(charCodeBeforeCursor)) {
            val twoCharBeforeChar = ic.getTextBeforeCursor(2, 0)
            if (!(twoCharBeforeChar!!.length == 2 && twoCharBeforeChar[0].code == 0x103a && twoCharBeforeChar[1].code == 0x1039)) {
                val temp = charArrayOf(8203.toChar(), primaryCode.toChar()) // ZWSP added
                hasZWSP = true
                outText = String(temp)
            }
            swapConsonant = false
            medialCount = 0
            swapMedial = false
            evowel_virama = false
            return outText
        }
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
            evowel_virama = false
            return primaryCode.toChar().toString() // else it is the first
        }
        // character no need to reorder
        // tha + ra_medial = aw vowel autocorrect
        /* if ((charCodeBeforeCursor == 0x101e) && (primaryCode == 0x103c)) {
            ic.deleteSurroundingText(1, 0);
            return String.valueOf((char) 0x1029);
        }*/
        // dot_above + au vowel = au vowel + dot_above autocorrect
        if ((charCodeBeforeCursor == 0x1036) && (primaryCode == 0x102f)) {
            val temp = charArrayOf(0x102f.toChar(), 0x1036.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }
        // ss + ya_medial = za myint zwe autocorrect
        if ((charCodeBeforeCursor == 0x1005) && (primaryCode == 0x103b)) {
            ic.deleteSurroundingText(1, 0)
            return 0x1008.toChar().toString()
        }
        // uu + aa_vowel = 0x1009 + aa_vowel autocorrect
        if ((charCodeBeforeCursor == 0x1025) && (primaryCode == 0x102c)) {
            val temp = charArrayOf(0x1009.toChar(), 0x102c.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }
        // uu_vowel+ii_vowel = u autocorrect
        if ((charCodeBeforeCursor == 0x1025) && (primaryCode == 0x102e)) {
            ic.deleteSurroundingText(1, 0)
            return 4134.toChar().toString() // U
        }
        // uu_vowel+asat autocorrect
        if ((charCodeBeforeCursor == 0x1025) && (primaryCode == 0x103a)) {
            val temp = charArrayOf(0x1009.toChar(), 0x103a.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }
        // asat + dot below to reorder dot below + asat
        if ((charCodeBeforeCursor == 0x103a) && (primaryCode == 0x1037)) {
            val temp = charArrayOf(0x1037.toChar(), 0x103a.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }
        // if PrimeBook Function is on
        if (MyConfig.isPrimeBookOn()) {
            return primeBookFunction(primaryCode, ic, charCodeBeforeCursor)
        }

        return primaryCode.toChar().toString()
    }

    private fun primeBookFunction(
        primaryCode: Int, ic: InputConnection,
        charCodeBeforeCursor: Int
    ): String {
        // E vowel + cons + virama + cons
        if ((primaryCode == 0x1039) and (swapConsonant)) {
            swapConsonant = false
            evowel_virama = true
            return primaryCode.toChar().toString()
        }

        if (evowel_virama) {
            if (isConsonant(primaryCode)) {
                swapConsonant = true
                ic.deleteSurroundingText(2, 0)
                val reorderChars = charArrayOf(
                    0x1039.toChar(), primaryCode.toChar(),
                    0x1031.toChar()
                )
                val reorderString = String(reorderChars)
                evowel_virama = false
                return reorderString
            } else {
                evowel_virama = false
            }
        }
        if (isOthers(primaryCode)) {
            swapConsonant = false
            medialCount = 0
            swapMedial = false
            evowel_virama = false
            return primaryCode.toChar().toString()
        }
        // if no previous E_vowel, no need to check Reorder.
        if (charCodeBeforeCursor != 0x1031) {
            return primaryCode.toChar().toString()
        }
        // if input character is consonant and consonant e_vowel swapped,
        // no need to reorder. con+vowel+con
        if (isConsonant(primaryCode) && (swapConsonant)) {
            swapConsonant = false
            swapMedial = false
            medialCount = 0
            return primaryCode.toChar().toString()
        }
        if (isConsonant(primaryCode)) {
            if (!swapConsonant) {
                swapConsonant = true
                return reorder_e_vowel(primaryCode, ic)
            } else {
                swapConsonant = false
                return primaryCode.toChar().toString()
            }
        }
        if (isMedial(primaryCode)) {
            // delete e_vowel and create Type character + e_vowel
            // (reordering)

            if (isValidMedial(primaryCode)) {
                medialStack[medialCount.toInt()] = primaryCode
                medialCount++
                swapMedial = true
                return reorder_e_vowel(primaryCode, ic)
            }
        }
        return primaryCode.toChar().toString()
    }

    fun handleMyanmarDelete(ic: InputConnection) {
        if (MyIME.isEndofText(ic)) {
            handleSingleDelete(ic)
        } else {
            handelMyanmarWordDelete(ic)
        }
        //temporary fixed for zwsp clear error
        //disabled single delete feature
        //handelMyanmarWordDelete(ic)
    }

    private fun handelMyanmarWordDelete(ic: InputConnection) {
        var i = 1
        var getText = ic.getTextBeforeCursor(1, 0)
        // null error fixed on issue of version 1.1
        if ((getText == null) || (getText.length <= 0)) {
            return  // fixed on issue of version 1.2, cause=(getText is null)
            // solution=(if getText is null, return)
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
        while (!(isConsonant(current) || MyIME.isWordSeparator(current)) // or
            // Word
            // separator
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
            var virama = 0
            getText = ic.getTextBeforeCursor(i + 1, 0)
            if (getText != null) virama = getText[0].code
            if (virama == 0x1039) {
                ic.deleteSurroundingText(i + 1, 0)
            } else {
                ic.deleteSurroundingText(i, 0)
            }
        }

        swapConsonant = false
        medialCount = 0
        swapMedial = false
    }

    fun handleSingleDelete(ic: InputConnection) {
        var getText = ic.getTextBeforeCursor(1, 0)
        val firstChar: Int
        val secPrev: Int
        // if getTextBeforeCursor return null, issues on version 1.1
        if (getText == null) {
            getText = ""
        }
        if (getText.length > 0) {
            firstChar = getText.get(0).code
            if (firstChar == 0x1031) {
                // Need to initialize FLAG
                swapConsonant = false
                swapMedial = false
                medialCount = 0
                stackPointer = 0
                // 2nd previous character
                getText = ic.getTextBeforeCursor(2, 0)
                secPrev = getText!![0].code
                if (isMedial(secPrev)) {
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
                    // /need to fix if getThirdText is NULL!
                    var thirdChar = 0
                    if (getThirdText!!.length == 3) thirdChar = getThirdText[0].code
                    if (thirdChar == 0x1039) {
                        deleteTwoCharBeforeEvowel(ic)
                    } else {
                        deleteCharBeforeEvowel(ic)
                    }
                    swapConsonant = false
                    swapMedial = false
                    medialCount = 0
                } else {
                    if (secPrev == 0x200b) ic.deleteSurroundingText(2, 0)
                    else ic.deleteSurroundingText(1, 0)
                }
            } else {
                // If not E_Vowel
                getText = ic.getTextBeforeCursor(2, 0)
                secPrev = getText!![0].code
                val getThirdText = ic.getTextBeforeCursor(3, 0)
                var thirdChar = 0
                if (getThirdText != null && getThirdText.length == 3) thirdChar =
                    getThirdText[0].code

                if (secPrev == 0x1031) {
                    swapConsonant = thirdChar != 0x200b
                }
                MyIME.deleteHandle(ic)
            }
        } else {
            // It is the start of input text box
            ic.deleteSurroundingText(1, 0)
        }
        stackPointer = 0
    }

    private fun deleteCharBeforeEvowel(ic: InputConnection) {
        ic.deleteSurroundingText(2, 0)
        ic.commitText(0x1031.toChar().toString(), 1)
    }

    private fun deleteTwoCharBeforeEvowel(ic: InputConnection) {
        ic.deleteSurroundingText(3, 0)
        ic.commitText(0x1031.toChar().toString(), 1)
    }

    private fun getFlagMedial(ic: InputConnection) {
        var getText = ic.getTextBeforeCursor(2, 0)
        var beforeLength = 0
        var currentLength = 1
        var i = 2
        if (getText == null) {
            getText = ""
        }
        var current: Int = getText.get(0).code
        // checking medial and store medial to stack orderly
        // till to Consonant or word separator or till at the start of input box
        while (!(isConsonant(current) || MyIME.isWordSeparator(current))
            && (beforeLength != currentLength) && (isMedial(current))
        ) {
            medialCount++
            pushMedialStack(current) //
            swapMedial = true
            swapConsonant = true
            i++
            beforeLength = currentLength
            getText = ic.getTextBeforeCursor(i, 0)
            currentLength = getText!!.length // set current length
            // of new
            current = getText[0].code
        }
        if (isConsonant(current)) {
            return
        }

        if ((!isMedial(current)) && (!isConsonant(current))) {
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

    private fun reorder_e_vowel(primaryCode: Int, ic: InputConnection): String {
        if (hasZWSP) {
            ic.deleteSurroundingText(2, 0)
            hasZWSP = false
        } else {
            ic.deleteSurroundingText(1, 0)
        }

        val reorderChars = charArrayOf(primaryCode.toChar(), 0x1031.toChar())
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
            && (primaryCode != 0x103e)
        )  // if previous medial is Wa medial, only Ha medial will followed, no
        // other medial followed
            false
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
        // Is Consonant
        return if ((primaryCode > 4095) && (primaryCode < 4130)) {
            true
        } else false
    }

    private fun isMedial(primaryCode: Int): Boolean {
        // Is Medial?

        return if ((primaryCode > 4154) && (primaryCode < 4159)) {
            true
        } else false
    }

    fun handleMoneySym(ic: InputConnection) {
        // TODO Auto-generated method stub

        val temp = charArrayOf(4096.toChar(), 4155.toChar(), 4117.toChar(), 4154.toChar())
        ic.commitText(String(temp), 1)
    }
}
