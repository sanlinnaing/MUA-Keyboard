package com.sanlin.mkeyboard.suggestion

/**
 * JNI bindings for native English n-gram prediction engine.
 * Uses bigram frequencies for context-aware next word prediction.
 */
class NgramNative {

    companion object {
        init {
            System.loadLibrary("english_ngram")
        }
    }

    /**
     * Initialize the native engine.
     */
    external fun initialize(): Boolean

    /**
     * Load vocabulary from binary data.
     */
    external fun loadVocabulary(data: ByteArray): Boolean

    /**
     * Load bigrams from binary data.
     */
    external fun loadBigrams(data: ByteArray): Boolean

    /**
     * Check if engine is ready (vocabulary and bigrams loaded).
     */
    external fun isReady(): Boolean

    /**
     * Get suggestions based on text context.
     * Returns alternating word/score pairs: [word1, score1, word2, score2, ...]
     */
    external fun getSuggestions(text: String, topK: Int): Array<String>?

    /**
     * Get next word predictions based on previous word.
     * Returns alternating word/score pairs.
     */
    external fun predict(prevWord: String, topK: Int): Array<String>?

    /**
     * Release native resources.
     */
    external fun release()

    /**
     * Get vocabulary size.
     */
    external fun getVocabSize(): Int

    /**
     * Get bigram count.
     */
    external fun getBigramCount(): Int
}
