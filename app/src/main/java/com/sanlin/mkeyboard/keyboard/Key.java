package com.sanlin.mkeyboard.keyboard;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

import com.sanlin.mkeyboard.R;


public class Key {
    private Context context;
    private int x, y;
    private int width, height;
    public Rect bounds;
    public int code;
    public String label;
    public boolean isModifier = false;
    public boolean isSticky = false;
    public boolean isPressed = false;
    public int popupKeyboardResId = -1;
    public Drawable icon = null;

    // Style hooks
    public int backgroundColor = Color.LTGRAY;
    public int textColor = Color.BLACK;


    public String popupCharacters;

    public Key(Context context, String label, int code, Rect bounds, int defaultWidth, int defaultHeight) {
        this.context = context;
        this.label = label;
        this.code = code;
        this.bounds = bounds != null ? bounds : new Rect();
        this.width = bounds != null ? bounds.width() : defaultWidth;
        this.height = bounds != null ? bounds.height() : defaultHeight;
    }

    public Key(Context context, AttributeSet attrs, TypedArray a, int x, int y, int defaultWidth, int defaultHeight) {
        this.x = x;
        this.y = y;

        String keyWidthAttr = a.getString(R.styleable.Key_keyWidth);
        String keyHeightAttr = a.getString(R.styleable.Key_keyHeight);
        popupCharacters = a.getString(R.styleable.Key_popupCharacters);

        this.width = (keyWidthAttr != null)
                ? parseSize(keyWidthAttr, context.getResources().getDisplayMetrics().widthPixels)
                : defaultWidth;

        this.height = (keyHeightAttr != null)
                ? parseSize(keyHeightAttr, context.getResources().getDisplayMetrics().heightPixels)
                : defaultHeight;

        this.bounds = new Rect(x, y, x + width, y + height);

        this.label = a.getString(R.styleable.Key_keyLabel);
        this.code = a.getInteger(R.styleable.Key_keyCode, 0);
        this.isModifier = a.getBoolean(R.styleable.Key_isModifier, false);
        Log.d("Key",label + code + popupCharacters+isModifier + keyWidthAttr);
        int iconId = a.getResourceId(R.styleable.Key_keyIcon, 0);
        if (iconId != 0) {
            icon = ContextCompat.getDrawable(context, iconId);
        }
    }

    public void draw(Canvas canvas, Paint paint) {
        if (bounds == null) return;
        // Background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isPressed ? Color.DKGRAY : backgroundColor);
        canvas.drawRect(bounds, paint);

        // Icon or label
        if (icon != null) {
            icon.setBounds(bounds);
            icon.draw(canvas);
        } else if (label != null) {
            paint.setColor(textColor);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(40);
            float x = bounds.centerX();
            float y = bounds.centerY() - ((paint.descent() + paint.ascent()) / 2);
            canvas.drawText(label, x, y, paint);
        }

        // Border (optional)
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.GRAY);
        canvas.drawRect(bounds, paint);
    }

    private int parseSize(String value, int baseWidth) {
        if (value == null) return 0;
        if (value.endsWith("%p")) {
            float percent = Float.parseFloat(value.replace("%p", ""));
            return (int) (baseWidth * percent / 100);
        } else if (value.endsWith("dp")) {
            float dp = Float.parseFloat(value.replace("dp", ""));
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().getDisplayMetrics());
        } else if (value.endsWith("dip")) {
            float dp = Float.parseFloat(value.replace("dip", ""));
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().getDisplayMetrics());
        } else {
            float px = Float.parseFloat(value.replace("px", ""));
            return (int) px;
        }
    }
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean contains(int px, int py) {
        return bounds != null && bounds.contains(px, py);
    }

    public void onPress() {
        isPressed = true;
    }

    public void onRelease() {
        isPressed = false;
        if (isSticky) isPressed = !isPressed;  // toggle for sticky keys
    }
}
