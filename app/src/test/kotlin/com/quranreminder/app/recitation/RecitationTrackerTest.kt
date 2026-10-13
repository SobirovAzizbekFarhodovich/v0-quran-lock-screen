package com.quranreminder.app.recitation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecitationTrackerTest {
    private val locator get() = QuranLocatorTest.locator

    private fun text(surah: Int, from: Int, to: Int) =
        (from..to).joinToString(" ") { a ->
            val first = locator.firstWordOf(surah, a)!!
            val next = locator.firstWordOf(surah, a + 1) ?: locator.endOfSurah(surah)
            locator.words.subList(first, next).joinToString(" ") { it.text }
        }

    @Test fun automaticModeFindsTheAyahAndFollowsOn() {
        val tracker = RecitationTracker.automatic(locator)
        assertNull(tracker.update("تبارك", final = false))
        assertFalse(tracker.located)
        tracker.update(text(67, 1, 1), final = false)
        assertTrue(tracker.located)
        assertEquals(67 to 1, tracker.passage.first().let { it.surah to it.ayah })
        // Reading on into 67:2 and 67:3 moves the current word along.
        tracker.update(text(67, 1, 3), final = false)
        val current = tracker.passage[tracker.currentIndex()]
        assertEquals(67, current.surah)
        assertTrue(current.ayah >= 3)
    }

    @Test fun automaticFinalIsCutAfterTheLastAyahRead() {
        val tracker = RecitationTracker.automatic(locator)
        tracker.update(text(67, 1, 3), final = true)
        val results = tracker.ayahResults()
        assertEquals(listOf(1, 2, 3), results.map { it.ayah })
        assertTrue(results.all { it.correct == it.words })
    }

    @Test fun fatihaWithItsBasmalaStartsAtTheFirstAyah() {
        val tracker = RecitationTracker.automatic(locator)
        tracker.update(text(1, 1, 3), final = true)
        assertEquals(listOf(1, 2, 3), tracker.ayahResults().map { it.ayah })
    }

    @Test fun basmalaBeforeAnotherSurahIsNotJudged() {
        val tracker = RecitationTracker.automatic(locator)
        tracker.update("بسم الله الرحمن الرحيم " + text(112, 1, 4), final = true)
        val results = tracker.ayahResults()
        assertEquals(listOf(1, 2, 3, 4), results.map { it.ayah })
        assertTrue(results.all { it.surah == 112 && it.correct == it.words })
    }

    @Test fun chosenWholeSurahPartlyReadMarksTheRestMissed() {
        val tracker = RecitationTracker.chosen(locator, 67, 1, fullSurah = true)!!
        tracker.update(text(67, 1, 2), final = true)
        val results = tracker.ayahResults()
        assertEquals(30, results.size)
        assertTrue(results[0].correct == results[0].words)
        assertTrue(results[5].missed == results[5].words)
    }

    @Test fun wholeBaqaraReadThroughIsAlignedQuickly() {
        val tracker = RecitationTracker.chosen(locator, 2, 1, fullSurah = true)!!
        val all = text(2, 1, 286)
        val started = System.currentTimeMillis()
        tracker.update(all, final = true)
        val took = System.currentTimeMillis() - started
        assertEquals(286, tracker.ayahResults().size)
        assertTrue(tracker.ayahResults().all { it.correct == it.words })
        assertTrue("took $took ms", took < 8_000)
    }
}
