package com.quranreminder.app.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One ayah of one recitation check, kept on the device so history, the review list and the
 * daily goal work offline. A full-surah check stores a row per ayah.
 */
@Entity(
    tableName = "recitation_results",
    indices = [
        Index(value = ["surahNumber", "ayahNumber"]),
        Index(value = ["day"]),
        Index(value = ["clientId"], unique = true)
    ]
)
data class RecitationResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surahNumber: Int,
    val ayahNumber: Int,
    val wordCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val missedCount: Int,
    /** "check" or "memorize". */
    val mode: String,
    val createdAt: Long,
    /** Local calendar day (epoch day), so "today" and streaks follow the user's clock. */
    val day: Long,
    /** Stable id shared with the server backup, so a row is never stored twice. */
    @androidx.room.ColumnInfo(defaultValue = "")
    val clientId: String = java.util.UUID.randomUUID().toString(),
    /** Sent to the server backup. */
    @androidx.room.ColumnInfo(defaultValue = "0")
    val synced: Boolean = false
)

data class DayActivity(val day: Long, val ayahs: Int)

/** The latest result of an ayah whose last attempt still had mistakes. */
data class ReviewAyah(
    val surahNumber: Int,
    val ayahNumber: Int,
    val wordCount: Int,
    val correctCount: Int,
    val createdAt: Long,
    val attempts: Int
)

@Dao
interface RecitationHistoryDao {
    @Insert
    suspend fun insertAll(results: List<RecitationResultEntity>)

    /** Rows restored from the server backup; ones already here (same clientId) are skipped. */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertRestored(results: List<RecitationResultEntity>)

    @Query("SELECT * FROM recitation_results WHERE synced = 0 ORDER BY createdAt LIMIT :limit")
    suspend fun unsynced(limit: Int = 500): List<RecitationResultEntity>

    @Query("UPDATE recitation_results SET synced = 1 WHERE clientId IN (:clientIds)")
    suspend fun markSynced(clientIds: List<String>)

    @Query("SELECT * FROM recitation_results ORDER BY createdAt")
    fun observeAll(): Flow<List<RecitationResultEntity>>

    @Query("DELETE FROM recitation_results")
    suspend fun clear()

    /** Distinct ayahs recited per day since [fromDay]. */
    @Query(
        "SELECT day, COUNT(DISTINCT surahNumber * 1000 + ayahNumber) AS ayahs " +
            "FROM recitation_results WHERE day >= :fromDay GROUP BY day ORDER BY day"
    )
    fun observeActivity(fromDay: Long): Flow<List<DayActivity>>

    /** Every day with activity, newest first: enough to compute a streak of any length. */
    @Query(
        "SELECT day, COUNT(DISTINCT surahNumber * 1000 + ayahNumber) AS ayahs " +
            "FROM recitation_results GROUP BY day ORDER BY day DESC"
    )
    fun observeAllActivity(): Flow<List<DayActivity>>

    /** Ayahs to go over again: their most recent attempt was not perfect. */
    @Query(
        "SELECT r.surahNumber, r.ayahNumber, r.wordCount, r.correctCount, r.createdAt, x.attempts " +
            "FROM recitation_results r JOIN (" +
            "  SELECT surahNumber AS s, ayahNumber AS a, MAX(createdAt) AS last, COUNT(*) AS attempts " +
            "  FROM recitation_results GROUP BY surahNumber, ayahNumber" +
            ") x ON r.surahNumber = x.s AND r.ayahNumber = x.a AND r.createdAt = x.last " +
            "WHERE r.correctCount < r.wordCount ORDER BY r.createdAt DESC LIMIT :limit"
    )
    fun observeReview(limit: Int = 30): Flow<List<ReviewAyah>>

    @Query("SELECT * FROM recitation_results ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<RecitationResultEntity>>

    @Query("SELECT COUNT(DISTINCT surahNumber * 1000 + ayahNumber) FROM recitation_results")
    fun observeDistinctAyahs(): Flow<Int>
}
