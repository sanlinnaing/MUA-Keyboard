package com.sanlin.mkeyboard;

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.InputConnection;

public class ShanKeyboard extends MyKeyboard {
	private boolean swapConsonant = false;
	private boolean swapMedial = false;
	private final int MY_E = 4145;
	private final int SH_E = 4228;
	private final int ASAT = 4154;

	public ShanKeyboard(Context context, int xmlLayoutResId) {
		super(context, xmlLayoutResId);
		// TODO Auto-generated constructor stub
	}

	public ShanKeyboard(Context context, int layoutTemplateResId,
			CharSequence characters, int columns, int horizontalPadding) {
		super(context, layoutTemplateResId, characters, columns,
				horizontalPadding);
		// TODO Auto-generated constructor stub
	}

	public String handleShanInputText(int primaryCode, InputConnection ic) {
		CharSequence charBeforeCursor = ic.getTextBeforeCursor(1, 0);
		// if getTextBeforeCursor return null, issues on version 1.1
		if (charBeforeCursor == null) {
			charBeforeCursor = "";
		}
		Integer charcodeBeforeCursor = null;
		if (charBeforeCursor.length() > 0)
			charcodeBeforeCursor = Integer.valueOf(charBeforeCursor.charAt(0));
		else {
			return String.valueOf((char) primaryCode);
		}
		if (charcodeBeforeCursor == ASAT && primaryCode == 4226) {
			char temp[] = { (char) 4226, (char) ASAT };
			ic.deleteSurroundingText(1, 0);
			return String.valueOf(temp);
		}
		if (charcodeBeforeCursor == 4230 && primaryCode == 4194) {
			char temp[] = { (char) 4194, (char) 4230 };
			ic.deleteSurroundingText(1, 0);
			return String.valueOf(temp);
		}
		if (charcodeBeforeCursor == 4144 && primaryCode == 4141) {
			char temp[] = { (char) 4141, (char) 4144 };
			ic.deleteSurroundingText(1, 0);
			return String.valueOf(temp);
		}
		if (charcodeBeforeCursor == 4143 && primaryCode == 4141) {
			char temp[] = { (char) 4141, (char) 4143 };
			ic.deleteSurroundingText(1, 0);
			return String.valueOf(temp);
		}
		Log.d("handleShanInputText", "primeBook= " + MyConfig.isPrimeBookOn());
		if (MyConfig.isPrimeBookOn())
			return handleShanTyping(primaryCode, ic);
		return String.valueOf((char) primaryCode);
	}

	private String handleShanTyping(int primaryCode, InputConnection ic) {
		CharSequence charBeforeCursor = ic.getTextBeforeCursor(1, 0);
		// if getTextBeforeCursor return null, issues on version 1.1
		if (charBeforeCursor == null) {
			charBeforeCursor = "";
		}
		Integer charcodeBeforeCursor = null;

		if (isOthers(primaryCode)) {
			swapConsonant = false;
			swapMedial = false;
			return String.valueOf((char) primaryCode);
		}
		if (charBeforeCursor.length() > 0)
			charcodeBeforeCursor = Integer.valueOf(charBeforeCursor.charAt(0));
		else {
			swapConsonant = false;
			swapMedial = false;
			return String.valueOf((char) primaryCode);
		}
		if ((primaryCode == MY_E || primaryCode == SH_E)) {
			String outText = String.valueOf((char) primaryCode);
			if (isConsonant(charcodeBeforeCursor)) {
				char temp[] = { (char) 8203, (char) primaryCode }; // ZWSP added
				outText = String.valueOf(temp);
			}
			swapConsonant = false;
			swapMedial = false;
			return outText;
		}
		if ((charcodeBeforeCursor == MY_E || charcodeBeforeCursor == SH_E)) {
			Log.d("ShanHandle", "E vowel");
			if (isConsonant(primaryCode)) {
				Log.d("ShanHandle", "consonant");
				if (!swapConsonant) {
					swapConsonant = true;
					return reorder(primaryCode, charcodeBeforeCursor, ic);
				} else {
					swapConsonant = false;
					swapMedial = false;
					return String.valueOf((char) primaryCode);
				}

			}
			if (primaryCode == ASAT) {// ////CAUTION!!!!
				if (swapConsonant == true) {
					swapConsonant = false;
					return reorder(primaryCode, charcodeBeforeCursor, ic);
				}
			}
			if (isMedial(primaryCode)) {// ////CAUTION!!!!
				if (!swapMedial && swapConsonant) {
					swapConsonant = false;
					swapMedial = true;
					return reorder(primaryCode, charcodeBeforeCursor, ic);
				}
			}
		}
		return String.valueOf((char) primaryCode);
	}

