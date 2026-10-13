package com.quranreminder.app.recitation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecitationAlignmentTest {
    // Al-Fatiha 1:2 and 1:6 as stored in the app (Uthmani, with harakat and marks).
    private val fatiha2 = RecitationAlignment.words("ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ")
    private val fatiha6 = RecitationAlignment.words("ٱهْدِنَا ٱلصِّرَٰطَ ٱلْمُسْتَقِيمَ")

    private fun statuses(a: WordAlignment) = a.marks.map { it.status }

    @Test fun correctRecitationInPlainSpelling() {
        val a = RecitationAlignment.align(fatiha2, "الحمد لله رب العالمين", final = true)
        assertEquals(List(4) { WordStatus.CORRECT }, statuses(a))
        assertEquals(100, a.accuracy)
    }

    @Test fun recognizerHarakatAndUthmaniAlefsAreNotMistakes() {
        val a = RecitationAlignment.align(fatiha6, "اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ", final = true)
        assertEquals(List(3) { WordStatus.CORRECT }, statuses(a))
    }

    @Test fun skippedWordIsMissed() {
        val a = RecitationAlignment.align(fatiha2, "الحمد لله العالمين", final = true)
        assertEquals(listOf(WordStatus.CORRECT, WordStatus.CORRECT, WordStatus.MISSED, WordStatus.CORRECT), statuses(a))
        assertEquals(75, a.accuracy)
    }

    @Test fun replacedWordIsWrongAndKeepsWhatWasHeard() {
        val a = RecitationAlignment.align(fatiha2, "الحمد لله رب الناس", final = true)
        assertEquals(WordStatus.WRONG, a.marks[3].status)
        assertEquals("الناس", a.marks[3].heard)
    }

    @Test fun liveResultLeavesTheUnreadTailPending() {
        val a = RecitationAlignment.align(fatiha2, "الحمد لله", final = false)
        assertEquals(listOf(WordStatus.CORRECT, WordStatus.CORRECT, WordStatus.PENDING, WordStatus.PENDING), statuses(a))
        assertEquals(2, a.reached)
    }

    @Test fun finalResultCountsTheUnreadTailAsMissed() {
        val a = RecitationAlignment.align(fatiha2, "الحمد لله", final = true)
        assertEquals(WordStatus.MISSED, a.marks[3].status)
        assertEquals(50, a.accuracy)
    }

    @Test fun extraWordsDoNotShiftTheRest() {
        val a = RecitationAlignment.align(fatiha2, "بسم الحمد لله رب العالمين", final = true)
        assertEquals(List(4) { WordStatus.CORRECT }, statuses(a))
        assertEquals(listOf("بسم"), a.extraWords)
    }

    @Test fun longVowelSpellingIsAccepted() {
        assertTrue(RecitationAlignment.sameWord("ٱلْمُسْتَقِيمَ", "المستقيم"))
        assertTrue(RecitationAlignment.sameWord("ٱلْمُسْتَقِيمَ", "المستقم"))
        assertTrue(RecitationAlignment.sameWord("ٱلصَّلَوٰةَ", "الصلاة"))
    }

    @Test fun aSwappedConsonantIsAMistake() {
        // ش read as س: a makhraj mistake, not spelling.
        assertFalse(RecitationAlignment.sameWord("يَشْعُرُونَ", "يسعرون"))
        assertFalse(RecitationAlignment.sameWord("ٱلْعَٰلَمِينَ", "العالم"))
    }

    @Test fun emptyTranscriptMissesEverything() {
        val a = RecitationAlignment.align(fatiha2, "", final = true)
        assertEquals(List(4) { WordStatus.MISSED }, statuses(a))
        assertEquals(0, a.accuracy)
    }
}
