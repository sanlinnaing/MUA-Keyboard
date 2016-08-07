package com.sanlin.mkeyboard;

import android.content.Context;
import android.view.inputmethod.InputConnection;

public class BamarKeyboard extends MyKeyboard {

    private int stackPointer = 0;
    private int[] stack = new int[3];

    private boolean swapConsonant = false;
    private short medialCount = 0;
    private boolean swapMedial = false;
    private boolean evowel_virama = false;
    private int[] medialStack = new int[3];

    public BamarKeyboard(Context context, int xmlLayoutResId) {
        super(context, xmlLayoutResId);
        // TODO Auto-generated constructor stub
    }

    public String handelMyanmarInputText(int primaryCode, InputConnection ic) {
        // if e_vowel renew checking flag if
        if (primaryCode == 0x1031 && MyConfig.isPrimeBookOn()) {
            String outText = String.valueOf((char) primaryCode);
            // if (isConsonant(charcodeBeforeCursor)) {
            CharSequence twoCharBeforeChar = ic.getTextBeforeCursor(2, 0);
            if (!(twoCharBeforeChar.length() == 2 && twoCharBeforeChar.charAt(0) == 0x103a && twoCharBeforeChar.charAt(1) == 0x1039)) {
                char temp[] = {(char) 8203, (char) primaryCode}; // ZWSP added
                outText = String.valueOf(temp);
            }
            swapConsonant = false;
            medialCount = 0;
            swapMedial = false;
            evowel_virama = false;
            return outText;
        }
        CharSequence charBeforeCursor = ic.getTextBeforeCursor(1, 0);
        Integer charcodeBeforeCursor;
        // if getTextBeforeCursor return null, issues on version 1.1
        if (charBeforeCursor == null) {
            charBeforeCursor = "";
        }
        if (charBeforeCursor.length() > 0)
            charcodeBeforeCursor = Integer.valueOf(charBeforeCursor.charAt(0));
        else {
            swapConsonant = false;
            medialCount = 0;
            swapMedial = false;
            evowel_virama = false;
            return String.valueOf((char) primaryCode);// else it is the first
        } // character no need to
        // reorder
        // tha + ra_medial = aw vowel autocorrect
       /* if ((charcodeBeforeCursor == 0x101e) && (primaryCode == 0x103c)) {
            ic.deleteSurroundingText(1, 0);
            return String.valueOf((char) 0x1029);
        }*/
        // dot_above + au vowel = au vowel + dot_above autocorrect
        if ((charcodeBeforeCursor == 0x1036) && (primaryCode == 0x102f)) {
            char temp[] = {(char) 0x102f, (char) 0x1036};
            ic.deleteSurroundingText(1, 0);
            return String.valueOf(temp);
        }
        // ss + ya_medial = za myint zwe autocorrect
        if ((charcodeBeforeCursor == 0x1005) && (primaryCode == 0x103b)) {
            ic.deleteSurroundingText(1, 0);
            return String.valueOf((char) 0x1008);
        }
        // uu + aa_vowel = 0x1009 + aa_vowel autocorrect
        if ((charcodeBeforeCursor == 0x1025) && (primaryCode == 0x102c)) {
            char temp[] = {(char) 0x1009, (char) 0x102c};
            ic.deleteSurroundingText(1, 0);
            return String.valueOf(temp);
        }
        // uu_vowel+ii_vowel = u autocorrect
        if ((charcodeBeforeCursor == 0x1025) && (primaryCode == 0x102e)) {

            ic.deleteSurroundingText(1, 0);
            return String.valueOf((char) 4134); // U
        }
        // uu_vowel+asat autocorrect
        if ((charcodeBeforeCursor == 0x1025) && (primaryCode == 0x103a)) {
            char temp[] = {(char) 0x1009, (char) 0x103a};
            ic.deleteSurroundingText(1, 0);
            return String.valueOf(temp);
        }
        // asat + dot below to reorder dot below + asat
        if ((charcodeBeforeCursor == 0x103a) && (primaryCode == 0x1037)) {
            char temp[] = {(char) 0x1037, (char) 0x103a};
            ic.deleteSurroundingText(1, 0);
            return String.valueOf(temp);
        }
        // if PrimeBook Function is on
        if (MyConfig.isPrimeBookOn()) {
            return primeBookFunction(primaryCode, ic, charcodeBeforeCursor);
        }

        return String.valueOf((char) primaryCode);

    }

