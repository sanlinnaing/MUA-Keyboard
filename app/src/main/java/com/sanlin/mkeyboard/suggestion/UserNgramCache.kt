package com.sanlin.mkeyboard.suggestion

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.ln

/**
 * Stores and queries user n-gram frequencies for personalization.
 * Uses in-memory HashMaps for fast access, with binary serialization for persistence.
 */
class UserNgramCache {

    companion object {
        private const val VERSION = 1
        const val MAX_UNIGRAMS = 10_000
        const val MAX_BIGRAM_CONTEXTS = 5_000
        const val MAX_TRIGRAM_CONTEXTS = 2_000
        private const val PRUNE_THRESHOLD = 0.8f

        // Weights for boost calculation
        private const val UNIGRAM_WEIGHT = 0.2f
        private const val BIGRAM_WEIGHT = 0.3f
        private const val TRIGRAM_WEIGHT = 0.5f
    }

    // Unigram: word/syllable -> frequency
    private val unigrams = HashMap<String, Int>()

    // Bigram: context -> (next -> frequency)
    private val bigrams = HashMap<String, HashMap<String, Int>>()

    // Trigram: "context1|context2" -> (next -> frequency)
    private val trigrams = HashMap<String, HashMap<String, Int>>()

    // Track max frequency for normalization
    private var maxUnigramFreq = 1
    private var maxBigramFreq = 1
    private var maxTrigramFreq = 1

    /**
     * Record user input tokens to update n-gram frequencies.
     * Records ALL tokens as unigrams and ALL consecutive pairs/triples as bigrams/trigrams.
     * Use recordLatest() instead to avoid re-counting earlier context tokens.
     *
     * @param tokens list of tokens (syllables for Myanmar, words for English)
     */
    fun record(tokens: List<String>) {
        if (tokens.isEmpty()) return

        // Update unigrams
        for (token in tokens) {
            if (token.isBlank()) continue
            val freq = unigrams.getOrDefault(token, 0) + 1
            unigrams[token] = freq
            if (freq > maxUnigramFreq) maxUnigramFreq = freq
        }

        // Update bigrams
        for (i in 0 until tokens.size - 1) {
            val context = tokens[i]
            val next = tokens[i + 1]
            if (context.isBlank() || next.isBlank()) continue

            val contextMap = bigrams.getOrPut(context) { HashMap() }
            val freq = contextMap.getOrDefault(next, 0) + 1
            contextMap[next] = freq
            if (freq > maxBigramFreq) maxBigramFreq = freq
        }

        // Update trigrams
        for (i in 0 until tokens.size - 2) {
            val context1 = tokens[i]
            val context2 = tokens[i + 1]
            val next = tokens[i + 2]
            if (context1.isBlank() || context2.isBlank() || next.isBlank()) continue

            val trigramKey = "$context1|$context2"
            val contextMap = trigrams.getOrPut(trigramKey) { HashMap() }
            val freq = contextMap.getOrDefault(next, 0) + 1
            contextMap[next] = freq
            if (freq > maxTrigramFreq) maxTrigramFreq = freq
        }

        // Check if pruning is needed
        maybePrune()
    }

    /**
     * Record only the LAST token in the list as new input.
     * Context tokens (all but the last) are used only for bigram/trigram keys,
     * not counted as unigrams. This prevents re-counting earlier words
     * that were already recorded on previous space presses.
     *
     * @param tokens context window: [context1, context2, newToken]
     */
    fun recordLatest(tokens: List<String>) {
        if (tokens.isEmpty()) return

        val newToken = tokens.last()
        if (newToken.isBlank()) return

        // Only increment unigram for the newest token
        val freq = unigrams.getOrDefault(newToken, 0) + 1
        unigrams[newToken] = freq
        if (freq > maxUnigramFreq) maxUnigramFreq = freq

        // Record bigram: (second-to-last → last)
        if (tokens.size >= 2) {
            val context = tokens[tokens.size - 2]
            if (context.isNotBlank()) {
                val contextMap = bigrams.getOrPut(context) { HashMap() }
                val biFreq = contextMap.getOrDefault(newToken, 0) + 1
                contextMap[newToken] = biFreq
                if (biFreq > maxBigramFreq) maxBigramFreq = biFreq
            }
        }

        // Record trigram: (third-to-last|second-to-last → last)
        if (tokens.size >= 3) {
            val context1 = tokens[tokens.size - 3]
            val context2 = tokens[tokens.size - 2]
            if (context1.isNotBlank() && context2.isNotBlank()) {
                val trigramKey = "$context1|$context2"
                val contextMap = trigrams.getOrPut(trigramKey) { HashMap() }
                val triFreq = contextMap.getOrDefault(newToken, 0) + 1
                contextMap[newToken] = triFreq
                if (triFreq > maxTrigramFreq) maxTrigramFreq = triFreq
            }
        }

        maybePrune()
    }

