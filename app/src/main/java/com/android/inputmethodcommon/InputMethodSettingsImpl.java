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
 * Updated to use AndroidX Preference classes.
 */

package com.android.inputmethodcommon;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import java.util.List;

/* package private */ class InputMethodSettingsImpl implements InputMethodSettingsInterface {
    private Preference mSubtypeEnablerPreference;
    private int mInputMethodSettingsCategoryTitleRes;
    private CharSequence mInputMethodSettingsCategoryTitle;
    private int mSubtypeEnablerTitleRes;
    private CharSequence mSubtypeEnablerTitle;
    private int mSubtypeEnablerIconRes;
    private Drawable mSubtypeEnablerIcon;
    private InputMethodManager mImm;
    private InputMethodInfo mImi;
    private Context mContext;

    /**
     * Initialize internal states of this object.
     * @param context the context for this application.
     * @param prefScreen a PreferenceScreen of PreferenceFragmentCompat.
     * @return true if this application is an IME and has two or more subtypes, false otherwise.
     */
    public boolean init(final Context context, final PreferenceScreen prefScreen) {
        mContext = context;
        mImm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        mImi = getMyImi(context, mImm);
        if (mImi == null || mImi.getSubtypeCount() <= 1) {
            return false;
        }
        mSubtypeEnablerPreference = new Preference(context);
        mSubtypeEnablerPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Check if keyboard is enabled before allowing language selection
                if (!isInputMethodEnabled(context, mImm, mImi)) {
                    Toast.makeText(context,
                        "Please enable this keyboard in system settings first",
                        Toast.LENGTH_LONG).show();
                    // Open IME settings so user can enable the keyboard
                    Intent enableIntent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
                    enableIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(enableIntent);
                    return true;
                }

                final CharSequence title = getSubtypeEnablerTitle(context);
                final Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS);
                intent.putExtra(Settings.EXTRA_INPUT_METHOD_ID, mImi.getId());
                if (!TextUtils.isEmpty(title)) {
                    intent.putExtra(Intent.EXTRA_TITLE, title);
                }
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(intent);
                return true;
            }
        });
        prefScreen.addPreference(mSubtypeEnablerPreference);
        updateSubtypeEnabler();
        return true;
    }

    private static InputMethodInfo getMyImi(Context context, InputMethodManager imm) {
        final List<InputMethodInfo> imis = imm.getInputMethodList();
        for (int i = 0; i < imis.size(); ++i) {
            final InputMethodInfo imi = imis.get(i);
            if (imis.get(i).getPackageName().equals(context.getPackageName())) {
                return imi;
            }
        }
        return null;
    }

    /**
     * Check if this input method is enabled in system settings.
     * Language selection only works for enabled input methods.
     */
    private static boolean isInputMethodEnabled(Context context, InputMethodManager imm, InputMethodInfo imi) {
        if (imm == null || imi == null) return false;
        final List<InputMethodInfo> enabledImis = imm.getEnabledInputMethodList();
        for (InputMethodInfo enabledImi : enabledImis) {
            if (enabledImi.getId().equals(imi.getId())) {
                return true;
            }
        }
        return false;
    }

    private static String getEnabledSubtypesLabel(
            Context context, InputMethodManager imm, InputMethodInfo imi) {
        if (context == null || imm == null || imi == null) return null;
        final List<InputMethodSubtype> subtypes = imm.getEnabledInputMethodSubtypeList(imi, true);
        final StringBuilder sb = new StringBuilder();
        final int N = subtypes.size();
        for (int i = 0; i < N; ++i) {
            final InputMethodSubtype subtype = subtypes.get(i);
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(subtype.getDisplayName(context, imi.getPackageName(),
                    imi.getServiceInfo().applicationInfo));
        }
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInputMethodSettingsCategoryTitle(int resId) {
        mInputMethodSettingsCategoryTitleRes = resId;
        updateSubtypeEnabler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInputMethodSettingsCategoryTitle(CharSequence title) {
        mInputMethodSettingsCategoryTitleRes = 0;
        mInputMethodSettingsCategoryTitle = title;
        updateSubtypeEnabler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubtypeEnablerTitle(int resId) {
        mSubtypeEnablerTitleRes = resId;
        updateSubtypeEnabler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubtypeEnablerTitle(CharSequence title) {
        mSubtypeEnablerTitleRes = 0;
        mSubtypeEnablerTitle = title;
        updateSubtypeEnabler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubtypeEnablerIcon(int resId) {
        mSubtypeEnablerIconRes = resId;
        updateSubtypeEnabler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubtypeEnablerIcon(Drawable drawable) {
        mSubtypeEnablerIconRes = 0;
        mSubtypeEnablerIcon = drawable;
        updateSubtypeEnabler();
    }

    private CharSequence getSubtypeEnablerTitle(Context context) {
        if (mSubtypeEnablerTitleRes != 0) {
            return context.getString(mSubtypeEnablerTitleRes);
        } else {
            return mSubtypeEnablerTitle;
        }
    }

    public void updateSubtypeEnabler() {
        if (mSubtypeEnablerPreference != null && mContext != null) {
            // Refresh InputMethodManager to get latest data
            mImm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            mImi = getMyImi(mContext, mImm);

            if (mSubtypeEnablerTitleRes != 0) {
                mSubtypeEnablerPreference.setTitle(mSubtypeEnablerTitleRes);
            } else if (!TextUtils.isEmpty(mSubtypeEnablerTitle)) {
                mSubtypeEnablerPreference.setTitle(mSubtypeEnablerTitle);
            }

            // Check if keyboard is enabled
            if (!isInputMethodEnabled(mContext, mImm, mImi)) {
                mSubtypeEnablerPreference.setSummary("Enable this keyboard first");
            } else {
                final String summary = getEnabledSubtypesLabel(mContext, mImm, mImi);
                if (!TextUtils.isEmpty(summary)) {
                    mSubtypeEnablerPreference.setSummary(summary);
                } else {
                    mSubtypeEnablerPreference.setSummary("");
                }
            }

            if (mSubtypeEnablerIconRes != 0) {
                mSubtypeEnablerPreference.setIcon(mSubtypeEnablerIconRes);
            } else if (mSubtypeEnablerIcon != null) {
                mSubtypeEnablerPreference.setIcon(mSubtypeEnablerIcon);
            }
        }
    }
}
