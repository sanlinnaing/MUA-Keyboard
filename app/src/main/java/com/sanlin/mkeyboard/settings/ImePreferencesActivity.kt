/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sanlin.mkeyboard.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import com.android.inputmethodcommon.InputMethodSettingsFragment
import com.sanlin.mkeyboard.R

/**
 * Displays the IME preferences inside the input method setting.
 */
open class ImePreferencesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)

        // Set up the toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        setTitle(R.string.settings_name)

        imeManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment(), "settings_fragment")
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the fragment's subtype display when activity resumes
        val fragment: Fragment? = supportFragmentManager.findFragmentByTag("settings_fragment")
        if (fragment is SettingsFragment) {
            fragment.refreshSubtypeEnabler()
        }
    }

    class SettingsFragment : InputMethodSettingsFragment() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            super.onCreatePreferences(savedInstanceState, rootKey)

            setInputMethodSettingsCategoryTitle(R.string.language_selection_title)
            setSubtypeEnablerTitle(R.string.select_language)

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.ime_preferences)

            findPreference<Preference>("go_to_ime")?.setOnPreferenceClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                true
            }

            findPreference<Preference>("choose_keyboard")?.setOnPreferenceClickListener {
                imeManager?.showInputMethodPicker()
                true
            }
        }
    }

    companion object {
        @JvmStatic
        var imeManager: InputMethodManager? = null
    }
}
