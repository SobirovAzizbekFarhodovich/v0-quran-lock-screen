package com.quranreminder.app.recitation

import com.quranreminder.app.database.RecitationResultEntity

/** An ayah due for review: when it is due and how it went last time. */
data class DueAyah(
    val surah: Int,
    val ayah: Int,
    val dueDay: Long,
    val lastAccuracy: Int,
    val attempts: Int,
    /** Perfect readings in a row, ending with the last one. */
    val streak: Int
)

/**
 * Spaced review of recited ayahs: one read with mistakes comes back at once; each perfect
 * reading in a row pushes the next review further out (1, 3, 7, 16, 35, then 80 days), so
 * memorised ayahs come back just before they would be forgotten.
 */
object ReviewSchedule {
    private val INTERVALS = longArrayOf(1, 3, 7, 16, 35, 80)

    fun schedule(results: List<RecitationResultEntity>): List<DueAyah> =
        results.groupBy { it.surahNumber to it.ayahNumber }.map { (key, rows) ->
            val ordered = rows.sortedBy { it.createdAt }
            val last = ordered.last()
            val streak = ordered.asReversed().takeWhile { it.correctCount >= it.wordCount && it.wordCount > 0 }.size
            val due = if (streak == 0) last.day else last.day + INTERVALS[minOf(streak, INTERVALS.size) - 1]
            DueAyah(
                surah = key.first,
                ayah = key.second,
                dueDay = due,
                lastAccuracy = if (last.wordCount == 0) 0 else last.correctCount * 100 / last.wordCount,
                attempts = ordered.size,
                streak = streak
            )
        }

    /** Ayahs due today or overdue: mistakes first, then the longest overdue. */
    fun due(results: List<RecitationResultEntity>, today: Long): List<DueAyah> =
        schedule(results).filter { it.dueDay <= today }
            .sortedWith(compareBy<DueAyah> { it.streak > 0 }.thenBy { it.dueDay }.thenBy { it.surah }.thenBy { it.ayah })
}
