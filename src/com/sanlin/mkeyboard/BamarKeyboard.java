package com.sanlin.mkeyboard;

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.InputConnection;

public class BamarKeyboard extends MyKeyboard {

	private int DOT_BELOW = 4151;
	private int ASAT = 4154;
	private int NYA = 4105;
	private int UU = 4133;
	private int II_VOWEL = 4142;
	private int AA_VOWEL = 4140;
	private int SS = 4101;
	private int YA_MEDIAL = 4155;

	private int stackPointer = 0;
	private int[] stack = new int[3];

	private boolean swapConsonant = false;
	private short medialCount = 0;
	private boolean swapMedial = false;
	private int E_VOWEL = 4145;
	private int VIRAMA = 4153;
	private boolean EVOWEL_VIRAMA = false;
	private int[] medialStack = new int[3];

	public BamarKeyboard(Context context, int xmlLayoutResId) {
		super(context, xmlLayoutResId);
		// TODO Auto-generated constructor stub
	}

	public String handelMyanmarInputText(int primaryCode, InputConnection ic) {
		// TODO Auto-generated method stub

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
			EVOWEL_VIRAMA = false;
			return String.valueOf((char) primaryCode);// else it is the first
		} // character no need to
			// reorder
		// tha + ra_medial = aw vowel autocorrect
		if ((charcodeBeforeCursor == 4126) && (primaryCode == 4156)) {
			ic.deleteSurroundingText(1, 0);
			return String.valueOf((char) 4137);
		}
		// dot_above + au vowel = au vowel + dot_above autocorrect
		if ((charcodeBeforeCursor == 4150) && (primaryCode == 4143)) {
			char temp[] = { (char) 4143, (char) 4150 };
			ic.deleteSurroundingText(1, 0);
			return String.valueOf(temp);
		}
		// ss + ya_medial = za myint zwe autocorrect
		if ((charcodeBeforeCursor == SS) && (primaryCode == YA_MEDIAL)) {
			ic.deleteSurroundingText(1, 0);
			return String.valueOf((char) 4104);
		}
		// uu + aa_vowel = NYA + aa_vowel autocorrect
		if ((charcodeBeforeCursor == UU) && (primaryCode == AA_VOWEL)) {
			char temp[] = { (char) NYA, (char) AA_VOWEL };
			ic.deleteSurroundingText(1, 0);
			return String.valueOf(temp);
		}
		// uu_vowel+ii_vowel = u autocorrect
		if ((charcodeBeforeCursor == UU) && (primaryCode == II_VOWEL)) {

			ic.deleteSurroundingText(1, 0);
			return String.valueOf((char) 4134); // U
		}
		// uu_vowel+asat autocorrect
		if ((charcodeBeforeCursor == UU) && (primaryCode == ASAT)) {
			char temp[] = { (char) NYA, (char) ASAT };
			ic.deleteSurroundingText(1, 0);
			return String.valueOf(temp);
		}
		// asat + dot below to reorder dot below + asat
		if ((charcodeBeforeCursor == ASAT) && (primaryCode == DOT_BELOW)) {
			char temp[] = { (char) DOT_BELOW, (char) ASAT };
			ic.deleteSurroundingText(1, 0);
			return String.valueOf(temp);
		}
		// if PrimeBook Function is on
		if (MyConfig.isPrimeBookOn()) {
			Log.d("HandleMyanmarText", "Prime Book on");
			return primeBookFunction(primaryCode, ic, charcodeBeforeCursor);
		}

