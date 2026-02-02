package com.sanlin.mkeyboard.emoji

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages recently used emojis using SharedPreferences.
 * Stores up to MAX_RECENT emojis in order of most recent use.
 */
object RecentEmojiManager {
    private const val PREFS_NAME = "emoji_prefs"
    private const val KEY_RECENT_EMOJIS = "recent_emojis"
    private const val MAX_RECENT = 50
    private const val SEPARATOR = ","

    private var prefs: SharedPreferences? = null
    private val recentEmojis = mutableListOf<String>()

    /**
     * Initialize the manager with context.
     * Call this once when the keyboard service starts.
     */
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadRecentEmojis()
    }

    /**
     * Load recent emojis from SharedPreferences.
     */
    private fun loadRecentEmojis() {
        recentEmojis.clear()
        val saved = prefs?.getString(KEY_RECENT_EMOJIS, "") ?: ""
        if (saved.isNotEmpty()) {
            // Split and filter out empty strings
            saved.split(SEPARATOR)
                .filter { it.isNotEmpty() }
                .take(MAX_RECENT)
                .forEach { recentEmojis.add(it) }
        }
    }

    /**
     * Add an emoji to the recent list.
     * If already present, moves it to the front.
     */
    fun addEmoji(emoji: String) {
        // Remove if already exists
        recentEmojis.remove(emoji)
        // Add to front
        recentEmojis.add(0, emoji)
        // Trim to max size
        while (recentEmojis.size > MAX_RECENT) {
            recentEmojis.removeAt(recentEmojis.size - 1)
        }
        // Save
        saveRecentEmojis()
    }

    /**
     * Save recent emojis to SharedPreferences.
     */
    private fun saveRecentEmojis() {
        prefs?.edit()?.apply {
            putString(KEY_RECENT_EMOJIS, recentEmojis.joinToString(SEPARATOR))
            apply()
        }
    }

    /**
     * Get the list of recent emojis.
     */
    fun getRecentEmojis(): List<String> {
        return recentEmojis.toList()
    }

    /**
     * Check if there are any recent emojis.
     */
    fun hasRecentEmojis(): Boolean {
        return recentEmojis.isNotEmpty()
    }

    /**
     * Clear all recent emojis.
     */
    fun clear() {
        recentEmojis.clear()
        saveRecentEmojis()
    }
}
