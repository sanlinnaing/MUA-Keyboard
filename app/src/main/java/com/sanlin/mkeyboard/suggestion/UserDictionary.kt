package com.sanlin.mkeyboard.suggestion

import android.content.Context
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * User Dictionary that stores words the user has typed which are NOT in the
 * LSTM/Trie vocabulary. Uses context-keyed SyllableTrie for efficient
 * prefix-based retrieval.
 *
 * Architecture:
 * - Context key = last 2+ syllables before the word, joined with "|"
 * - Each context maps to a SyllableTrie storing the words typed in that context
 * - A global trie (no context) stores all user words for fallback matching
 *
 * Word boundary detection:
 * - Words are detected via composing state in MuaKeyboardService
 * - When user commits text (space/punctuation/suggestion), the composed text
 *   is checked against LSTM vocabulary
 * - If any syllable in the word is NOT in LSTM vocab, the word is recorded
 */
class UserDictionary(private val context: Context) {

    companion object {
        private const val TAG = "UserDictionary"
        private const val FILENAME = "user_dictionary.bin"
        private const val VERSION = 1
        private const val MAX_CONTEXTS = 3_000
        private const val MAX_GLOBAL_WORDS = 5_000
        private const val CONTEXT_BOOST = 1.5f
        private const val RECENCY_DECAY_MS = 7L * 24 * 60 * 60 * 1000  // 7 days
        private const val SAVE_INTERVAL = 10  // Save periodically (settings reads from memory now)
    }

    private var recordCount = 0

    // Context-keyed tries: "syllable1|syllable2" -> SyllableTrie
    private val contextTries = HashMap<String, SyllableTrie>()

    // Global trie (no context required) for fallback
    private val globalTrie = SyllableTrie()

    private var isLoaded = false

    /**
     * Initialize the dictionary by loading from disk.
     * Should be called from a background thread.
     */
    fun initialize(): Boolean {
        return try {
            load()
            isLoaded = true
            Log.i(TAG, "UserDictionary loaded: ${contextTries.size} contexts, ${globalTrie.wordCount()} global words")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize UserDictionary", e)
            isLoaded = true  // Mark as loaded even on failure so we can start recording
            false
        }
    }

    val isReady: Boolean get() = isLoaded

    /**
     * Record a word that the user typed.
     * Only records if the word contains syllables not in the LSTM vocabulary.
     *
     * @param contextSyllables the 2+ syllables before this word (for context key)
     * @param word the full word string
     * @param wordSyllables the word broken into syllables
     */
    fun recordWord(contextSyllables: List<String>, word: String, wordSyllables: List<String>) {
        if (word.isBlank() || wordSyllables.isEmpty()) return

        Log.d(TAG, "Recording word: '$word' (syllables=$wordSyllables, context=$contextSyllables)")

        // Add to global trie (always)
        globalTrie.insert(wordSyllables, word)

        // Add to context-keyed tries
        if (contextSyllables.size >= 2) {
            val contextKey = contextSyllables.takeLast(2).joinToString("|")
            val trie = contextTries.getOrPut(contextKey) { SyllableTrie() }
            trie.insert(wordSyllables, word)
        }

        // Also store with single-syllable context as fallback
        if (contextSyllables.isNotEmpty()) {
            val singleKey = contextSyllables.last()
            val trie = contextTries.getOrPut(singleKey) { SyllableTrie() }
            trie.insert(wordSyllables, word)
        }

        // Prune if needed
        maybePrune()

        // Periodic save
        recordCount++
        if (recordCount >= SAVE_INTERVAL) {
            recordCount = 0
            Thread { save() }.start()
        }
    }

    /**
     * Get suggestions from the user dictionary.
     * Searches both context-specific and global tries, merges and ranks results.
     *
     * @param contextSyllables the preceding syllables for context matching
     * @param prefixSyllables the current input syllables to use as prefix
     * @param maxResults maximum number of suggestions to return
     * @return list of Suggestion objects scored and ranked
     */
    fun getSuggestions(
        contextSyllables: List<String>,
        prefixSyllables: List<String>,
        maxResults: Int = 5
    ): List<Suggestion> {
        if (!isLoaded) return emptyList()

        val now = System.currentTimeMillis()
        val candidates = mutableMapOf<String, Float>()  // word -> score

        // 1. Search context-keyed tries (higher priority)
        if (contextSyllables.size >= 2) {
            val contextKey = contextSyllables.takeLast(2).joinToString("|")
            val contextTrie = contextTries[contextKey]
            if (contextTrie != null) {
                val results = contextTrie.searchByPrefix(prefixSyllables, maxResults * 2)
                for (entry in results) {
                    val score = scoreEntry(entry, now, hasContextMatch = true)
                    val existing = candidates[entry.fullWord]
                    if (existing == null || score > existing) {
                        candidates[entry.fullWord] = score
                    }
                }
            }
        }

        // 2. Search single-syllable context (medium priority)
        if (contextSyllables.isNotEmpty()) {
            val singleKey = contextSyllables.last()
            val singleTrie = contextTries[singleKey]
            if (singleTrie != null) {
                val results = singleTrie.searchByPrefix(prefixSyllables, maxResults * 2)
                for (entry in results) {
                    val score = scoreEntry(entry, now, hasContextMatch = false)
                    val existing = candidates[entry.fullWord]
                    if (existing == null || score > existing) {
                        candidates[entry.fullWord] = score
                    }
                }
            }
        }

        // 3. Search global trie (fallback, lower priority)
        val globalResults = globalTrie.searchByPrefix(prefixSyllables, maxResults * 2)
        for (entry in globalResults) {
            val score = scoreEntry(entry, now, hasContextMatch = false) * 0.8f
            val existing = candidates[entry.fullWord]
            if (existing == null || score > existing) {
                candidates[entry.fullWord] = score
            }
        }

        // Convert to Suggestion objects and return top results
        return candidates.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { (word, score) -> Suggestion(word, score.toInt().coerceAtLeast(1)) }
    }

