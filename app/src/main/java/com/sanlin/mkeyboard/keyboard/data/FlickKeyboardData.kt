package com.sanlin.mkeyboard.keyboard.data

import com.sanlin.mkeyboard.keyboard.model.FlickCharacter
import com.sanlin.mkeyboard.keyboard.model.FlickKey

/**
 * Data definitions for Myanmar Flick Keyboard character mappings.
 *
 * Layout based on user's design:
 * - 4 rows x 3 columns = 12 flick keys
 * - Each key has up to 5 characters (center + 4 directions)
 * - Yellow = center (tap), surrounding = flick directions
 *
 * Direction mapping from the layout image:
 * - Center: Primary character (yellow in image)
 * - Up: Top character
 * - Down: Bottom character
 * - Left: Left character
 * - Right: Right character
 */
object FlickKeyboardData {

    // Myanmar Unicode constants for readability
    private const val KA = 0x1000      // က
    private const val KHA = 0x1001     // ခ
    private const val GA = 0x1002      // ဂ
    private const val GHA = 0x1003     // ဃ
    private const val NGA = 0x1004     // င

    private const val CA = 0x1005      // စ
    private const val CHA = 0x1006    // ဆ
    private const val JA = 0x1007      // ဇ
    private const val JHA = 0x1008     // ဈ
    private const val NYA_SMALL = 0x1009  // ဉ
    private const val NYA = 0x100A     // ည

    private const val TTA = 0x100B     // ဋ
    private const val TTHA = 0x100C    // ဌ
    private const val DDA = 0x100D     // ဍ
    private const val DDHA = 0x100E    // ဎ
    private const val NNA = 0x100F     // ဏ

    private const val TA = 0x1010      // တ
    private const val THA = 0x1011     // ထ
    private const val DA = 0x1012      // ဒ
    private const val DHA = 0x1013     // ဓ
    private const val NA = 0x1014      // န

    private const val PA = 0x1015      // ပ
    private const val PHA = 0x1016     // ဖ
    private const val BA = 0x1017      // ဗ
    private const val BHA = 0x1018     // ဘ
    private const val MA = 0x1019      // မ

    private const val YA = 0x101A      // ယ
    private const val RA = 0x101B      // ရ
    private const val LA = 0x101C      // လ
    private const val WA = 0x101D      // ဝ
    private const val SA = 0x101E      // သ

    private const val HA = 0x101F      // ဟ
    private const val LLA = 0x1020     // ဠ
    private const val A = 0x1021       // အ
    private const val I_INDEPENDENT = 0x1023  // ဣ
    private const val U_INDEPENDENT = 0x1025  // ဥ
    private const val UU_INDEPENDENT = 0x1026 // ဦ
    private const val E_INDEPENDENT = 0x1027  // ဧ

    // Vowels
    private const val E_VOWEL = 0x1031     // ေ
    private const val I_VOWEL = 0x102D     // ိ
    private const val II_VOWEL = 0x102E    // ီ
    private const val U_VOWEL = 0x102F     // ု
    private const val UU_VOWEL = 0x1030    // ူ

    private const val TALL_AA = 0x102B     // ါ
    private const val AA = 0x102C          // ာ
    private const val ANUSVARA = 0x1036    // ံ
    private const val VISARGA = 0x1038     // း
    private const val DOT_BELOW = 0x1037   // ့

    // Medials
    private const val MEDIAL_YA = 0x103B   // ျ
    private const val MEDIAL_RA = 0x103C   // ြ
    private const val MEDIAL_WA = 0x103D   // ွ
    private const val MEDIAL_HA = 0x103E   // ှ
    private const val GREAT_SA = 0x103F    // ဿ

    // Other marks
    private const val VIRAMA = 0x1039      // ္ (stacker)
    private const val ASAT = 0x103A        // ် (killer)
    private const val AI_VOWEL = 0x1032    // ဲ

    // Punctuation
    private const val SECTION = 0x104A     // ၊
    private const val SENTENCE = 0x104B    // ။
    private const val LOCATIVE = 0x104C    // ၌
    private const val COMPLETED = 0x104D   // ၍
    private const val GENITIVE = 0x104F    // ၏

