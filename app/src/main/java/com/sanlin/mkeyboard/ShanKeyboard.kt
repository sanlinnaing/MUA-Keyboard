package com.sanlin.mkeyboard

import android.content.Context
import android.view.inputmethod.InputConnection

class ShanKeyboard : MyKeyboard {
    private var swapConsonant = false
    private var swapMedial = false
    private val MY_E = 0x1031
    private val SH_E = 0x1084
    private val ASAT = 0x103a

    constructor(context: Context?, xmlLayoutResId: Int) : super(context, xmlLayoutResId)

    constructor(
        context: Context?, layoutTemplateResId: Int,
        characters: CharSequence?, columns: Int, horizontalPadding: Int
    ) : super(
        context, layoutTemplateResId, characters, columns,
        horizontalPadding
    )

    fun handleShanInputText(primaryCode: Int, ic: InputConnection): String {
        var charBeforeCursor = ic.getTextBeforeCursor(1, 0)
        if ((primaryCode == MY_E || primaryCode == SH_E)
            && MyConfig.isPrimeBookOn()
        ) {
            val temp = charArrayOf(0x200b.toChar(), primaryCode.toChar()) // ZWSP added
            val outText = String(temp)
            swapConsonant = false
            swapMedial = false
            return outText
        }
        // if getTextBeforeCursor return null, issues on version 1.1
        if (charBeforeCursor == null) {
            charBeforeCursor = ""
        }
        var charCodeBeforeCursor: Int? = null
        if (charBeforeCursor.length > 0) charCodeBeforeCursor = charBeforeCursor[0].code
        else {
            return primaryCode.toChar().toString()
        }
        if (charCodeBeforeCursor == ASAT && primaryCode == 0x1082) {
            val temp = charArrayOf(0x1082.toChar(), ASAT.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }
        if (charCodeBeforeCursor == 0x1086 && primaryCode == 0x1062) {
            val temp = charArrayOf(0x1062.toChar(), 0x1086.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }
        if (charCodeBeforeCursor == 0x1030 && primaryCode == 0x102d) {
            val temp = charArrayOf(0x102d.toChar(), 0x1030.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }
        if (charCodeBeforeCursor == 0x102f && primaryCode == 0x102d) {
            val temp = charArrayOf(0x102d.toChar(), 0x102f.toChar())
            ic.deleteSurroundingText(1, 0)
            return String(temp)
        }
        if (MyConfig.isPrimeBookOn()) return handleShanTyping(primaryCode, ic)
        return primaryCode.toChar().toString()
    }

    private fun handleShanTyping(primaryCode: Int, ic: InputConnection): String {
        var charBeforeCursor = ic.getTextBeforeCursor(1, 0)
        // if getTextBeforeCursor return null, issues on version 1.1
        if (charBeforeCursor == null) {
            charBeforeCursor = ""
        }
        var charCodeBeforeCursor: Int? = null

        if (isOthers(primaryCode)) {
            swapConsonant = false
            swapMedial = false
            return primaryCode.toChar().toString()
        }
        if (charBeforeCursor.length > 0) charCodeBeforeCursor = charBeforeCursor[0].code
        else {
            swapConsonant = false
            swapMedial = false
            return primaryCode.toChar().toString()
        }

        if ((charCodeBeforeCursor == MY_E || charCodeBeforeCursor == SH_E)) {
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
            if (primaryCode == ASAT) {
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
        return primaryCode == 0x103b || primaryCode == 0x103c
    }

    private fun reorder(
        primaryCode: Int, charCodeBeforeCursor: Int,
        ic: InputConnection
    ): String {
        ic.deleteSurroundingText(1, 0)
        val reorderChars = charArrayOf(primaryCode.toChar(), charCodeBeforeCursor.toChar())
        return String(reorderChars)
    }

    fun handleShanDelete(ic: InputConnection) {
        if (MyIME.isEndofText(ic)) {
            handleSingleDelete(ic)
        } else {
            MyIME.deleteHandle(ic)
        }
    }

    private fun handleSingleDelete(ic: InputConnection) {
        var getText = ic.getTextBeforeCursor(1, 0)
        // if getTextBeforeCursor return null, issues on version 1.1
        if (getText == null) {
            getText = ""
        }

        val firstChar: Int
        val secPrev: Int
        if (getText.isNotEmpty()) {
            firstChar = getText.get(0).code
            if (firstChar == MY_E || firstChar == SH_E) {
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
                if (getThirdText != null && getThirdText.length == 3) thirdChar =
                    getThirdText[0].code

                if (secPrev == MY_E || secPrev == SH_E) swapConsonant = thirdChar != 0x200b
                // ic.deleteSurroundingText(1, 0);
                MyIME.deleteHandle(ic)
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
        return MyIME.isShanConsonant(primaryCode)
    }

    fun shanVowel1(): String {
        val outText = charArrayOf(4226.toChar(), 4154.toChar())
        return String(outText)
    }

    fun handleShanMoneySym(ic: InputConnection) {
        val temp = charArrayOf(0x1015.toChar(), 0x103b.toChar(), 0x1083.toChar(), 0x1038.toChar())
        ic.commitText(String(temp), 1)
    }
}
