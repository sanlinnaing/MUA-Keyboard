package com.sanlin.mkeyboard.suggestion

import android.content.Context
import android.util.Log
import java.io.IOException

/**
 * High-level suggestion engine that combines syllable breaking with trie lookups.
 * This is the main entry point for getting word suggestions.
 */
class SuggestionEngine(private val context: Context) {

    companion object {
        private const val TAG = "SuggestionEngine"
        private const val MODEL_FILE = "myanmar_dict_trie.bin"
        private const val MAX_CONTEXT_SYLLABLES = 3
    }

    private val trieNative = TrieNative()
    private var isLoaded = false

    /**
     * Initialize the suggestion engine by loading the dictionary model.
     * This should be called from a background thread as it may take some time.
     * @return true if initialization was successful
     */
    fun initialize(): Boolean {
        if (isLoaded) return true

        try {
            if (!trieNative.initialize()) {
                Log.e(TAG, "Failed to create trie instance")
                return false
            }

            // Load model from assets
            val data = context.assets.open(MODEL_FILE).use { inputStream ->
                inputStream.readBytes()
            }

            if (!trieNative.loadFromMemory(data)) {
                Log.e(TAG, "Failed to load trie model from assets")
                return false
            }

            isLoaded = true
            Log.i(TAG, "Suggestion engine initialized successfully")
            return true

        } catch (e: IOException) {
            Log.e(TAG, "Error loading dictionary model", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error initializing suggestion engine", e)
            return false
        }
    }

    /**
     * Check if the engine is ready to provide suggestions.
     */
    val isReady: Boolean
        get() = isLoaded && trieNative.isInitialized

    /**
     * Get word suggestions based on the current input text.
     *
     * @param text the text before cursor (typically last 50-100 characters)
     * @param topK maximum number of suggestions to return
     * @return list of suggestions sorted by relevance
     */
    fun getSuggestions(text: String, topK: Int = 5): List<Suggestion> {
        if (!isReady) return emptyList()
        if (text.isEmpty()) return emptyList()

        // Extract Myanmar portion from the end
        val myanmarText = SyllableBreaker.extractMyanmarSuffix(text)
        if (myanmarText.isEmpty()) return emptyList()

        // Get last N syllables for context
        val syllables = SyllableBreaker.lastSyllables(myanmarText, MAX_CONTEXT_SYLLABLES)
        if (syllables.isEmpty()) return emptyList()

        // Filter out whitespace-only syllables
        val filteredSyllables = syllables.filter { it.isNotBlank() }
        if (filteredSyllables.isEmpty()) return emptyList()

        return trieNative.suggest(filteredSyllables.toTypedArray(), topK)
    }

    /**
     * Calculate the number of characters to delete when replacing with a suggestion.
     * This accounts for the syllables that were used for matching.
     * Only considers text after the last punctuation.
     *
     * @param text the text before cursor
     * @return number of characters to delete
     */
    fun getReplacementLength(text: String): Int {
        if (text.isEmpty()) return 0

        // extractMyanmarSuffix already handles punctuation boundaries
        val myanmarText = SyllableBreaker.extractMyanmarSuffix(text)
        if (myanmarText.isEmpty()) return 0

        // Get the syllables that were used for matching
        val syllables = SyllableBreaker.lastSyllables(myanmarText, MAX_CONTEXT_SYLLABLES)
        return syllables.sumOf { it.length }
    }

    /**
     * Release native resources.
     * Should be called when the engine is no longer needed.
     */
    fun release() {
        trieNative.release()
        isLoaded = false
    }
}