    /**
     * Get frequency boost for a suggestion given context.
     * @param context list of preceding tokens (max 2 for trigram)
     * @param suggestion the suggested word/syllable
     * @return boost value between 0.0 and 1.0
     */
    fun getBoost(context: List<String>, suggestion: String): Float {
        var boost = 0f

        // Unigram contribution (low weight)
        val unigramFreq = unigrams[suggestion] ?: 0
        boost += UNIGRAM_WEIGHT * normalize(unigramFreq, maxUnigramFreq)

        // Bigram contribution (medium weight)
        if (context.isNotEmpty()) {
            val lastContext = context.last()
            val bigramFreq = bigrams[lastContext]?.get(suggestion) ?: 0
            boost += BIGRAM_WEIGHT * normalize(bigramFreq, maxBigramFreq)
        }

        // Trigram contribution (high weight - most context-aware)
        if (context.size >= 2) {
            val trigramKey = "${context[context.size - 2]}|${context.last()}"
            val trigramFreq = trigrams[trigramKey]?.get(suggestion) ?: 0
            boost += TRIGRAM_WEIGHT * normalize(trigramFreq, maxTrigramFreq)
        }

        return boost.coerceIn(0f, 1f)
    }

    /**
     * Normalize frequency using log scale.
     */
    private fun normalize(freq: Int, maxFreq: Int): Float {
        if (freq == 0) return 0f
        return (ln(freq.toFloat() + 1) / ln(maxFreq.toFloat() + 1)).coerceIn(0f, 1f)
    }

    /**
     * Check if pruning is needed and perform if so.
     */
    private fun maybePrune() {
        if (unigrams.size > (MAX_UNIGRAMS * PRUNE_THRESHOLD).toInt()) {
            pruneMap(unigrams, MAX_UNIGRAMS / 2)
            recalculateMaxUnigram()
        }

        if (bigrams.size > (MAX_BIGRAM_CONTEXTS * PRUNE_THRESHOLD).toInt()) {
            pruneNestedMap(bigrams, MAX_BIGRAM_CONTEXTS / 2)
            recalculateMaxBigram()
        }

        if (trigrams.size > (MAX_TRIGRAM_CONTEXTS * PRUNE_THRESHOLD).toInt()) {
            pruneNestedMap(trigrams, MAX_TRIGRAM_CONTEXTS / 2)
            recalculateMaxTrigram()
        }
    }

    /**
     * Prune a frequency map by removing lowest frequency entries.
     */
    private fun pruneMap(map: HashMap<String, Int>, targetSize: Int) {
        if (map.size <= targetSize) return

        // Sort by frequency and keep top entries
        val sorted = map.entries.sortedByDescending { it.value }
        map.clear()
        for (i in 0 until minOf(targetSize, sorted.size)) {
            map[sorted[i].key] = sorted[i].value
        }
    }

    /**
     * Prune a nested map (bigrams/trigrams) by removing lowest-total-frequency contexts.
     */
    private fun pruneNestedMap(map: HashMap<String, HashMap<String, Int>>, targetSize: Int) {
        if (map.size <= targetSize) return

        // Calculate total frequency for each context and sort - preserve the inner maps
        val sorted = map.entries.map { (key, innerMap) ->
            Triple(key, HashMap(innerMap), innerMap.values.sum())
        }.sortedByDescending { it.third }

        map.clear()
        for (i in 0 until minOf(targetSize, sorted.size)) {
            map[sorted[i].first] = sorted[i].second
        }
    }

    private fun recalculateMaxUnigram() {
        maxUnigramFreq = unigrams.values.maxOrNull() ?: 1
    }

    private fun recalculateMaxBigram() {
        maxBigramFreq = bigrams.values.flatMap { it.values }.maxOrNull() ?: 1
    }

    private fun recalculateMaxTrigram() {
        maxTrigramFreq = trigrams.values.flatMap { it.values }.maxOrNull() ?: 1
    }

