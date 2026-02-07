package com.sanlin.mkeyboard.autocorrect

import android.view.inputmethod.InputConnection
import com.sanlin.mkeyboard.suggestion.Suggestion

/**
 * Manages autocorrection with session-based skip tracking.
 *
 * When a user deletes a suggested word, that word is added to a skip list
 * for the current session. The skip list is cleared when a new typing
 * session begins (e.g., switching fields or after period).
 *
 * Also handles contraction autocorrection (Im → I'm, dont → don't, etc.)
 */
class AutoCorrector {

    companion object {
        // Common contractions: wrong → correct
        private val CONTRACTIONS = mapOf(
            // I contractions
            "im" to "I'm",
            "ive" to "I've",
            "id" to "I'd",
            "ill" to "I'll",
            // Common contractions
            "dont" to "don't",
            "wont" to "won't",
            "cant" to "can't",
            "didnt" to "didn't",
            "doesnt" to "doesn't",
            "isnt" to "isn't",
            "arent" to "aren't",
            "wasnt" to "wasn't",
            "werent" to "weren't",
            "havent" to "haven't",
            "hasnt" to "hasn't",
            "hadnt" to "hadn't",
            "wouldnt" to "wouldn't",
            "couldnt" to "couldn't",
            "shouldnt" to "shouldn't",
            // You/they/we contractions
            "youre" to "you're",
            "theyre" to "they're",
            "weve" to "we've",
            "youve" to "you've",
            "theyve" to "they've",
            "youd" to "you'd",
            "theyd" to "they'd",
            "wed" to "we'd",
            "youll" to "you'll",
            "theyll" to "they'll",
            "well" to "we'll",
            // He/she/it contractions
            "hes" to "he's",
            "shes" to "she's",
            "hed" to "he'd",
            "shed" to "she'd",
            "hell" to "he'll",
            "shell" to "she'll",
            // Other common contractions
            "thats" to "that's",
            "whats" to "what's",
            "whos" to "who's",
            "wheres" to "where's",
            "hows" to "how's",
            "lets" to "let's",
            "theres" to "there's",
            "heres" to "here's",
            "itll" to "it'll",
            "thatll" to "that'll",
            "wholl" to "who'll",
            "thisll" to "this'll"
        )
    }

    // Words to skip in current session (cleared on session reset)
    private val sessionSkipWords = mutableSetOf<String>()

    // Track the last committed suggestion for delete detection
    private var lastCommittedSuggestion: String? = null
    private var lastCommittedLength: Int = 0

    /**
     * Mark a word as skipped for this session.
     * Called when user deletes a previously suggested word.
     */
    fun markSkipped(word: String) {
        sessionSkipWords.add(word.lowercase())
    }

    /**
     * Check if a word should be skipped.
     */
    fun isSkipped(word: String): Boolean {
        return word.lowercase() in sessionSkipWords
    }

    /**
     * Filter out skipped words from suggestions.
     */
    fun filterSkipped(suggestions: List<Suggestion>): List<Suggestion> {
        if (sessionSkipWords.isEmpty()) return suggestions
        return suggestions.filter { !isSkipped(it.word) }
    }

    /**
     * Record that a suggestion was committed.
     * Used to detect if user deletes it.
     */
    fun onSuggestionCommitted(word: String) {
        lastCommittedSuggestion = word
        lastCommittedLength = word.length
    }

    /**
     * Check if user is deleting the last committed suggestion.
     * If so, mark it as skipped.
     *
     * @param textBeforeCursor current text before cursor
     * @param deletedLength how many characters were deleted
     * @return true if a suggestion was marked as skipped
     */
    fun onDelete(textBeforeCursor: String, deletedLength: Int): Boolean {
        val lastSuggestion = lastCommittedSuggestion ?: return false

        // Check if user is deleting within the suggested word
        // This happens when user commits a suggestion then immediately deletes
        if (deletedLength > 0 && lastCommittedLength > 0) {
            lastCommittedLength -= deletedLength

            // If they've deleted the whole word or most of it, mark as skipped
            if (lastCommittedLength <= 0) {
                markSkipped(lastSuggestion)
                lastCommittedSuggestion = null
                lastCommittedLength = 0
                return true
            }
        }

        return false
    }

    /**
     * Called when user types a character (not delete).
     * Clears the delete tracking since they're continuing to type.
     */
    fun onCharacterTyped() {
        lastCommittedSuggestion = null
        lastCommittedLength = 0
    }

    /**
     * Reset the session skip list.
     * Called when starting a new typing session.
     */
    fun resetSession() {
        sessionSkipWords.clear()
        lastCommittedSuggestion = null
        lastCommittedLength = 0
    }

    /**
     * Clear all state.
     */
    fun clear() {
        resetSession()
    }

    /**
     * Check and correct contractions when space is typed.
     * Call this after committing a space.
     *
     * @param ic the InputConnection
     * @return true if a correction was made
     */
    fun correctContraction(ic: InputConnection?): Boolean {
        if (ic == null) return false

        // Get the word before the space we just typed
        val textBefore = ic.getTextBeforeCursor(20, 0)?.toString() ?: return false
        if (textBefore.length < 2) return false

        // Find the last word (before the trailing space)
        val trimmed = textBefore.trimEnd()
        val lastSpaceIndex = trimmed.lastIndexOf(' ')
        val lastWord = if (lastSpaceIndex >= 0) {
            trimmed.substring(lastSpaceIndex + 1)
        } else {
            trimmed
        }

        if (lastWord.isEmpty()) return false

        // Check if it's a contraction that needs correction
        val correction = CONTRACTIONS[lastWord.lowercase()]
        if (correction != null) {
            // Check if this word was skipped by user
            if (isSkipped(correction)) return false

            // Calculate how many characters to delete (word + space)
            val deleteLength = lastWord.length + 1  // +1 for the space

            // Delete the wrong word and space
            ic.deleteSurroundingText(deleteLength, 0)

            // Insert the corrected word with space
            val correctedWithCase = matchCase(correction, lastWord)
            ic.commitText("$correctedWithCase ", 1)

            return true
        }

        return false
    }

    /**
     * Match case of original word to correction.
     */
    private fun matchCase(correction: String, original: String): String {
        return when {
            // All uppercase
            original.all { it.isUpperCase() } -> correction.uppercase()
            // First letter uppercase
            original.firstOrNull()?.isUpperCase() == true -> {
                correction.replaceFirstChar { it.uppercase() }
            }
            // Lowercase
            else -> correction
        }
    }
}
