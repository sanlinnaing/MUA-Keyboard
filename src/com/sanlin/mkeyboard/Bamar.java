package com.sanlin.mkeyboard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.InputConnection;

public class Bamar extends MyKeyboard {
	public Bamar(Context context, int xmlLayoutResId) {
		super(context, xmlLayoutResId);
	}

	private final static String cons = "[\u1000-\u1021\u1023-\u1027\u1029\u102A\u103F]";
	// consonants + independent_vowel (!caution! 103F is need extra requirement)
	private final static String medial = "([\u103B-\u103E])*";
	private final static String vowel = "([\u102B-\u1032](\u103A)?)*";
	private final static String cons_final = "(" + cons + "\u103A)?";
	private final static String tone = "([\u1036\u1037\u1038])*";
	private final static String digit = "[\u1040-\u1049]+";
	private final static String symbol = "[\u104C\u104D\u104F]|\u104E\u1004\u103A\u1038|\u104E\u1004\u103A\u1039|\u104E";
	private final static String independent_vowel = "[\u1023-\u1027\u1029\u102A]";
	static String wordSegmentPattern = cons + "\u103A(\u103B)?|" + "(" + cons
			+ "[\u1004\u101B]\u103A\u1039)?(" + cons + medial + vowel + cons
			+ "\u1039)?" + cons + medial + vowel + cons_final + tone + "("
			+ cons + "\u103A(\u103B)?)*";
	static Pattern wSegmentPtn = Pattern.compile(wordSegmentPattern);

	private int stackPointer = 0;
	private int[] stack = new int[3];

	private boolean swapConsonant = false;
	private short medialCount = 0;
	private boolean swapMedial = false;
	private boolean evowel_virama = false;
	private int[] medialStack = new int[3];
	String currentComposing = MyIME.curComposing;

	private boolean isOneBamarVoice(String mComposing) {
		Matcher wordMatcher = wSegmentPtn.matcher(mComposing);
		int length = mComposing.length();
		int count = 0;
		String found = null;
		while (wordMatcher.find()) {
			found = wordMatcher.group();
			if (found != "") {
				count++;
			}
		}
		boolean two_voices_end_with_cons = false;
		if ((count == 2)
				&& ((isConsonant(mComposing.charAt(length - 1)) || (mComposing
						.charAt(length - 1) == 0x1039))))
			two_voices_end_with_cons = true;
		return (two_voices_end_with_cons) || (count == 1);
	}

