package com.sanlin.mkeyboard.suggestion

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Manages suggestion engines and provides a unified interface.
 * Supports switching between word (Trie), syllable (LSTM), or combined modes.
 * Also supports English suggestions via SymSpell.
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
    private var currentMethod: SuggestionMethod = SuggestionMethod.WORD
    private var currentOrder: SuggestionOrder = SuggestionOrder.LSTM_FIRST

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
     */
    fun getSuggestions(text: String): List<Suggestion> {
        return when (currentMethod) {
            SuggestionMethod.WORD -> getWordSuggestions(text)
            SuggestionMethod.SYLLABLE -> getSyllableSuggestions(text)
            SuggestionMethod.BOTH -> getCombinedSuggestions(text)
        }
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

    private fun getCombinedSuggestions(text: String): List<Suggestion> {
        // Read current order preference
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val orderStr = prefs.getString("suggestion_order", "lstm_first") ?: "lstm_first"
        currentOrder = when (orderStr) {
            "trie_first" -> SuggestionOrder.TRIE_FIRST
            "hybrid" -> SuggestionOrder.HYBRID
            else -> SuggestionOrder.LSTM_FIRST
        }

        return when (currentOrder) {
            SuggestionOrder.LSTM_FIRST -> getCombinedLstmFirst(text)
            SuggestionOrder.TRIE_FIRST -> getCombinedTrieFirst(text)
            SuggestionOrder.HYBRID -> getCombinedHybrid(text)
        }
    }

    /**
     * LSTM first, then Trie.
     * Shows context-aware LSTM predictions first, fills remaining with Trie matches.
     */
    private fun getCombinedLstmFirst(text: String): List<Suggestion> {
        val results = mutableListOf<Suggestion>()
        val seenWords = mutableSetOf<String>()

        // First, get LSTM suggestions
        val lstmSuggestions = lstmEngine?.let { engine ->
            if (engine.isReady) engine.getSuggestions(text, MAX_SUGGESTIONS) else emptyList()
        } ?: emptyList()

        for (suggestion in lstmSuggestions) {
            if (suggestion.word !in seenWords && results.size < MAX_SUGGESTIONS) {
                results.add(suggestion)
                seenWords.add(suggestion.word)
            }
        }

        // Fill remaining with Trie suggestions
        if (results.size < MAX_SUGGESTIONS) {
            val trieSuggestions = trieEngine?.let { engine ->
                if (engine.isReady) engine.getSuggestions(text, MAX_SUGGESTIONS) else emptyList()
            } ?: emptyList()

            for (suggestion in trieSuggestions) {
                if (suggestion.word !in seenWords && results.size < MAX_SUGGESTIONS) {
                    results.add(suggestion)
                    seenWords.add(suggestion.word)
                }
            }
        }

        return results
    }

    /**
     * Trie first, then LSTM.
     * Shows dictionary matches first, fills remaining with LSTM predictions.
     */
    private fun getCombinedTrieFirst(text: String): List<Suggestion> {
        val results = mutableListOf<Suggestion>()
        val seenWords = mutableSetOf<String>()

        // First, get Trie suggestions
        val trieSuggestions = trieEngine?.let { engine ->
            if (engine.isReady) engine.getSuggestions(text, MAX_SUGGESTIONS) else emptyList()
        } ?: emptyList()

        for (suggestion in trieSuggestions) {
            if (suggestion.word !in seenWords && results.size < MAX_SUGGESTIONS) {
                results.add(suggestion)
                seenWords.add(suggestion.word)
            }
        }

        // Fill remaining with LSTM suggestions
        if (results.size < MAX_SUGGESTIONS) {
            val lstmSuggestions = lstmEngine?.let { engine ->
                if (engine.isReady) engine.getSuggestions(text, MAX_SUGGESTIONS) else emptyList()
            } ?: emptyList()

            for (suggestion in lstmSuggestions) {
                if (suggestion.word !in seenWords && results.size < MAX_SUGGESTIONS) {
                    results.add(suggestion)
                    seenWords.add(suggestion.word)
                }
            }
        }

        return results
    }

    /**
     * Hybrid: LSTM predictions validated by Trie first, then remaining LSTM, then Trie.
     * Prioritizes context-aware predictions that are also valid dictionary words.
     */
    private fun getCombinedHybrid(text: String): List<Suggestion> {
        val results = mutableListOf<Suggestion>()
        val seenWords = mutableSetOf<String>()

        // Get both sets of suggestions
        val lstmSuggestions = lstmEngine?.let { engine ->
            if (engine.isReady) engine.getSuggestions(text, MAX_SUGGESTIONS) else emptyList()
        } ?: emptyList()

        val trieSuggestions = trieEngine?.let { engine ->
            if (engine.isReady) engine.getSuggestions(text, MAX_SUGGESTIONS) else emptyList()
        } ?: emptyList()

        val trieWords = trieSuggestions.map { it.word }.toSet()

        // 1. First, add LSTM suggestions that also exist in Trie (validated + contextual)
        for (suggestion in lstmSuggestions) {
            if (suggestion.word in trieWords && suggestion.word !in seenWords && results.size < MAX_SUGGESTIONS) {
                results.add(suggestion)
                seenWords.add(suggestion.word)
            }
        }

        // 2. Then, add remaining LSTM suggestions
        for (suggestion in lstmSuggestions) {
            if (suggestion.word !in seenWords && results.size < MAX_SUGGESTIONS) {
                results.add(suggestion)
                seenWords.add(suggestion.word)
            }
        }

        // 3. Finally, fill with Trie suggestions
        for (suggestion in trieSuggestions) {
            if (suggestion.word !in seenWords && results.size < MAX_SUGGESTIONS) {
                results.add(suggestion)
                seenWords.add(suggestion.word)
            }
        }

        return results
    }

    /**
     * Get replacement length based on current method and which engine provided the suggestion.
     */
    fun getReplacementLength(text: String, suggestionWord: String): Int {
        // Check if this is a word suggestion (exists in trie results)
        val wordSuggestions = trieEngine?.getSuggestions(text, MAX_SUGGESTIONS) ?: emptyList()
        val isWordSuggestion = wordSuggestions.any { it.word == suggestionWord }

        return if (isWordSuggestion) {
            // Word suggestion - replace the matched syllables
            trieEngine?.getReplacementLength(text) ?: 0
        } else {
            // Syllable suggestion - replace incomplete syllable if any
            lstmEngine?.getReplacementLength(text) ?: 0
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
    }

    // ============ English Support (LSTM with n-gram fallback) ============

    /**
     * Initialize the English suggestion engine.
     * Tries LSTM first (if model files exist), falls back to n-gram.
     * Should be called from a background thread.
     */
    fun initializeEnglish(): Boolean {
        // Try LSTM first (preferred method)
        if (englishLstmEngine == null) {
            englishLstmEngine = EnglishLstmSuggestionEngine(context)
            if (englishLstmEngine?.initialize() == true) {
                Log.i(TAG, "English LSTM engine initialized")
                return true
            } else {
                Log.w(TAG, "English LSTM not available, trying n-gram fallback")
                englishLstmEngine = null
            }
        } else if (englishLstmEngine?.isReady == true) {
            return true
        }

        // Fall back to n-gram
        if (ngramEngine == null) {
            ngramEngine = NgramSuggestionEngine(context)
            val success = ngramEngine?.initialize() ?: false

            if (success) {
                Log.i(TAG, "English n-gram engine initialized (fallback)")
            } else {
                Log.e(TAG, "Failed to initialize English n-gram engine")
                ngramEngine = null
            }

            return success
        }

        return ngramEngine?.isReady == true
    }

    /**
     * Check if English engine is ready.
     */
    val isEnglishReady: Boolean
        get() = englishLstmEngine?.isReady == true || ngramEngine?.isReady == true

    /**
     * Get English suggestions for the given text.
     * Uses LSTM if available, otherwise n-gram.
     */
    fun getEnglishSuggestions(text: String): List<Suggestion> {
        // Try LSTM first
        val lstmEngine = englishLstmEngine
        if (lstmEngine?.isReady == true) {
            val suggestions = lstmEngine.getSuggestions(text, MAX_SUGGESTIONS)
            if (suggestions.isNotEmpty()) {
                Log.d(TAG, "English LSTM suggestions for '$text': ${suggestions.size} results")
                return suggestions
            }
        }

        // Fall back to n-gram
        val nEngine = ngramEngine
        if (nEngine?.isReady == true) {
            val suggestions = nEngine.getSuggestions(text, MAX_SUGGESTIONS)
            Log.d(TAG, "English n-gram suggestions for '$text': ${suggestions.size} results")
            return suggestions
        }

        Log.d(TAG, "No English engine available")
        return emptyList()
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
}