    private String primeBookFunction(int primaryCode, InputConnection ic,
                                     Integer charcodeBeforeCursor) {
        // E vowel + cons + virama + cons
        if ((primaryCode == 0x1039) & (swapConsonant)) {
            swapConsonant = false;
            evowel_virama = true;
            return String.valueOf((char) primaryCode);
        }

        if (evowel_virama) {
            if (isConsonant(primaryCode)) {
                swapConsonant = true;
                ic.deleteSurroundingText(2, 0);
                char[] reorderChars = {(char) 0x1039, (char) primaryCode,
                        (char) 0x1031};
                String reorderString = String.valueOf(reorderChars);
                evowel_virama = false;
                return reorderString;
            } else {
                evowel_virama = false;
            }
        }
        if (isOthers(primaryCode)) {
            swapConsonant = false;
            medialCount = 0;
            swapMedial = false;
            evowel_virama = false;
            return String.valueOf((char) primaryCode);
        }
        // if no previous E_vowel, no need to check Reorder.
        if (charcodeBeforeCursor != 0x1031) {
            return String.valueOf((char) primaryCode);
        }
        // if input character is consonant and consonant e_vowel swapped, no
        // need
        // to reorder. con+vowel+con
        if (isConsonant(primaryCode) && (swapConsonant)) {
            swapConsonant = false;
            swapMedial = false;
            medialCount = 0;
            return String.valueOf((char) primaryCode);
        }
        if (isConsonant(primaryCode)) {
            if (!swapConsonant) {
                swapConsonant = true;
                return reorder_e_vowel(primaryCode, ic);
            } else {
                swapConsonant = false;
                return String.valueOf((char) primaryCode);
            }
        }
        if (isMedial(primaryCode)) {
            // delete e_vowel and create Type character + e_vowel
            // (reordering)

            if (isValidMedial(primaryCode)) {
                medialStack[medialCount] = primaryCode;
                medialCount++;
                swapMedial = true;
                return reorder_e_vowel(primaryCode, ic);
            }
        }
        return String.valueOf((char) primaryCode);
    }

    public void handleMyanmarDelete(InputConnection ic) {
        if (MyIME.isEndofText(ic)) {
            handleSingleDelete(ic);
        } else {
            handelMyanmarWordDelete(ic);
        }
    }

    private void handelMyanmarWordDelete(InputConnection ic) {
        int i = 1;
        CharSequence getText = ic.getTextBeforeCursor(1, 0);
        // null error fixed on issue of version 1.1
        if ((getText == null) || (getText.length() <= 0)) {
            return;// fixed on issue of version 1.2, cause=(getText is null)
            // solution=(if getText is null, return)
        }
        // for Emotion delete
        if (Character.isLowSurrogate(getText.charAt(0))
                || Character.isHighSurrogate(getText.charAt(0))) {
            ic.deleteSurroundingText(2, 0);
            return;
        }
        Integer current;
        int beforeLength = 0;
        int currentLength = 1;

        current = Integer.valueOf(getText.charAt(0));
        while (!(isConsonant(current) || MyIME.isWordSeparator(current))// or
                // Word
                // separator
                && (beforeLength != currentLength)) {
            i++;
            beforeLength = currentLength;
            getText = ic.getTextBeforeCursor(i, 0);
            currentLength = getText.length();
            current = Integer.valueOf(getText.charAt(0));
        }
        if (beforeLength == currentLength) {
            ic.deleteSurroundingText(1, 0);
        } else {
            int virama = 0;
            getText = ic.getTextBeforeCursor(i + 1, 0);
            if (getText != null)
                virama = getText.charAt(0);
            if (virama == 0x1039) {
                ic.deleteSurroundingText(i + 1, 0);
            } else {
                ic.deleteSurroundingText(i, 0);
            }
        }

        swapConsonant = false;
        medialCount = 0;
        swapMedial = false;

    }

    public void handleSingleDelete(InputConnection ic) {

        CharSequence getText = ic.getTextBeforeCursor(1, 0);
        Integer firstChar;
        Integer secPrev;
        // if getTextBeforeCursor return null, issues on version 1.1
        if (getText == null) {
            getText = "";
        }
        if (getText.length() > 0) {
            firstChar = Integer.valueOf(getText.charAt(0));
            if (firstChar == 0x1031) {
                // Need to initialize FLAG
                swapConsonant = false;
                swapMedial = false;
                medialCount = 0;
                stackPointer = 0;
                // 2nd previous character
                getText = ic.getTextBeforeCursor(2, 0);
                secPrev = Integer.valueOf(getText.charAt(0));
                if (isMedial(secPrev)) {
                    getFlagMedial(ic);
                    if (swapConsonant == true) {
                        deleteCharBeforeEvowel(ic);
                        medialCount--;

                        stackPointer--;
                        if (medialCount <= 0) {
                            swapMedial = false;
                        }
                        for (int j = 0; j < medialCount; j++) {
                            medialStack[j] = stack[stackPointer];
                            stackPointer--;
                        }
                        // nul point exception cause medialCount = -1
                        if (medialCount < 0) {
                            medialCount = 0;
                        }
                    } else {
                        ic.deleteSurroundingText(1, 0);
                    }

                } else if (isConsonant(secPrev)) {
                    CharSequence getThirdText = ic.getTextBeforeCursor(3, 0);
                    // /need to fix if getThirdText is NULL!
                    int thirdChar = 0;
                    if (getThirdText.length() == 3)
                        thirdChar = getThirdText.charAt(0);
                    if (thirdChar == 0x1039) {
                        deleteTwoCharBeforeEvowel(ic);
                    } else {
                        deleteCharBeforeEvowel(ic);
                    }
                    swapConsonant = false;
                    swapMedial = false;
                    medialCount = 0;
                } else {
                    if (secPrev == 0x200b)
                        ic.deleteSurroundingText(2, 0);
                    else
                        ic.deleteSurroundingText(1, 0);
                }
            } else {
                // If not E_Vowel
                getText = ic.getTextBeforeCursor(2, 0);
                secPrev = Integer.valueOf(getText.charAt(0));
                CharSequence getThirdText = ic.getTextBeforeCursor(3, 0);
                int thirdChar = 0;
                if (getThirdText != null && getThirdText.length() == 3)
                    thirdChar = getThirdText.charAt(0);

                if (secPrev == 0x1031) {
                    if (thirdChar == 0x200b)
                        swapConsonant = false;
                    else
                        swapConsonant = true;
                }
                MyIME.deleteHandle(ic);
            }
        } else {
            // It is the start of input text box
            ic.deleteSurroundingText(1, 0);
        }
        stackPointer = 0;

    }

