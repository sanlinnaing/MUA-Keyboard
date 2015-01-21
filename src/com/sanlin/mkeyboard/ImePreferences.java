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

package com.sanlin.mkeyboard;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;

import com.android.inputmethodcommon.InputMethodSettingsFragment;
import com.sanlin.mkeyboard.R;


/**
 * Displays the IME preferences inside the input method setting.
 */
public class ImePreferences extends PreferenceActivity {
	static InputMethodManager imeManager;
	@Override
	public Intent getIntent() {
		final Intent modIntent = new Intent(super.getIntent());
		modIntent.putExtra(EXTRA_SHOW_FRAGMENT,
				SettingsLanguage.class.getName());
		modIntent.putExtra(EXTRA_NO_HEADERS, true);
		return modIntent;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// We overwrite the title of the activity, as the default one is
		// "Voice Search".
		setTitle(R.string.settings_name);
		
		imeManager=(InputMethodManager) getApplicationContext().getSystemService(INPUT_METHOD_SERVICE);

	}

	@Override
	protected boolean isValidFragment(String fragmentName) {
		// TODO Auto-generated method stub
		return SettingsLanguage.class.getName().equals(fragmentName);
	}
	
	public static class SettingsLanguage extends InputMethodSettingsFragment {
		
		@SuppressLint("NewApi")
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setInputMethodSettingsCategoryTitle(R.string.language_selection_title);
			setSubtypeEnablerTitle(R.string.select_language);

			// Load the preferences from an XML resource
			addPreferencesFromResource(R.xml.ime_preferences);
			
			Preference myPref = (Preference) findPreference("go_to_ime");
			myPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			             public boolean onPreferenceClick(Preference preference) {
			                 //open browser or intent here
			            	 startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
			            	 return true;
			             }
			         });
			
			Preference myPref2 = (Preference) findPreference("choose_keyboard");
			myPref2.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			             public boolean onPreferenceClick(Preference preference) {
			                 //open browser or intent here
			            	 imeManager.showInputMethodPicker();
			            	 return true;
			             }
			         });
		}
	}
}
