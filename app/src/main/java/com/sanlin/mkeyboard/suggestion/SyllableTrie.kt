package com.sanlin.mkeyboard.suggestion

import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * A Trie data structure with syllable-level nodes for efficient prefix search.
 * Used by UserDictionary to store and retrieve user-typed words.
 *
 * Each node maps a syllable string to child nodes. End-of-word nodes contain
 * a WordEntry with the full word, frequency count, and last-used timestamp.
 *
 * Example:
 *   root
 *   +-- "သူ"
 *   |   +-- "ဇာ" -> WordEntry("သူဇာ", freq=5) [END]
 *   |   +-- "မ"  -> WordEntry("သူမ", freq=3)   [END]
 *   |   +-- "ငယ်"
 *   |       +-- "ချင်း" -> WordEntry("သူငယ်ချင်း", freq=2) [END]
 */
class SyllableTrie {

    private val root = TrieNode()

    /**
     * Insert a word (as a list of syllables) into the trie.
     * If the word already exists, increments frequency and updates lastUsed.
     *
     * @param syllables the word broken into syllables
     * @param fullWord the complete word string
     */
    fun insert(syllables: List<String>, fullWord: String) {
        if (syllables.isEmpty()) return

        var current = root
        for (syllable in syllables) {
            current = current.children.getOrPut(syllable) { TrieNode() }
        }

        if (current.isEndOfWord && current.wordEntry != null) {
            current.wordEntry!!.frequency++
            current.wordEntry!!.lastUsed = System.currentTimeMillis()
        } else {
            current.isEndOfWord = true
            current.wordEntry = WordEntry(
                fullWord = fullWord,
                syllables = syllables,
                frequency = 1,
                lastUsed = System.currentTimeMillis()
            )
        }
    }

    /**
     * Search for words by syllable prefix.
     * Returns all WordEntry objects whose syllable sequence starts with the given prefix.
     *
     * @param prefixSyllables the prefix syllables to match
     * @param maxResults maximum number of results to return
     * @return list of matching WordEntry objects, sorted by frequency descending
     */
    fun searchByPrefix(prefixSyllables: List<String>, maxResults: Int = 10): List<WordEntry> {
        if (prefixSyllables.isEmpty()) {
            // Return all words in the trie
            val results = mutableListOf<WordEntry>()
            collectAllWords(root, results, maxResults * 2)
            return results.sortedByDescending { it.frequency }.take(maxResults)
        }

        // Navigate to the prefix node
        var current = root
        for ((i, syllable) in prefixSyllables.withIndex()) {
            val exactMatch = current.children[syllable]
            if (exactMatch != null) {
                current = exactMatch
            } else if (i == prefixSyllables.lastIndex) {
                // Last prefix element has no exact match:
                // try character-level prefix matching against child keys.
                // This handles English words stored as single "syllables"
                // (e.g., prefix "keyb" matches child key "keyboard").
                val results = mutableListOf<WordEntry>()
                for ((key, child) in current.children) {
                    if (key.startsWith(syllable)) {
                        if (child.isEndOfWord && child.wordEntry != null) {
                            results.add(child.wordEntry!!)
                        }
                        collectAllWords(child, results, maxResults * 2)
                    }
                }
                return results.sortedByDescending { it.frequency }.take(maxResults)
            } else {
                return emptyList()
            }
        }

        // Exact prefix match - collect all words under this node
        val results = mutableListOf<WordEntry>()

        // Check if the prefix itself is a word
        if (current.isEndOfWord && current.wordEntry != null) {
            results.add(current.wordEntry!!)
        }

        // Collect words from all children
        collectAllWords(current, results, maxResults * 2)

        return results.sortedByDescending { it.frequency }.take(maxResults)
    }

    /**
     * Get total number of words in the trie.
     */
    fun wordCount(): Int {
        var count = 0
        fun traverse(node: TrieNode) {
            if (node.isEndOfWord) count++
            for (child in node.children.values) {
                traverse(child)
            }
        }
        traverse(root)
        return count
    }