    /**
     * Score a word entry based on frequency, recency, and context match.
     */
    private fun scoreEntry(
        entry: SyllableTrie.WordEntry,
        now: Long,
        hasContextMatch: Boolean
    ): Float {
        val freqScore = entry.frequency.toFloat()

        // Recency decay: recent words get higher scores
        val ageMs = (now - entry.lastUsed).coerceAtLeast(0)
        val recencyFactor = if (ageMs < RECENCY_DECAY_MS) {
            1.0f
        } else {
            0.5f  // Older words get half score
        }

        // Context match boost
        val contextFactor = if (hasContextMatch) CONTEXT_BOOST else 1.0f

        // Prefix ratio: full matches score higher than partial
        return freqScore * recencyFactor * contextFactor * 100
    }

    /**
     * Get all words in the dictionary for display purposes.
     */
    fun getAllWords(maxResults: Int = 100): List<SyllableTrie.WordEntry> {
        return globalTrie.getAllWords(maxResults)
    }

    /**
     * Get total word count.
     */
    fun wordCount(): Int = globalTrie.wordCount()

    /**
     * Clear all data.
     */
    fun clear() {
        contextTries.clear()
        globalTrie.prune(Int.MAX_VALUE)  // Remove all
        val file = File(context.filesDir, FILENAME)
        if (file.exists()) file.delete()
        Log.i(TAG, "UserDictionary cleared")
    }

    /**
     * Save the dictionary to disk.
     */
    fun save() {
        try {
            val file = File(context.filesDir, FILENAME)
            val baos = ByteArrayOutputStream()
            DataOutputStream(baos).use { dos ->
                dos.writeInt(VERSION)

                // Write global trie
                globalTrie.writeTo(dos)

                // Write context tries
                dos.writeInt(contextTries.size)
                for ((key, trie) in contextTries) {
                    dos.writeUTF(key)
                    trie.writeTo(dos)
                }
            }

            FileOutputStream(file).use { fos ->
                fos.write(baos.toByteArray())
            }

            Log.d(TAG, "Saved UserDictionary: ${contextTries.size} contexts, ${globalTrie.wordCount()} global words")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save UserDictionary", e)
        }
    }

    /**
     * Load the dictionary from disk.
     */
    private fun load() {
        val file = File(context.filesDir, FILENAME)
        if (!file.exists()) {
            Log.d(TAG, "No UserDictionary file found")
            return
        }

        try {
            val data = FileInputStream(file).use { it.readBytes() }
            DataInputStream(ByteArrayInputStream(data)).use { dis ->
                val version = dis.readInt()
                if (version != VERSION) {
                    Log.w(TAG, "Unknown UserDictionary version: $version")
                    return
                }

                // Read global trie
                globalTrie.readFrom(dis)

                // Read context tries
                val contextCount = dis.readInt()
                contextTries.clear()
                for (i in 0 until contextCount) {
                    val key = dis.readUTF()
                    val trie = SyllableTrie()
                    trie.readFrom(dis)
                    contextTries[key] = trie
                }
            }

            Log.d(TAG, "Loaded UserDictionary: ${contextTries.size} contexts, ${globalTrie.wordCount()} global words")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load UserDictionary", e)
        }
    }

    /**
     * Prune context tries if too many contexts accumulated.
     */
    private fun maybePrune() {
        if (contextTries.size > MAX_CONTEXTS) {
            // Remove contexts with fewest total words
            val sorted = contextTries.entries
                .map { (key, trie) -> key to trie.wordCount() }
                .sortedByDescending { it.second }

            val toKeep = sorted.take(MAX_CONTEXTS / 2).map { it.first }.toSet()
            val iterator = contextTries.keys.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() !in toKeep) {
                    iterator.remove()
                }
            }
            Log.d(TAG, "Pruned contexts: ${contextTries.size} remaining")
        }

        if (globalTrie.wordCount() > MAX_GLOBAL_WORDS) {
            // Remove words with frequency 1
            val removed = globalTrie.prune(2)
            Log.d(TAG, "Pruned global trie: removed $removed low-frequency words")
        }
    }
}
