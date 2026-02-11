package com.sanlin.mkeyboard.suggestion

/**
 * Represents a word suggestion from the dictionary.
 *
 * @property word the suggested word
 * @property frequency the frequency/score of this word (higher = more common)
 * @property boosted true if personalization boosted this suggestion's score
 * @property fromUserDict true if this suggestion came from the user dictionary
 */
data class Suggestion(
    val word: String,
    val frequency: Int,
    val boosted: Boolean = false,
    val fromUserDict: Boolean = false
)