    /**
     * Serialize the cache to a byte array.
     */
    fun toByteArray(): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { dos ->
            // Version
            dos.writeInt(VERSION)

            // Unigrams
            dos.writeInt(unigrams.size)
            for ((key, freq) in unigrams) {
                dos.writeUTF(key)
                dos.writeInt(freq)
            }

            // Bigrams
            dos.writeInt(bigrams.size)
            for ((context, innerMap) in bigrams) {
                dos.writeUTF(context)
                dos.writeInt(innerMap.size)
                for ((key, freq) in innerMap) {
                    dos.writeUTF(key)
                    dos.writeInt(freq)
                }
            }

            // Trigrams
            dos.writeInt(trigrams.size)
            for ((context, innerMap) in trigrams) {
                dos.writeUTF(context)
                dos.writeInt(innerMap.size)
                for ((key, freq) in innerMap) {
                    dos.writeUTF(key)
                    dos.writeInt(freq)
                }
            }

            // Max frequencies
            dos.writeInt(maxUnigramFreq)
            dos.writeInt(maxBigramFreq)
            dos.writeInt(maxTrigramFreq)
        }
        return baos.toByteArray()
    }

    /**
     * Deserialize the cache from a byte array.
     * @return true if successful, false otherwise
     */
    fun fromByteArray(data: ByteArray): Boolean {
        return try {
            DataInputStream(ByteArrayInputStream(data)).use { dis ->
                // Version check
                val version = dis.readInt()
                if (version != VERSION) return false

                // Clear existing data
                unigrams.clear()
                bigrams.clear()
                trigrams.clear()

                // Unigrams
                val unigramCount = dis.readInt()
                for (i in 0 until unigramCount) {
                    val key = dis.readUTF()
                    val freq = dis.readInt()
                    unigrams[key] = freq
                }

                // Bigrams
                val bigramCount = dis.readInt()
                for (i in 0 until bigramCount) {
                    val context = dis.readUTF()
                    val innerCount = dis.readInt()
                    val innerMap = HashMap<String, Int>()
                    for (j in 0 until innerCount) {
                        val key = dis.readUTF()
                        val freq = dis.readInt()
                        innerMap[key] = freq
                    }
                    bigrams[context] = innerMap
                }

                // Trigrams
                val trigramCount = dis.readInt()
                for (i in 0 until trigramCount) {
                    val context = dis.readUTF()
                    val innerCount = dis.readInt()
                    val innerMap = HashMap<String, Int>()
                    for (j in 0 until innerCount) {
                        val key = dis.readUTF()
                        val freq = dis.readInt()
                        innerMap[key] = freq
                    }
                    trigrams[context] = innerMap
                }

                // Max frequencies
                maxUnigramFreq = dis.readInt()
                maxBigramFreq = dis.readInt()
                maxTrigramFreq = dis.readInt()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear all data.
     */
    fun clear() {
        unigrams.clear()
        bigrams.clear()
        trigrams.clear()
        maxUnigramFreq = 1
        maxBigramFreq = 1
        maxTrigramFreq = 1
    }

    /**
     * Get the total number of entries (for debugging/stats).
     */
    fun size(): Int {
        return unigrams.size + bigrams.size + trigrams.size
    }

    /**
     * Check if the cache is empty.
     */
    fun isEmpty(): Boolean {
        return unigrams.isEmpty() && bigrams.isEmpty() && trigrams.isEmpty()
    }

    /**
     * Get top N unigrams sorted by frequency (descending).
     * @param n maximum number of entries to return
     * @return list of (word, frequency) pairs
     */
    fun getTopUnigrams(n: Int): List<Pair<String, Int>> {
        return unigrams.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }
    }

    /**
     * Get top N bigrams sorted by frequency (descending).
     * @param n maximum number of entries to return
     * @return list of (context, nextWord, frequency) triples
     */
    fun getTopBigrams(n: Int): List<Triple<String, String, Int>> {
        return bigrams.flatMap { (context, innerMap) ->
            innerMap.map { (next, freq) -> Triple(context, next, freq) }
        }
            .sortedByDescending { it.third }
            .take(n)
    }

    /**
     * Get top N trigrams sorted by frequency (descending).
     * @param n maximum number of entries to return
     * @return list of (contextKey, nextWord, frequency) triples
     *         where contextKey is "context1|context2"
     */
    fun getTopTrigrams(n: Int): List<Triple<String, String, Int>> {
        return trigrams.flatMap { (contextKey, innerMap) ->
            innerMap.map { (next, freq) -> Triple(contextKey, next, freq) }
        }
            .sortedByDescending { it.third }
            .take(n)
    }

    /**
     * Get statistics about the cache.
     */
    fun getStats(): CacheStats {
        return CacheStats(
            unigramCount = unigrams.size,
            bigramContextCount = bigrams.size,
            trigramContextCount = trigrams.size,
            totalBigramEntries = bigrams.values.sumOf { it.size },
            totalTrigramEntries = trigrams.values.sumOf { it.size }
        )
    }

    /**
     * Statistics about the cache contents.
     */
    data class CacheStats(
        val unigramCount: Int,
        val bigramContextCount: Int,
        val trigramContextCount: Int,
        val totalBigramEntries: Int,
        val totalTrigramEntries: Int
    )
}
