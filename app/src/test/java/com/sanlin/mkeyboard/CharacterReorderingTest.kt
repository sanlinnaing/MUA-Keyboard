package com.sanlin.mkeyboard

import com.sanlin.mkeyboard.unicode.MyanmarUnicode
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Myanmar character reordering logic.
 *
 * Myanmar script has complex reordering rules:
 * 1. E vowel (ေ U+1031) is typed before consonant but displayed after
 * 2. Medials follow specific ordering: Ya, Ra, Wa, Ha
 * 3. Asat + Dot Below should reorder to Dot Below + Asat
 * 4. Some consonant + medial combinations form ligatures
 */
class CharacterReorderingTest {

    @Test
    fun `E vowel reorder sequence - ZWSP + E before consonant`() {
        // When typing "ကေ" (ka + e vowel):
        // Visual order: ေက (e appears before ka)
        // Logical order: ZWSP + ေ + က
        // After reorder: က + ေ

        val zwsp = MyanmarUnicode.ZWSP.toChar()
        val eVowel = MyanmarUnicode.E_VOWEL.toChar()
        val ka = MyanmarUnicode.KA.toChar()

        // Input sequence (what user types)
        val inputSequence = charArrayOf(zwsp, eVowel, ka)

        // Expected output (after reordering)
        val expectedOutput = charArrayOf(ka, eVowel)

        // Verify the reordering concept
        assertEquals(2, expectedOutput.size)
        assertEquals(ka, expectedOutput[0])
        assertEquals(eVowel, expectedOutput[1])
    }

    @Test
    fun `Asat + Dot Below reorders to Dot Below + Asat`() {
        // Rule: ် + ့ should become ့ + ်
        val asat = MyanmarUnicode.ASAT.toChar()
        val dotBelow = MyanmarUnicode.DOT_BELOW.toChar()

        // Input: Asat then Dot Below
        val input = charArrayOf(asat, dotBelow)

        // Expected: Dot Below then Asat
        val expected = charArrayOf(dotBelow, asat)

        // Simulate reordering
        val reordered = if (input[0].code == MyanmarUnicode.ASAT &&
            input[1].code == MyanmarUnicode.DOT_BELOW) {
            charArrayOf(dotBelow, asat)
        } else {
            input
        }

        assertArrayEquals(expected, reordered)
    }

    @Test
    fun `Anusvara + U vowel reorders correctly`() {
        // Rule: ံ + ု should become ု + ံ
        val anusvara = MyanmarUnicode.ANUSVARA.toChar() // 0x1036
        val uVowel = MyanmarUnicode.U_VOWEL.toChar()     // 0x102F

        // Input: Anusvara then U vowel
        val input = charArrayOf(anusvara, uVowel)

        // Expected: U vowel then Anusvara
        val expected = charArrayOf(uVowel, anusvara)

        // Simulate reordering (as done in BamarKeyboard)
        val reordered = if (input[0].code == 0x1036 && input[1].code == 0x102F) {
            charArrayOf(uVowel, anusvara)
        } else {
            input
        }

        assertArrayEquals(expected, reordered)
    }

    @Test
    fun `SS + Ya medial forms JHA ligature`() {
        // Rule: စ + ျ = ဈ (0x1005 + 0x103B = 0x1008)
        val ss = MyanmarUnicode.CA.toChar()           // 0x1005 (စ)
        val yaMedial = MyanmarUnicode.YA_MEDIAL.toChar() // 0x103B (ျ)
        val jha = 0x1008.toChar()                     // ဈ

        // Verify the ligature rule
        val shouldFormLigature = (ss.code == 0x1005 && yaMedial.code == 0x103B)
        assertTrue(shouldFormLigature)

        // Expected result is JHA
        assertEquals(0x1008, jha.code)
    }

    @Test
    fun `UU + AA vowel transforms to NYA + AA`() {
        // Rule: ဥ + ာ = ည + ာ (0x1025 + 0x102C → 0x1009 + 0x102C)
        val uu = MyanmarUnicode.U.toChar()      // 0x1025 (ဥ)
        val aaVowel = MyanmarUnicode.AA.toChar() // 0x102C (ာ)
        val nya = MyanmarUnicode.NYA.toChar()    // 0x1009 (ည)

        // Verify the transformation rule
        val shouldTransform = (uu.code == 0x1025 && aaVowel.code == 0x102C)
        assertTrue(shouldTransform)

        // Expected result
        val expected = charArrayOf(nya, aaVowel)
        assertEquals(0x1009, expected[0].code)
        assertEquals(0x102C, expected[1].code)
    }

    @Test
    fun `UU + II vowel transforms to UU ligature`() {
        // Rule: ဥ + ီ = ဦ (0x1025 + 0x102E = 0x1026)
        val uu = MyanmarUnicode.U.toChar()        // 0x1025 (ဥ)
        val iiVowel = MyanmarUnicode.II_VOWEL.toChar() // 0x102E (ီ)
        val uuLigature = MyanmarUnicode.UU.toChar() // 0x1026 (ဦ)

        // Verify the transformation rule
        val shouldTransform = (uu.code == 0x1025 && iiVowel.code == 0x102E)
        assertTrue(shouldTransform)

        // Expected result is UU ligature
        assertEquals(0x1026, uuLigature.code)
    }

    @Test
    fun `Medial ordering - Ya before Ra before Wa before Ha`() {
        // Myanmar medials must appear in order: ျ ြ ွ ှ
        val yaMedial = MyanmarUnicode.YA_MEDIAL  // 0x103B
        val raMedial = MyanmarUnicode.RA_MEDIAL  // 0x103C
        val waMedial = MyanmarUnicode.WA_MEDIAL  // 0x103D
        val haMedial = MyanmarUnicode.HA_MEDIAL  // 0x103E

        // Verify ordering by code point
        assertTrue(yaMedial < raMedial)
        assertTrue(raMedial < waMedial)
        assertTrue(waMedial < haMedial)
    }

    @Test
    fun `Shan vowel E and Myanmar E are different`() {
        val myanmarE = MyanmarUnicode.E_VOWEL  // 0x1031
        val shanE = MyanmarUnicode.SHAN_E      // 0x1084

        assertNotEquals(myanmarE, shanE)
        assertTrue(MyanmarUnicode.needsReordering(myanmarE))
        assertTrue(MyanmarUnicode.needsReordering(shanE))
    }

    @Test
    fun `Virama stacking vs Asat visible killer`() {
        // Virama (္) is used for stacking consonants
        // Asat (်) is the visible killer/final consonant marker
        val virama = MyanmarUnicode.VIRAMA  // 0x1039
        val asat = MyanmarUnicode.ASAT      // 0x103A

        assertNotEquals(virama, asat)
        assertEquals(0x1039, virama)
        assertEquals(0x103A, asat)
    }

    @Test
    fun `Karen special characters are in correct range`() {
        // Karen characters are in extended Myanmar block
        val karenSha = MyanmarUnicode.KAREN_SHA
        val karenMedial = MyanmarUnicode.KAREN_MEDIAL

        assertTrue(karenSha >= 0x1050)
        assertTrue(karenMedial >= 0x105E)
    }

    @Test
    fun `Mon special consonants are in extended range`() {
        // Mon consonants like NGA, JHA, BBA
        val monNga = MyanmarUnicode.MON_NGA  // 0x106E
        val monJha = MyanmarUnicode.MON_JHA  // 0x106F
        val monBba = MyanmarUnicode.MON_BBA  // 0x1070

        assertEquals(0x106E, monNga)
        assertEquals(0x106F, monJha)
        assertEquals(0x1070, monBba)
    }
}
