package com.sanlin.mkeyboard.suggestion

import android.content.Context
import android.util.Log

/**
 * Manages suggestion engines and provides a unified interface.
 * Supports switching between word (Trie), syllable (LSTM), or combined modes.
 * Also supports English suggestions via SymSpell.
 * Integrates personalization to boost suggestions based on user history.
 */
class SuggestionManager(private val context: Context) {

    companion object {
        private const val TAG = "SuggestionManager"
        private const val MAX_SUGGESTIONS = 5
    }

    private var trieEngine: SuggestionEngine? = null
    private var lstmEngine: LstmSuggestionEngine? = null
    private var ngramEngine: NgramSuggestionEngine? = null  // N-gram based English predictions (fallback)
    private var englishLstmEngine: EnglishLstmSuggestionEngine? = null  // LSTM-based English predictions
    private var symSpellEngine: EnglishSuggestionEngine? = null  // SymSpell for spelling correction
    private var currentMethod: SuggestionMethod = SuggestionMethod.WORD
    // Personalization
    private var personalizationManager: PersonalizationManager? = null
    private var personalizationEnabled = true

    // User Dictionary (for words not in LSTM/Trie)
    private var userDictionary: UserDictionary? = null

    /**
     * Initialize the suggestion engines.
     * Should be called from a background thread.
     *
     * @param method the suggestion method to use
     * @return true if at least one engine initialized successfully
     */
    fun initialize(method: SuggestionMethod): Boolean {
        currentMethod = method
        var success = false

        // Initialize Trie engine if needed
        if (method == SuggestionMethod.WORD || method == SuggestionMethod.BOTH) {
            trieEngine = SuggestionEngine(context)
            if (trieEngine?.initialize() == true) {
                Log.i(TAG, "Trie engine initialized")
                success = true
            } else {
                Log.e(TAG, "Failed to initialize Trie engine")
            }
        }

        // Initialize LSTM engine if needed
        if (method == SuggestionMethod.SYLLABLE || method == SuggestionMethod.BOTH) {
            lstmEngine = LstmSuggestionEngine(context)
            if (lstmEngine?.initialize() == true) {
                Log.i(TAG, "LSTM engine initialized")
                success = true
            } else {
                Log.e(TAG, "Failed to initialize LSTM engine")
            }
        }

        return success
    }

    /**
     * Update the suggestion method.
     * May need to initialize engines that weren't loaded before.
     */
    fun setMethod(method: SuggestionMethod) {
        if (method == currentMethod) return
        currentMethod = method

        // Initialize engines if needed
        if ((method == SuggestionMethod.WORD || method == SuggestionMethod.BOTH)
            && trieEngine == null) {
            trieEngine = SuggestionEngine(context)
            trieEngine?.initialize()
        }

        if ((method == SuggestionMethod.SYLLABLE || method == SuggestionMethod.BOTH)
            && lstmEngine == null) {
            lstmEngine = LstmSuggestionEngine(context)
            lstmEngine?.initialize()
        }
    }

    /**
     * Check if the manager is ready to provide suggestions.
     */
    val isReady: Boolean
        get() = when (currentMethod) {
            SuggestionMethod.WORD -> trieEngine?.isReady == true
            SuggestionMethod.SYLLABLE -> lstmEngine?.isReady == true
            SuggestionMethod.BOTH -> trieEngine?.isReady == true || lstmEngine?.isReady == true
        }

    /**
     * Get suggestions based on the current method.
     * Merges results from LSTM/Trie engines, User Dictionary, and personalization boost.
     */
    fun getSuggestions(text: String): List<Suggestion> {
        val baseSuggestions = when (currentMethod) {
            SuggestionMethod.WORD -> getWordSuggestions(text)
            SuggestionMethod.SYLLABLE -> getSyllableSuggestions(text)
            SuggestionMethod.BOTH -> getLstmGuidedTrieSuggestions(text)
        }

        // Merge with User Dictionary suggestions
        val mergedSuggestions = mergeWithUserDictionary(baseSuggestions, text)

        return if (personalizationEnabled && personalizationManager?.isReady() == true) {
            personalizationManager?.boostSuggestions(mergedSuggestions, text, isMyanmarr = true)
                ?: mergedSuggestions
        } else {
            mergedSuggestions
        }
    }

