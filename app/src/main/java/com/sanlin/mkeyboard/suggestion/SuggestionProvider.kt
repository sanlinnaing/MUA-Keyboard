package com.sanlin.mkeyboard.suggestion

/**
 * Interface for suggestion providers.
 * Allows switching between different suggestion engines (Trie, LSTM, etc.)
 */
interface SuggestionProvider {
    /**
     * Check if the provider is ready to give suggestions.
     */
    val isReady: Boolean

    /**
     * Get suggestions based on input text.
     *
     * @param text the text before cursor
     * @param topK maximum number of suggestions
     * @return list of suggestions
     */
    fun getSuggestions(text: String, topK: Int = 5): List<Suggestion>

    /**
     * Get the number of characters to delete when committing a suggestion.
     *
     * @param text the text before cursor
     * @return number of characters to delete
     */
    fun getReplacementLength(text: String): Int
}

/**
 * Suggestion method types.
 */
enum class SuggestionMethod {
    WORD,      // Patricia Trie - word suggestions
    SYLLABLE,  // LSTM - next syllable predictions
    BOTH       // Combined - words first, syllables as supplement
}

