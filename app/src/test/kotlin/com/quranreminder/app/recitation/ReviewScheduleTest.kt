package com.quranreminder.app.recitation

import com.quranreminder.app.database.RecitationResultEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewScheduleTest {
    private var clock = 0L
    private fun row(ayah: Int, day: Long, correct: Int, words: Int = 4) = RecitationResultEntity(
        surahNumber = 1, ayahNumber = ayah, wordCount = words, correctCount = correct,
        wrongCount = words - correct, missedCount = 0, mode = "check", createdAt = ++clock, day = day
    )

    @Test fun anAyahReadWithMistakesIsDueAtOnce() {
        val due = ReviewSchedule.due(listOf(row(2, 100, 3)), today = 100)
        assertEquals(listOf(2), due.map { it.ayah })
    }

    @Test fun perfectReadingsPushTheReviewOut() {
        val once = ReviewSchedule.schedule(listOf(row(2, 100, 4))).single()
        assertEquals(101, once.dueDay)
        val thrice = ReviewSchedule.schedule(listOf(row(2, 100, 4), row(2, 101, 4), row(2, 104, 4))).single()
        assertEquals(3, thrice.streak)
        assertEquals(111, thrice.dueDay)
    }

    @Test fun aMistakeResetsTheStreak() {
        val s = ReviewSchedule.schedule(listOf(row(2, 100, 4), row(2, 101, 4), row(2, 104, 2))).single()
        assertEquals(0, s.streak)
        assertEquals(104, s.dueDay)
    }

    @Test fun notYetDueAyahsAreNotListedAndMistakesComeFirst() {
        val results = listOf(row(2, 100, 4), row(3, 95, 4), row(4, 100, 1))
        val due = ReviewSchedule.due(results, today = 100)
        assertEquals(listOf(4, 3), due.map { it.ayah })
        assertTrue(due.none { it.ayah == 2 })
    }
}