    /**
     * Merge base suggestions with User Dictionary matches.
     * User Dictionary words are added if not already present in base suggestions.
     */
    private fun mergeWithUserDictionary(baseSuggestions: List<Suggestion>, text: String): List<Suggestion> {
        val dict = userDictionary ?: return baseSuggestions
        if (!dict.isReady) return baseSuggestions

        // Extract context and prefix from current text
        val syllables = SyllableBreaker.breakSyllables(text).filter { it.isNotBlank() && it != " " }
        if (syllables.isEmpty()) return baseSuggestions

        // The last few syllables form the current prefix (what user is typing now)
        // Context is what came before the current word
        // Since we don't have composing state here, use the last syllable as prefix
        // and everything before as context
        val prefixSyllables = listOf(syllables.last())
        val contextSyllables = if (syllables.size > 1) syllables.dropLast(1) else emptyList()

        val userSuggestions = dict.getSuggestions(contextSyllables, prefixSyllables, MAX_SUGGESTIONS)
        if (userSuggestions.isEmpty()) return baseSuggestions

        // Merge: keep base suggestions first, then add unique user dictionary entries
        val results = baseSuggestions.toMutableList()
        val seenWords = baseSuggestions.map { it.word }.toMutableSet()

        for (suggestion in userSuggestions) {
            if (suggestion.word !in seenWords && results.size < MAX_SUGGESTIONS) {
                results.add(Suggestion(suggestion.word, suggestion.frequency, fromUserDict = true))
                seenWords.add(suggestion.word)
            }
        }

        return results.take(MAX_SUGGESTIONS)
    }

    private fun getWordSuggestions(text: String): List<Suggestion> {
        val engine = trieEngine ?: return emptyList()
        if (!engine.isReady) return emptyList()
        return engine.getSuggestions(text, MAX_SUGGESTIONS)
    }

    private fun getSyllableSuggestions(text: String): List<Suggestion> {
        val engine = lstmEngine ?: return emptyList()
        if (!engine.isReady) return emptyList()
        return engine.getSuggestions(text, MAX_SUGGESTIONS)
    }

    /**
     * LSTM-guided Trie pipeline:
     * 1. LSTM predicts next syllable(s) to extend the composing prefix
     * 2. Extended prefix is searched in both Trie and User Dictionary
     * 3. Direct Trie + User Dictionary search as fallback
     * 4. Results sorted by frequency
     */
    private fun getLstmGuidedTrieSuggestions(text: String): List<Suggestion> {
        val trie = trieEngine ?: return getSyllableSuggestions(text)
        if (!trie.isReady) return getSyllableSuggestions(text)
        val lstm = lstmEngine
        val userDict = userDictionary

        // 1. Get composing syllables from text (same logic as Trie)
        val myanmarText = SyllableBreaker.extractMyanmarSuffix(text)
        if (myanmarText.isEmpty()) return emptyList()
        val composingSyllables = SyllableBreaker.lastSyllables(myanmarText, 3)
            .filter { it.isNotBlank() }
        if (composingSyllables.isEmpty()) return emptyList()

        // Extract context syllables for User Dictionary (syllables before composing)
        val allMyanmarSyllables = SyllableBreaker.breakSyllables(myanmarText)
            .filter { it.isNotBlank() && it != " " }
        val contextSyllables = if (allMyanmarSyllables.size > composingSyllables.size) {
            allMyanmarSyllables.dropLast(composingSyllables.size).takeLast(3)
        } else {
            emptyList()
        }

        val results = mutableListOf<Suggestion>()
        val seenWords = mutableSetOf<String>()

        // 2. LSTM-guided search: predict next syllable, extend prefix, search Trie + User Dict
        if (lstm?.isReady == true) {
            val predictions = lstm.predictNextSyllables(text, topN = 3)

            for ((predictedSyllable, _) in predictions) {
                val lastSyllable = composingSyllables.last()
                val extendedSyllables = if (
                    SyllableBreaker.isConsonantOnly(lastSyllable) && SyllableBreaker.isTail(predictedSyllable) ||
                    SyllableBreaker.isIncompleteWithVowelE(lastSyllable) && SyllableBreaker.isTailAfterVowelE(predictedSyllable)
                ) {
                    // Combine last incomplete syllable with predicted tail
                    composingSyllables.dropLast(1) + listOf(lastSyllable + predictedSyllable)
                } else {
                    // Append predicted syllable as new syllable
                    composingSyllables + listOf(predictedSyllable)
                }

                // Search Trie with extended syllables
                val trieSuggestions = trie.getSuggestionsBySyllables(extendedSyllables, MAX_SUGGESTIONS)
                for (s in trieSuggestions) {
                    if (s.word !in seenWords && results.size < MAX_SUGGESTIONS * 2) {
                        results.add(s)
                        seenWords.add(s.word)
                    }
                }

                // Search User Dictionary with extended syllables
                if (userDict?.isReady == true) {
                    val userSuggestions = userDict.getSuggestions(contextSyllables, extendedSyllables, MAX_SUGGESTIONS)
                    for (s in userSuggestions) {
                        if (s.word !in seenWords && results.size < MAX_SUGGESTIONS * 2) {
                            results.add(Suggestion(s.word, s.frequency, fromUserDict = true))
                            seenWords.add(s.word)
                        }
                    }
                }
            }
        }

        // 3. Direct Trie search (fallback / fill remaining)
        val directTrieSuggestions = trie.getSuggestionsBySyllables(composingSyllables, MAX_SUGGESTIONS)
        for (s in directTrieSuggestions) {
            if (s.word !in seenWords) {
                results.add(s)
                seenWords.add(s.word)
            }
        }

        // 4. Direct User Dictionary search (fallback / fill remaining)
        if (userDict?.isReady == true) {
            val directUserSuggestions = userDict.getSuggestions(contextSyllables, composingSyllables, MAX_SUGGESTIONS)
            for (s in directUserSuggestions) {
                if (s.word !in seenWords) {
                    results.add(Suggestion(s.word, s.frequency, fromUserDict = true))
                    seenWords.add(s.word)
                }
            }
        }

        // 5. Sort by frequency, take top N
        return results.sortedByDescending { it.frequency }.take(MAX_SUGGESTIONS)
    }

