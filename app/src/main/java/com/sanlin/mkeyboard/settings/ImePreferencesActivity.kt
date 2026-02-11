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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.android.inputmethodcommon.InputMethodSettingsFragment
import com.sanlin.mkeyboard.R
import com.sanlin.mkeyboard.service.MuaKeyboardService
import com.sanlin.mkeyboard.suggestion.PersonalizationStorage
import com.sanlin.mkeyboard.suggestion.UserDictionary

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

            findPreference<Preference>("view_learned_words")?.setOnPreferenceClickListener {
                showLearnedWordsDialog()
                true
            }

            findPreference<Preference>("clear_personalization")?.setOnPreferenceClickListener {
                showClearPersonalizationDialog()
                true
            }
        }

        private fun showLearnedWordsDialog() {
            val context = context ?: return

            // Load learned words in background
            Thread {
                val learnedWords: PersonalizationStorage.LearnedWords
                val userDictWords: List<com.sanlin.mkeyboard.suggestion.SyllableTrie.WordEntry>

                // Try reading from running service's in-memory data (real-time, no disk I/O)
                val service = MuaKeyboardService.instance
                if (service != null) {
                    learnedWords = service.getLearnedWords(100)
                        ?: PersonalizationStorage.LearnedWords(
                            emptyList(), emptyList(), emptyList(),
                            emptyList(), emptyList(), emptyList(),
                            null, null
                        )
                    userDictWords = service.getUserDictWords(50)
                } else {
                    // Fall back to disk (service not running)
                    val storage = PersonalizationStorage(context)
                    learnedWords = storage.getLearnedWords(100)
                    val userDict = UserDictionary(context)
                    userDict.initialize()
                    userDictWords = userDict.getAllWords(50)
                }

                // Show dialog on UI thread
                activity?.runOnUiThread {
                    if (learnedWords.isEmpty && userDictWords.isEmpty()) {
                        AlertDialog.Builder(context)
                            .setTitle(R.string.view_learned_words_title)
                            .setMessage(R.string.view_learned_words_empty)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    } else {
                        showLearnedWordsListDialog(context, learnedWords, userDictWords)
                    }
                }
            }.start()
        }

        private fun showLearnedWordsListDialog(
            context: android.content.Context,
            learnedWords: PersonalizationStorage.LearnedWords,
            userDictWords: List<com.sanlin.mkeyboard.suggestion.SyllableTrie.WordEntry>
        ) {
            val message = buildString {
                // User Dictionary section (unknown words)
                if (userDictWords.isNotEmpty()) {
                    append(getString(R.string.view_user_dict_section, userDictWords.size))
                    append("\n")
                    userDictWords.take(30).forEach { entry ->
                        append("  ${entry.fullWord} (${entry.frequency}x)\n")
                    }
                    if (userDictWords.size > 30) {
                        append("  ... and ${userDictWords.size - 30} more\n")
                    }
                    append("\n")
                }

                // Myanmar section
                val hasMyanmarData = learnedWords.myanmarWords.isNotEmpty() ||
                    learnedWords.myanmarBigrams.isNotEmpty() ||
                    learnedWords.myanmarTrigrams.isNotEmpty()
                if (hasMyanmarData) {
                    val stats = learnedWords.myanmarStats
                    val myanmarCount = stats?.unigramCount ?: learnedWords.myanmarWords.size
                    append(getString(R.string.view_learned_words_myanmar, myanmarCount))
                    append("\n")

                    // Unigrams
                    if (learnedWords.myanmarWords.isNotEmpty()) {
                        append(getString(R.string.ngram_unigrams_header))
                        append("\n")
                        learnedWords.myanmarWords.take(20).forEach { (word, freq) ->
                            append("  $word ($freq)\n")
                        }
                    }

                    // Bigrams
                    if (learnedWords.myanmarBigrams.isNotEmpty()) {
                        val bigramTotal = stats?.totalBigramEntries ?: learnedWords.myanmarBigrams.size
                        append(getString(R.string.ngram_bigrams_header, bigramTotal))
                        append("\n")
                        learnedWords.myanmarBigrams.take(20).forEach { (ctx, next, freq) ->
                            append("  $ctx \u2192 $next ($freq)\n")
                        }
                    }

                    // Trigrams
                    if (learnedWords.myanmarTrigrams.isNotEmpty()) {
                        val trigramTotal = stats?.totalTrigramEntries ?: learnedWords.myanmarTrigrams.size
                        append(getString(R.string.ngram_trigrams_header, trigramTotal))
                        append("\n")
                        learnedWords.myanmarTrigrams.take(20).forEach { (ctxKey, next, freq) ->
                            val parts = ctxKey.split("|")
                            val ctx = if (parts.size == 2) "${parts[0]} ${parts[1]}" else ctxKey
                            append("  $ctx \u2192 $next ($freq)\n")
                        }
                    }
                    append("\n")
                }

                // English section
                val hasEnglishData = learnedWords.englishWords.isNotEmpty() ||
                    learnedWords.englishBigrams.isNotEmpty() ||
                    learnedWords.englishTrigrams.isNotEmpty()
                if (hasEnglishData) {
                    val stats = learnedWords.englishStats
                    val englishCount = stats?.unigramCount ?: learnedWords.englishWords.size
                    append(getString(R.string.view_learned_words_english, englishCount))
                    append("\n")

                    // Unigrams
                    if (learnedWords.englishWords.isNotEmpty()) {
                        append(getString(R.string.ngram_unigrams_header))
                        append("\n")
                        learnedWords.englishWords.take(20).forEach { (word, freq) ->
                            append("  $word ($freq)\n")
                        }
                    }

                    // Bigrams
                    if (learnedWords.englishBigrams.isNotEmpty()) {
                        val bigramTotal = stats?.totalBigramEntries ?: learnedWords.englishBigrams.size
                        append(getString(R.string.ngram_bigrams_header, bigramTotal))
                        append("\n")
                        learnedWords.englishBigrams.take(20).forEach { (ctx, next, freq) ->
                            append("  $ctx \u2192 $next ($freq)\n")
                        }
                    }

                    // Trigrams
                    if (learnedWords.englishTrigrams.isNotEmpty()) {
                        val trigramTotal = stats?.totalTrigramEntries ?: learnedWords.englishTrigrams.size
                        append(getString(R.string.ngram_trigrams_header, trigramTotal))
                        append("\n")
                        learnedWords.englishTrigrams.take(20).forEach { (ctxKey, next, freq) ->
                            val parts = ctxKey.split("|")
                            val ctx = if (parts.size == 2) "${parts[0]} ${parts[1]}" else ctxKey
                            append("  $ctx \u2192 $next ($freq)\n")
                        }
                    }
                }
            }

            AlertDialog.Builder(context)
                .setTitle(R.string.view_learned_words_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        private fun showClearPersonalizationDialog() {
            val context = context ?: return
            AlertDialog.Builder(context)
                .setTitle(R.string.clear_personalization_title)
                .setMessage(R.string.clear_personalization_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    // Clear files on disk
                    val storage = PersonalizationStorage(context)
                    storage.clearAll()
                    val userDict = UserDictionary(context)
                    userDict.initialize()
                    userDict.clear()

                    // Signal running keyboard service to clear in-memory caches
                    PreferenceManager.getDefaultSharedPreferences(context)
                        .edit()
                        .putLong("personalization_cleared_at", System.currentTimeMillis())
                        .apply()

                    Toast.makeText(context, R.string.clear_personalization_done, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    companion object {
        @JvmStatic
        var imeManager: InputMethodManager? = null
    }
}