	public String handelMyanmarInputText(int primaryCode, InputConnection ic) {
		// if e_vowel renew checking flag if
		if (primaryCode == 0x1031 && MyConfig.isPrimeBookOn()) {
			swapConsonant = false;
			medialCount = 0;
			swapMedial = false;
			evowel_virama = false;
			callCommitComposing(ic);
			addCharToComposing(primaryCode);
			return currentComposing;

		}
		currentComposing = MyIME.curComposing;
		int lastChar;
		if (currentComposing.length() > 0)
			lastChar = Integer.valueOf(currentComposing.charAt(currentComposing
					.length() - 1));
		else {
			swapConsonant = false;
			medialCount = 0;
			swapMedial = false;
			evowel_virama = false;
			addCharToComposing(primaryCode);
			return currentComposing;
		}
		// tha + ra_medial = aw vowel autocorrect
		if ((lastChar == 0X101e) && (primaryCode == 0x103c)) {
			// ic.deleteSurroundingText(1, 0);
			deleteChar(1);
			addCharToComposing(0x1029);
			return currentComposing;
		}
		// dot_above + au vowel = au vowel + dot_above autocorrect
		if ((lastChar == 0x1036) && (primaryCode == 0x102f)) {
			char temp[] = { (char) 0x102f, (char) 0x1036 };
			deleteChar(1);
			addCharsToComposing(temp);
			return currentComposing;
		}
		// ss + ya_medial = za myint zwe autocorrect
		if ((lastChar == 0x1005) && (primaryCode == 0x103b)) {
			// ic.deleteSurroundingText(1, 0);
			deleteChar(1);
			addCharToComposing(0x1008);
			return currentComposing;
		}
		// uu + aa_vowel = 0x1009 + aa_vowel autocorrect
		if ((lastChar == 0x1025) && (primaryCode == 0x102c)) {
			char temp[] = { (char) 0x1009, (char) 0x102c };
			deleteChar(1);
			addCharsToComposing(temp);
			return currentComposing;
		}
		// uu_vowel+ii_vowel = u autocorrect
		if ((lastChar == 0x1025) && (primaryCode == 0x102e)) {
			deleteChar(1);
			addCharToComposing(0x1026);
			return currentComposing; // U
		}
		// uu_vowel+asat autocorrect
		if ((lastChar == 0x1025) && (primaryCode == 0x103a)) {
			char temp[] = { (char) 0x1009, (char) 0x103a };
			deleteChar(1);
			addCharsToComposing(temp);
			return currentComposing;
		}
		// asat + dot below to reorder dot below + asat
		if ((lastChar == 0x103a) && (primaryCode == 0x1037)) {
			char temp[] = { (char) 0x1037, (char) 0x103a };
			deleteChar(1);
			addCharsToComposing(temp);
			return currentComposing;
		}
		// if PrimeBook Function is on
		if (MyConfig.isPrimeBookOn()) {
			primeBookFunction(primaryCode, ic, lastChar);
			if(!isOneBamarVoice(currentComposing)){
				int length=currentComposing.length();
				ic.commitText(currentComposing.substring(0, length-2), 1);
				currentComposing=currentComposing.substring(length-2, length);
				Log.d("notOneVoice","current"+currentComposing);
				return currentComposing;
			}
			return currentComposing;
		}
		addCharToComposing(primaryCode);
		return currentComposing;

	}

	private void addCharsToComposing(char[] temp) {
		currentComposing = currentComposing + String.valueOf(temp);
	}

	private void deleteChar(int i) {
		if (currentComposing.length() <= 1)
			currentComposing = "";
		else
			currentComposing = currentComposing.substring(0,
					currentComposing.length() - i);
	}

	private void addCharToComposing(int primaryCode) {
		currentComposing = currentComposing
				+ String.valueOf((char) primaryCode);
		Log.d("Barma", "current" + currentComposing);
	}

	private void callCommitComposing(InputConnection ic) {
		ic.commitText(MyIME.curComposing, 1);
		MyIME.curComposing = "";
		currentComposing = "";
	}

	private String primeBookFunction(int primaryCode, InputConnection ic,
			int charcodeBeforeCursor) {
		// E vowel + cons + virama + cons
		if ((primaryCode == 0x1039) & (swapConsonant)) {
			swapConsonant = false;
			evowel_virama = true;
			addCharToComposing(primaryCode);
			return currentComposing;
		}

		if (evowel_virama) {
			if (isConsonant(primaryCode)) {
				swapConsonant = true;
				// ic.deleteSurroundingText(2, 0);
				deleteChar(2);
				char[] reorderChars = { (char) 0x1039, (char) primaryCode,
						(char) 0x1031 };
				evowel_virama = false;
				addCharsToComposing(reorderChars);
				return currentComposing;
			} else {
				evowel_virama = false;
			}
		}
		if (isOthers(primaryCode)) {
			swapConsonant = false;
			medialCount = 0;
			swapMedial = false;
			evowel_virama = false;
			addCharToComposing(primaryCode);
			return currentComposing;
		}
		// if no previous E_vowel, no need to check Reorder.
		if (charcodeBeforeCursor != 0x1031) {
			addCharToComposing(primaryCode);
			return currentComposing;
		}
		// if input character is consonant and consonant e_vowel swapped, no
		// need
		// to reorder. con+vowel+con
		if (isConsonant(primaryCode) && (swapConsonant)) {
			swapConsonant = false;
			swapMedial = false;
			medialCount = 0;
			addCharToComposing(primaryCode);
			return currentComposing;
		}
		if (isConsonant(primaryCode)) {
			if (!swapConsonant) {
				swapConsonant = true;
				reorder_e_vowel(primaryCode);
				return currentComposing;
			} else {
				swapConsonant = false;
				addCharToComposing(primaryCode);
				return currentComposing;
			}
		}
		if (isMedial(primaryCode)) {
			// delete e_vowel and create Type character + e_vowel
			// (reordering)

			if (isValidMedial(primaryCode)) {
				medialStack[medialCount] = primaryCode;
				medialCount++;
				swapMedial = true;
				reorder_e_vowel(primaryCode);
				return currentComposing;
			}
		}
		addCharToComposing(primaryCode);
		return currentComposing;
	}

