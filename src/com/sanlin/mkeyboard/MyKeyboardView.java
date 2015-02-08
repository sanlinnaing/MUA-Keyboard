package com.sanlin.mkeyboard;

import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
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
		Paint paint = new Paint();
		paint.setTextAlign(Paint.Align.CENTER);
		paint.setTextSize(28);
		paint.setAntiAlias(true);
		// paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
		paint.setColor(Color.parseColor("#FFCC00"));
		for (Key key : keys) {
			if (key.label != null) {
				if (key.label.equals(";)")) {
					key.label = new String(Character.toChars(key.codes[0]));
					Log.d("onDraw", "Length= " + key.label.length()
							+ " : CharSeq =" + key.label.toString());
				}
				if (MyConfig.isShowHintLabel()) {
					if (key.popupCharacters != null) {
						String popKeyLabel = "";
						int xPos = key.x + key.width / 5;
						if (key.popupCharacters.length() > 3) {
							xPos = key.x + key.width / 2;
							popKeyLabel = key.popupCharacters.subSequence(0, 2)
									.toString();
						} else {
							popKeyLabel = key.popupCharacters.toString();
						}
						canvas.drawText(popKeyLabel, xPos, key.y + 30, paint);
					}
				}
			}
		}
	}
}
