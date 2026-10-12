package com.quranreminder.app.recitation

import android.content.Context
import com.quranreminder.app.database.DayActivity
import java.time.LocalDate
import java.time.ZoneId

/** Daily ayah goal (on the device) and the streak worked out from recitation history. */
object RecitationGoals {
    private const val PREFS = "recitation_goals"
    private const val KEY_DAILY_GOAL = "daily_ayah_goal"
    const val DEFAULT_GOAL = 5
    val GOAL_RANGE = 1..50

    private var goalState: androidx.compose.runtime.MutableState<Int>? = null

    /** The daily goal as Compose state, shared by every screen that shows it. */
    fun dailyGoal(context: Context): androidx.compose.runtime.State<Int> = goalState ?: androidx.compose.runtime.mutableStateOf(
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DAILY_GOAL, DEFAULT_GOAL)
            .coerceIn(GOAL_RANGE)
    ).also { goalState = it }

    fun setDailyGoal(context: Context, goal: Int) {
        val value = goal.coerceIn(GOAL_RANGE)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DAILY_GOAL, value).apply()
        (goalState ?: dailyGoal(context) as androidx.compose.runtime.MutableState<Int>).value = value
    }

    fun today(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    fun dayOf(millis: Long): Long =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    /**
     * Consecutive days, ending today, on which the goal was met. A day still in progress does
     * not break it: until today's goal is met the streak counts up to yesterday.
     */
    fun streak(activity: List<DayActivity>, goal: Int, today: Long): Int {
        val met = activity.filter { it.ayahs >= goal }.map { it.day }.toHashSet()
        var day = if (today in met) today else today - 1
        var count = 0
        while (day in met) {
            count++
            day--
        }
        return count
    }
}
