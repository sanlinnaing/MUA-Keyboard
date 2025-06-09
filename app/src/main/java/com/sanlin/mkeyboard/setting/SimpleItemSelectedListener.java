package com.sanlin.mkeyboard.setting;

import android.view.View;
import android.widget.AdapterView;

public class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    private final OnSelected callback;

    public interface OnSelected {
        void onSelected(int position);
    }

    public SimpleItemSelectedListener(OnSelected callback) {
        this.callback = callback;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        callback.onSelected(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}
}
