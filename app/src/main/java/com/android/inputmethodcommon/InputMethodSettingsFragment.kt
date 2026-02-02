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
 * Updated to use AndroidX PreferenceFragmentCompat and converted to Kotlin.
 */

package com.android.inputmethodcommon

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceFragmentCompat

/**
 * This is a helper class for an IME's settings preference fragment. It's recommended for every
 * IME to have its own settings preference fragment which inherits this class.
 */
abstract class InputMethodSettingsFragment : PreferenceFragmentCompat(), InputMethodSettingsInterface {

    private val settings = InputMethodSettingsImpl()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = activity ?: return
        preferenceScreen = preferenceManager.createPreferenceScreen(context)
        settings.init(context, preferenceScreen)
    }

    override fun setInputMethodSettingsCategoryTitle(resId: Int) {
        settings.setInputMethodSettingsCategoryTitle(resId)
    }

    override fun setInputMethodSettingsCategoryTitle(title: CharSequence) {
        settings.setInputMethodSettingsCategoryTitle(title)
    }

    override fun setSubtypeEnablerTitle(resId: Int) {
        settings.setSubtypeEnablerTitle(resId)
    }

    override fun setSubtypeEnablerTitle(title: CharSequence) {
        settings.setSubtypeEnablerTitle(title)
    }

    override fun setSubtypeEnablerIcon(resId: Int) {
        settings.setSubtypeEnablerIcon(resId)
    }

    override fun setSubtypeEnablerIcon(drawable: Drawable) {
        settings.setSubtypeEnablerIcon(drawable)
    }

    override fun onResume() {
        super.onResume()
        refreshSubtypeEnabler()
    }

    /**
     * Refresh the subtype enabler preference to show current enabled subtypes.
     * Call this when returning from system settings.
     */
    fun refreshSubtypeEnabler() {
        val handler = Handler(Looper.getMainLooper())

        // Update immediately
        settings.updateSubtypeEnabler()

        // The system may take time to persist subtype changes, so we refresh
        // multiple times with increasing delays to catch the update
        val delays = intArrayOf(200, 500, 1000, 2000)
        for (delay in delays) {
            handler.postDelayed({
                if (isAdded) {
                    settings.updateSubtypeEnabler()
                }
            }, delay.toLong())
        }
    }
}
