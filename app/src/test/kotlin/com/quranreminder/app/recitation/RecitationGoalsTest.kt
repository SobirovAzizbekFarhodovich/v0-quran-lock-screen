package com.quranreminder.app.recitation

import com.quranreminder.app.database.DayActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class RecitationGoalsTest {
    private val today = 20_000L

    @Test fun streakCountsBackFromToday() {
        val days = listOf(DayActivity(today, 5), DayActivity(today - 1, 6), DayActivity(today - 2, 9))
        assertEquals(3, RecitationGoals.streak(days, goal = 5, today = today))
    }

    @Test fun unfinishedTodayDoesNotBreakTheStreak() {
        val days = listOf(DayActivity(today, 2), DayActivity(today - 1, 5), DayActivity(today - 2, 5))
        assertEquals(2, RecitationGoals.streak(days, goal = 5, today = today))
    }

    @Test fun aMissedDayEndsIt() {
        val days = listOf(DayActivity(today, 5), DayActivity(today - 2, 5))
        assertEquals(1, RecitationGoals.streak(days, goal = 5, today = today))
    }

    @Test fun belowGoalDaysDoNotCount() {
        val days = listOf(DayActivity(today - 1, 4))
        assertEquals(0, RecitationGoals.streak(days, goal = 5, today = today))
    }
}
