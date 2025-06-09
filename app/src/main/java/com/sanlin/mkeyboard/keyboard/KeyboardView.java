package com.sanlin.mkeyboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;

import java.util.List;

public class KeyboardView extends View {

    private static final int PROXIMITY_THRESHOLD_SQ = 75 * 75; // pixels²

    private Keyboard keyboard;
    private Key activeKey = null;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnKeyboardActionListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isLongPressed = false;
    private List<Key> popupKeys = null;
    private boolean showingPopup = false;

    public KeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(40);
    }

    public void setKeyboard(Keyboard keyboard) {
        this.keyboard = keyboard;
        invalidate();
    }

    public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (keyboard == null) return;

        for (Row row : keyboard.rows) {
            for (Key key : row.keys) {
                key.draw(canvas, paint);
            }
        }
        if (showingPopup && popupKeys != null && !popupKeys.isEmpty()) {
            // Compute popup bounds
            int left = popupKeys.get(0).bounds.left;
            int top = popupKeys.get(0).bounds.top;
            int right = popupKeys.get(popupKeys.size() - 1).bounds.right;
            int bottom = popupKeys.get(0).bounds.bottom;

            // Optional: Add padding
            int padding = 10;
            RectF popupBackground = new RectF(left - padding, top - padding, right + padding, bottom + padding);

            // Draw background
            Paint bgPaint = new Paint();
            bgPaint.setColor(Color.parseColor("#DDDDDD"));
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setAntiAlias(true);
            canvas.drawRoundRect(popupBackground, 20, 20, bgPaint);

            // Draw each popup key on top
            for (Key k : popupKeys) {
                k.draw(canvas, paint);
            }
        }


    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (keyboard == null) return false;
        int action = event.getAction();
        int x = (int) event.getX();
        int y = (int) event.getY();


        if (showingPopup && popupKeys != null) {
            Key popupKey = findKeyInPopup(x, y);
            if (popupKey != null) {
                if (action == MotionEvent.ACTION_UP) {
                    if (listener != null) {
                        listener.onKey(popupKey.code, popupKey);
                    }
                    dismissPopup();
                }
                return true;
            }else{
                if (action == MotionEvent.ACTION_DOWN) {
                    dismissPopup();
                    return true;
                }
            }
        }


        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activeKey = findKeyAt(x, y);
                if (activeKey != null) {
                    activeKey.onPress();
                    isLongPressed = false;

                    // Start long press handler
                    longPressKey = activeKey;
                    handler.postDelayed(longPressRunnable = () -> {
                        isLongPressed = true;
                        if (listener != null) {
                            listener.onLongPress(longPressKey);
                        }
                    }, 500);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                Key movedKey = findKeyAt(x, y);
                if (movedKey != activeKey) {
                    if (activeKey != null) activeKey.onRelease();
                    if (movedKey != null) movedKey.onPress();
                    activeKey = movedKey;
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);

                if (activeKey != null) {
                    activeKey.onRelease();
                    if (!isLongPressed && listener != null) {
                        listener.onKey(activeKey.code, activeKey);  // Normal press
                    }
                    activeKey = null;
                    invalidate();
                }
                break;
        }

        return true;
    }


    private Key longPressKey = null;
    private Runnable longPressRunnable = () -> {
        isLongPressed = false;
    };

    public void showPopupForKey(Key baseKey) {
        if (baseKey.popupCharacters == null || baseKey.popupCharacters.isEmpty()) return;

        int keyWidth = baseKey.bounds.width();
        int keyHeight = baseKey.bounds.height();

        PopupKeyboard popupKeyboard = new PopupKeyboard(getContext(), baseKey.popupCharacters, keyWidth, keyHeight);
        popupKeys = popupKeyboard.getPopupKeys();

        // Adjust popup keys' x, y based on baseKey position
        for (Key k : popupKeys) {
            k.bounds.offset(baseKey.bounds.left, baseKey.bounds.top - keyHeight); // Position above
        }

        showingPopup = true;
        invalidate();
    }

    private Key findKeyAt(int x, int y) {
        Key closest = null;
        int closestDistSq = Integer.MAX_VALUE;

        for (Row row : keyboard.rows) {
            for (Key key : row.keys) {
                int dx = key.bounds.centerX() - x;
                int dy = key.bounds.centerY() - y;
                int distSq = dx * dx + dy * dy;

                if (key.contains(x, y)) {
                    return key;  // Exact hit
                }

                if (distSq < PROXIMITY_THRESHOLD_SQ && distSq < closestDistSq) {
                    closest = key;
                    closestDistSq = distSq;
                }
            }
        }
        return closest;
    }

    private Key findKeyInPopup(int x, int y) {
        for (Key key : popupKeys) {
            if (key.bounds.contains(x, y)) {
                return key;
            }
        }
        return null;
    }

    public void dismissPopup() {
        showingPopup = false;
        popupKeys = null;
        invalidate();
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredDp = 300;
        float density = getResources().getDisplayMetrics().density;
        int desiredHeight = (int) (desiredDp * density);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    private PopupWindow popupWindow;

    // Callback interface
    public interface OnKeyboardActionListener {
        void onKey(int primaryCode, Key key);

        void onLongPress(Key key);

    }

}

