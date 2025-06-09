package com.sanlin.mkeyboard.keyboard;

import android.content.Context;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;

public class PopupKeyboard {
    private List<Key> popupKeys = new ArrayList<>();

    public PopupKeyboard(Context context, String popupCharacters, int keyWidth, int keyHeight) {
        int x = 0;
        int y = 0;
        for (int i = 0; i < popupCharacters.length(); i++) {
            char ch = popupCharacters.charAt(i);
            Rect bounds = new Rect(x, y, x + keyWidth, y + keyHeight);
            Key key = new Key(context, String.valueOf(ch), ch, bounds, keyWidth, keyHeight);
            key.label = String.valueOf(ch);
            key.code = ch;
            popupKeys.add(key);
            x += keyWidth;
        }
    }

    public List<Key> getPopupKeys() {
        return popupKeys;
    }
}
