package com.sanlin.mkeyboard

import android.content.Context
import android.view.inputmethod.InputConnection

class EastPwoKarenKeyboard : MyKeyboard {
    private var swapConsonant = false
    private var swapMedial = false

    constructor(context: Context?, xmlLayoutResId: Int) : super(context, xmlLayoutResId)

    constructor(
        context: Context?, layoutTemplateResId: Int,
        characters: CharSequence?, columns: Int, horizontalPadding: Int
    ) : super(
        context, layoutTemplateResId, characters, columns,
        horizontalPadding
    )

    fun handleEastPwoKarenDelete(ic: InputConnection) {
        if (MyIME.isEndofText(ic)) {
            handleSingleDelete(ic)
        } else {
            handelWordDelete(ic)
        }
    }

    private fun handelWordDelete(ic: InputConnection) {
        MyIME.deleteHandle(ic)
    }

    private fun handleSingleDelete(ic: InputConnection) {
        var getText = ic.getTextBeforeCursor(1, 0)
        var secPrev = 0
        var thirdPrevChar = 0
        if ((getText == null) || (getText.length <= 0)) {
            ic.deleteSurroundingText(1, 0)
            return
        }
        val firstChar = getText[0].code
        getText = ic.getTextBeforeCursor(2, 0)
        if ((getText != null) && (getText.length == 2)) {
            secPrev = getText[0].code
        }
        getText = ic.getTextBeforeCursor(3, 0)
        if ((getText != null) && (getText.length == 3)) {
            thirdPrevChar = getText[0].code
        }

        if (firstChar == 0x1031) {
            if ((secPrev == 0x103A) && thirdPrevChar == 0x103E) {
                deleteCharBeforeEVowel(ic)
                swapConsonant = true
                swapMedial = true
            } else if (thirdPrevChar == 0x1039) {
                // delete consonant medial
                delete2CharBeforeEVowel(ic)
                swapConsonant = true
                swapMedial = false
            } else if ((isSymMedial(secPrev)) && isConsonant(thirdPrevChar)) {
                // delete medial
                deleteCharBeforeEVowel(ic)
                swapConsonant = true
                swapMedial = false
            } else if (isConsonant(secPrev)) {
                // delete consonant
                deleteCharBeforeEVowel(ic)
                swapConsonant = false
                swapMedial = false
            } else {
                if (secPrev == 8203) ic.deleteSurroundingText(2, 0)
                else MyIME.deleteHandle(ic)
                swapConsonant = false
                swapMedial = false
            }

            return
        }
        if (secPrev == 0x1039) {
            ic.deleteSurroundingText(2, 0)
            if (thirdPrevChar == 0x1031) {
                swapConsonant = true
            }
            return
        }

        MyIME.deleteHandle(ic)
        if (secPrev == 0x1031) {
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
        ic.commitText(0x1031.toChar().toString(), 1)
    }

    private fun deleteCharBeforeEVowel(ic: InputConnection) {
        ic.deleteSurroundingText(2, 0)
        ic.commitText(0x1031.toChar().toString(), 1)
    }

    fun handleEastPwoKarenInput(primaryCode: Int, ic: InputConnection): String {
        var charBeforeCursor = ic.getTextBeforeCursor(1, 0)
        var charCodeBeforeCursor = 0
        if (charBeforeCursor == null) {
            charBeforeCursor = ""
        }
        if (charBeforeCursor.length > 0) charCodeBeforeCursor = charBeforeCursor.get(0).code

        if ((charCodeBeforeCursor == 0x102F) && (primaryCode == 0x103A)) {
            ic.deleteSurroundingText(1, 0)
            val temp = charArrayOf(0x103A.toChar(), 0x102F.toChar())
            return String(temp)
        }
        if ((charCodeBeforeCursor == 0x1003) && (primaryCode == 0x103E)) {
            ic.deleteSurroundingText(1, 0)
            return 0x1070.toChar().toString()
        }
        if ((charCodeBeforeCursor == 0x101F) && (primaryCode == 0x103E)) {
            ic.deleteSurroundingText(1, 0)
            return 0x106F.toChar().toString()
        }
        if (MyConfig.isPrimeBookOn()) {
            return primeInput(primaryCode, ic)
        }

        if (primaryCode < 0) {
            val temp = charArrayOf(0x1039.toChar(), (primaryCode * (-1)).toChar())
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
        if (charBeforeCursor.length > 0) charCodeBeforeCursor = charBeforeCursor.get(0).code
        else {
            // else it is the first character no need to reorder
            swapConsonant = false
            swapMedial = false
            if (primaryCode < 0) {
                val temp = charArrayOf(0x1039.toChar(), (primaryCode * (-1)).toChar())
                return String(temp)
            }
            return primaryCode.toChar().toString()
        }

        if (primaryCode == 0x1031) {
            if (isConsonant(charCodeBeforeCursor)
                || isSymMedial(charCodeBeforeCursor)
            ) {
                val reorderChars = charArrayOf(8203.toChar(), 0x1031.toChar())
                // ZWSP added
                val reorderString = String(reorderChars)
                swapConsonant = false
                swapMedial = false
                return reorderString
            }
        }

        if (charCodeBeforeCursor == 0x1031) {
            if ((primaryCode == 0x103A)) {
                var secPrev = 0
                val getText = ic.getTextBeforeCursor(2, 0)
                if ((getText != null) && (getText.length == 2)) {
                    secPrev = getText[0].code
                }
                if (secPrev == 0x103E) {
                    ic.deleteSurroundingText(2, 0)
                    val reorderChars = charArrayOf(
                        0x103E.toChar(), 0x103A.toChar(),
                        0x1031.toChar()
                    )
                    val reorderString = String(reorderChars)
                    swapConsonant = true
                    swapMedial = true
                    return reorderString
                }
            }
            if (isConsonant(primaryCode) && (!swapConsonant)) {
                // Reorder function
                swapConsonant = true
                swapMedial = false
                return reorder_e_vowel(primaryCode, ic)
            }
            if ((!swapMedial) && (swapConsonant)) {
                if (isSymMedial(primaryCode)) {
                    // Reorder function
                    swapMedial = true
                    return reorder_e_vowel(primaryCode, ic)
                }
                if (primaryCode < 0) {
                    // Reorder virama+(-1 * primaryCode)
                    swapMedial = true
                    return reorder_e_vowel_con_medial(primaryCode, ic)
                }
            }
        }
        swapConsonant = false
        swapMedial = false

        if (primaryCode < 0) {
            val temp = charArrayOf(0x1039.toChar(), (primaryCode * (-1)).toChar())
            return String(temp)
        }
        return primaryCode.toChar().toString()
    }

    private fun reorder_e_vowel(primaryCode: Int, ic: InputConnection): String {
        ic.deleteSurroundingText(1, 0)
        val reorderChars = charArrayOf(primaryCode.toChar(), 0x1031.toChar())
        return String(reorderChars)
    }

    private fun reorder_e_vowel_con_medial(
        primaryCode: Int,
        ic: InputConnection
    ): String {
        ic.deleteSurroundingText(1, 0)
        val reorderChars = charArrayOf(
            0x1039.toChar(), (primaryCode * (-1)).toChar(),
            0x1031.toChar()
        )
        return String(reorderChars)
    }

    private fun isConsonant(code: Int): Boolean {
        if (((code >= 0x1000) && (code <= 0x1021)) || (code == 0x1070)
            || (code == 0x106F) || (code == 0x105C) || (code == 0x106E)
        ) return true
        return false
    }

    private fun isSymMedial(code: Int): Boolean {
        return ((code >= 0x103B) && (code <= 0x103E))
                || ((code >= 0x105E) && (code <= 0x1060))
    }
}
