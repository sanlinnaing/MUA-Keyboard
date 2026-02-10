package com.sanlin.mkeyboard.suggestion

import android.content.Context
import android.util.Log
import com.darkrockstudios.symspellkt.api.SpellChecker
import com.darkrockstudios.symspellkt.common.SpellCheckSettings
import com.darkrockstudios.symspellkt.common.Verbosity
import com.darkrockstudios.symspellkt.impl.SymSpell

/**
 * English word suggestion engine using SymSpell algorithm.
 * Provides word completions and spelling corrections.
 */
class EnglishSuggestionEngine(private val context: Context) : SuggestionProvider {

    companion object {
        private const val TAG = "EnglishSuggestionEngine"
        private const val DICTIONARY_FILE = "en_word_freq.txt"
        private const val MAX_EDIT_DISTANCE = 2.0
        private const val PREFIX_LENGTH = 7
    }

    private var spellChecker: SpellChecker? = null
    private var isLoaded = false

    override val isReady: Boolean
        get() = isLoaded && spellChecker != null

    /**
     * Initialize the SymSpell engine with word frequency dictionary.
     * Should be called from a background thread.
     */
    fun initialize(): Boolean {
        if (isLoaded) return true

        try {
            val settings = SpellCheckSettings(
                maxEditDistance = MAX_EDIT_DISTANCE,
                prefixLength = PREFIX_LENGTH
            )
            spellChecker = SymSpell(spellCheckSettings = settings)

            // Load dictionary from assets
            // Limit to top 50K words for faster loading and memory efficiency
            var wordCount = 0
            val maxWords = 50000
            context.assets.open(DICTIONARY_FILE).bufferedReader().useLines { lines ->
                lines.take(maxWords).forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size >= 2) {
                        val word = parts[0].lowercase()
                        // Normalize large frequencies to avoid overflow
                        // Use log scale: log10(freq) * 1000 to get reasonable range
                        val rawFreq = parts[1].toLongOrNull() ?: 1L
                        val normalizedFreq = (kotlin.math.log10(rawFreq.toDouble() + 1) * 1000).toInt()
                            .coerceAtLeast(1)
                        spellChecker?.createDictionaryEntry(word, normalizedFreq)
                        wordCount++
                    }
                }
            }

            isLoaded = true
            Log.i(TAG, "English dictionary loaded: $wordCount words")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize English engine: ${e.message}", e)
            return false
        }
    }

    /**
     * Debug helper to check engine state
     */
    fun debugState(): String {
        return "EnglishEngine: isLoaded=$isLoaded, spellChecker=${spellChecker != null}"
    }

    override fun getSuggestions(text: String, topK: Int): List<Suggestion> {
        if (!isReady) return emptyList()

        val currentWord = extractCurrentWord(text)
        if (currentWord.isEmpty() || currentWord.length < 2) return emptyList()

        val results = mutableListOf<Suggestion>()
        val seenWords = mutableSetOf<String>()

        try {
            // Get lookup suggestions (spelling corrections + completions)
            val suggestions = spellChecker?.lookup(
                currentWord.lowercase(),
                Verbosity.Closest,
                MAX_EDIT_DISTANCE
            ) ?: emptyList()

            for (suggestion in suggestions) {
                val word = matchCase(suggestion.term, currentWord)
                if (word !in seenWords && results.size < topK) {
                    // Convert frequency to score (log scale for better distribution)
                    val score = (kotlin.math.log10(suggestion.frequency.toDouble() + 1) * 100).toInt()
                        .coerceAtLeast(1)
                    results.add(Suggestion(word, score))
                    seenWords.add(word)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting suggestions", e)
        }

        return results.take(topK)
    }

    /**
     * Get the best spelling correction for a word.
     * Returns null if no correction needed or found.
     */
    fun getCorrection(word: String): String? {
        if (!isReady || word.isEmpty()) return null

        try {
            // Use Closest verbosity to get the best edit-distance match
            val suggestions = spellChecker?.lookup(
                word.lowercase(),
                Verbosity.Closest,
                MAX_EDIT_DISTANCE
            ) ?: return null

            Log.d(TAG, "Spelling lookup for '$word': ${suggestions.size} suggestions")

            val topSuggestion = suggestions.firstOrNull() ?: return null

            Log.d(TAG, "Top suggestion: '${topSuggestion.term}' (frequency: ${topSuggestion.frequency})")

            // Only suggest correction if it's different from input
            if (topSuggestion.term.lowercase() != word.lowercase()) {
                return matchCase(topSuggestion.term, word)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting correction", e)
        }

        return null
    }

    override fun getReplacementLength(text: String): Int {
        return extractCurrentWord(text).length
    }

    /**
     * Extract the current word being typed (from last space or start).
     */
    private fun extractCurrentWord(text: String): String {
        if (text.isEmpty()) return ""

        val lastSpace = text.lastIndexOf(' ')
        return if (lastSpace >= 0) {
            text.substring(lastSpace + 1)
        } else {
            text
        }
    }

    /**
     * Match the case pattern of the original word.
     */
    private fun matchCase(suggestion: String, original: String): String {
        if (original.isEmpty()) return suggestion

        return when {
            // All uppercase
            original.all { it.isUpperCase() } -> suggestion.uppercase()
            // First letter uppercase (capitalized)
            original[0].isUpperCase() -> suggestion.replaceFirstChar { it.uppercase() }
            // All lowercase
            else -> suggestion.lowercase()
        }
    }

    fun release() {
        spellChecker = null
        isLoaded = false
    }
}
