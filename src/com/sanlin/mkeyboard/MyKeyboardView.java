package com.sanlin.mkeyboard;

import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.Keyboard.Key;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;

public class MyKeyboardView extends KeyboardView {

	public MyKeyboardView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	public MyKeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		// TODO Auto-generated constructor stub
	}

	public void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		List<Key> keys = getKeyboard().getKeys();
		for (Key key : keys) {
			if (key.label != null) {
				if (key.label.equals(";)")) {
					key.label = new String(Character.toChars(key.codes[0]));
					Log.d("onDraw","Length= "+key.label.length()+" : CharSeq ="+key.label.toString());
				}
			}
		}
	}
}
