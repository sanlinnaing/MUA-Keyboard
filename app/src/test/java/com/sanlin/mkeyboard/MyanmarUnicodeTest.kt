package com.sanlin.mkeyboard

import com.sanlin.mkeyboard.unicode.MyanmarUnicode
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MyanmarUnicode helper functions
 */
class MyanmarUnicodeTest {

    @Test
    fun `isConsonant returns true for Myanmar consonants`() {
        // Test first consonant (Ka)
        assertTrue(MyanmarUnicode.isConsonant(MyanmarUnicode.KA))
        // Test middle consonant (Na)
        assertTrue(MyanmarUnicode.isConsonant(MyanmarUnicode.NA))
        // Test last consonant (A)
        assertTrue(MyanmarUnicode.isConsonant(MyanmarUnicode.A))
        // Test Nya
        assertTrue(MyanmarUnicode.isConsonant(MyanmarUnicode.NYA))
    }

    @Test
    fun `isConsonant returns false for non-consonants`() {
        // Vowel should not be consonant
        assertFalse(MyanmarUnicode.isConsonant(MyanmarUnicode.E_VOWEL))
        // Medial should not be consonant
        assertFalse(MyanmarUnicode.isConsonant(MyanmarUnicode.YA_MEDIAL))
        // Digit should not be consonant
        assertFalse(MyanmarUnicode.isConsonant(MyanmarUnicode.DIGIT_ZERO))
        // Asat should not be consonant
        assertFalse(MyanmarUnicode.isConsonant(MyanmarUnicode.ASAT))
    }

    @Test
    fun `isMedial returns true for Myanmar medials`() {
        assertTrue(MyanmarUnicode.isMedial(MyanmarUnicode.YA_MEDIAL))
        assertTrue(MyanmarUnicode.isMedial(MyanmarUnicode.RA_MEDIAL))
        assertTrue(MyanmarUnicode.isMedial(MyanmarUnicode.WA_MEDIAL))
        assertTrue(MyanmarUnicode.isMedial(MyanmarUnicode.HA_MEDIAL))
    }

    @Test
    fun `isMedial returns false for non-medials`() {
        assertFalse(MyanmarUnicode.isMedial(MyanmarUnicode.KA))
        assertFalse(MyanmarUnicode.isMedial(MyanmarUnicode.E_VOWEL))
        assertFalse(MyanmarUnicode.isMedial(MyanmarUnicode.ASAT))
        assertFalse(MyanmarUnicode.isMedial(MyanmarUnicode.VIRAMA))
    }

    @Test
    fun `isExtendedMedial includes Karen medials`() {
        // Standard medials
        assertTrue(MyanmarUnicode.isExtendedMedial(MyanmarUnicode.YA_MEDIAL))
        assertTrue(MyanmarUnicode.isExtendedMedial(MyanmarUnicode.HA_MEDIAL))
        // Karen medials
        assertTrue(MyanmarUnicode.isExtendedMedial(MyanmarUnicode.KAREN_MEDIAL))
        assertTrue(MyanmarUnicode.isExtendedMedial(MyanmarUnicode.KAREN_MEDIAL_2))
        assertTrue(MyanmarUnicode.isExtendedMedial(MyanmarUnicode.KAREN_MEDIAL_3))
    }

    @Test
    fun `isDependentVowel returns true for dependent vowels`() {
        assertTrue(MyanmarUnicode.isDependentVowel(MyanmarUnicode.TALL_AA))
        assertTrue(MyanmarUnicode.isDependentVowel(MyanmarUnicode.AA))
        assertTrue(MyanmarUnicode.isDependentVowel(MyanmarUnicode.I_VOWEL))
        assertTrue(MyanmarUnicode.isDependentVowel(MyanmarUnicode.II_VOWEL))
        assertTrue(MyanmarUnicode.isDependentVowel(MyanmarUnicode.U_VOWEL))
        assertTrue(MyanmarUnicode.isDependentVowel(MyanmarUnicode.UU_VOWEL))
        assertTrue(MyanmarUnicode.isDependentVowel(MyanmarUnicode.E_VOWEL))
        assertTrue(MyanmarUnicode.isDependentVowel(MyanmarUnicode.AI))
    }

    @Test
    fun `isDependentVowel returns false for non-vowels`() {
        assertFalse(MyanmarUnicode.isDependentVowel(MyanmarUnicode.KA))
        assertFalse(MyanmarUnicode.isDependentVowel(MyanmarUnicode.ASAT))
        assertFalse(MyanmarUnicode.isDependentVowel(MyanmarUnicode.DOT_BELOW))
    }

    @Test
    fun `isDigit returns true for Myanmar digits`() {
        assertTrue(MyanmarUnicode.isDigit(MyanmarUnicode.DIGIT_ZERO))
        assertTrue(MyanmarUnicode.isDigit(MyanmarUnicode.DIGIT_ONE))
        assertTrue(MyanmarUnicode.isDigit(MyanmarUnicode.DIGIT_FIVE))
        assertTrue(MyanmarUnicode.isDigit(MyanmarUnicode.DIGIT_NINE))
    }

    @Test
    fun `isDigit returns false for non-digits`() {
        assertFalse(MyanmarUnicode.isDigit(MyanmarUnicode.KA))
        assertFalse(MyanmarUnicode.isDigit(MyanmarUnicode.E_VOWEL))
        // ASCII digit should return false
        assertFalse(MyanmarUnicode.isDigit('0'.code))
    }

    @Test
    fun `needsReordering returns true for E vowels`() {
        assertTrue(MyanmarUnicode.needsReordering(MyanmarUnicode.E_VOWEL))
        assertTrue(MyanmarUnicode.needsReordering(MyanmarUnicode.SHAN_E))
    }

    @Test
    fun `needsReordering returns false for other characters`() {
        assertFalse(MyanmarUnicode.needsReordering(MyanmarUnicode.AA))
        assertFalse(MyanmarUnicode.needsReordering(MyanmarUnicode.KA))
        assertFalse(MyanmarUnicode.needsReordering(MyanmarUnicode.I_VOWEL))
    }

    @Test
    fun `isPostVowelSign returns true for post-consonant signs`() {
        assertTrue(MyanmarUnicode.isPostVowelSign(MyanmarUnicode.TALL_AA))
        assertTrue(MyanmarUnicode.isPostVowelSign(MyanmarUnicode.AA))
        assertTrue(MyanmarUnicode.isPostVowelSign(MyanmarUnicode.DOT_BELOW))
        assertTrue(MyanmarUnicode.isPostVowelSign(MyanmarUnicode.VISARGA))
    }

    @Test
    fun `isPostVowelSign returns false for non-post signs`() {
        assertFalse(MyanmarUnicode.isPostVowelSign(MyanmarUnicode.E_VOWEL))
        assertFalse(MyanmarUnicode.isPostVowelSign(MyanmarUnicode.I_VOWEL))
        assertFalse(MyanmarUnicode.isPostVowelSign(MyanmarUnicode.KA))
    }

    @Test
    fun `Unicode constants have correct values`() {
        // Verify some key constants
        assertEquals(0x1000, MyanmarUnicode.KA)
        assertEquals(0x1031, MyanmarUnicode.E_VOWEL)
        assertEquals(0x103A, MyanmarUnicode.ASAT)
        assertEquals(0x1039, MyanmarUnicode.VIRAMA)
        assertEquals(0x103B, MyanmarUnicode.YA_MEDIAL)
        assertEquals(0x1037, MyanmarUnicode.DOT_BELOW)
    }
}
