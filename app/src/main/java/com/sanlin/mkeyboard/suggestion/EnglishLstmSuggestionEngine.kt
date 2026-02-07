package com.sanlin.mkeyboard.suggestion

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * LSTM-based English word prediction engine using native C++ inference.
 * Predicts next words based on previous word context.
 *
 * Uses the same native LSTM engine as Myanmar, but with word-level tokens
 * instead of syllables.
 */
class EnglishLstmSuggestionEngine(private val context: Context) : SuggestionProvider {

    companion object {
        private const val TAG = "EnglishLstmEngine"
        private const val MODEL_FILE = "en_lstm_model.bin"
        private const val INDICES_FILE = "en_word_indices.json"
        private const val SEQUENCE_LENGTH = 5
        private const val MIN_CONFIDENCE = 0.01f

        // Words to exclude from suggestions (punctuation, special tokens)
        private val EXCLUDED_WORDS = setOf(
            "<PAD>", "<UNK>", ".", ",", "!", "?", ";", ":", "'", "\"",
            "(", ")", "[", "]", "{", "}", "-", "_", "/", "\\", "@", "#",
            "$", "%", "^", "&", "*", "+", "=", "<", ">", "|", "~", "`"
        )
    }

    private var lstmNative: LstmNative? = null
    private var wordToIndex: Map<String, Int> = emptyMap()
    private var indexToWord: Map<Int, String> = emptyMap()
    private var wordFrequencies: Map<String, Int> = emptyMap()  // For prefix completion ranking
    private var vocabSize: Int = 0
    private var isLoaded = false

    override val isReady: Boolean
        get() = isLoaded && lstmNative?.isInitialized == true

    /**
     * Initialize the LSTM engine.
     * Should be called from a background thread.
     */
    fun initialize(): Boolean {
        if (isLoaded) return true

        try {
            // Check if model files exist
            if (!assetExists(MODEL_FILE) || !assetExists(INDICES_FILE)) {
                Log.w(TAG, "English LSTM model files not found in assets")
                return false
            }

            // Load vocabulary first
            if (!loadVocabulary()) {
                Log.e(TAG, "Failed to load vocabulary")
                return false
            }

            // Initialize native engine
            lstmNative = LstmNative()
            if (!lstmNative!!.initialize()) {
                Log.e(TAG, "Failed to create native LSTM engine")
                return false
            }

            // Load vocabulary into native
            val jsonString = context.assets.open(INDICES_FILE).bufferedReader().use { it.readText() }
            if (!lstmNative!!.loadVocab(jsonString)) {
                Log.e(TAG, "Failed to load native vocabulary")
                return false
            }

            // Load model from assets
            val modelData = context.assets.open(MODEL_FILE).use { it.readBytes() }
            Log.i(TAG, "Loading model: ${modelData.size} bytes")

            if (!lstmNative!!.loadModel(modelData)) {
                Log.e(TAG, "Failed to load native model")
                return false
            }

            vocabSize = lstmNative!!.getVocabSize()
            isLoaded = true
            Log.i(TAG, "English LSTM engine initialized: vocab=$vocabSize")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing LSTM engine", e)
            return false
        }
    }

    private fun assetExists(filename: String): Boolean {
        return try {
            context.assets.open(filename).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun loadVocabulary(): Boolean {
        return try {
            val jsonString = context.assets.open(INDICES_FILE).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)

            val wordToIdx = mutableMapOf<String, Int>()
            val idxToWord = mutableMapOf<Int, String>()

            // First pass: load all words
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val word = keys.next()
                val index = jsonObject.getInt(word)
                wordToIdx[word] = index
                idxToWord[index] = word
            }

            val totalWords = wordToIdx.size

            // Second pass: calculate frequency scores (lower index = more frequent)
            val wordFreqs = mutableMapOf<String, Int>()
            for ((word, index) in wordToIdx) {
                if (index >= 2) {  // Skip special tokens <PAD>, <UNK>
                    wordFreqs[word] = totalWords - index
                }
            }

            wordToIndex = wordToIdx
            indexToWord = idxToWord
            wordFrequencies = wordFreqs
            vocabSize = totalWords

            Log.i(TAG, "Loaded $vocabSize words")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading vocabulary", e)
            false
        }
    }