    private void deleteCharBeforeEvowel(InputConnection ic) {
        ic.deleteSurroundingText(2, 0);
        ic.commitText(String.valueOf((char) 0x1031), 1);
    }

    private void deleteTwoCharBeforeEvowel(InputConnection ic) {
        ic.deleteSurroundingText(3, 0);
        ic.commitText(String.valueOf((char) 0x1031), 1);
    }

    private boolean getFlagMedial(InputConnection ic) {
        CharSequence getText = ic.getTextBeforeCursor(2, 0);
        int beforeLength = 0;
        int currentLength = 1;
        int i = 2;
        if (getText == null) {
            getText = "";
        }
        int current = getText.charAt(0);
        // checking medial and store medial to stack orderly
        // till to Consonant or word sperator or till at the start of input box
        while (!(isConsonant(current) || MyIME.isWordSeparator(current))
                && (beforeLength != currentLength) && (isMedial(current))) {
            medialCount++;
            pushMedialStack(current);//
            swapMedial = true;
            swapConsonant = true;
            i++;
            beforeLength = currentLength;
            getText = ic.getTextBeforeCursor(i, 0);
            currentLength = getText.length();// set current length
            // of new
            current = Integer.valueOf(getText.charAt(0));

        }
        if (isConsonant(current)) {

            return true;

        }

        if ((!isMedial(current)) && (!isConsonant(current))) {
            swapMedial = false;
            swapConsonant = false;
            medialCount = 0;
            stackPointer = 0;
            return false;

        }
        if (beforeLength == currentLength) {
            swapMedial = false;
            swapConsonant = false;
            medialCount = 0;
            stackPointer = 0;
            return false;
        }
        return true;
    }

    private void pushMedialStack(Integer current) {
        // TODO Auto-generated method stub
        stack[stackPointer] = current;
        stackPointer++;

    }

    private String reorder_e_vowel(int primaryCode, InputConnection ic) {
        ic.deleteSurroundingText(1, 0);
        char[] reorderChars = {(char) primaryCode, (char) 0x1031};
        String reorderString = String.valueOf(reorderChars);
        return reorderString;
    }

    private boolean isValidMedial(int primaryCode) {

        if (!swapConsonant)// if no previous consonant, it is invalid
            return false;
        else if (!swapMedial)// if no previous medial, no need to check it is
            // valid
            return true;
        else if (medialCount > 2)// only 3 times of medial;
            return false;
        else if (medialStack[medialCount - 1] == 0x103e)// if previous medial is
            // Ha medial, no other
            // medial followed
            return false;
        else if ((medialStack[medialCount - 1] == 0x103d)
                && (primaryCode != 0x103e))
            // if previous medial is Wa medial, only Ha madial will followed, no
            // other medial followed
            return false;
        else if (((medialStack[medialCount - 1] == 0x103b) && (primaryCode == 0x103c))
                // if previous medial Ya medial and then Ra medial followed
                || ((medialStack[medialCount - 1] == 0x103c) && (primaryCode == 0x103b))
                // if previous medial is Ra medial and then Ya medial followed
                || ((medialStack[medialCount - 1] == 0x103b) && (primaryCode == 0x103b))
                // if previous medial is Ya medial and then Ya medial followed
                || ((medialStack[medialCount - 1] == 0x103c) && (primaryCode == 0x103c)))
            // if previous medial is Ra medial and then Ra medial followed
            return false;
        // if All condition is passed, medial is valid :D Bravo
        return true;
    }

    private boolean isOthers(int primaryCode) {
        switch (primaryCode) {
            case 0x102b:
            case 0x102c:
            case 0x1037:
            case 0x1038:
                return true;
        }
        return false;
    }

    private boolean isConsonant(int primaryCode) {
        // Is Consonant
        if ((primaryCode > 4095) && (primaryCode < 4130)) {
            return true;
        } else
            return false;
    }

    private boolean isMedial(int primaryCode) {

        // Is Medial?
        if ((primaryCode > 4154) && (primaryCode < 4159)) {
            return true;
        } else
            return false;
    }

    public void handleMoneySym(InputConnection ic) {
        // TODO Auto-generated method stub

        char temp[] = {4096, 4155, 4117, 4154};
        ic.commitText(String.valueOf(temp), 1);

    }

}
