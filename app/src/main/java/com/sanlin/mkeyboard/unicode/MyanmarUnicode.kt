package com.sanlin.mkeyboard.unicode

/**
 * Myanmar Unicode character constants.
 * Unicode range: U+1000 to U+109F (Myanmar block)
 * Extended range: U+AA60 to U+AA7F (Myanmar Extended-A)
 *
 * Reference: https://unicode.org/charts/PDF/U1000.pdf
 */
object MyanmarUnicode {

    // ===== CONSONANTS (U+1000 - U+1021) =====
    const val KA = 0x1000        // က
    const val KHA = 0x1001       // ခ
    const val GA = 0x1002        // ဂ
    const val GHA = 0x1003       // ဃ
    const val NGA = 0x1004       // င
    const val CA = 0x1005        // စ (SS)
    const val CHA = 0x1006       // ဆ
    const val JA = 0x1007        // ဇ
    const val JHA = 0x1008       // ဈ (JA + YA_MEDIAL ligature)
    const val NYA = 0x1009       // ည
    const val NNYA = 0x100A      // ဉ
    const val TTA = 0x100B       // ဋ
    const val TTHA = 0x100C      // ဌ
    const val DDA = 0x100D       // ဍ
    const val DDHA = 0x100E      // ဎ
    const val NNA = 0x100F       // ဏ
    const val TA = 0x1010        // တ
    const val THA = 0x1011       // ထ
    const val DA = 0x1012        // ဒ
    const val DHA = 0x1013       // ဓ
    const val NA = 0x1014        // န
    const val PA = 0x1015        // ပ
    const val PHA = 0x1016       // ဖ
    const val BA = 0x1017        // ဗ
    const val BHA = 0x1018       // ဘ
    const val MA = 0x1019        // မ
    const val YA = 0x101A        // ယ
    const val RA = 0x101B        // ရ
    const val LA = 0x101C        // လ
    const val WA = 0x101D        // ဝ
    const val SA = 0x101E        // သ
    const val HA = 0x101F        // ဟ
    const val LLA = 0x1020       // ဠ
    const val A = 0x1021         // အ

    // ===== INDEPENDENT VOWELS (U+1023 - U+1029) =====
    const val I = 0x1023         // ဣ
    const val II = 0x1024        // ဤ
    const val U = 0x1025         // ဥ (UU)
    const val UU = 0x1026        // ဦ
    const val E = 0x1027         // ဧ
    const val MON_E = 0x1028     // ဨ
    const val O = 0x1029         // ဩ

    // ===== DEPENDENT VOWELS (U+102B - U+1032) =====
    const val TALL_AA = 0x102B   // ါ
    const val AA = 0x102C        // ာ (AA_VOWEL)
    const val I_VOWEL = 0x102D  // ိ
    const val II_VOWEL = 0x102E // ီ
    const val U_VOWEL = 0x102F  // ု
    const val UU_VOWEL = 0x1030 // ူ
    const val E_VOWEL = 0x1031  // ေ (E_VOWEL - reordering character)
    const val AI = 0x1032       // ဲ

    // ===== DEPENDENT VOWEL SIGNS (U+1036 - U+1038) =====
    const val ANUSVARA = 0x1036  // ံ (Kinzi-forming)
    const val DOT_BELOW = 0x1037 // ့ (Aukmyit)
    const val VISARGA = 0x1038   // း (Wisanalonpan)

    // ===== VARIOUS SIGNS (U+1039 - U+103A) =====
    const val VIRAMA = 0x1039    // ္ (Killer/Halant - stacking)
    const val ASAT = 0x103A      // ်  (Killer - visible)

    // ===== MEDIALS (U+103B - U+103E) =====
    const val YA_MEDIAL = 0x103B // ျ (Ya Yit)
    const val RA_MEDIAL = 0x103C // ြ (Ya Ping/Ra)
    const val WA_MEDIAL = 0x103D // ွ (Wa Hswe)
    const val HA_MEDIAL = 0x103E // ှ (Ha Hato)

