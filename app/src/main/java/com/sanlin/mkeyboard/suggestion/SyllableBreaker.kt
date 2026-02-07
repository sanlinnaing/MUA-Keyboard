package com.sanlin.mkeyboard.suggestion

/**
 * Myanmar syllable breaker.
 * Breaks Myanmar text into syllables and tokenizes them for trie lookup.
 *
 * Based on Myanmar script syllable structure:
 * - Consonant (required)
 * - Medials (optional, 1-4 characters)
 * - Vowels (optional, 1-2 characters)
 * - Consonant + Asat (optional)
 * - Tone marks (optional)
 */
object SyllableBreaker {

    // Character ranges
    private const val CONS = "\u1000-\u1021"
    private const val CONS_INIT = "[$CONS\u1025\u1027\u1029\u103f]"
    private const val MEDIAL = "[\u103b-\u103e]{1,4}"
    private const val VOWEL = "[\u102b-\u1032]{1,2}\u103a?"
    private const val INDEPENDENT_VOWEL = "[\u1023-\u1027\u1029\u102a]"
    private const val ASAT = "\u103a"
    private const val VIRAMA = "\u1039"
    private const val CONS_ASAT = "[$CONS](?:\u103a\u102f\u1015\u103a|$ASAT$VIRAMA|[$ASAT$VIRAMA])"
    private const val KINZI = "[\u1004\u101b]$ASAT$VIRAMA"
    private const val TONE = "[\u1036\u1037\u1038]{1,2}"
    private const val LEGAUND = "\u104e\u1004$ASAT\u1038|\u104e\u1004$ASAT"
    private const val SYMBOL1 = "[\u104c\u104d\u104f]|$LEGAUND|\u104e"
    private const val SYMBOL2 = "[$CONS]\u103b$ASAT"
    private const val CONTRA1 = "\u103b\u102c"
    private const val DIGIT = "[\u1040-\u1049,.]+"
    private const val OTHERS = " "

    // Base pattern of Myanmar syllable
    private const val SYLLABLE_BASE_PATTERN =
        "$CONS_INIT(?:$MEDIAL)?(?:$VOWEL)?(?:$CONS_ASAT(?:$CONTRA1)?)?(?:$TONE)?"

    // Group 1: The Consonant
    private const val CONS_INIT_PATTERN = "(?<consonant>[$CONS\u1025\u1027\u1029\u103f])"

    // Group 2: The "Tail" (everything after the consonant)
    private const val TAIL_PATTERN =
        "(?<tail>(?:$MEDIAL)?(?:$VOWEL)?(?:$CONS_ASAT(?:$CONTRA1)?)?(?:$TONE)?)"

    // Combined pattern for extracting consonant and tail
    private val syllableBreakPattern = Regex("$CONS_INIT_PATTERN$TAIL_PATTERN")

    // Full syllable pattern for breaking text into syllables
    private val syllablePattern = Regex(
        "$SYMBOL1|$SYMBOL2|$SYLLABLE_BASE_PATTERN|$INDEPENDENT_VOWEL|$OTHERS"
    )

    /**
     * Break text into Myanmar syllables.
     * @param text Myanmar text to break
     * @return list of syllables
     */
    fun breakSyllables(text: String): List<String> {
        // Preprocessing: reorder common typos
        val processed = text.replace("\u1037\u103a", "\u103a\u1037")

        return syllablePattern.findAll(processed)
            .map { it.value }
            .filter { it.isNotEmpty() }
            .toList()
    }

    /**
     * Separate a syllable into consonant and tail.
     * @param syllable a single Myanmar syllable
     * @return Pair of (consonant, tail), or (null, null) if not matched
     */
    fun separateConsTail(syllable: String): Pair<String?, String?> {
        val match = syllableBreakPattern.find(syllable) ?: return null to null

        val consonant = match.groups["consonant"]?.value
        val tail = match.groups["tail"]?.value

        return consonant to tail
    }

    /**
     * Tokenize text into consonant + tail tokens for trie lookup.
     * Each syllable is split into at most 2 tokens: consonant and tail.
     * @param text Myanmar text
     * @return list of tokens
     */
    fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()

        for (syllable in breakSyllables(text)) {
            if (syllable.isBlank()) continue

            val (consonant, tail) = separateConsTail(syllable)
            if (consonant != null) {
                tokens.add(consonant)
                if (!tail.isNullOrEmpty()) {
                    tokens.add(tail)
                }
            } else {
                // If not matched as consonant+tail, add as-is
                tokens.add(syllable)
            }
        }

