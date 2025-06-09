package com.sanlin.mkeyboard;

import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.sanlin.mkeyboard.autocomplete.AutocompleteTrie;
import com.sanlin.mkeyboard.keyboard.CandidateView;
import com.sanlin.mkeyboard.keyboard.Keyboard;
import com.sanlin.mkeyboard.keyboard.KeyboardActionListener;
import com.sanlin.mkeyboard.keyboard.KeyboardView;

import java.util.Collections;
import java.util.List;

public class KeyMandalarInputMethodService extends InputMethodService {
    private AutocompleteTrie autocomplete = new AutocompleteTrie();
    private StringBuilder composing = new StringBuilder();
    CandidateView candidateView = null;

    @Override
    public View onCreateInputView() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean vibrate = prefs.getBoolean("vibration", true);
        boolean sound = prefs.getBoolean("sound", true);
        int theme = prefs.getInt("theme", 0);
        Log.d("KeyMandalar", "vibrate: " + vibrate + ", sound: " + sound + ", theme: " + theme);
        View root = getLayoutInflater().inflate(R.layout.keyboard_container, null);
        View dragHandle = root.findViewById(R.id.drag_handle);
        KeyboardView keyboardView = root.findViewById(R.id.keyboard_view);
        candidateView = root.findViewById(R.id.candidate_view);

        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            float initialY;
            int originalHeight;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = event.getRawY();
                        originalHeight = keyboardView.getHeight();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaY = event.getRawY() - initialY;
                        int newHeight = (int) (originalHeight - deltaY);
                        newHeight = Math.max((int)(150 * getResources().getDisplayMetrics().density), newHeight); // min height
                        ViewGroup.LayoutParams params = keyboardView.getLayoutParams();
                        params.height = newHeight;
                        keyboardView.setLayoutParams(params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        // Persist the height (optional)
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                                .edit().putInt("keyboard_height_px", keyboardView.getHeight()).apply();
                        return true;
                }
                return false;
            }
        });
        int savedHeight = PreferenceManager.getDefaultSharedPreferences(this)
                .getInt("keyboard_height_px", -1);
        if (savedHeight > 0) {
            ViewGroup.LayoutParams params = keyboardView.getLayoutParams();
            params.height = savedHeight;
            keyboardView.setLayoutParams(params);
        }
        Keyboard keyboard = new Keyboard(this, R.xml.qwerty, getMaxWidth());
        keyboardView.setKeyboard(keyboard);

        keyboardView.setOnKeyboardActionListener(new KeyboardActionListener(this, keyboardView));

        candidateView.setOnCandidateSelectedListener(word -> {
            composing.setLength(0);
            getCurrentInputConnection().commitText(word + " ", 1);
            candidateView.setSuggestions(Collections.emptyList());
        });

        preloadDictionary();
        return root;
    }

    public void onCharacterTyped(int primaryCode) {
        char c = (char) primaryCode;
        if (Character.isLetterOrDigit(c)) {
            composing.append(c);
            updateSuggestions(candidateView);
        } else if (primaryCode == KeyEvent.KEYCODE_DEL && composing.length() > 0) {
            composing.setLength(composing.length() - 1);
            updateSuggestions(candidateView);
        } else {
            composing.setLength(0);
            updateSuggestions(candidateView);
        }
    }

    private void updateSuggestions(CandidateView view) {
        String typed = composing.toString();
        if (!typed.isEmpty()) {
            List<String> suggestions = autocomplete.getSuggestions(typed, 5);
            view.setSuggestions(suggestions);
        } else {
            view.setSuggestions(Collections.emptyList());
        }
    }


    private void preloadDictionary() {
        autocomplete.insert("hello", 100);
        autocomplete.insert("hey", 90);
        autocomplete.insert("hi", 80);
        autocomplete.insert("house", 70);
        autocomplete.insert("how", 60);
        autocomplete.insert("hover", 50);
    }


}

