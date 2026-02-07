package com.sanlin.mkeyboard.suggestion

import android.content.Context
import android.util.Log

/**
 * English word prediction engine using n-gram model.
 * Provides context-aware next word predictions based on bigram frequencies.
 */
class NgramSuggestionEngine(private val context: Context) : SuggestionProvider {

    companion object {
        private const val TAG = "NgramSuggestionEngine"
        private const val VOCAB_FILE = "en_ngram_vocab.bin"
        private const val BIGRAM_FILE = "en_ngram_bigram.bin"
    }

    private var ngramNative: NgramNative? = null
    private var isLoaded = false

    override val isReady: Boolean
        get() = isLoaded && ngramNative?.isReady() == true

    /**
     * Initialize the n-gram engine.
     * Should be called from a background thread.
     */
    fun initialize(): Boolean {
        if (isLoaded) return true

        try {
            ngramNative = NgramNative()

            if (!ngramNative!!.initialize()) {
                Log.e(TAG, "Failed to initialize native engine")
                return false
            }

            // Load vocabulary
            val vocabData = context.assets.open(VOCAB_FILE).use { it.readBytes() }
            Log.i(TAG, "Loading vocabulary: ${vocabData.size} bytes")

            if (!ngramNative!!.loadVocabulary(vocabData)) {
                Log.e(TAG, "Failed to load vocabulary")
                return false
            }

            // Load bigrams
            val bigramData = context.assets.open(BIGRAM_FILE).use { it.readBytes() }
            Log.i(TAG, "Loading bigrams: ${bigramData.size} bytes")

            if (!ngramNative!!.loadBigrams(bigramData)) {
                Log.e(TAG, "Failed to load bigrams")
                return false
            }

            isLoaded = true
            Log.i(TAG, "N-gram engine initialized: vocab=${ngramNative!!.getVocabSize()}, bigrams=${ngramNative!!.getBigramCount()}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing n-gram engine", e)
            return false
        }
    }

    override fun getSuggestions(text: String, topK: Int): List<Suggestion> {
        if (!isReady) return emptyList()
        if (text.isEmpty()) return emptyList()

        val native = ngramNative ?: return emptyList()

        try {
            val results = native.getSuggestions(text, topK) ?: return emptyList()

            // Parse alternating word/score pairs
            val suggestions = mutableListOf<Suggestion>()
            var i = 0
            while (i + 1 < results.size) {
                val word = results[i]
                val score = results[i + 1].toIntOrNull() ?: 0

                // Match case of current word being typed
                val currentWord = extractCurrentWord(text)
                val caseMatchedWord = matchCase(word, currentWord)

                suggestions.add(Suggestion(caseMatchedWord, score))
                i += 2
            }

            return suggestions

        } catch (e: Exception) {
            Log.e(TAG, "Error getting suggestions", e)
            return emptyList()
        }
    }

    /**
     * Get next word predictions based on previous word only.
     */
    fun getNextWordPredictions(prevWord: String, topK: Int = 5): List<Suggestion> {
        if (!isReady || prevWord.isEmpty()) return emptyList()

        val native = ngramNative ?: return emptyList()

        try {
            val results = native.predict(prevWord.lowercase(), topK) ?: return emptyList()

            val suggestions = mutableListOf<Suggestion>()
            var i = 0
            while (i + 1 < results.size) {
                val word = results[i]
                val score = results[i + 1].toIntOrNull() ?: 0
                suggestions.add(Suggestion(word, score))
                i += 2
            }

            return suggestions

        } catch (e: Exception) {
            Log.e(TAG, "Error getting predictions", e)
            return emptyList()
        }
    }

    override fun getReplacementLength(text: String): Int {
        return extractCurrentWord(text).length
    }

    /**
     * Extract the current word being typed.
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
     * Match case pattern of original word.
     */
    private fun matchCase(suggestion: String, original: String): String {
        if (original.isEmpty()) return suggestion

        return when {
            original.all { it.isUpperCase() } -> suggestion.uppercase()
            original.isNotEmpty() && original[0].isUpperCase() ->
                suggestion.replaceFirstChar { it.uppercase() }
            else -> suggestion.lowercase()
        }
    }

    fun release() {
        ngramNative?.release()
        ngramNative = null
        isLoaded = false
    }
}