    /**
     * Get all words in the trie.
     */
    fun getAllWords(maxResults: Int = Int.MAX_VALUE): List<WordEntry> {
        val results = mutableListOf<WordEntry>()
        collectAllWords(root, results, maxResults)
        return results.sortedByDescending { it.frequency }
    }

    /**
     * Remove words with frequency below threshold.
     * @return number of words removed
     */
    fun prune(minFrequency: Int): Int {
        var removed = 0
        fun pruneNode(node: TrieNode): Boolean {
            // Prune children first
            val childIterator = node.children.iterator()
            while (childIterator.hasNext()) {
                val (_, child) = childIterator.next()
                if (pruneNode(child)) {
                    childIterator.remove()
                }
            }
            // Remove this node's word if below threshold
            if (node.isEndOfWord && (node.wordEntry?.frequency ?: 0) < minFrequency) {
                node.isEndOfWord = false
                node.wordEntry = null
                removed++
            }
            // Node can be removed if it has no children and no word
            return !node.isEndOfWord && node.children.isEmpty()
        }
        pruneNode(root)
        return removed
    }

    /**
     * Check if the trie is empty.
     */
    fun isEmpty(): Boolean = root.children.isEmpty() && !root.isEndOfWord

    /**
     * Serialize the trie to a DataOutputStream.
     */
    fun writeTo(dos: DataOutputStream) {
        writeNode(dos, root)
    }

    /**
     * Deserialize the trie from a DataInputStream.
     */
    fun readFrom(dis: DataInputStream) {
        root.children.clear()
        root.isEndOfWord = false
        root.wordEntry = null
        readNode(dis, root)
    }

    private fun collectAllWords(node: TrieNode, results: MutableList<WordEntry>, maxResults: Int) {
        if (results.size >= maxResults) return

        for ((_, child) in node.children) {
            if (child.isEndOfWord && child.wordEntry != null) {
                results.add(child.wordEntry!!)
                if (results.size >= maxResults) return
            }
            collectAllWords(child, results, maxResults)
        }
    }

    private fun writeNode(dos: DataOutputStream, node: TrieNode) {
        // Write number of children
        dos.writeInt(node.children.size)

        // Write each child
        for ((syllable, child) in node.children) {
            dos.writeUTF(syllable)

            // Write word entry if end of word
            dos.writeBoolean(child.isEndOfWord)
            if (child.isEndOfWord && child.wordEntry != null) {
                val entry = child.wordEntry!!
                dos.writeUTF(entry.fullWord)
                dos.writeInt(entry.syllables.size)
                for (s in entry.syllables) {
                    dos.writeUTF(s)
                }
                dos.writeInt(entry.frequency)
                dos.writeLong(entry.lastUsed)
            }

            // Recursively write children
            writeNode(dos, child)
        }
    }

    private fun readNode(dis: DataInputStream, node: TrieNode) {
        val childCount = dis.readInt()

        for (i in 0 until childCount) {
            val syllable = dis.readUTF()
            val child = TrieNode()

            val isEnd = dis.readBoolean()
            child.isEndOfWord = isEnd
            if (isEnd) {
                val fullWord = dis.readUTF()
                val syllableCount = dis.readInt()
                val syllables = mutableListOf<String>()
                for (j in 0 until syllableCount) {
                    syllables.add(dis.readUTF())
                }
                val frequency = dis.readInt()
                val lastUsed = dis.readLong()
                child.wordEntry = WordEntry(fullWord, syllables, frequency, lastUsed)
            }

            node.children[syllable] = child
            readNode(dis, child)
        }
    }

    /**
     * A node in the syllable trie.
     */
    private class TrieNode {
        val children = HashMap<String, TrieNode>()
        var isEndOfWord = false
        var wordEntry: WordEntry? = null
    }

    /**
     * Entry for a word stored in the trie.
     */
    data class WordEntry(
        val fullWord: String,
        val syllables: List<String>,
        var frequency: Int,
        var lastUsed: Long
    )
}