    override fun getSuggestions(text: String, topK: Int): List<Suggestion> {
        if (!isReady) return emptyList()
        if (text.isEmpty()) return emptyList()

        val currentPrefix = extractCurrentWord(text)
        val words = tokenize(text)

        // Case 1: User is typing a word (no trailing space) - show completions
        if (currentPrefix.isNotEmpty() && currentPrefix.length >= 1) {
            return getPrefixCompletions(currentPrefix, words, topK)
        }

        // Case 2: After space - predict next word using LSTM
        if (words.isNotEmpty()) {
            return getNextWordPredictions(words, topK)
        }

        return emptyList()
    }

    /**
     * Get word completions based on prefix.
     * Combines vocabulary matching with LSTM context boosting.
     */
    private fun getPrefixCompletions(prefix: String, contextWords: List<String>, topK: Int): List<Suggestion> {
        val prefixLower = prefix.lowercase()
        val results = mutableListOf<Suggestion>()
        val seenWords = mutableSetOf<String>()

        // Get LSTM predictions for context boosting
        val context = contextWords.dropLast(1).takeLast(SEQUENCE_LENGTH)
        val indices = context.map { wordToIndex[it.lowercase()] ?: 1 }
        val lstmPredictions = predictNextWord(indices)
        val lstmBoost = lstmPredictions.associate { (idx, prob) ->
            (indexToWord[idx] ?: "") to prob
        }

        // Find all words matching the prefix (exclude punctuation)
        val matchingWords = wordFrequencies.keys
            .filter { it.startsWith(prefixLower) && it != prefixLower && it !in EXCLUDED_WORDS && it.all { c -> c.isLetter() } }
            .map { word ->
                val baseScore = wordFrequencies[word] ?: 0
                val boost = lstmBoost[word] ?: 0f
                // Combine frequency score with LSTM boost
                val finalScore = baseScore + (boost * 10000).toInt()
                word to finalScore
            }
            .sortedByDescending { it.second }
            .take(topK * 2)

        for ((word, score) in matchingWords) {
            if (results.size >= topK) break
            if (word in seenWords) continue

            val caseMatched = matchCase(word, prefix)
            results.add(Suggestion(caseMatched, score.coerceAtLeast(1)))
            seenWords.add(word)
        }

        return results
    }

    /**
     * Get next word predictions using LSTM.
     */
    private fun getNextWordPredictions(contextWords: List<String>, topK: Int): List<Suggestion> {
        val context = contextWords.takeLast(SEQUENCE_LENGTH)
        val indices = context.map { wordToIndex[it.lowercase()] ?: 1 }

        val predictions = predictNextWord(indices)
        if (predictions.isEmpty()) return emptyList()

        val results = mutableListOf<Suggestion>()
        val seenWords = mutableSetOf<String>()

        for ((wordIndex, prob) in predictions) {
            if (results.size >= topK) break

            val word = indexToWord[wordIndex] ?: continue
            // Skip punctuation and special tokens
            if (word in EXCLUDED_WORDS) continue
            if (!word.all { it.isLetter() }) continue
            if (word in seenWords) continue

            val score = (prob * 1000).toInt().coerceAtLeast(1)
            results.add(Suggestion(word, score))
            seenWords.add(word)
        }

        return results
    }

    private fun predictNextWord(indices: List<Int>): List<Pair<Int, Float>> {
        val native = lstmNative ?: return emptyList()

        val probs = native.predict(indices.toIntArray()) ?: return emptyList()

        // Get top predictions
        val results = mutableListOf<Pair<Int, Float>>()
        for (i in probs.indices) {
            if (probs[i] >= MIN_CONFIDENCE) {
                results.add(i to probs[i])
            }
        }

        return results.sortedByDescending { it.second }.take(20)
    }

    /**
     * Tokenize text into words.
     */
    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
    }

    /**
     * Extract the current word being typed (from last space or start).
     */
    private fun extractCurrentWord(text: String): String {
        if (text.isEmpty()) return ""

        // If ends with space, no current word
        if (text.endsWith(" ")) return ""

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

    override fun getReplacementLength(text: String): Int {
        return extractCurrentWord(text).length
    }

    fun release() {
        lstmNative?.release()
        lstmNative = null
        isLoaded = false
    }
}