        return tokens
    }

    /**
     * Extract the last N syllables from text.
     * Used to limit context for trie lookup.
     * @param text Myanmar text
     * @param n maximum number of syllables to return
     * @return list of last N syllables
     */
    fun lastSyllables(text: String, n: Int): List<String> {
        val syllables = breakSyllables(text)
        return if (syllables.size <= n) {
            syllables
        } else {
            syllables.takeLast(n)
        }
    }

    // Myanmar punctuation marks
    private const val MYANMAR_SECTION = '\u104B'      // ။
    private const val MYANMAR_COMMA = '\u104A'        // ၊

    // Punctuation that should break suggestion context (excludes space for LSTM)
    private val PUNCTUATION_CHARS_NO_SPACE = setOf(
        // Myanmar punctuation
        MYANMAR_SECTION, MYANMAR_COMMA,
        // English/common punctuation
        '.', ',', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '{', '}',
        '/', '\\', '@', '#', '$', '%', '^', '&', '*', '-', '+', '=', '<', '>',
        '~', '`', '|',
        // Whitespace except space
        '\t', '\n', '\r'
    )

    // Common punctuation that should break suggestion context (includes space for Trie)
    private val PUNCTUATION_CHARS = PUNCTUATION_CHARS_NO_SPACE + setOf(' ')

    /**
     * Check if a character is a punctuation that breaks suggestion context.
     */
    fun isPunctuation(c: Char): Boolean {
        return c in PUNCTUATION_CHARS
    }

    /**
     * Check if a character is a punctuation (excluding space).
     * Used for LSTM which treats space as a valid token.
     */
    fun isPunctuationNoSpace(c: Char): Boolean {
        return c in PUNCTUATION_CHARS_NO_SPACE
    }

    /**
     * Extract text after last punctuation, keeping spaces.
     * Used for LSTM which treats space as a valid syllable token.
     * @param text input text
     * @return text after last punctuation (spaces preserved)
     */
    fun extractTextAfterPunctuation(text: String): String {
        if (text.isEmpty()) return ""

        // Find the last punctuation position (excluding space)
        var lastPunctuationIndex = -1
        for (i in text.indices.reversed()) {
            if (isPunctuationNoSpace(text[i])) {
                lastPunctuationIndex = i
                break
            }
        }

        // Extract text after last punctuation
        return if (lastPunctuationIndex >= 0) {
            text.substring(lastPunctuationIndex + 1)
        } else {
            text
        }
    }

    /**
     * Break text into syllables, keeping spaces as separate tokens.
     * Used for LSTM which expects space as a syllable (index 0).
     * @param text Myanmar text (may contain spaces)
     * @return list of syllables with spaces preserved as separate items
     */
    fun breakSyllablesWithSpaces(text: String): List<String> {
        if (text.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        var currentSegment = StringBuilder()

        for (c in text) {
            if (c == ' ') {
                // Process accumulated segment
                if (currentSegment.isNotEmpty()) {
                    result.addAll(breakSyllables(currentSegment.toString()))
                    currentSegment = StringBuilder()
                }
                // Add space as separate token
                result.add(" ")
            } else {
                currentSegment.append(c)
            }
        }

        // Process remaining segment
        if (currentSegment.isNotEmpty()) {
            result.addAll(breakSyllables(currentSegment.toString()))
        }

        return result
    }

    /**
     * Get the last N syllables including spaces for LSTM.
     * @param text input text
     * @param n maximum number of syllables/spaces to return
     * @return list of last N items (syllables and spaces)
     */
    fun lastSyllablesWithSpaces(text: String, n: Int): List<String> {
        val textAfterPunct = extractTextAfterPunctuation(text)
        val syllables = breakSyllablesWithSpaces(textAfterPunct)
        return if (syllables.size <= n) {
            syllables
        } else {
            syllables.takeLast(n)
        }
    }

    /**
     * Check if a character is a Myanmar character.
     * @param c character to check
     * @return true if in Myanmar Unicode range
     */
    fun isMyanmarChar(c: Char): Boolean {
        val code = c.code
        return code in 0x1000..0x109F
    }

    /**
     * Check if a string is a consonant-only (no vowels, medials, or other modifiers).
     * Used to detect incomplete syllables that need tail completion.
     * @param text text to check
     * @return true if text is a single consonant with no modifiers
     */
    fun isConsonantOnly(text: String): Boolean {
        if (text.isEmpty()) return false
        if (text.length > 1) return false  // More than one char means it has modifiers
        val c = text[0].code
        // Myanmar consonants: U+1000 to U+1021, plus U+1025, U+1027, U+1029, U+103F
        return c in 0x1000..0x1021 || c == 0x1025 || c == 0x1027 || c == 0x1029 || c == 0x103F
    }

    /**
     * Check if a string is a tail (vowel, medial, asat, tone - no consonant).
     * @param text text to check
     * @return true if text contains only modifiers (no consonant at start)
     */
    fun isTail(text: String): Boolean {
        if (text.isEmpty()) return false
        val firstChar = text[0].code
        // Tails start with medials (U+103B-U+103E) or vowels (U+102B-U+1032)
        // or other modifiers, NOT consonants
        val isConsonant = firstChar in 0x1000..0x1021 ||
                          firstChar == 0x1025 || firstChar == 0x1027 ||
                          firstChar == 0x1029 || firstChar == 0x103F
        return !isConsonant && isMyanmarChar(text[0])
    }

    // Vowel E (ေ) - special because it's typed before consonant but stored after
    private const val VOWEL_E = '\u1031'

    // Medials: ျ ြ ွ ှ (U+103B - U+103E)
    private val MEDIAL_RANGE = '\u103B'..'\u103E'

    /**
     * Check if a syllable is incomplete and ends with ေ (U+1031).
     * Patterns: consonant + ေ, or consonant + medial(s) + ေ
     * These are incomplete because they could have more vowels, tones, etc.
     * @param text syllable to check
     * @return true if it's an incomplete syllable ending with ေ
     */
    fun isIncompleteWithVowelE(text: String): Boolean {
        if (text.isEmpty()) return false
        if (text.last() != VOWEL_E) return false

        // Must start with consonant
        val firstChar = text[0].code
        val startsWithConsonant = firstChar in 0x1000..0x1021 ||
                                  firstChar == 0x1025 || firstChar == 0x1027 ||
                                  firstChar == 0x1029 || firstChar == 0x103F
        if (!startsWithConsonant) return false

        // Pattern 1: consonant + ေ (length 2)
        if (text.length == 2) return true

        // Pattern 2: consonant + medial(s) + ေ
        // Check that characters between consonant and ေ are all medials
        for (i in 1 until text.length - 1) {
            if (text[i] !in MEDIAL_RANGE) return false
        }
        return true
    }

    /**
     * Check if a tail can follow ေ (vowels that combine with ေ, tone marks, asat, etc.)
     * @param text tail to check
     * @return true if this tail can follow a syllable ending with ေ
     */
    fun isTailAfterVowelE(text: String): Boolean {
        if (text.isEmpty()) return false
        val firstChar = text[0].code
        // After ေ, valid tails include:
        // - Tone marks: U+1036 (ံ), U+1037 (့), U+1038 (း)
        // - Asat combinations: U+103A (်) followed by other chars
        // - Other vowels that can combine: U+102C (ာ), etc.
        // - Could also be consonant+asat for closed syllables
        return firstChar == 0x1036 || firstChar == 0x1037 || firstChar == 0x1038 ||
               firstChar == 0x103A || firstChar == 0x102C || firstChar == 0x102B ||
               // Also allow consonant (for closed syllable like ကေက်)
               firstChar in 0x1000..0x1021
    }

    /**
     * Check if text contains Myanmar characters.
     * @param text text to check
     * @return true if contains at least one Myanmar character
     */
    fun containsMyanmarText(text: String): Boolean {
        return text.any { isMyanmarChar(it) }
    }

    /**
     * Extract Myanmar text portion from the end of input, stopping at punctuation.
     * For suggestions, we only want text after the last punctuation.
     * e.g., "မြန်မာ။ န" -> "န", "Hello, မြန်" -> "မြန်"
     * @param text input text
     * @return Myanmar portion from the end, after last punctuation
     */
    fun extractMyanmarSuffix(text: String): String {
        if (text.isEmpty()) return ""

        // Find the last punctuation position
        var lastPunctuationIndex = -1
        for (i in text.indices.reversed()) {
            if (isPunctuation(text[i])) {
                lastPunctuationIndex = i
                break
            }
        }

        // Extract text after last punctuation (or from start if no punctuation)
        val textAfterPunctuation = if (lastPunctuationIndex >= 0) {
            text.substring(lastPunctuationIndex + 1)
        } else {
            text
        }

        // Now extract Myanmar suffix from this portion
        if (textAfterPunctuation.isEmpty()) return ""

        var startIndex = textAfterPunctuation.length
        for (i in textAfterPunctuation.indices.reversed()) {
            if (isMyanmarChar(textAfterPunctuation[i])) {
                startIndex = i
            } else if (startIndex < textAfterPunctuation.length) {
                // Found non-Myanmar char after finding Myanmar chars
                break
            }
        }

        return if (startIndex < textAfterPunctuation.length) {
            textAfterPunctuation.substring(startIndex)
        } else {
            ""
        }
    }

    /**
     * Calculate the byte length of the last N syllables.
     * Used for determining how much text to delete when selecting a suggestion.
     * @param text original text
     * @param syllableCount number of syllables
     * @return character count of the last N syllables
     */
    fun syllablesCharCount(text: String, syllableCount: Int): Int {
        val syllables = lastSyllables(text, syllableCount)
        return syllables.sumOf { it.length }
    }
}