    // Independent vowels
    private const val O_INDEPENDENT = 0x1029  // ဩ
    private const val AU_INDEPENDENT = 0x102A // ဪ

    /**
     * Create the standard Myanmar flick keys.
     *
     * Layout (format: center/up/down/left/right):
     * Row 1: [က/ခ/ဂ/ဃ/င] [စ/ဆ/ဇ/ဉ/ည] [ဋ/ဌ/ဍ/ဎ/ဏ]
     * Row 2: [တ/ထ/ဒ/ဓ/န] [ပ/ဖ/ဗ/ဘ/မ] [ယ/ရ/လ/ဝ/သ]
     * Row 3: [အ/ဠ/ဟ/ဿ/ဥ] [ာ/ီ/ု/ူ/ိ] [ေ/ဲ/ံ/့/း]
     * Row 4: [ျ/ြ/ွ/ဦ/ှ] [။/၌/၍/၊/၏] [်/္/ဩ/ဪ/ဧ]
     */
    fun createMyanmarFlickKeys(): List<FlickKey> {
        return listOf(
            // Row 1
            // Key 1: [က/ခ/ဂ/ဃ/င]
            FlickKey(
                center = FlickCharacter.fromCodePoint(KA),      // က
                up = FlickCharacter.fromCodePoint(KHA),         // ခ
                down = FlickCharacter.fromCodePoint(GA),        // ဂ
                left = FlickCharacter.fromCodePoint(GHA),       // ဃ
                right = FlickCharacter.fromCodePoint(NGA)       // င
            ),
            // Key 2: [စ/ဆ/ဇ/ဈ/ည]
            FlickKey(
                center = FlickCharacter.fromCodePoint(CA),      // စ
                up = FlickCharacter.fromCodePoint(CHA),         // ဆ
                down = FlickCharacter.fromCodePoint(JA),        // ဇ
                left = FlickCharacter.fromCodePoint(JHA),       // ဈ
                right = FlickCharacter.fromCodePoint(NYA)       // ည
            ),
            // Key 3: [ဋ/ဌ/ဍ/ဎ/ဏ]
            FlickKey(
                center = FlickCharacter.fromCodePoint(TTA),     // ဋ
                up = FlickCharacter.fromCodePoint(TTHA),        // ဌ
                down = FlickCharacter.fromCodePoint(DDA),       // ဍ
                left = FlickCharacter.fromCodePoint(DDHA),      // ဎ
                right = FlickCharacter.fromCodePoint(NNA)       // ဏ
            ),

            // Row 2
            // Key 4: [တ/ထ/ဒ/ဓ/န]
            FlickKey(
                center = FlickCharacter.fromCodePoint(TA),      // တ
                up = FlickCharacter.fromCodePoint(THA),         // ထ
                down = FlickCharacter.fromCodePoint(DA),        // ဒ
                left = FlickCharacter.fromCodePoint(DHA),       // ဓ
                right = FlickCharacter.fromCodePoint(NA)        // န
            ),
            // Key 5: [ပ/ဖ/ဗ/ဘ/မ]
            FlickKey(
                center = FlickCharacter.fromCodePoint(PA),      // ပ
                up = FlickCharacter.fromCodePoint(PHA),         // ဖ
                down = FlickCharacter.fromCodePoint(BA),        // ဗ
                left = FlickCharacter.fromCodePoint(BHA),       // ဘ
                right = FlickCharacter.fromCodePoint(MA)        // မ
            ),
            // Key 6: [ယ/ရ/လ/ဝ/သ]
            FlickKey(
                center = FlickCharacter.fromCodePoint(YA),      // ယ
                up = FlickCharacter.fromCodePoint(RA),          // ရ
                down = FlickCharacter.fromCodePoint(LA),        // လ
                left = FlickCharacter.fromCodePoint(WA),        // ဝ
                right = FlickCharacter.fromCodePoint(SA)        // သ
            ),

            // Row 3
            // Key 7: [အ/ဠ/ဟ/ဿ/ဥ]
            FlickKey(
                center = FlickCharacter.fromCodePoint(A),       // အ
                up = FlickCharacter.fromCodePoint(LLA),         // ဠ
                down = FlickCharacter.fromCodePoint(HA),        // ဟ
                left = FlickCharacter.fromCodePoint(GREAT_SA),  // ဿ
                right = FlickCharacter.fromCodePoint(U_INDEPENDENT) // ဥ
            ),
            // Key 8: [ာ/ီ/ု/ူ/ိ]
            FlickKey(
                center = FlickCharacter.fromCodePoint(AA),      // ာ
                up = FlickCharacter.fromCodePoint(II_VOWEL),    // ီ
                down = FlickCharacter.fromCodePoint(U_VOWEL),   // ု
                left = FlickCharacter.fromCodePoint(UU_VOWEL),  // ူ
                right = FlickCharacter.fromCodePoint(I_VOWEL)   // ိ
            ),
            // Key 9: [ေ/ဲ/ံ/့/း]
            FlickKey(
                center = FlickCharacter.fromCodePoint(E_VOWEL), // ေ
                up = FlickCharacter.fromCodePoint(AI_VOWEL),    // ဲ
                down = FlickCharacter.fromCodePoint(ANUSVARA),  // ံ
                left = FlickCharacter.fromCodePoint(DOT_BELOW), // ့
                right = FlickCharacter.fromCodePoint(VISARGA)   // း
            ),

            // Row 4
            // Key 10: [ျ/ြ/ွ/ဦ/ှ]
            FlickKey(
                center = FlickCharacter.fromCodePoint(MEDIAL_YA),  // ျ
                up = FlickCharacter.fromCodePoint(MEDIAL_RA),      // ြ
                down = FlickCharacter.fromCodePoint(MEDIAL_WA),    // ွ
                left = FlickCharacter.fromCodePoint(UU_INDEPENDENT), // ဦ
                right = FlickCharacter.fromCodePoint(MEDIAL_HA)    // ှ
            ),
            // Key 11: [။/၌/၍/၊/၏]
            FlickKey(
                center = FlickCharacter.fromCodePoint(SENTENCE),   // ။
                up = FlickCharacter.fromCodePoint(LOCATIVE),       // ၌
                down = FlickCharacter.fromCodePoint(COMPLETED),    // ၍
                left = FlickCharacter.fromCodePoint(SECTION),      // ၊
                right = FlickCharacter.fromCodePoint(GENITIVE)     // ၏
            ),
            // Key 12: [်/္/ဩ/ဪ/ဧ]
            FlickKey(
                center = FlickCharacter.fromCodePoint(ASAT),       // ်
                up = FlickCharacter.fromCodePoint(VIRAMA),         // ္
                down = FlickCharacter.fromCodePoint(O_INDEPENDENT), // ဩ
                left = FlickCharacter.fromCodePoint(AU_INDEPENDENT), // ဪ
                right = FlickCharacter.fromCodePoint(E_INDEPENDENT) // ဧ
            )
        )
    }

    /**
     * Get the text to commit for a FlickCharacter.
     * Some characters like ို and ိုး are multi-character sequences.
     */
    fun getCommitText(char: FlickCharacter): String {
        // Check for multi-character labels
        return if (char.label.length > 1) {
            char.label  // Use the full label as commit text for sequences
        } else {
            String(Character.toChars(char.code))
        }
    }

    /**
     * Check if the given character is a vowel that may need reordering (E-vowel).
     */
    fun isReorderingVowel(code: Int): Boolean {
        return code == E_VOWEL
    }

    /**
     * Check if the given character is a Myanmar consonant.
     */
    fun isConsonant(code: Int): Boolean {
        return code in KA..A || code == GREAT_SA
    }

    /**
     * Check if the given character is a Myanmar vowel sign.
     */
    fun isVowelSign(code: Int): Boolean {
        return code in I_VOWEL..AI_VOWEL || code == TALL_AA || code == AA
    }

    /**
     * Check if the given character is the stacker (virama).
     */
    fun isVirama(code: Int): Boolean {
        return code == VIRAMA
    }
}