	private boolean isOthers(int primaryCode) {
		return isConsonant(primaryCode) && isMedial(primaryCode);
	}

	private boolean isMedial(int primaryCode) {
		// TODO Auto-generated method stub
		// medial Ya, Ra
		if (primaryCode == 4155 || primaryCode == 4156)
			return true;
		else
			return false;
	}

	private String reorder(int primaryCode, int charcodeBeforeCursor,
			InputConnection ic) {
		ic.deleteSurroundingText(1, 0);
		char[] reorderChars = { (char) primaryCode, (char) charcodeBeforeCursor };
		String reorderString = String.valueOf(reorderChars);
		return reorderString;
		// TODO Auto-generated method stub

	}

	public void handleShanDelete(InputConnection ic) {
		if (MyIME.isEndofText(ic)) {
			singleDelete(ic);
		} else {
			MyIME.deleteHandle(ic);
		}
	}

	private void singleDelete(InputConnection ic) {
		CharSequence getText = ic.getTextBeforeCursor(1, 0);
		// if getTextBeforeCursor return null, issues on version 1.1
		if (getText == null) {
			getText = "";
		}
		
		Integer firstChar;
		Integer secPrev;
		if (getText.length() > 0) {
			firstChar = Integer.valueOf(getText.charAt(0));
			if (firstChar == MY_E || firstChar == SH_E) {
				// Need to initialize FLAG
				swapConsonant = false;
				swapMedial = false;
				getText = ic.getTextBeforeCursor(2, 0);
				secPrev = Integer.valueOf(getText.charAt(0));
				if (isMedial(secPrev)) {
					swapConsonant = true;
					swapMedial = false;
					deleteCharBefore(firstChar, ic);
				} else if (isConsonant(secPrev)) {
					swapMedial = false;
					swapConsonant = false;
					deleteCharBefore(firstChar, ic);
				} else {
					ic.deleteSurroundingText(1, 0);
				}

			} else {
				getText = ic.getTextBeforeCursor(2, 0);
				secPrev = Integer.valueOf(getText.charAt(0));
				if (secPrev == MY_E || secPrev == SH_E)
					swapConsonant = true;
				//ic.deleteSurroundingText(1, 0);
				MyIME.deleteHandle(ic);
			}
		} else {
			ic.deleteSurroundingText(1, 0);
		}
	}

	private void deleteCharBefore(int firstChar, InputConnection ic) {
		// TODO Auto-generated method stub
		ic.deleteSurroundingText(2, 0);
		ic.commitText(String.valueOf((char) firstChar), 1);
	}

	private boolean isConsonant(int primaryCode) {
		return MyIME.isShanConsonant(primaryCode);
	}

	public String shanVowel1() {
		char[] outText = { (char) 4226, (char) 4154 };
		String outString = String.valueOf(outText);
		return outString;
	}

	public void handleShanMoneySym(InputConnection ic) {
		// TODO Auto-generated method stub
		char temp[] = { 4117, 4155, 4227, 4152 };
		ic.commitText(String.valueOf(temp), 1);
	}

}
