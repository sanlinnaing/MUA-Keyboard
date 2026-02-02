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

/**
 * This is a part of the inputmethod-common static Java library.
 * The original source code can be found at frameworks/opt/inputmethodcommon of Android Open Source
 * Project.
 *
 * Updated to use AndroidX PreferenceFragmentCompat.
 */

package com.android.inputmethodcommon;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.PreferenceFragmentCompat;

/**
 * This is a helper class for an IME's settings preference fragment. It's recommended for every
 * IME to have its own settings preference fragment which inherits this class.
 */
public abstract class InputMethodSettingsFragment extends PreferenceFragmentCompat
        implements InputMethodSettingsInterface {
    private final InputMethodSettingsImpl mSettings = new InputMethodSettingsImpl();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context context = getActivity();
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(context));
        mSettings.init(context, getPreferenceScreen());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInputMethodSettingsCategoryTitle(int resId) {
        mSettings.setInputMethodSettingsCategoryTitle(resId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInputMethodSettingsCategoryTitle(CharSequence title) {
        mSettings.setInputMethodSettingsCategoryTitle(title);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubtypeEnablerTitle(int resId) {
        mSettings.setSubtypeEnablerTitle(resId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubtypeEnablerTitle(CharSequence title) {
        mSettings.setSubtypeEnablerTitle(title);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubtypeEnablerIcon(int resId) {
        mSettings.setSubtypeEnablerIcon(resId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubtypeEnablerIcon(Drawable drawable) {
        mSettings.setSubtypeEnablerIcon(drawable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();
        refreshSubtypeEnabler();
    }

    /**
     * Refresh the subtype enabler preference to show current enabled subtypes.
     * Call this when returning from system settings.
     */
    public void refreshSubtypeEnabler() {
        final Handler handler = new Handler(Looper.getMainLooper());

        // Update immediately
        mSettings.updateSubtypeEnabler();

        // The system may take time to persist subtype changes, so we refresh
        // multiple times with increasing delays to catch the update
        final int[] delays = {200, 500, 1000, 2000};
        for (final int delay : delays) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isAdded()) {
                        mSettings.updateSubtypeEnabler();
                    }
                }
            }, delay);
        }
    }
}
