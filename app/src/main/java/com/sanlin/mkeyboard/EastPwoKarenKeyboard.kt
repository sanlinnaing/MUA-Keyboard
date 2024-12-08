package com.sanlin.mkeyboard;

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.InputConnection;

public class EastPwoKarenKeyboard extends MyKeyboard {
	private boolean swapConsonant = false;
	private boolean swapMedial = false;

	public EastPwoKarenKeyboard(Context context, int xmlLayoutResId) {
		super(context, xmlLayoutResId);
		// TODO Auto-generated constructor stub
	}

	public EastPwoKarenKeyboard(Context context, int layoutTemplateResId,
			CharSequence characters, int columns, int horizontalPadding) {
		super(context, layoutTemplateResId, characters, columns,
				horizontalPadding);
		// TODO Auto-generated constructor stub
	}

	public void handleEastPwoKarenDelete(InputConnection ic) {

		if (MyIME.isEndofText(ic)) {
			handleSingleDelete(ic);
		} else {

			handelWordDelete(ic);
		}

	}

	private void handelWordDelete(InputConnection ic) {
		// TODO Auto-generated method stub
		MyIME.deleteHandle(ic);
	}

	private void handleSingleDelete(InputConnection ic) {
		// TODO Auto-generated method stub
		CharSequence getText = ic.getTextBeforeCursor(1, 0);
		int firstChar;
		int secPrev = 0;
		int thirdPrevChar = 0;
		if ((getText == null) || (getText.length() <= 0)) {
			ic.deleteSurroundingText(1, 0);
			return;
		}
		firstChar = Integer.valueOf(getText.charAt(0));
		getText = ic.getTextBeforeCursor(2, 0);
		if ((getText == null) || (getText.length() == 2)) {
			secPrev = Integer.valueOf(getText.charAt(0));
		}
		getText = ic.getTextBeforeCursor(3, 0);
		if ((getText == null) || (getText.length() == 3)) {
			thirdPrevChar = Integer.valueOf(getText.charAt(0));
		}

		if (firstChar == 0x1031) {
			if ((secPrev == 0x103A) && thirdPrevChar == 0x103E) {
				deleteCharBeforeEVowel(ic);
				swapConsonant = true;
				swapMedial = true;
			} else if (thirdPrevChar == 0x1039) {
				// delete consonant medial
				delete2CharBeforeEVowel(ic);
				swapConsonant = true;
				swapMedial = false;
			} else if ((isSymMedial(secPrev)) && isConsonant(thirdPrevChar)) {
				// delete medial
				deleteCharBeforeEVowel(ic);
				swapConsonant = true;
				swapMedial = false;
			} else if (isConsonant(secPrev)) {
				// delete consonant
				deleteCharBeforeEVowel(ic);
				swapConsonant = false;
				swapMedial = false;
			} else {
				if (secPrev == 8203)
					ic.deleteSurroundingText(2, 0);
				else
					MyIME.deleteHandle(ic);
				swapConsonant = false;
				swapMedial = false;
			}

			return;
		}
		if (secPrev == 0x1039) {
			ic.deleteSurroundingText(2, 0);
			if(thirdPrevChar==0x1031){
				swapConsonant=true;
			}
			return;
		}

		MyIME.deleteHandle(ic);
		if (secPrev == 0x1031) {
			if (isConsonant(thirdPrevChar)) {
				swapConsonant = true;
				swapMedial = false;
			}
			if (isSymMedial(thirdPrevChar)) {
				swapConsonant = true;
				swapMedial = true;
			}
		}
	}

	private void delete2CharBeforeEVowel(InputConnection ic) {
		Log.d("delete2CharBeforeEVowel",
				"inside" + String.valueOf((char) 0x1031));
		ic.deleteSurroundingText(3, 0);
		ic.commitText(String.valueOf((char) 0x1031), 1);
	}

	private void deleteCharBeforeEVowel(InputConnection ic) {
		Log.d("deleteCharBeforeEVowel",
				"inside" + String.valueOf((char) 0x1031));
		ic.deleteSurroundingText(2, 0);
		ic.commitText(String.valueOf((char) 0x1031), 1);
	}

