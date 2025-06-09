package com.sanlin.mkeyboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class CandidateView extends View {

    private List<String> suggestions = new ArrayList<>();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnCandidateSelectedListener listener;
    private List<Rect> wordBounds = new ArrayList<>();

    public CandidateView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setTextSize(40);
        paint.setColor(Color.BLACK);
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
        invalidate();
    }

    public void setOnCandidateSelectedListener(OnCandidateSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        wordBounds.clear();

        int x = 20;
        int y = getHeight() / 2;
        for (String word : suggestions) {
            float textWidth = paint.measureText(word);
            Rect bounds = new Rect(x - 10, 0, (int) (x + textWidth + 10), getHeight());
            wordBounds.add(bounds);

            // Background
            paint.setColor(Color.LTGRAY);
            canvas.drawRect(bounds, paint);

            // Text
            paint.setColor(Color.BLACK);
            canvas.drawText(word, x + textWidth / 2, y - ((paint.descent() + paint.ascent()) / 2), paint);

            x += textWidth + 40;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            for (int i = 0; i < wordBounds.size(); i++) {
                if (wordBounds.get(i).contains((int) x, (int) event.getY())) {
                    if (listener != null) {
                        listener.onCandidateSelected(suggestions.get(i));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public interface OnCandidateSelectedListener {
        void onCandidateSelected(String word);
    }
}

