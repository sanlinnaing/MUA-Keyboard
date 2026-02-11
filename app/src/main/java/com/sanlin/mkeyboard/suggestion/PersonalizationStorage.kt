package com.sanlin.mkeyboard.suggestion

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Handles binary file persistence for personalization n-gram caches.
 * Stores separate files for Myanmar and English caches.
 */
class PersonalizationStorage(private val context: Context) {

    companion object {
        private const val TAG = "PersonalizationStorage"
        private const val MYANMAR_FILE = "myanmar_user_ngrams.bin"
        private const val ENGLISH_FILE = "english_user_ngrams.bin"
    }

    /**
     * Save Myanmar cache to file.
     * @param cache the cache to save
     * @return true if successful
     */
    fun saveMyanmarCache(cache: UserNgramCache): Boolean {
        return saveCache(cache, MYANMAR_FILE)
    }

    /**
     * Load Myanmar cache from file.
     * @return the loaded cache, or null if file doesn't exist or load failed
     */
    fun loadMyanmarCache(): UserNgramCache? {
        return loadCache(MYANMAR_FILE)
    }

    /**
     * Save English cache to file.
     * @param cache the cache to save
     * @return true if successful
     */
    fun saveEnglishCache(cache: UserNgramCache): Boolean {
        return saveCache(cache, ENGLISH_FILE)
    }

    /**
     * Load English cache from file.
     * @return the loaded cache, or null if file doesn't exist or load failed
     */
    fun loadEnglishCache(): UserNgramCache? {
        return loadCache(ENGLISH_FILE)
    }

    /**
     * Delete all personalization data files.
     * @return true if all files were deleted successfully
     */
    fun clearAll(): Boolean {
        var success = true
        try {
            val myanmarFile = File(context.filesDir, MYANMAR_FILE)
            if (myanmarFile.exists() && !myanmarFile.delete()) {
                Log.w(TAG, "Failed to delete Myanmar cache file")
                success = false
            }

            val englishFile = File(context.filesDir, ENGLISH_FILE)
            if (englishFile.exists() && !englishFile.delete()) {
                Log.w(TAG, "Failed to delete English cache file")
                success = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing personalization files", e)
            success = false
        }
        return success
    }

    /**
     * Check if Myanmar cache file exists.
     */
    fun hasMyanmarCache(): Boolean {
        return File(context.filesDir, MYANMAR_FILE).exists()
    }

    /**
     * Check if English cache file exists.
     */
    fun hasEnglishCache(): Boolean {
        return File(context.filesDir, ENGLISH_FILE).exists()
    }

    /**
     * Get top learned words from both Myanmar and English caches.
     * @param maxPerLanguage maximum words to return per language
     * @return LearnedWords containing Myanmar and English word lists
     */
    fun getLearnedWords(maxPerLanguage: Int = 50): LearnedWords {
        val myanmarCache = loadMyanmarCache()
        val englishCache = loadEnglishCache()

        return LearnedWords(
            myanmarWords = myanmarCache?.getTopUnigrams(maxPerLanguage) ?: emptyList(),
            englishWords = englishCache?.getTopUnigrams(maxPerLanguage) ?: emptyList(),
            myanmarBigrams = myanmarCache?.getTopBigrams(maxPerLanguage) ?: emptyList(),
            englishBigrams = englishCache?.getTopBigrams(maxPerLanguage) ?: emptyList(),
            myanmarTrigrams = myanmarCache?.getTopTrigrams(maxPerLanguage) ?: emptyList(),
            englishTrigrams = englishCache?.getTopTrigrams(maxPerLanguage) ?: emptyList(),
            myanmarStats = myanmarCache?.getStats(),
            englishStats = englishCache?.getStats()
        )
    }

    /**
     * Container for learned words from both languages.
     */
    data class LearnedWords(
        val myanmarWords: List<Pair<String, Int>>,
        val englishWords: List<Pair<String, Int>>,
        val myanmarBigrams: List<Triple<String, String, Int>>,
        val englishBigrams: List<Triple<String, String, Int>>,
        val myanmarTrigrams: List<Triple<String, String, Int>>,
        val englishTrigrams: List<Triple<String, String, Int>>,
        val myanmarStats: UserNgramCache.CacheStats?,
        val englishStats: UserNgramCache.CacheStats?
    ) {
        val isEmpty: Boolean
            get() = myanmarWords.isEmpty() && englishWords.isEmpty()

        val totalWords: Int
            get() = (myanmarStats?.unigramCount ?: 0) + (englishStats?.unigramCount ?: 0)
    }

    private fun saveCache(cache: UserNgramCache, filename: String): Boolean {
        return try {
            val file = File(context.filesDir, filename)
            val data = cache.toByteArray()

            FileOutputStream(file).use { fos ->
                fos.write(data)
            }

            Log.d(TAG, "Saved cache to $filename (${data.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache to $filename", e)
            false
        }
    }

    private fun loadCache(filename: String): UserNgramCache? {
        val file = File(context.filesDir, filename)
        if (!file.exists()) {
            Log.d(TAG, "Cache file $filename does not exist")
            return null
        }

        return try {
            val data = FileInputStream(file).use { fis ->
                fis.readBytes()
            }

            val cache = UserNgramCache()
            if (cache.fromByteArray(data)) {
                Log.d(TAG, "Loaded cache from $filename (${cache.size()} entries)")
                cache
            } else {
                Log.w(TAG, "Failed to parse cache from $filename")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache from $filename", e)
            null
        }
    }
}