		return String.valueOf((char) primaryCode);

	}

	private String primeBookFunction(int primaryCode, InputConnection ic,
			Integer charcodeBeforeCursor) {
		// E vowel + cons + virama + cons
		if ((primaryCode == VIRAMA) & (swapConsonant)) {
			swapConsonant = false;
			EVOWEL_VIRAMA = true;
			return String.valueOf((char) primaryCode);
		}

		// if e_vowel renew checking flag if
		if (primaryCode == E_VOWEL) {
			String outText = String.valueOf((char) primaryCode);
			if (isConsonant(charcodeBeforeCursor)) {
				char temp[] = { (char) 8203, (char) primaryCode }; // ZWSP added
				outText = String.valueOf(temp);
			}
			swapConsonant = false;
			medialCount = 0;
			swapMedial = false;
			EVOWEL_VIRAMA = false;
			return outText;
		}

		if (EVOWEL_VIRAMA) {
			if (isConsonant(primaryCode)) {
				swapConsonant = true;
				ic.deleteSurroundingText(2, 0);
				char[] reorderChars = { (char) VIRAMA, (char) primaryCode,
						(char) E_VOWEL };
				String reorderString = String.valueOf(reorderChars);
				EVOWEL_VIRAMA = false;
				return reorderString;
			} else {
				EVOWEL_VIRAMA = false;
			}
		}
		if (isOthers(primaryCode)) {
			swapConsonant = false;
			medialCount = 0;
			swapMedial = false;
			EVOWEL_VIRAMA = false;
			return String.valueOf((char) primaryCode);
		}
		// if no previous E_vowel, no need to check Reorder.
		if (charcodeBeforeCursor != E_VOWEL) {
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
			Log.d("HandleMyanmarDelete", "it is end of text box");
		} else {
			handelMyanmarWordDelete(ic);
			Log.d("HandleMyanmarDelete", "it is not the end of text");
		}
	}

	private void handelMyanmarWordDelete(InputConnection ic) {
		int i = 1;
		CharSequence getText = ic.getTextBeforeCursor(1, 0);
		// null error fixed on issue of version 1.1
		if (getText == null) {
			return;// fixed on issue of version 1.2, cause=(getText is null) solution=(if getText is null, return)
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
		if (getText.length() > 0) {
			current = Integer.valueOf(getText.charAt(0));
			Log.d("handleDelete", String.valueOf(current.intValue()));
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
				getText = ic.getTextBeforeCursor(i + 1, 0);
				int virama = getText.charAt(0);
				if (virama == VIRAMA) {
					ic.deleteSurroundingText(i + 1, 0);
				} else {
					ic.deleteSurroundingText(i, 0);
				}
			}
		} else
			ic.deleteSurroundingText(1, 0);
		swapConsonant = false;
		medialCount = 0;
		swapMedial = false;

	}

	private void handleSingleDelete(InputConnection ic) {

		CharSequence getText = ic.getTextBeforeCursor(1, 0);
		Integer firstChar;
		Integer secPrev;
		// if getTextBeforeCursor return null, issues on version 1.1
		if (getText == null) {
			getText = "";
		}
		if (getText.length() > 0) {
			firstChar = Integer.valueOf(getText.charAt(0));
			if (firstChar == E_VOWEL) {
				// Need to initialize FLAG
				swapConsonant = false;
				swapMedial = false;
				medialCount = 0;
				stackPointer = 0;
				// 2nd previous characher
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
///need to fix if getThirdText is NULL!
					int thirdChar = 0;
					if (getThirdText.length() == 3)
						thirdChar = getThirdText.charAt(0);
					if (thirdChar == VIRAMA) {
						deleteTwoCharBeforeEvowel(ic);
					} else {
						deleteCharBeforeEvowel(ic);
					}
					swapConsonant = false;
					swapMedial = false;
					medialCount = 0;
				} else {
					ic.deleteSurroundingText(1, 0);
				}
			} else {
				// If not E_Vowel
				getText = ic.getTextBeforeCursor(2, 0);
				secPrev = Integer.valueOf(getText.charAt(0));
				if (secPrev == E_VOWEL)
					swapConsonant = true;
				//ic.deleteSurroundingText(1, 0);
				MyIME.deleteHandle(ic);
			}
		} else {
			// It is the start of input text box
			ic.deleteSurroundingText(1, 0);
		}
		stackPointer = 0;
		// / Log output need to delete
		String logText = "";
		for (int k = 0; k < medialCount; k++) {
			logText = logText + String.valueOf((char) medialStack[k]) + " | ";
		}
		Log.d("SingleDelete", "medialSTack" + logText);

	}

	private void deleteCharBeforeEvowel(InputConnection ic) {
		ic.deleteSurroundingText(2, 0);
		ic.commitText(String.valueOf((char) E_VOWEL), 1);
	}

	private void deleteTwoCharBeforeEvowel(InputConnection ic) {
		ic.deleteSurroundingText(3, 0);
		ic.commitText(String.valueOf((char) E_VOWEL), 1);
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
		Log.d("getMedialFlag", "MedialCount = " + medialCount);
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
		char[] reorderChars = { (char) primaryCode, (char) E_VOWEL };
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
		else if (medialStack[medialCount - 1] == 4158)// if previous medial is
														// Ha medial, no other
														// medial followed
			return false;
		else if ((medialStack[medialCount - 1] == 4157)
				&& (primaryCode == 4158))
			// if previous medial is Wa medial, only Ha madial will followed, no
			// other medial followed
			return true;
		else if (((medialStack[medialCount - 1] == 4155) && (primaryCode == 4156))
				// if previous medial Ya medial and then Ra medial followed
				|| ((medialStack[medialCount - 1] == 4156) && (primaryCode == 4155))
				// if previous medial is Ra medial and then Ya medial followed
				|| ((medialStack[medialCount - 1] == 4155) && (primaryCode == 4155))
				// if previous medial is Ya medial and then Ya medial followed
				|| ((medialStack[medialCount - 1] == 4156) && (primaryCode == 4156)))
			// if previous medial is Ra medial and then Ra medial followed
			return false;
		// if All condition is passed, medial is valid :D Bravo
		return true;
	}

	private boolean isOthers(int primaryCode) {
		switch (primaryCode) {
		case 4139:
		case 4140:
		case 4151:
		case 4152:
			return true;
		}
		return false;
	}

	private boolean isConsonant(int primaryCode) {
		// Is Consonant
		if ((primaryCode > 4095) & (primaryCode < 4130)) {
			Log.d("handleInputText", "It is a consonant");
			return true;
		} else
			return false;
	}

	private boolean isMedial(int primaryCode) {

		// Is Medial?
		if ((primaryCode > 4154) & (primaryCode < 4159)) {
			Log.d("handleInputText", "It is a medial");
			return true;
		} else
			return false;
	}

	public void handleMoneySym(InputConnection ic) {
		// TODO Auto-generated method stub
		
		char temp[] = { 4096, 4155, 4117, 4154 };
		ic.commitText(String.valueOf(temp), 1);
		
	}

}