    /**
     * Get replacement length based on current method and which engine provided the suggestion.
     */
    fun getReplacementLength(text: String, suggestionWord: String): Int {
        return when (currentMethod) {
            SuggestionMethod.BOTH -> {
                // LSTM-guided pipeline: all suggestions (Trie + User Dict) are matched
                // against composing syllables, so always replace the composing syllables
                trieEngine?.getReplacementLength(text) ?: 0
            }
            SuggestionMethod.WORD -> {
                trieEngine?.getReplacementLength(text) ?: 0
            }
            SuggestionMethod.SYLLABLE -> {
                lstmEngine?.getReplacementLength(text) ?: 0
            }
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        trieEngine?.release()
        lstmEngine?.release()
        ngramEngine?.release()
        englishLstmEngine?.release()
        trieEngine = null
        lstmEngine = null
        ngramEngine = null
        englishLstmEngine = null

        // Save personalization before release
        personalizationManager?.save()
        personalizationManager = null

        // Save user dictionary before release
        userDictionary?.save()
        userDictionary = null
    }

    // ============ User Dictionary ============

    /**
     * Initialize the User Dictionary.
     * Should be called from a background thread.
     */
    fun initializeUserDictionary() {
        userDictionary = UserDictionary(context)
        userDictionary?.initialize()
        Log.i(TAG, "User Dictionary initialized")
    }

    /**
     * Check if a syllable is in the LSTM vocabulary.
     * @return true if known, false if unknown
     */
    fun isKnownSyllable(syllable: String): Boolean {
        return lstmEngine?.isKnownSyllable(syllable) ?: true  // Default to known if engine not ready
    }

    /**
     * Check if a word (as syllables) contains any unknown syllables.
     * @return true if at least one syllable is NOT in the LSTM vocabulary
     */
    fun hasUnknownSyllables(syllables: List<String>): Boolean {
        return lstmEngine?.hasUnknownSyllables(syllables) ?: false
    }

    /**
     * Record a composed word to the User Dictionary if it's not already known.
     * Records if the word has unknown syllables (not in LSTM vocab)
     * OR if the word is not found in the Trie dictionary.
     * Called from MuaKeyboardService when a word boundary is detected.
     *
     * @param contextSyllables the 2+ syllables before this word
     * @param composedWord the full word that was composed
     */
    fun recordComposedWord(contextSyllables: List<String>, composedWord: String) {
        if (composedWord.isBlank()) return

        val wordSyllables = SyllableBreaker.breakSyllables(composedWord)
            .filter { it.isNotBlank() && it != " " }

        if (wordSyllables.isEmpty()) return

        // Record if word has unknown syllables OR is not in Trie dictionary
        val hasUnknown = hasUnknownSyllables(wordSyllables)
        val inTrie = isWordInTrie(composedWord, wordSyllables)

        if (!hasUnknown && inTrie) {
            Log.d(TAG, "recordComposedWord: word known in both LSTM and Trie, skipping '$composedWord'")
            return
        }

        Log.d(TAG, "recordComposedWord: recording '$composedWord' (unknown=$hasUnknown, inTrie=$inTrie, context=$contextSyllables)")
        userDictionary?.recordWord(contextSyllables, composedWord, wordSyllables)
    }

    /**
     * Check if a word exists in the Trie dictionary.
     */
    private fun isWordInTrie(word: String, syllables: List<String>): Boolean {
        val trie = trieEngine ?: return false
        if (!trie.isReady) return false
        val suggestions = trie.getSuggestionsBySyllables(syllables, 20)
        return suggestions.any { it.word == word }
    }

    /**
     * Check if an English word is known in any engine vocabulary.
     * @return true if known in English LSTM or SymSpell, false if unknown
     */
    fun isKnownEnglishWord(word: String): Boolean {
        if (word.isBlank()) return true

        // Check English LSTM vocabulary
        if (englishLstmEngine?.isKnownWord(word) == true) return true

        // Check SymSpell dictionary (if correction returns null for exact match, it's known)
        val correction = symSpellEngine?.getCorrection(word)
        if (correction == null) {
            // getCorrection returns null when word IS in dictionary (no correction needed)
            // But also returns null when word is not found at all
            // We check LSTM first which is more reliable
            return false
        }

        return false
    }

    /**
     * Record an English word to the User Dictionary if it's unknown.
     * Called from MuaKeyboardService when space is pressed after an English word.
     *
     * @param contextWords the preceding words for context
     * @param word the English word to record
     */
    fun recordEnglishWord(contextWords: List<String>, word: String) {
        if (word.isBlank()) return

        // Skip short words (likely abbreviations or typos)
        if (word.length < 2) return

        // Skip if word is known
        if (isKnownEnglishWord(word)) {
            Log.d(TAG, "recordEnglishWord: known word, skipping '$word'")
            return
        }

        Log.d(TAG, "recordEnglishWord: unknown word, recording '$word' (context=$contextWords)")
        // For English, we store the word as a single "syllable" in the trie
        userDictionary?.recordWord(contextWords, word, listOf(word.lowercase()))
    }

    /**
     * Save User Dictionary to disk.
     */
    fun saveUserDictionary() {
        userDictionary?.save()
    }

    /**
     * Clear User Dictionary.
     */
    fun clearUserDictionary() {
        userDictionary?.clear()
    }

    /**
     * Get User Dictionary for display purposes.
     */
    fun getUserDictionary(): UserDictionary? = userDictionary

    /**
     * Get learned words from in-memory personalization caches (no disk I/O).
     * Used by settings UI via the service's static instance.
     */
    fun getLearnedWordsFromMemory(maxPerLanguage: Int): PersonalizationStorage.LearnedWords? {
        return personalizationManager?.getLearnedWords(maxPerLanguage)
    }

    // ============ Personalization ============

    /**
     * Initialize the personalization manager.
     * Should be called from a background thread.
     */
    fun initializePersonalization() {
        personalizationManager = PersonalizationManager(context)
        personalizationManager?.initialize()
        Log.i(TAG, "Personalization initialized")
    }

    /**
     * Set whether personalization is enabled.
     */
    fun setPersonalizationEnabled(enabled: Boolean) {
        personalizationEnabled = enabled
    }

    /**
     * Check if personalization is enabled.
     */
    fun isPersonalizationEnabled(): Boolean = personalizationEnabled

    /**
     * Record user input for personalization learning.
     * @param text the text to record
     * @param isMyanmarr true for Myanmar, false for English
     */
    fun recordUserInput(text: String, isMyanmarr: Boolean) {
        if (!personalizationEnabled) {
            Log.d(TAG, "recordUserInput: personalization disabled")
            return
        }

        if (personalizationManager == null) {
            Log.w(TAG, "recordUserInput: personalizationManager is null")
            return
        }

        Log.d(TAG, "recordUserInput: text='$text', isMyanmarr=$isMyanmarr")
        if (isMyanmarr) {
            personalizationManager?.recordMyanmarInput(text)
        } else {
            personalizationManager?.recordEnglishInput(text)
        }
    }

    /**
     * Save personalization data to storage.
     */
    fun savePersonalization() {
        personalizationManager?.save()
    }

    /**
     * Clear personalization history.
     */
    fun clearPersonalizationHistory() {
        personalizationManager?.clearHistory()
    }

    // ============ English Support (LSTM with n-gram fallback) ============

    /**
     * Initialize the English suggestion engine.
     * Tries LSTM first (if model files exist), falls back to n-gram.
     * Also initializes SymSpell for spelling correction.
     * Should be called from a background thread.
     */
    fun initializeEnglish(): Boolean {
        var success = false

        // Try LSTM first (preferred method for suggestions)
        if (englishLstmEngine == null) {
            englishLstmEngine = EnglishLstmSuggestionEngine(context)
            if (englishLstmEngine?.initialize() == true) {
                Log.i(TAG, "English LSTM engine initialized")
                success = true
            } else {
                Log.w(TAG, "English LSTM not available, trying n-gram fallback")
                englishLstmEngine = null
            }
        } else if (englishLstmEngine?.isReady == true) {
            success = true
        }

        // Fall back to n-gram if LSTM not available
        if (!success && ngramEngine == null) {
            ngramEngine = NgramSuggestionEngine(context)
            success = ngramEngine?.initialize() ?: false

            if (success) {
                Log.i(TAG, "English n-gram engine initialized (fallback)")
            } else {
                Log.e(TAG, "Failed to initialize English n-gram engine")
                ngramEngine = null
            }
        }

        // Initialize SymSpell for spelling correction (independent of suggestions)
        if (symSpellEngine == null) {
            symSpellEngine = EnglishSuggestionEngine(context)
            if (symSpellEngine?.initialize() == true) {
                Log.i(TAG, "SymSpell engine initialized for spelling correction")
            } else {
                Log.w(TAG, "Failed to initialize SymSpell engine")
                symSpellEngine = null
            }
        }

        return success || ngramEngine?.isReady == true
    }

    /**
     * Check if English engine is ready.
     */
    val isEnglishReady: Boolean
        get() = englishLstmEngine?.isReady == true || ngramEngine?.isReady == true

    /**
     * Get English suggestions for the given text.
     * Searches LSTM/n-gram and User Dictionary together, then applies personalization.
     */
    fun getEnglishSuggestions(text: String): List<Suggestion> {
        val results = mutableListOf<Suggestion>()
        val seenWords = mutableSetOf<String>()

        // 1. LSTM suggestions
        val lstmEngine = englishLstmEngine
        if (lstmEngine?.isReady == true) {
            val suggestions = lstmEngine.getSuggestions(text, MAX_SUGGESTIONS)
            for (s in suggestions) {
                if (s.word.lowercase() !in seenWords) {
                    results.add(s)
                    seenWords.add(s.word.lowercase())
                }
            }
        }

        // 2. N-gram fallback if no LSTM results
        if (results.isEmpty()) {
            val nEngine = ngramEngine
            if (nEngine?.isReady == true) {
                val suggestions = nEngine.getSuggestions(text, MAX_SUGGESTIONS)
                for (s in suggestions) {
                    if (s.word.lowercase() !in seenWords) {
                        results.add(s)
                        seenWords.add(s.word.lowercase())
                    }
                }
            }
        }

        // 3. User Dictionary search with same context + prefix
        val userDict = userDictionary
        if (userDict?.isReady == true) {
            val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isNotEmpty()) {
                val currentPrefix = words.last().lowercase()
                val contextWords = if (words.size > 1) words.dropLast(1).map { it.lowercase() } else emptyList()

                val userSuggestions = userDict.getSuggestions(contextWords, listOf(currentPrefix), MAX_SUGGESTIONS)
                for (s in userSuggestions) {
                    if (s.word.lowercase() !in seenWords) {
                        results.add(Suggestion(s.word, s.frequency, fromUserDict = true))
                        seenWords.add(s.word.lowercase())
                    }
                }
            }
        }

        // 4. Sort by frequency, take top N
        val baseSuggestions = results.sortedByDescending { it.frequency }.take(MAX_SUGGESTIONS)

        // 5. Apply personalization boost
        return if (personalizationEnabled && personalizationManager?.isReady() == true) {
            personalizationManager?.boostSuggestions(baseSuggestions, text, isMyanmarr = false)
                ?: baseSuggestions
        } else {
            baseSuggestions
        }
    }

    /**
     * Get replacement length for English text (current word length).
     */
    fun getEnglishReplacementLength(text: String): Int {
        // Use whichever engine is active
        if (englishLstmEngine?.isReady == true) {
            return englishLstmEngine?.getReplacementLength(text) ?: 0
        }
        return ngramEngine?.getReplacementLength(text) ?: 0
    }

    /**
     * Get spelling correction for a word using SymSpell.
     * Returns null if no correction needed or engine not ready.
     */
    fun getSpellingCorrection(word: String): String? {
        return symSpellEngine?.getCorrection(word)
    }

    /**
     * Check if spelling correction is available.
     */
    val isSpellingCorrectionReady: Boolean
        get() = symSpellEngine?.isReady == true
}
