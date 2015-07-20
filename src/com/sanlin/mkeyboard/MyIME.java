package com.sanlin.mkeyboard;

import com.sanlin.mkeyboard.R;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.media.AudioManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

public class MyIME extends InputMethodService implements
		OnKeyboardActionListener {
	private KeyboardView kv;
	private InputMethodManager mInputMethodManager;
	private boolean caps = false;
	private MyKeyboard enKeyboard;
	private MyKeyboard symKeyboard;
	private MyKeyboard sym_shifted_Keyboard;
	private MyKeyboard currentKeyboard;
	private MyKeyboard emojiKeyboard;
	SharedPreferences sharedPref;
	private static String mWordSeparators;
	private static String shanConsonants;

	private boolean shifted = false;
	private boolean symbol = false;

	public static String curComposing = "";

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		mWordSeparators = getResources().getString(R.string.word_separators);
		shanConsonants = getResources().getString(R.string.shan_consonants);
		mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

	}

	@Override
	public void onInitializeInterface() {
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		MyConfig.setDoubleTap(sharedPref.getBoolean("double_tap", false));
		enKeyboard = new MyKeyboard(this, R.xml.en_qwerty);
		symKeyboard = new MyKeyboard(this, R.xml.en_symbol);
		sym_shifted_Keyboard = new MyKeyboard(this, R.xml.en_shift_symbol);
		emojiKeyboard = new MyKeyboard(this, R.xml.emotion);
		shifted = false;
		symbol = false;
		currentKeyboard = enKeyboard;
		currentKeyboard = getKeyboard(getLocaleId());

	}

	@Override
	public View onCreateInputView() {
		// TODO Auto-generated method stub

		// get setting
		MyConfig.setSoundOn(sharedPref.getBoolean("play_sound", false));
		MyConfig.setPrimeBookOn(sharedPref.getBoolean(
				"prime_book_typing_on_off", true));
		MyConfig.setCurrentTheme(Integer.valueOf(sharedPref.getString(
				"choose_theme", "1")));
		MyConfig.setShowHintLabel(sharedPref.getBoolean("hint_keylabel", true));
		MyConfig.setDoubleTap(sharedPref.getBoolean("double_tap", false));
		switch (MyConfig.getCurrentTheme()) {
		case 5:
			kv = (MyKeyboardView) getLayoutInflater().inflate(
					R.layout.gold_keyboard, null);
			break;
		case 4:
			kv = (MyKeyboardView) getLayoutInflater().inflate(
					R.layout.blue_gray_keyboard, null);
			break;
		case 3:
			kv = (MyKeyboardView) getLayoutInflater().inflate(
					R.layout.flat_green_keyboard, null);
			break;
		case 2:
			kv = (MyKeyboardView) getLayoutInflater().inflate(
					R.layout.flat_black_keyboard, null);
			break;
		default:
			kv = (MyKeyboardView) getLayoutInflater().inflate(
					R.layout.default_keyboard, null);
		}

		kv.setKeyboard(currentKeyboard);
		kv.setOnKeyboardActionListener(this);

		return kv;
	}

	@Override
	public void onStartInputView(EditorInfo info, boolean restarting) {
		super.onStartInputView(info, restarting);

		setInputView(onCreateInputView());
		curComposing = "";
	}

	@Override
	public void onFinishInput() {
		super.onFinishInput();
		commitComposing();
	}

	@Override
	protected void onCurrentInputMethodSubtypeChanged(
			InputMethodSubtype newSubtype) {
		super.onCurrentInputMethodSubtypeChanged(newSubtype);
		kv.setKeyboard(getKeyboard(Integer.valueOf(newSubtype.getExtraValue())));
		caps = false;
		shifted = false;
		kv.getKeyboard().setShifted(false);
		currentKeyboard = (MyKeyboard) kv.getKeyboard();
		currentKeyboard.setSpaceBarSubtypeName(getString(newSubtype
				.getNameResId()),
				getResources().getDrawable(R.drawable.sym_keyboard_space));
	}

	private MyKeyboard getKeyboard(int subTypeId) {
		switch (subTypeId) {
		case 1:
			return enKeyboard;

		case 2:
			if (MyConfig.isDoubleTap())
				return new Bamar(this, R.xml.my_2_qwerty);
			else
				return new Bamar(this, R.xml.my_qwerty);
		case 3:
			return new ShanKeyboard(this, R.xml.shn_qwerty);
		case 4:
			return new MonKeyboard(this, R.xml.mon_qwerty);
		case 5:
			return new MyKeyboard(this, R.xml.sg_karen_qwerty);
		case 6:
			return new MyKeyboard(this, R.xml.wp_karen_qwerty);
		case 7:
			return new EastPwoKarenKeyboard(this, R.xml.ep_karen_qwerty);
		}
		return currentKeyboard;
	}

	private MyKeyboard getSymKeyboard(int subTypeId) {
		switch (subTypeId) {
		case 1:
			return symKeyboard;

		case 2:
			return new BamarKeyboard(this, R.xml.my_symbol);
		case 3:
			return new ShanKeyboard(this, R.xml.shn_symbol);
		case 4:
			return new MonKeyboard(this, R.xml.mon_symbol);
		case 5:
			return new MyKeyboard(this, R.xml.sg_karen_symbol);
		case 6:
			return new MyKeyboard(this, R.xml.wp_karen_symbol);
		case 7:
			return new EastPwoKarenKeyboard(this, R.xml.ep_karen_symbol);
		}
		return currentKeyboard;
	}

	private MyKeyboard getShiftedKeyboard(int subTypeId) {
		switch (subTypeId) {
		case 1:
			kv.getKeyboard().setShifted(true);
			caps = true;
			kv.invalidateAllKeys();
			return null;

		case 2:
			return new Bamar(this, R.xml.my_shifted_qwerty);
		case 3:
			return new ShanKeyboard(this, R.xml.shn_shifted_qwerty);
		case 4:
			return new MonKeyboard(this, R.xml.mon_shifted_qwerty);
		case 5:
			return new MyKeyboard(this, R.xml.sg_karen_shifted_qwerty);
		case 6:
			return new MyKeyboard(this, R.xml.wp_karen_shifted_qwerty);
		case 7:
			return new EastPwoKarenKeyboard(this, R.xml.ep_karen_shifted_qwerty);
		}
		return currentKeyboard;
	}

	private IBinder getToken() {
		final Dialog dialog = getWindow();
		if (dialog == null) {
			return null;
		}
		final Window window = dialog.getWindow();
		if (window == null) {
			return null;
		}
		return window.getAttributes().token;
	}

	private void playClick(int keyCode) {
		AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
		switch (keyCode) {
		case 32:
			am.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR);
			break;
		case Keyboard.KEYCODE_DONE:
		case 10:
			am.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN);
			break;
		case Keyboard.KEYCODE_DELETE:
			am.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE);
			break;
		default:
			am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == Keyboard.KEYCODE_DONE) {
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@SuppressLint("NewApi")
	@Override
	public void onKey(int primaryCode, int[] arg1) {
		InputConnection ic = getCurrentInputConnection();
		// emotion
		if ((primaryCode >= 128000) && (primaryCode <= 128567)) {
			curComposing = curComposing
					+ new String(Character.toChars(primaryCode));
			commitComposing();
			return;
		}
		if (isWordSeparator(primaryCode) || isDigit(primaryCode)) {
			curComposing = curComposing + (char) primaryCode;

			commitComposing();
			return;
		}

		Log.d("onKey", "Sound play = " + MyConfig.isSoundOn());

		if (MyConfig.isSoundOn())
			playClick(primaryCode);
		switch (primaryCode) {
		case 42264154:// shan vowel
			ic.commitText(((ShanKeyboard) currentKeyboard).shanVowel1(), 1);
			unshiftByLocale();
			break;
		case 300001:
			commitComposing();
			switch (getLocaleId()) {
			case 2:
				((BamarKeyboard) currentKeyboard).handleMoneySym(ic);
				break;
			case 3:
				((ShanKeyboard) currentKeyboard).handleShanMoneySym(ic);
				break;
			case 4:
				((MonKeyboard) currentKeyboard).handleMonMoneySym(ic);
				break;
			}

		case -2:
			Log.d("onKey", "switch Symbol key");
			handleSymByLocale();
			break;
		case 300000:// Myanmar/Shan delete key
			Log.d("onKey", "Myanmar/Shan Delete key code");
			if (MyConfig.isPrimeBookOn()) {
				Log.d("onKey", "Prime Book on");
				switch (getLocaleId()) {
				case 2:
					// ((BamarKeyboard)
					// currentKeyboard).handleMyanmarDelete(ic);
					((Bamar) currentKeyboard).handleSingleDelete(ic);
					break;
				case 3:
					((ShanKeyboard) currentKeyboard).handleShanDelete(ic);
					break;
				case 4:
					((MonKeyboard) currentKeyboard).handleMonDelete(ic);
					break;
				case 7:
					((EastPwoKarenKeyboard) currentKeyboard)
							.handleEastPwoKarenDelete(ic);
					break;
				default:
					deleteHandle(ic);
				}
				break;
			}
			// if Prime book function is OFF following case will do.
			// "case Keyboard.KEYCODE_DELETE:"
			// Don't Interrupt anything, here. ::::!!!!!!
		case Keyboard.KEYCODE_DELETE:
			Log.d("onKey", "Delete key code double tap");
			CharSequence ch = ic.getTextBeforeCursor(1, 0);

			// if (evowel_swapped && ch != null && ch.charAt(0) == 0x1031) {
			if (currentKeyboard instanceof Bamar && MyConfig.isPrimeBookOn()
					&& MyConfig.isDoubleTap()) {
				((Bamar) currentKeyboard).handleSingleDelete(ic);
			} else {
				commitComposing();
				deleteHandle(ic);
			}
			break;

		case -202:
			currentKeyboard = (MyKeyboard) kv.getKeyboard();
			kv.setKeyboard(emojiKeyboard);
			break;
		case -3:
			kv.setKeyboard(currentKeyboard);
			break;
		case Keyboard.KEYCODE_DONE:
			commitComposing();
			ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
					KeyEvent.KEYCODE_ENTER));
			ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
					KeyEvent.KEYCODE_ENTER));

			break;
		case -101:// Switch Keyboard;
			commitComposing();
			mInputMethodManager
					.switchToNextInputMethod(getToken(), true /* onlyCurrentIme */);// Need
																					// to
																					// think
																					// ??????
			break;
		case Keyboard.KEYCODE_SHIFT:
			handleShift();
			break;
		default:
			char code = (char) primaryCode;
			if (Character.isLetter(code) && caps) {
				code = Character.toUpperCase(code);
			}
			String cText = String.valueOf(code);

			switch (getLocaleId()) {
			case 1:
				cText = curComposing + cText;
				if(!Character.isLetter(code)){
					curComposing=cText;
					commitComposing();
					return;
				}
				break;
			case 2:
				cText = ((Bamar) currentKeyboard).handelMyanmarInputText(
						primaryCode, ic);
				Log.d("onKey", "MyanmarHandle");
				break;
			case 3:
				cText = ((ShanKeyboard) currentKeyboard).handleShanInputText(
						primaryCode, ic);
				Log.d("onKey", "ShanHandle");
				break;
			case 4:
				cText = ((MonKeyboard) currentKeyboard).handleMonInput(
						primaryCode, ic);
				break;
			case 7:
				cText = ((EastPwoKarenKeyboard) currentKeyboard)
						.handleEastPwoKarenInput(primaryCode, ic);
			}
			if (cText != "" && cText != null) {
				ic.setComposingText(cText, 1);
				curComposing = cText;
			}
			Log.d("onKey", "curComposing = " + cText);
			// ic.commitText(cText, 1);
			if (shifted) {
				unshiftByLocale();
			}
		}
	}

	private void commitComposing() {
		getCurrentInputConnection().commitText(curComposing, 1);
		curComposing = "";

	}

	public static void deleteHandle(InputConnection ic) {
		CharSequence ch = ic.getTextBeforeCursor(1, 0);
		if (ch.length() > 0) {
			if (Character.isLowSurrogate(ch.charAt(0))
					|| Character.isHighSurrogate(ch.charAt(0))) {
				ic.deleteSurroundingText(2, 0);
			} else {
				ic.deleteSurroundingText(1, 0);
			}
		} else {
			ic.deleteSurroundingText(1, 0);
		}
	}

	public static boolean isEndofText(InputConnection ic) {
		CharSequence charAfterCursor = ic.getTextAfterCursor(1, 0);
		if (charAfterCursor == null)
			return true;
		if (charAfterCursor.length() > 0)
			return false;
		else
			return true;
	}

	private void handleSymByLocale() {
		int localeId = getLocaleId();
		if (!symbol) {
			kv.setKeyboard(getSymKeyboard(localeId));
			symbol = true;
		} else {
			kv.setKeyboard(getKeyboard(localeId));
			symbol = false;
		}
	}

	private void handleShift() {
		if (!shifted) {
			// shift();
			shiftByLocale();
		} else {
			// unshift();
			unshiftByLocale();
		}

	}

	private void shiftByLocale() {
		if (kv.getKeyboard().equals(symKeyboard)) {
			kv.setKeyboard(sym_shifted_Keyboard);
			kv.getKeyboard().setShifted(true);

		} else {
			if (getShiftedKeyboard(getLocaleId()) != null) {
				kv.setKeyboard(getShiftedKeyboard(getLocaleId()));
				kv.getKeyboard().setShifted(true);
			}
		}
		shifted = true;
	}

	private void unshiftByLocale() {
		if (kv.getKeyboard().equals(sym_shifted_Keyboard)) {
			kv.setKeyboard(symKeyboard);
			kv.getKeyboard().setShifted(false);

		} else {
			kv.setKeyboard(getKeyboard(getLocaleId()));
			kv.getKeyboard().setShifted(false);
			caps = false;
			if (getLocaleId() == 1)
				kv.invalidateAllKeys();

		}
		shifted = false;
	}

	/**
	 * Some Android Phone have return ExtraValue from previous phone. So try to
	 * fix it as default en_ locale as Return by default 1 as a en_ locale
	 * otherwise return ExtraValue as defined in /xml/method.xml fixed on
	 * version 1.3
	 */
	private Integer getLocaleId() {
		int localeId = 1;
		try {
			localeId = Integer.valueOf((mInputMethodManager
					.getCurrentInputMethodSubtype().getExtraValue()));
		} catch (NumberFormatException ex) {
			localeId = 1;
		}
		return localeId;
	}

	public static boolean isWordSeparator(int code) {
		String separators = mWordSeparators;
		return separators.contains(String.valueOf((char) code));
	}

	public boolean isDigit(int code) {
		return Character.isDigit(code);
	}

	public static boolean isShanConsonant(int code) {
		String separators = shanConsonants;
		return separators.contains(String.valueOf((char) code));
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		if (keyCode == Keyboard.KEYCODE_DONE) {
			Log.d("onKeyLongPress", "Enter key is long press");
			return true;
		}

		return super.onKeyLongPress(keyCode, event);
	}

	@Override
	public void onPress(int arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onRelease(int arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onText(CharSequence arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void swipeDown() {
		// TODO Auto-generated method stub

	}

	@SuppressLint("NewApi")
	@Override
	public void swipeLeft() {
		// TODO Auto-generated method stub
		mInputMethodManager
				.switchToNextInputMethod(getToken(), true /* onlyCurrentIme */);

	}

	@SuppressLint("NewApi")
	@Override
	public void swipeRight() {
		// TODO Auto-generated method stub
		mInputMethodManager
				.switchToNextInputMethod(getToken(), true /* onlyCurrentIme */);
	}

	@Override
	public void swipeUp() {
		// TODO Auto-generated method stub

	}

}
