package com.sanlin.mkeyboard.setting;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.sanlin.mkeyboard.R;

public class KeyboardSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        setTheme(R.style.Theme_KeyMandalar);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keyboard_settings);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SwitchMaterial soundSwitch = findViewById(R.id.switch_sound);
        soundSwitch.setChecked(prefs.getBoolean("sound", true));
        soundSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("sound", isChecked).apply()
        );

        SwitchMaterial vibrationSwitch = findViewById(R.id.switch_vibration);
        vibrationSwitch.setChecked(prefs.getBoolean("vibration", true));
        vibrationSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("vibration", isChecked).apply()
        );

        Spinner themeSpinner = findViewById(R.id.spinner_theme);
        String[] themes = {"Default", "Dark"};
        themeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, themes));
        themeSpinner.setSelection(prefs.getInt("theme", 0));
        themeSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(pos ->
                prefs.edit().putInt("theme", pos).apply()
        ));
    }
}