    // ===== DIGITS (U+1040 - U+1049) =====
    const val DIGIT_ZERO = 0x1040  // ၀
    const val DIGIT_ONE = 0x1041   // ၁
    const val DIGIT_TWO = 0x1042   // ၂
    const val DIGIT_THREE = 0x1043 // ၃
    const val DIGIT_FOUR = 0x1044  // ၄
    const val DIGIT_FIVE = 0x1045  // ၅
    const val DIGIT_SIX = 0x1046   // ၆
    const val DIGIT_SEVEN = 0x1047 // ၇
    const val DIGIT_EIGHT = 0x1048 // ၈
    const val DIGIT_NINE = 0x1049  // ၉

    // ===== PUNCTUATION (U+104A - U+104F) =====
    const val LITTLE_SECTION = 0x104A // ၊
    const val SECTION = 0x104B        // ။

    // ===== SHAN CHARACTERS =====
    const val SHAN_DIGIT_ZERO = 0x1090 // ႐
    const val SHAN_E = 0x1084          // ႄ (Shan E vowel)
    const val SHAN_MEDIAL_WA = 0x1082  // ႂ
    const val SHAN_TONE_2 = 0x1086     // ႆ
    const val SHAN_TONE_3 = 0x1087     // ႇ
    const val SHAN_TONE_5 = 0x1089     // ႉ
    const val SHAN_TONE_6 = 0x108A     // ႊ
    const val SHAN_COUNCIL_TONE_2 = 0x1062 // Ⴂ
    const val SHAN_VOWEL_O = 0x1083    // ႃ

    // ===== KAREN CHARACTERS =====
    const val KAREN_SHA = 0x105C       // ၜ
    const val KAREN_MEDIAL = 0x105E    // ၞ
    const val KAREN_MEDIAL_2 = 0x105F  // ၟ
    const val KAREN_MEDIAL_3 = 0x1060  // ၠ
    const val SGAW_KAREN_EU = 0x1062   // Ⴂ

    // ===== PALI/MON CHARACTERS =====
    const val MON_NGA = 0x106E         // ၮ
    const val MON_JHA = 0x106F         // ၯ
    const val MON_BBA = 0x1070         // ၰ

    // ===== SPECIAL CHARACTERS =====
    const val ZWSP = 8203              // Zero Width Space (for E vowel reordering)

    // ===== HELPER FUNCTIONS =====

    /**
     * Check if the character is a Myanmar consonant (U+1000 - U+1021)
     */
    @JvmStatic
    fun isConsonant(code: Int): Boolean {
        return code in KA..A
    }

    /**
     * Check if the character is a Myanmar medial (U+103B - U+103E)
     */
    @JvmStatic
    fun isMedial(code: Int): Boolean {
        return code in YA_MEDIAL..HA_MEDIAL
    }

    /**
     * Check if the character is an extended medial (includes Karen medials)
     */
    @JvmStatic
    fun isExtendedMedial(code: Int): Boolean {
        return isMedial(code) || code in KAREN_MEDIAL..KAREN_MEDIAL_3
    }

    /**
     * Check if the character is a dependent vowel
     */
    @JvmStatic
    fun isDependentVowel(code: Int): Boolean {
        return code in TALL_AA..AI
    }

    /**
     * Check if the character is a Myanmar digit
     */
    @JvmStatic
    fun isDigit(code: Int): Boolean {
        return code in DIGIT_ZERO..DIGIT_NINE
    }

    /**
     * Check if the character needs reordering (E vowel)
     */
    @JvmStatic
    fun needsReordering(code: Int): Boolean {
        return code == E_VOWEL || code == SHAN_E
    }

    /**
     * Check if the character is a vowel sign that appears after consonants
     */
    @JvmStatic
    fun isPostVowelSign(code: Int): Boolean {
        return when (code) {
            TALL_AA, AA, DOT_BELOW, VISARGA -> true
            else -> false
        }
    }
}
