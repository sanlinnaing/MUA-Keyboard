package com.sanlin.mkeyboard.suggestion

/**
 * Represents a word suggestion from the dictionary.
 *
 * @property word the suggested word
 * @property frequency the frequency/score of this word (higher = more common)
 */
data class Suggestion(
    val word: String,
    val frequency: Int
)
