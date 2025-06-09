package com.sanlin.mkeyboard.keyboard;

import android.view.inputmethod.InputConnection;

import com.sanlin.mkeyboard.KeyMandalarInputMethodService;

public class KeyboardActionListener implements KeyboardView.OnKeyboardActionListener {

    private final KeyMandalarInputMethodService ims;
    private final KeyboardView keyboardView;

    public KeyboardActionListener(KeyMandalarInputMethodService ims, KeyboardView keyboardView) {
        this.ims = ims;
        this.keyboardView = keyboardView;
    }

    @Override
    public void onKey(int primaryCode, Key key) {
        InputConnection ic = ims.getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(String.valueOf((char) primaryCode), 1);
        }

        // Let the IMS update candidates
        if (ims instanceof KeyMandalarInputMethodService) {
            ((KeyMandalarInputMethodService) ims).onCharacterTyped(primaryCode);
        }
    }

    @Override
    public void onLongPress(Key key) {
        if (key.popupCharacters != null && !key.popupCharacters.isEmpty()) {
            keyboardView.showPopupForKey(key);
        }
    }

}
