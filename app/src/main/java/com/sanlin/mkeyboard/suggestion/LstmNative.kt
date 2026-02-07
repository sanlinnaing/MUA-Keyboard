package com.sanlin.mkeyboard.suggestion

/**
 * JNI wrapper for the native LSTM syllable prediction engine.
 * Provides fast, memory-efficient LSTM inference without TFLite Java dependency.
 */
class LstmNative {

    companion object {
        init {
            System.loadLibrary("myanmar_lstm")
        }
    }

    /**
     * Native handle to the LSTM engine instance.
     */
    private var handle: Long = 0

    /**
     * Check if the engine has been initialized.
     */
    val isInitialized: Boolean
        get() = handle != 0L

    /**
     * Create a new LSTM engine instance.
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
     * Load model weights from binary data.
     * @param data the binary model data (custom format)
     * @return true if successful
     */
    fun loadModel(data: ByteArray): Boolean {
        if (handle == 0L) return false
        return loadModel(handle, data) == 1
    }

    /**
     * Load vocabulary from JSON string.
     * @param json JSON string in format {"syllable": index, ...}
     * @return true if successful
     */
    fun loadVocab(json: String): Boolean {
        if (handle == 0L) return false
        return loadVocab(handle, json) == 1
    }

    /**
     * Predict next syllable probabilities.
     * @param indices array of syllable indices (context)
     * @return float array of probabilities for each syllable in vocabulary
     */
    fun predict(indices: IntArray): FloatArray? {
        if (handle == 0L) return null
        return predict(handle, indices)
    }

    /**
     * Get vocabulary size.
     */
    fun getVocabSize(): Int {
        if (handle == 0L) return 0
        return getVocabSize(handle)
    }

    /**
     * Get syllable string for an index.
     */
    fun getSyllable(index: Int): String? {
        if (handle == 0L) return null
        return getSyllable(handle, index)
    }

    /**
     * Get index for a syllable string.
     * @return index, or -1 if not found
     */
    fun getIndex(syllable: String): Int {
        if (handle == 0L) return -1
        return getIndex(handle, syllable)
    }

    /**
     * Get the expected sequence length.
     */
    fun getSequenceLength(): Int {
        if (handle == 0L) return 5
        return getSequenceLength(handle)
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
    private external fun loadModel(handle: Long, data: ByteArray): Int
    private external fun loadVocab(handle: Long, json: String): Int
    private external fun predict(handle: Long, indices: IntArray): FloatArray?
    private external fun getVocabSize(handle: Long): Int
    private external fun getSyllable(handle: Long, index: Int): String?
    private external fun getIndex(handle: Long, syllable: String): Int
    private external fun getSequenceLength(handle: Long): Int
}
