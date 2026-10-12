package com.quranreminder.app.recitation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HarakatCheckTest {
    @Test fun uthmaniSpellingAgainstTheModelsSpellingIsNoSlip() {
        // Mushaf (Uthmani) vs what the Quran model writes for a correct reading of 1:2.
        val written = "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ".split(" ")
        val heard = "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ".split(" ")
        written.zip(heard).forEach { (w, h) -> assertNull("$w / $h", HarakatCheck.slip(w, h)) }
    }

    @Test fun vowelSwapInsideTheWordIsCaught() {
        // Warsh مَيْسُرَةٍ against the Hafs مَيْسَرَةٍ (2:280): sin with damma instead of fatha.
        val slip = HarakatCheck.slip("مَيْسَرَةٍ", "مَيْسُرَةٍ")!!
        assertEquals('س', slip.letter)
        assertEquals(3, slip.position)
        assertEquals(Vowel.FATHA, slip.expected)
        assertEquals(Vowel.DAMMA, slip.heard)
    }

    @Test fun firstLetterVowelIsJudged() {
        val slip = HarakatCheck.slip("يَكْذِبُونَ", "يُكَذِّبُونَ")!!
        assertEquals(1, slip.position)
        assertEquals(Vowel.FATHA, slip.expected)
        assertEquals(Vowel.DAMMA, slip.heard)
    }

    @Test fun lastLetterIsNeverJudged() {
        // Stopping on أَحَد with sukun is how the ayah is read.
        assertNull(HarakatCheck.slip("أَحَدٌ", "أَحَدْ"))
        assertNull(HarakatCheck.slip("ٱلصَّمَدُ", "الصَّمَدَ"))
    }

    @Test fun missingShaddaInsideTheWordIsCaught() {
        val slip = HarakatCheck.slip("رَبِّكَ", "رَبِكَ")!!
        assertEquals('ب', slip.letter)
        assertEquals(Vowel.SHADDA, slip.expected)
    }

    @Test fun differentLettersAreLeftToTheWordCheck() {
        assertNull(HarakatCheck.slip("قُلْ", "قَتَلَ"))
    }

    @Test fun plainSpellingWithoutMarksIsNotJudged() {
        assertNull(HarakatCheck.slip("لِلَّهِ", "لله"))
    }

    @Test fun longVowelReadForAShortOneShowsAsAVowelSlip() {
        // «قَالَ» for «قُلْ»: letters match once long alefs are set aside, the vowel does not.
        assertEquals(Vowel.DAMMA, HarakatCheck.slip("قُلْ", "قَالَ")!!.expected)
    }

    @Test fun alignmentMarksHarakatSlipsOnlyInFinalResults() {
        val target = RecitationAlignment.words("فَٱنظِرَةٌ إِلَىٰ مَيْسَرَةٍ")
        val final = RecitationAlignment.align(target, "فَنَظِرَةٌ إِلَى مَيْسُرَةٍ", final = true)
        assertEquals(WordStatus.HARAKAT, final.marks[2].status)
        val live = RecitationAlignment.align(target, "فَنَظِرَةٌ إِلَى مَيْسُرَةٍ", final = false)
        assertEquals(WordStatus.CORRECT, live.marks[2].status)
    }
}
