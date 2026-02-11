package com.sanlin.mkeyboard.suggestion

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * LSTM-based word prediction engine using native C++ inference.
 * Uses iterative syllable prediction to build complete words.
 *
 * Note: This implementation uses a native C++ LSTM engine to avoid
 * TFLite library dependency (which doesn't support 16KB page alignment).
 */
class LstmSuggestionEngine(private val context: Context) {

    companion object {
        private const val TAG = "LstmSuggestionEngine"
        private const val MODEL_FILE = "syll_predict_model.bin"
        private const val INDICES_FILE = "syll_indices_uni.json"  // Updated vocabulary (10186 entries)
        private const val SEQUENCE_LENGTH = 5
        private const val MAX_WORD_SYLLABLES = 5
        private const val SPACE_INDEX = 0
        private const val MIN_CONFIDENCE = 0.01f
    }

    private var lstmNative: LstmNative? = null
    private var syllableToIndex: Map<String, Int> = emptyMap()
    private var indexToSyllable: Map<Int, String> = emptyMap()
    private var vocabSize: Int = 0
    private var isLoaded = false

    /**
     * Initialize the LSTM engine.
     * Should be called from a background thread.
     */
    fun initialize(): Boolean {
        if (isLoaded) return true

        try {
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

            // Load model from assets (trained model)
            val modelData = try {
                context.assets.open(MODEL_FILE).use { it.readBytes() }
            } catch (e: Exception) {
                Log.w(TAG, "Trained model not found in assets, creating fallback model")
                // Fall back to creating a model in internal storage
                val modelFile = File(context.filesDir, MODEL_FILE)
                if (!modelFile.exists() || modelFile.length() < 10_000_000) {
                    if (!createNativeModel(modelFile)) {
                        Log.e(TAG, "Failed to create native model")
                        return false
                    }
                }
                modelFile.readBytes()
            }

            Log.i(TAG, "Loading model: ${modelData.size} bytes")
            if (!lstmNative!!.loadModel(modelData)) {
                Log.e(TAG, "Failed to load native model")
                return false
            }

            vocabSize = lstmNative!!.getVocabSize()
            isLoaded = true
            Log.i(TAG, "LSTM engine initialized: vocab=$vocabSize")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing LSTM engine", e)
            return false
        }
    }

    /**
     * Create a native model file with initialized weights.
     * Uses streaming write to avoid large memory allocation.
     */
    private fun createNativeModel(outputFile: File): Boolean {
        try {
            val embeddingDim = 256
            val hiddenSize = 256

            java.io.DataOutputStream(
                java.io.BufferedOutputStream(FileOutputStream(outputFile))
            ).use { dos ->
                // Write header (little-endian)
                dos.writeInt(Integer.reverseBytes(0x4C53544D))  // Magic "LSTM"
                dos.writeInt(Integer.reverseBytes(1))           // Version
                dos.writeInt(Integer.reverseBytes(vocabSize))
                dos.writeInt(Integer.reverseBytes(embeddingDim))
                dos.writeInt(Integer.reverseBytes(hiddenSize))
                dos.writeInt(Integer.reverseBytes(SEQUENCE_LENGTH))

                val random = java.util.Random(42)

                // Helper to write float in little-endian
                fun writeFloatLE(f: Float) {
                    val bits = java.lang.Float.floatToIntBits(f)
                    dos.writeInt(Integer.reverseBytes(bits))
                }

                // Embedding weights - small random
                for (i in 0 until vocabSize * embeddingDim) {
                    writeFloatLE((random.nextFloat() - 0.5f) * 0.1f)
                }

                // LSTM kernel - Xavier initialization
                val kernelScale = Math.sqrt(2.0 / (embeddingDim + 4 * hiddenSize)).toFloat()
                for (i in 0 until 4 * hiddenSize * embeddingDim) {
                    writeFloatLE((random.nextFloat() - 0.5f) * 2 * kernelScale)
                }

                // LSTM recurrent - Xavier initialization
                val recurrentScale = Math.sqrt(2.0 / (hiddenSize + 4 * hiddenSize)).toFloat()
                for (i in 0 until 4 * hiddenSize * hiddenSize) {
                    writeFloatLE((random.nextFloat() - 0.5f) * 2 * recurrentScale)
                }

                // LSTM bias - zeros with forget gate bias = 1.0
                for (i in 0 until 4 * hiddenSize) {
                    val bias = if (i >= hiddenSize && i < 2 * hiddenSize) 1.0f else 0.0f
                    writeFloatLE(bias)
                }

                // Dense weights - near zero so bias dominates (until proper training)
                for (i in 0 until vocabSize * hiddenSize) {
                    writeFloatLE(0.0f)
                }

                // Dense bias - frequency-based (lower indices = more common syllables)
                // Higher values = higher probability after softmax
                for (i in 0 until vocabSize) {
                    val bias = when {
                        i == 0 -> -5.0f   // Space - unlikely to start words
                        i < 50 -> 3.0f    // Most common syllables
                        i < 200 -> 2.0f   // Very common syllables
                        i < 500 -> 1.0f   // Common syllables
                        i < 1000 -> 0.0f  // Medium frequency
                        i < 2000 -> -1.0f // Less common
                        else -> -2.0f     // Rare
                    }
                    writeFloatLE(bias)
                }
            }

            Log.i(TAG, "Created native model: ${outputFile.length()} bytes")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating native model", e)
            return false
        }
    }

    private fun loadVocabulary(): Boolean {
        return try {
            val jsonString = context.assets.open(INDICES_FILE).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)

            val syllToIdx = mutableMapOf<String, Int>()
            val idxToSyll = mutableMapOf<Int, String>()

            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val syllable = keys.next()
                val index = jsonObject.getInt(syllable)
                syllToIdx[syllable] = index
                idxToSyll[index] = syllable
            }

            syllableToIndex = syllToIdx
            indexToSyllable = idxToSyll
            vocabSize = syllToIdx.size

            Log.i(TAG, "Loaded $vocabSize syllables")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading vocabulary", e)
            false
        }
    }

    val isReady: Boolean
        get() = isLoaded && lstmNative?.isInitialized == true

    /**
     * Check if a syllable exists in the LSTM vocabulary.
     * @return true if the syllable is known, false if unknown
     */
    fun isKnownSyllable(syllable: String): Boolean {
        return syllableToIndex.containsKey(syllable)
    }

    /**
     * Check if a word (list of syllables) contains any unknown syllables.
     * @return true if at least one syllable is NOT in the vocabulary
     */
    fun hasUnknownSyllables(syllables: List<String>): Boolean {
        return syllables.any { !syllableToIndex.containsKey(it) }
    }

    /**
     * Predict the next syllable(s) given the current text context.
     * Returns top N predicted syllables with their probabilities.
     * Used by the LSTM-guided Trie pipeline.
     */
    fun predictNextSyllables(text: String, topN: Int = 3): List<Pair<String, Float>> {
        if (!isReady || text.isEmpty()) return emptyList()

        val lastSyllables = SyllableBreaker.lastSyllablesWithSpaces(text, SEQUENCE_LENGTH)
        if (lastSyllables.isEmpty()) return emptyList()

        val indices = lastSyllables.map { syll -> syllableToIndex[syll] ?: SPACE_INDEX }
        val predictions = predictNextSyllable(indices)

        return predictions
            .filter { (index, prob) -> index != SPACE_INDEX && prob >= MIN_CONFIDENCE }
            .take(topN)
            .mapNotNull { (index, prob) ->
                val syllable = indexToSyllable[index] ?: return@mapNotNull null
                if (syllable.isBlank()) return@mapNotNull null
                syllable to prob
            }
    }

    fun getSuggestions(text: String, topK: Int = 5): List<Suggestion> {
        if (!isReady) return emptyList()
        if (text.isEmpty()) return emptyList()

        val lastSyllables = SyllableBreaker.lastSyllablesWithSpaces(text, SEQUENCE_LENGTH)
        if (lastSyllables.isEmpty()) return emptyList()

        // Check if the last token is an incomplete syllable
        val lastToken = lastSyllables.lastOrNull() ?: ""
        val lastIsConsonant = SyllableBreaker.isConsonantOnly(lastToken)
        val lastEndsWithVowelE = SyllableBreaker.isIncompleteWithVowelE(lastToken)

        // Determine the prefix to prepend to tail predictions
        val incompletePrefix = when {
            lastIsConsonant -> lastToken      // e.g., "မ"
            lastEndsWithVowelE -> lastToken   // e.g., "ကေ" or "ကြေ"
            else -> ""
        }
        val hasIncompletePrefix = incompletePrefix.isNotEmpty()

        val indices = lastSyllables.map { syll ->
            syllableToIndex[syll] ?: SPACE_INDEX
        }

        val firstPredictions = predictNextSyllable(indices)
        if (firstPredictions.isEmpty()) return emptyList()

        val words = mutableListOf<Suggestion>()
        val seenWords = mutableSetOf<String>()
        val candidates = firstPredictions.take(topK * 2)

        for ((firstIndex, firstProb) in candidates) {
            if (firstIndex == SPACE_INDEX) continue

            var firstSyllable = indexToSyllable[firstIndex] ?: continue
            if (firstSyllable.isBlank()) continue

            // Combine incomplete prefix with predicted tail
            if (hasIncompletePrefix) {
                val shouldCombine = when {
                    lastIsConsonant -> SyllableBreaker.isTail(firstSyllable)
                    lastEndsWithVowelE -> SyllableBreaker.isTailAfterVowelE(firstSyllable)
                    else -> false
                }
                if (shouldCombine) {
                    firstSyllable = incompletePrefix + firstSyllable
                }
            }

            val word = buildWord(indices, firstIndex, firstProb, hasIncompletePrefix, incompletePrefix)
            if (word != null && word.word.isNotBlank() && word.word !in seenWords) {
                seenWords.add(word.word)
                words.add(word)
                if (words.size >= topK) break
            }
        }

        return words.sortedByDescending { it.frequency }.take(topK)
    }

    private fun buildWord(
        contextIndices: List<Int>,
        firstSyllableIndex: Int,
        firstProb: Float,
        hasIncompletePrefix: Boolean = false,
        incompletePrefix: String = ""
    ): Suggestion? {
        val wordSyllables = mutableListOf<String>()
        var currentIndices = contextIndices.toMutableList()
        var accumulatedProb = firstProb

        var firstSyllable = indexToSyllable[firstSyllableIndex] ?: return null

        // If last input was incomplete and first prediction is a valid tail,
        // combine them to form a complete syllable
        if (hasIncompletePrefix) {
            val shouldCombine = when {
                SyllableBreaker.isConsonantOnly(incompletePrefix) ->
                    SyllableBreaker.isTail(firstSyllable)
                SyllableBreaker.isIncompleteWithVowelE(incompletePrefix) ->
                    SyllableBreaker.isTailAfterVowelE(firstSyllable)
                else -> false
            }
            if (shouldCombine) {
                firstSyllable = incompletePrefix + firstSyllable
            }
        }

        wordSyllables.add(firstSyllable)

        currentIndices = shiftAndAdd(currentIndices, firstSyllableIndex)

        // Track incomplete syllable for subsequent predictions
        var pendingIncomplete: String? = null

        for (i in 1 until MAX_WORD_SYLLABLES) {
            val predictions = predictNextSyllable(currentIndices)
            if (predictions.isEmpty()) break

            val (nextIndex, nextProb) = predictions.first()

            if (nextIndex == SPACE_INDEX) break
            // Only stop if probability is extremely low (for random model support)
            if (nextProb < 0.0001f) break

            var nextSyllable = indexToSyllable[nextIndex] ?: break
            if (nextSyllable.isBlank()) break

            // Handle incomplete + tail combinations in subsequent predictions
            if (pendingIncomplete != null) {
                val shouldCombine = when {
                    SyllableBreaker.isConsonantOnly(pendingIncomplete) ->
                        SyllableBreaker.isTail(nextSyllable)
                    SyllableBreaker.isIncompleteWithVowelE(pendingIncomplete) ->
                        SyllableBreaker.isTailAfterVowelE(nextSyllable)
                    else -> false
                }
                if (shouldCombine) {
                    nextSyllable = pendingIncomplete + nextSyllable
                    pendingIncomplete = null
                } else {
                    // Pending wasn't combined, add it separately
                    wordSyllables.add(pendingIncomplete)
                    pendingIncomplete = null
                }
            }

            // Check if current syllable is incomplete
            if (SyllableBreaker.isConsonantOnly(nextSyllable) ||
                SyllableBreaker.isIncompleteWithVowelE(nextSyllable)) {
                pendingIncomplete = nextSyllable
                currentIndices = shiftAndAdd(currentIndices, nextIndex)
                accumulatedProb *= nextProb
                continue  // Don't add yet, wait for tail
            }

            wordSyllables.add(nextSyllable)
            accumulatedProb *= nextProb

            currentIndices = shiftAndAdd(currentIndices, nextIndex)
        }

        // If there's a pending incomplete syllable at the end, add it
        if (pendingIncomplete != null) {
            wordSyllables.add(pendingIncomplete)
        }

        if (wordSyllables.isEmpty()) return null

        val word = wordSyllables.joinToString("")
        val score = (accumulatedProb * 1000).toInt().coerceAtLeast(1)

        return Suggestion(word, score)
    }

    private fun shiftAndAdd(indices: List<Int>, newIndex: Int): MutableList<Int> {
        val result = if (indices.size >= SEQUENCE_LENGTH) {
            indices.drop(1).toMutableList()
        } else {
            indices.toMutableList()
        }
        result.add(newIndex)
        return result
    }

    private fun predictNextSyllable(indices: List<Int>): List<Pair<Int, Float>> {
        val native = lstmNative ?: return emptyList()

        val probs = native.predict(indices.toIntArray()) ?: return emptyList()

        // Get top predictions (don't filter by confidence for initial candidates)
        val results = mutableListOf<Pair<Int, Float>>()
        for (i in probs.indices) {
            results.add(i to probs[i])
        }

        // Return top 20 sorted by probability
        return results.sortedByDescending { it.second }.take(20)
    }

    /**
     * Get replacement length for LSTM suggestions.
     * When the last syllable is incomplete (consonant-only or ends with ေ),
     * we need to replace it when committing a suggestion that starts with that prefix.
     */
    fun getReplacementLength(text: String): Int {
        val lastSyllables = SyllableBreaker.lastSyllablesWithSpaces(text, SEQUENCE_LENGTH)
        if (lastSyllables.isEmpty()) return 0

        val lastToken = lastSyllables.lastOrNull() ?: return 0

        // If last token is incomplete, return its length for replacement
        return if (SyllableBreaker.isConsonantOnly(lastToken) ||
                   SyllableBreaker.isIncompleteWithVowelE(lastToken)) {
            lastToken.length
        } else {
            0
        }
    }

    fun release() {
        lstmNative?.release()
        lstmNative = null
        isLoaded = false
    }
}
