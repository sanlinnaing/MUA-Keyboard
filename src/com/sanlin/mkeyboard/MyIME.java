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
	private BamarKeyboard myKeyboard;
	private MyKeyboard enKeyboard;
	private MyKeyboard shnKeyboard;
	private MonKeyboard monKeyboard;
	private MyKeyboard mon_shifted_Keyboard;
	private MyKeyboard mon_symbol_Keyboard;
	private MyKeyboard symKeyboard;
	private MyKeyboard sym_shifted_Keyboard;
	private MyKeyboard my_shifted_Keyboard;
	private MyKeyboard shn_shifted_Keyboard;
	private MyKeyboard my_symbol_Keyboard;
	private MyKeyboard shn_symbol_Keyboard;
	private MyKeyboard currentKeyboard;
	private MyKeyboard emojiKeyboard;
	SharedPreferences sharedPref;
	private static String mWordSeparators;
	private static String shanConsonants;

	private boolean shifted = false;
	private boolean symbol = false;

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
		// TODO Auto-generated method stub
		myKeyboard = new BamarKeyboard(this, R.xml.my_qwerty);
		shnKeyboard = new ShanKeyboard(this, R.xml.shn_qwerty);
		enKeyboard = new MyKeyboard(this, R.xml.en_qwerty);
		symKeyboard = new MyKeyboard(this, R.xml.en_symbol);
		sym_shifted_Keyboard = new MyKeyboard(this, R.xml.en_shift_symbol);
		my_shifted_Keyboard = new MyKeyboard(this, R.xml.my_shifted_qwerty);
		shn_shifted_Keyboard = new ShanKeyboard(this, R.xml.shn_shifted_qwerty);
		my_symbol_Keyboard = new MyKeyboard(this, R.xml.my_symbol);
		shn_symbol_Keyboard = new ShanKeyboard(this, R.xml.shn_symbol);
		monKeyboard = new MonKeyboard(this, R.xml.mon_qwerty);
		mon_shifted_Keyboard = new MyKeyboard(this, R.xml.mon_shifted_qwerty);
		mon_symbol_Keyboard = new MyKeyboard(this, R.xml.mon_symbol);
		emojiKeyboard = new MyKeyboard(this, R.xml.emotion);
		shifted = false;
		symbol = false;
		switch (Integer.valueOf((mInputMethodManager
				.getCurrentInputMethodSubtype().getExtraValue()))) {
		case 1:
			currentKeyboard = enKeyboard;
			break;
		case 2:
			currentKeyboard = myKeyboard;
			break;
		case 3:
			currentKeyboard = shnKeyboard;
			break;
		case 4:
			currentKeyboard = monKeyboard;
			break;
		default:
			currentKeyboard = enKeyboard;
		}

	}

	@Override
	public View onCreateInputView() {
		// TODO Auto-generated method stub
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		// get setting
		MyConfig.setSoundOn(sharedPref.getBoolean("play_sound", false));
		MyConfig.setPrimeBookOn(sharedPref.getBoolean(
				"prime_book_typing_on_off", true));
		MyConfig.setCurrentTheme(Integer.valueOf(sharedPref.getString(
				"choose_theme", "1")));
		switch (MyConfig.getCurrentTheme()) {
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
			return myKeyboard;
		case 3:
			return shnKeyboard;
		case 4:
			return monKeyboard;
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
	public void onKey(int arg0, int[] arg1) {
		int primaryCode = arg0;
		InputConnection ic = getCurrentInputConnection();
		// emotion
		if ((primaryCode >= 128000) && (primaryCode <= 128567)) {
			ic.commitText(new String(Character.toChars(primaryCode)), 1);
			return;
		}

		Log.d("onKey", "Sound play = " + MyConfig.isSoundOn());

		if (MyConfig.isSoundOn())
			playClick(primaryCode);
		switch (primaryCode) {
		case 42264154:// shan vowel
			ic.commitText(((ShanKeyboard) shnKeyboard).shanVowel1(), 1);
			unshiftByLocale();
			break;
		case 300001:
			switch (Integer.valueOf((mInputMethodManager
					.getCurrentInputMethodSubtype().getExtraValue()))) {
			case 2:
				myKeyboard.handleMoneySym(ic);
				break;
			case 3:
				((ShanKeyboard) shnKeyboard).handleShanMoneySym(ic);
				break;
			case 4:
				((MonKeyboard) monKeyboard).handleMonMoneySym(ic);
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
				switch (Integer.valueOf((mInputMethodManager
						.getCurrentInputMethodSubtype().getExtraValue()))) {
				case 2:
					myKeyboard.handleMyanmarDelete(ic);
					break;
				case 3:
					((ShanKeyboard) shnKeyboard).handleShanDelete(ic);
					break;
				case 4:
					((MonKeyboard) monKeyboard).handleMonDelete(ic);
					break;

				}
				break;
			}
			// if Prime book function is OFF following case will do. "case Keyboard.KEYCODE_DELETE:"
			// Don't Interrupt anything, here. ::::!!!!!! 
		case Keyboard.KEYCODE_DELETE:
			Log.d("onKey", "Delete key code");

			deleteHandle(ic);
			break;

		case -202:
			currentKeyboard = (MyKeyboard) kv.getKeyboard();
			kv.setKeyboard(emojiKeyboard);
			break;
		case -3:
			kv.setKeyboard(currentKeyboard);
			break;
		case Keyboard.KEYCODE_DONE:
			ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
					KeyEvent.KEYCODE_ENTER));
			ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
					KeyEvent.KEYCODE_ENTER));
			break;
		case -101:// Switch Keyboard;
			// changeKeyboard();
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
			Log.d("handle shift", String.valueOf(code));
			if (Character.isLetter(code) && caps) {
				Log.d("handle shift", "Capital letters");
				code = Character.toUpperCase(code);
			}
			String cText = String.valueOf(code);
			Log.d("handle shift", cText);

			switch (Integer.valueOf((mInputMethodManager
					.getCurrentInputMethodSubtype().getExtraValue()))) {
			case 2:
				cText = myKeyboard.handelMyanmarInputText(primaryCode, ic);
				Log.d("onKey", "MyanmarHandle");
				break;
			case 3:
				cText = ((ShanKeyboard) shnKeyboard).handleShanInputText(
						primaryCode, ic);
				Log.d("onKey", "ShanHandle");
				break;
			case 4:
				cText = ((MonKeyboard) monKeyboard).handleMonInput(primaryCode,
						ic);
				break;

			}

			ic.commitText(cText, 1);
			if (shifted) {
				unshiftByLocale();
			}
		}
	}

	public static void deleteHandle(InputConnection ic) {
		CharSequence ch = ic.getTextBeforeCursor(1, 0);
		if (ch.length() != 0) {
			Log.d("Delete", "CharSequence length= " + ch.length()
					+ ": char= " + ch.toString());
			
			if (Character.isLowSurrogate(ch.charAt(0))||Character.isHighSurrogate(ch.charAt(0))) {
				ic.deleteSurroundingText(2, 0);
			} else
				ic.deleteSurroundingText(1, 0);
		} else
			ic.deleteSurroundingText(1, 0);
	}

	public static boolean isEndofText(InputConnection ic) {
		CharSequence charAfterCursor = ic.getTextAfterCursor(1, 0);// need to fix if getTextAfterCursor return null
		if (charAfterCursor.length() > 0)
			return false;
		else
			return true;
	}

	private void handleSymByLocale() {
		switch (Integer.valueOf((mInputMethodManager
				.getCurrentInputMethodSubtype().getExtraValue()))) {
		case 1:
			if (!symbol) {
				kv.setKeyboard(symKeyboard);
				symbol = true;
			} else {
				kv.setKeyboard(enKeyboard);
				symbol = false;
			}
			break;
		case 2:
			if (!symbol) {
				kv.setKeyboard(my_symbol_Keyboard);
				symbol = true;
			} else {
				kv.setKeyboard(myKeyboard);
				symbol = false;
			}
			break;
		case 3:
			if (!symbol) {
				kv.setKeyboard(shn_symbol_Keyboard);
				symbol = true;
			} else {
				kv.setKeyboard(shnKeyboard);
				symbol = false;
			}
			break;
		case 4:
			if (!symbol) {
				kv.setKeyboard(mon_symbol_Keyboard);
				symbol = true;
			} else {
				kv.setKeyboard(monKeyboard);
				symbol = false;
			}
			break;
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
			switch (Integer.valueOf((mInputMethodManager
					.getCurrentInputMethodSubtype().getExtraValue()))) {
			case 1:
				kv.getKeyboard().setShifted(true);
				caps = true;
				kv.invalidateAllKeys();
				break;
			case 2:
				kv.setKeyboard(my_shifted_Keyboard);
				kv.getKeyboard().setShifted(true);
				break;
			case 3:
				kv.setKeyboard(shn_shifted_Keyboard);
				kv.getKeyboard().setShifted(true);
				break;
			case 4:
				kv.setKeyboard(mon_shifted_Keyboard);
				kv.getKeyboard().setShifted(true);
				break;
			}
		}
		shifted = true;
	}

	private void unshiftByLocale() {
		if (kv.getKeyboard().equals(sym_shifted_Keyboard)) {
			kv.setKeyboard(symKeyboard);
			kv.getKeyboard().setShifted(false);

		} else {
			switch (Integer.valueOf((mInputMethodManager
					.getCurrentInputMethodSubtype().getExtraValue()))) {
			case 1:
				kv.getKeyboard().setShifted(false);
				caps = false;
				kv.invalidateAllKeys();
				break;
			case 2:
				kv.setKeyboard(myKeyboard);
				kv.getKeyboard().setShifted(false);
				break;
			case 3:
				kv.setKeyboard(shnKeyboard);
				kv.getKeyboard().setShifted(false);
				break;
			case 4:
				kv.setKeyboard(monKeyboard);
				kv.getKeyboard().setShifted(false);
				break;
			}
		}
		shifted = false;
	}

	public static boolean isWordSeparator(int code) {
		String separators = mWordSeparators;
		return separators.contains(String.valueOf((char) code));
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
