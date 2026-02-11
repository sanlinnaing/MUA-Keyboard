package com.sanlin.mkeyboard.suggestion

import android.content.Context
import android.util.Log

/**
 * Manages personalization for suggestions.
 * Learns from user typing patterns and boosts suggestion scores based on history.
 * Uses syllable-based n-grams for Myanmar and word-based n-grams for English.
 */
class PersonalizationManager(private val context: Context) {

    companion object {
        private const val TAG = "PersonalizationManager"
        private const val SAVE_INTERVAL = 15  // Save periodically (settings reads from memory now)
        private const val PERSONAL_WEIGHT = 0.3f  // How much personalization affects ranking
    }

    private var myanmarCache = UserNgramCache()
    private var englishCache = UserNgramCache()
    private val storage = PersonalizationStorage(context)

    private var myanmarInputCount = 0
    private var englishInputCount = 0
    private var isInitialized = false

    /**
     * Initialize the personalization manager.
     * Loads cached data from storage. Should be called from a background thread.
     */
    fun initialize() {
        load()
        isInitialized = true
        Log.i(TAG, "Personalization initialized - Myanmar: ${myanmarCache.size()}, English: ${englishCache.size()}")
    }

    /**
     * Record Myanmar input for learning.
     * Tokenizes text into syllables and updates the n-gram cache.
     * @param text the Myanmar text to record
     */
    fun recordMyanmarInput(text: String) {
        if (text.isBlank()) return

        // Tokenize into syllables using SyllableBreaker
        // Filter out pure digit tokens (English 0-9 and Myanmar ၀-၉)
        val syllables = SyllableBreaker.breakSyllables(text)
            .filter { it.isNotBlank() && it != " " && !it.all { c -> c.isDigit() || c in '\u1040'..'\u1049' } }

        if (syllables.isEmpty()) {
            Log.d(TAG, "recordMyanmarInput: no syllables from '$text'")
            return
        }

        Log.d(TAG, "recordMyanmarInput: recording latest from ${syllables.size} syllables: $syllables")
        myanmarCache.recordLatest(syllables)
        myanmarInputCount++

        // Periodic save
        if (myanmarInputCount >= SAVE_INTERVAL) {
            saveMyanmarAsync()
            myanmarInputCount = 0
        }
    }

    /**
     * Record English input for learning.
     * Tokenizes text into words and updates the n-gram cache.
     * @param text the English text to record
     */
    fun recordEnglishInput(text: String) {
        if (text.isBlank()) return

        // Tokenize into words
        val words = text.lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it.all { c -> c.isLetter() } }

        if (words.isEmpty()) {
            Log.d(TAG, "recordEnglishInput: no words from '$text'")
            return
        }

        Log.d(TAG, "recordEnglishInput: recording latest from ${words.size} words: $words")
        englishCache.recordLatest(words)
        englishInputCount++

        // Periodic save
        if (englishInputCount >= SAVE_INTERVAL) {
            saveEnglishAsync()
            englishInputCount = 0
        }
    }

    /**
     * Boost suggestion scores based on user history.
     * @param suggestions base suggestions from the engine
     * @param contextText the text before cursor for context extraction
     * @param isMyanmarr true for Myanmar, false for English
     * @return suggestions with boosted scores, re-sorted by score
     */
    fun boostSuggestions(
        suggestions: List<Suggestion>,
        contextText: String,
        isMyanmarr: Boolean
    ): List<Suggestion> {
        if (suggestions.isEmpty()) return suggestions

        val cache = if (isMyanmarr) myanmarCache else englishCache
        if (cache.isEmpty()) return suggestions

        // Extract context tokens
        val context = if (isMyanmarr) {
            extractMyanmarContext(contextText)
        } else {
            extractEnglishContext(contextText)
        }

        // Apply boost to each suggestion
        val boostedSuggestions = suggestions.map { suggestion ->
            val boost = cache.getBoost(context, suggestion.word)
            val boostedScore = blendScore(suggestion.frequency, boost)
            Suggestion(
                word = suggestion.word,
                frequency = boostedScore,
                boosted = boost > 0f,
                fromUserDict = suggestion.fromUserDict
            )
        }

        // Re-sort by boosted score (descending)
        return boostedSuggestions.sortedByDescending { it.frequency }
    }

    /**
     * Blend base score with personalization boost.
     */
    private fun blendScore(baseScore: Int, boost: Float): Int {
        val blendedScore = baseScore * (1 + PERSONAL_WEIGHT * boost)
        return blendedScore.toInt()
    }

    /**
     * Extract Myanmar context (last 2 syllables for trigram matching).
     */
    private fun extractMyanmarContext(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val syllables = SyllableBreaker.breakSyllables(text)
            .filter { it.isNotBlank() && it != " " }

        return syllables.takeLast(2)
    }

    /**
     * Extract English context (last 2 words for trigram matching).
     */
    private fun extractEnglishContext(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val words = text.lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it.all { c -> c.isLetter() } }

        return words.takeLast(2)
    }

    /**
     * Save both caches to storage.
     * Should be called from a background thread or on lifecycle events.
     */
    fun save() {
        if (myanmarCache.size() > 0) {
            storage.saveMyanmarCache(myanmarCache)
        }
        if (englishCache.size() > 0) {
            storage.saveEnglishCache(englishCache)
        }
        Log.d(TAG, "Saved personalization data")
    }

    /**
     * Load caches from storage.
     * Should be called from a background thread.
     */
    fun load() {
        storage.loadMyanmarCache()?.let { loaded ->
            myanmarCache = loaded
        }
        storage.loadEnglishCache()?.let { loaded ->
            englishCache = loaded
        }
    }

    /**
     * Clear all personalization history.
     */
    fun clearHistory() {
        myanmarCache.clear()
        englishCache.clear()
        storage.clearAll()
        myanmarInputCount = 0
        englishInputCount = 0
        Log.i(TAG, "Personalization history cleared")
    }

    /**
     * Get learned words from in-memory caches (no disk I/O).
     * Used by settings UI to show real-time data.
     */
    fun getLearnedWords(maxPerLanguage: Int): PersonalizationStorage.LearnedWords {
        return PersonalizationStorage.LearnedWords(
            myanmarWords = myanmarCache.getTopUnigrams(maxPerLanguage),
            englishWords = englishCache.getTopUnigrams(maxPerLanguage),
            myanmarBigrams = myanmarCache.getTopBigrams(maxPerLanguage),
            englishBigrams = englishCache.getTopBigrams(maxPerLanguage),
            myanmarTrigrams = myanmarCache.getTopTrigrams(maxPerLanguage),
            englishTrigrams = englishCache.getTopTrigrams(maxPerLanguage),
            myanmarStats = myanmarCache.getStats(),
            englishStats = englishCache.getStats()
        )
    }

    /**
     * Check if personalization is initialized.
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Get stats for debugging.
     */
    fun getStats(): String {
        return "Myanmar: ${myanmarCache.size()} entries, English: ${englishCache.size()} entries"
    }

    private fun saveMyanmarAsync() {
        Thread {
            storage.saveMyanmarCache(myanmarCache)
        }.start()
    }

    private fun saveEnglishAsync() {
        Thread {
            storage.saveEnglishCache(englishCache)
        }.start()
    }
}
