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
 * Updated to use AndroidX Preference classes and converted to Kotlin.
 */

package com.android.inputmethodcommon

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.text.TextUtils
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceScreen

internal class InputMethodSettingsImpl : InputMethodSettingsInterface {
    private var subtypeEnablerPreference: Preference? = null
    private var inputMethodSettingsCategoryTitleRes: Int = 0
    private var inputMethodSettingsCategoryTitle: CharSequence? = null
    private var subtypeEnablerTitleRes: Int = 0
    private var subtypeEnablerTitle: CharSequence? = null
    private var subtypeEnablerIconRes: Int = 0
    private var subtypeEnablerIcon: Drawable? = null
    private var imm: InputMethodManager? = null
    private var imi: InputMethodInfo? = null
    private var context: Context? = null

    /**
     * Initialize internal states of this object.
     * @param context the context for this application.
     * @param prefScreen a PreferenceScreen of PreferenceFragmentCompat.
     * @return true if this application is an IME and has two or more subtypes, false otherwise.
     */
    fun init(context: Context, prefScreen: PreferenceScreen): Boolean {
        this.context = context
        imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imi = getMyImi(context, imm)

        val currentImi = imi
        if (currentImi == null || currentImi.subtypeCount <= 1) {
            return false
        }

        subtypeEnablerPreference = Preference(context).apply {
            setOnPreferenceClickListener {
                // Check if keyboard is enabled before allowing language selection
                if (!isInputMethodEnabled(context, imm, imi)) {
                    Toast.makeText(
                        context,
                        "Please enable this keyboard in system settings first",
                        Toast.LENGTH_LONG
                    ).show()
                    // Open IME settings so user can enable the keyboard
                    val enableIntent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(enableIntent)
                    return@setOnPreferenceClickListener true
                }

                val title = getSubtypeEnablerTitle(context)
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS).apply {
                    putExtra(Settings.EXTRA_INPUT_METHOD_ID, imi?.id)
                    if (!TextUtils.isEmpty(title)) {
                        putExtra(Intent.EXTRA_TITLE, title)
                    }
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(intent)
                true
            }
        }

        subtypeEnablerPreference?.let { prefScreen.addPreference(it) }
        updateSubtypeEnabler()
        return true
    }

    override fun setInputMethodSettingsCategoryTitle(resId: Int) {
        inputMethodSettingsCategoryTitleRes = resId
        updateSubtypeEnabler()
    }

    override fun setInputMethodSettingsCategoryTitle(title: CharSequence) {
        inputMethodSettingsCategoryTitleRes = 0
        inputMethodSettingsCategoryTitle = title
        updateSubtypeEnabler()
    }

    override fun setSubtypeEnablerTitle(resId: Int) {
        subtypeEnablerTitleRes = resId
        updateSubtypeEnabler()
    }

    override fun setSubtypeEnablerTitle(title: CharSequence) {
        subtypeEnablerTitleRes = 0
        subtypeEnablerTitle = title
        updateSubtypeEnabler()
    }

    override fun setSubtypeEnablerIcon(resId: Int) {
        subtypeEnablerIconRes = resId
        updateSubtypeEnabler()
    }

    override fun setSubtypeEnablerIcon(drawable: Drawable) {
        subtypeEnablerIconRes = 0
        subtypeEnablerIcon = drawable
        updateSubtypeEnabler()
    }

    private fun getSubtypeEnablerTitle(context: Context): CharSequence? {
        return if (subtypeEnablerTitleRes != 0) {
            context.getString(subtypeEnablerTitleRes)
        } else {
            subtypeEnablerTitle
        }
    }

    fun updateSubtypeEnabler() {
        val pref = subtypeEnablerPreference ?: return
        val ctx = context ?: return

        // Refresh InputMethodManager to get latest data
        imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imi = getMyImi(ctx, imm)

        if (subtypeEnablerTitleRes != 0) {
            pref.setTitle(subtypeEnablerTitleRes)
        } else if (!TextUtils.isEmpty(subtypeEnablerTitle)) {
            pref.title = subtypeEnablerTitle
        }

        // Check if keyboard is enabled
        if (!isInputMethodEnabled(ctx, imm, imi)) {
            pref.summary = "Enable this keyboard first"
        } else {
            val summary = getEnabledSubtypesLabel(ctx, imm, imi)
            pref.summary = if (!TextUtils.isEmpty(summary)) summary else ""
        }

        if (subtypeEnablerIconRes != 0) {
            pref.setIcon(subtypeEnablerIconRes)
        } else if (subtypeEnablerIcon != null) {
            pref.icon = subtypeEnablerIcon
        }
    }

    companion object {
        private fun getMyImi(context: Context, imm: InputMethodManager?): InputMethodInfo? {
            val imis = imm?.inputMethodList ?: return null
            return imis.find { it.packageName == context.packageName }
        }

        /**
         * Check if this input method is enabled in system settings.
         * Language selection only works for enabled input methods.
         */
        private fun isInputMethodEnabled(
            context: Context,
            imm: InputMethodManager?,
            imi: InputMethodInfo?
        ): Boolean {
            if (imm == null || imi == null) return false
            val enabledImis = imm.enabledInputMethodList
            return enabledImis.any { it.id == imi.id }
        }

        private fun getEnabledSubtypesLabel(
            context: Context,
            imm: InputMethodManager?,
            imi: InputMethodInfo?
        ): String? {
            if (imm == null || imi == null) return null
            val subtypes = imm.getEnabledInputMethodSubtypeList(imi, true)
            return subtypes.joinToString(", ") { subtype ->
                subtype.getDisplayName(
                    context,
                    imi.packageName,
                    imi.serviceInfo.applicationInfo
                ).toString()
            }.ifEmpty { null }
        }
    }
}