	private void reorder_e_vowel(int primaryCode) {
		// TODO Auto-generated method stub
		deleteChar(1);
		char[] reorderChar = { (char) primaryCode, (char) 0x1031 };
		addCharsToComposing(reorderChar);

	}

	private boolean isValidMedial(int primaryCode) {

		if (!swapConsonant)// if no previous consonant, it is invalid
			return false;
		else if (!swapMedial)// if no previous medial, no need to
								// check it is
			// valid
			return true;
		else if (medialCount > 2)// only 3 times of medial;
			return false;
		else if (medialStack[medialCount - 1] == 4158)// if previous medial is
														// Ha medial, no other
														// medial followed
			return false;
		else if ((medialStack[medialCount - 1] == 4157)
				&& (primaryCode != 4158))
			// if previous medial is Wa medial, only Ha madial will followed, no
			// other medial followed
			return false;
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

	public void handleSingleDelete(InputConnection ic) {
		currentComposing=MyIME.curComposing;
		int length = currentComposing.length();
		int firstChar = 0;
		int secChar = 0;
		int thirdChar = 0;
		if (length > 0) {
			firstChar = currentComposing.charAt(length - 1);
			
			if (firstChar == 0x1031) {
				// Need to initialize FLAG
				swapConsonant = false;
				swapMedial = false;
				medialCount = 0;
				stackPointer = 0;
				// 2nd previous character
				// getText = ic.getTextBeforeCursor(2, 0);
				if (length > 1)
					secChar = currentComposing.charAt(length - 2);
				if (isMedial(secChar)) {
					getFlagMedial();
					if (swapConsonant == true) {
						deleteCharBeforeEvowel();
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
						deleteChar(1);
					}

				} else if (isConsonant(secChar)) {

					if (length > 2)
						thirdChar = currentComposing.charAt(length - 3);
					if (thirdChar == 0x1039) {
						deleteTwoCharBeforeEvowel();
					} else {
						deleteCharBeforeEvowel();
					}
					swapConsonant = false;
					swapMedial = false;
					medialCount = 0;
				} else {
					deleteChar(1);
				}
			} else {
				// If not E_Vowel
				if (length > 1)
					secChar = currentComposing.charAt(length - 2);
				if (secChar == 0x1031) {
					if (length < 3)
						swapConsonant = false;
					else
						swapConsonant = true;
				}
				if (currentComposing.length() > 0)
					deleteChar(1);
				else
					MyIME.deleteHandle(ic);
				// TODO need to set composing to previous cluster and delete 1
				// char;
			}
		} else {
			MyIME.deleteHandle(ic);
		}
		stackPointer = 0;
		MyIME.curComposing = currentComposing;
		ic.setComposingText(currentComposing, 1);
	}

	private boolean getFlagMedial() {
		int i = currentComposing.length() - 2;
		int current = currentComposing.charAt(i);
		// checking medial and store medial to stack orderly
		// till to Consonant or word sperator or till at the start of input box
		while (!(isConsonant(current) || MyIME.isWordSeparator(current))
				&& (i > 0) && (isMedial(current))) {
			medialCount++;
			pushMedialStack(current);//
			swapMedial = true;
			swapConsonant = true;
			i--;
			current = currentComposing.charAt(i);

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
		if (i <= 0) {
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

	private void deleteCharBeforeEvowel() {
		deleteChar(2);
		addCharToComposing(0x1031);
	}

	private void deleteTwoCharBeforeEvowel() {
		deleteChar(3);
		addCharToComposing(0x1031);
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

}