	public String handleEastPwoKarenInput(int primaryCode, InputConnection ic) {
		CharSequence charBeforeCursor = ic.getTextBeforeCursor(1, 0);
		Integer charCodeBeforeCursor = 0;
		if (charBeforeCursor == null) {
			charBeforeCursor = "";
		}
		if (charBeforeCursor.length() > 0)
			charCodeBeforeCursor = Integer.valueOf(charBeforeCursor.charAt(0));

		if ((charCodeBeforeCursor == 0x102F) && (primaryCode == 0x103A)) {
			ic.deleteSurroundingText(1, 0);
			char temp[] = { (char) 0x103A, (char) 0x102F };
			return String.valueOf(temp);
		}
		if ((charCodeBeforeCursor == 0x1003) && (primaryCode == 0x103E)) {
			ic.deleteSurroundingText(1, 0);
			return String.valueOf((char) 0x1070);
		}
		if ((charCodeBeforeCursor == 0x101F) && (primaryCode == 0x103E)) {
			ic.deleteSurroundingText(1, 0);
			return String.valueOf((char) 0x106F);
		}
		if(MyConfig.isPrimeBookOn()){
			return primeInput(primaryCode,ic);
		}
		
		if (primaryCode < 0) {
			char temp[] = { (char) 0x1039, (char) (primaryCode * (-1)) };
			return String.valueOf(temp);
		}
		return String.valueOf((char) primaryCode);


	}

	private String primeInput(int primaryCode, InputConnection ic) {
		CharSequence charBeforeCursor = ic.getTextBeforeCursor(1, 0);
		Integer charCodeBeforeCursor;
		if (charBeforeCursor == null) {
			charBeforeCursor = "";
		}
		if (charBeforeCursor.length() > 0)
			charCodeBeforeCursor = Integer.valueOf(charBeforeCursor.charAt(0));
		else {
			// else it is the first character no need to reorder
			swapConsonant = false;
			swapMedial = false;
			if (primaryCode < 0) {
				char temp[] = { (char) 0x1039, (char) (primaryCode * (-1)) };
				return String.valueOf(temp);
			}
			return String.valueOf((char) primaryCode);
		}

		if (primaryCode == 0x1031) {
			if (isConsonant(charCodeBeforeCursor)
					|| isSymMedial(charCodeBeforeCursor)) {
				char[] reorderChars = { (char) 8203, (char) 0x1031 };
				// ZWSP added
				String reorderString = String.valueOf(reorderChars);
				swapConsonant = false;
				swapMedial = false;
				return reorderString;
			}
		}

		if (charCodeBeforeCursor == 0x1031) {
			if ((primaryCode == 0x103A)) {
				CharSequence getText;
				int secPrev = 0;
				getText = ic.getTextBeforeCursor(2, 0);
				if ((getText == null) || (getText.length() == 2)) {
					secPrev = Integer.valueOf(getText.charAt(0));
				}
				if (secPrev == 0x103E) {
					ic.deleteSurroundingText(2, 0);
					char[] reorderChars = { (char) 0x103E, (char) 0x103A,
							(char) 0x1031 };
					String reorderString = String.valueOf(reorderChars);
					swapConsonant = true;
					swapMedial = true;
					return reorderString;
				}
			}
			if (isConsonant(primaryCode) && (!swapConsonant)) {
				// Reorder function
				swapConsonant = true;
				swapMedial = false;
				return reorder_e_vowel(primaryCode, ic);

			}
			if ((!swapMedial) && (swapConsonant)) {
				if (isSymMedial(primaryCode)) {
					// Reorder funciton
					swapMedial = true;
					return reorder_e_vowel(primaryCode, ic);
				}
				if (primaryCode < 0) {
					// Reorder virama+(-1 * primaryCode)
					swapMedial = true;
					return reorder_e_vowel_con_medial(primaryCode, ic);
				}
			}
		}
		swapConsonant = false;
		swapMedial = false;

		if (primaryCode < 0) {
			char temp[] = { (char) 0x1039, (char) (primaryCode * (-1)) };
			return String.valueOf(temp);
		}
		return String.valueOf((char) primaryCode);

	}

	private String reorder_e_vowel(int primaryCode, InputConnection ic) {
		ic.deleteSurroundingText(1, 0);
		char[] reorderChars = { (char) primaryCode, (char) 0x1031 };
		String reorderString = String.valueOf(reorderChars);
		return reorderString;
	}

	private String reorder_e_vowel_con_medial(int primaryCode,
			InputConnection ic) {
		ic.deleteSurroundingText(1, 0);
		char[] reorderChars = { (char) 0x1039, (char) (primaryCode * (-1)),
				(char) 0x1031 };
		String reorderString = String.valueOf(reorderChars);
		return reorderString;
	}

	private boolean isConsonant(int code) {
		if (((code >= 0x1000) && (code <= 0x1021)) || (code == 0x1070)
				|| (code == 0x106F) || (code == 0x105C) || (code == 0x106E))
			return true;
		return false;
	}

	private boolean isSymMedial(int code) {
		if (((code >= 0x103B) && (code <= 0x103E))
				|| ((code >= 0x105E) && (code <= 0x1060)))
			return true;

		return false;
	}

}
