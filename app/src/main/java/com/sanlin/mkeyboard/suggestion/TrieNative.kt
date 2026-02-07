package com.sanlin.mkeyboard.suggestion

/**
 * JNI wrapper for the native Myanmar trie library.
 * Provides fast dictionary lookups using a Patricia Trie data structure.
 */
class TrieNative {

    companion object {
        init {
            System.loadLibrary("myanmar_trie")
        }
    }

    /**
     * Native handle to the trie instance (pointer).
     */
    private var handle: Long = 0

    /**
     * Check if the trie has been initialized.
     */
    val isInitialized: Boolean
        get() = handle != 0L

    /**
     * Create a new trie instance.
     * @return true if successful
     */
    fun initialize(): Boolean {
        if (handle != 0L) {
            return true // Already initialized
        }
        handle = create()
        return handle != 0L
    }

    /**
     * Load dictionary from a file path.
     * @param path absolute path to the .bin file
     * @return true if successful
     */
    fun loadFromPath(path: String): Boolean {
        if (handle == 0L) return false
        return load(handle, path) == 1
    }

    /**
     * Load dictionary from memory buffer (useful for loading from assets).
     * @param data the binary data
     * @return true if successful
     */
    fun loadFromMemory(data: ByteArray): Boolean {
        if (handle == 0L) return false
        return loadFromMemory(handle, data) == 1
    }

    /**
     * Get word suggestions based on syllables.
     * @param syllables array of Myanmar syllables
     * @param topK maximum number of suggestions to return
     * @return list of Suggestion objects sorted by frequency
     */
    fun suggest(syllables: Array<String>, topK: Int = 5): List<Suggestion> {
        if (handle == 0L || syllables.isEmpty()) {
            return emptyList()
        }

        val result = suggestPartial(handle, syllables, topK) ?: return emptyList()

        if (result.size < 2) return emptyList()

        @Suppress("UNCHECKED_CAST")
        val words = result[0] as? Array<String> ?: return emptyList()
        val frequencies = result[1] as? IntArray ?: return emptyList()

        if (words.size != frequencies.size) return emptyList()

        return words.mapIndexed { index, word ->
            Suggestion(word, frequencies[index])
        }
    }

    /**
     * Release native resources.
     */
    fun release() {
        if (handle != 0L) {
            destroy(handle)
            handle = 0
        }
    }

    // Native methods
    private external fun create(): Long
    private external fun destroy(handle: Long)
    private external fun load(handle: Long, path: String): Int
    private external fun loadFromMemory(handle: Long, data: ByteArray): Int
    private external fun suggestPartial(handle: Long, syllables: Array<String>, topK: Int): Array<Any>?
}
