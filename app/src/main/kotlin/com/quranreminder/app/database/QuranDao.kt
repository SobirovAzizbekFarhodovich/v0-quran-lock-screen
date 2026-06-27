package com.quranreminder.app.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuranVerseDao {
    @Query("SELECT * FROM quran_verses ORDER BY surahNumber, ayahNumber")
    fun getAllVerses(): Flow<List<QuranVerse>>

    @Query("SELECT * FROM quran_verses ORDER BY surahNumber, ayahNumber")
    suspend fun getAllVersesOnce(): List<QuranVerse>

    @Query("SELECT * FROM quran_verses WHERE surahNumber = :surahNumber ORDER BY ayahNumber")
    fun getVersesBySurah(surahNumber: Int): Flow<List<QuranVerse>>

    @Query("SELECT * FROM quran_verses WHERE surahNumber = :surahNumber AND ayahNumber = :ayahNumber")
    fun getVerse(surahNumber: Int, ayahNumber: Int): Flow<QuranVerse?>

    @Query("SELECT * FROM quran_verses WHERE surahNumber = :surahNumber AND ayahNumber = :ayahNumber")
    suspend fun getVerseOnce(surahNumber: Int, ayahNumber: Int): QuranVerse?

    @Query("SELECT DISTINCT surahNumber, surahName, surahNameArabic FROM quran_verses ORDER BY surahNumber")
    fun getAllSurahs(): Flow<List<SurahHeader>>

    @Query("SELECT * FROM quran_verses WHERE ayahText LIKE '%' || :query || '%' OR translation LIKE '%' || :query || '%' OR surahName LIKE '%' || :query || '%'")
    fun searchVerses(query: String): Flow<List<QuranVerse>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerse(verse: QuranVerse)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerses(verses: List<QuranVerse>)

    @Update
    suspend fun updateVerse(verse: QuranVerse)

    @Delete
    suspend fun deleteVerse(verse: QuranVerse)

    @Query("SELECT COUNT(*) FROM quran_verses")
    suspend fun getVerseCount(): Int

    @Query("SELECT * FROM quran_verses ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomVerse(): QuranVerse?

    @Query("SELECT DISTINCT surahNumber FROM quran_verses ORDER BY surahNumber")
    suspend fun getAllSurahNumbers(): List<Int>
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY hourOfDay, minuteOfHour")
    fun getAllReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun getReminderById(id: Int): Flow<Reminder?>

    @Query("SELECT * FROM reminders WHERE isEnabled = 1")
    fun getEnabledReminders(): Flow<List<Reminder>>

    @Insert
    suspend fun insertReminder(reminder: Reminder): Long

    @Update
    suspend fun updateReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Int)

    @Query("SELECT COUNT(*) FROM reminders")
    suspend fun getReminderCount(): Int
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE surahNumber = :surahNumber AND ayahNumber = :ayahNumber")
    fun getBookmark(surahNumber: Int, ayahNumber: Int): Flow<Bookmark?>

    @Insert
    suspend fun insertBookmark(bookmark: Bookmark): Long

    @Update
    suspend fun updateBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("SELECT COUNT(*) FROM bookmarks WHERE surahNumber = :surahNumber AND ayahNumber = :ayahNumber")
    suspend fun isBookmarked(surahNumber: Int, ayahNumber: Int): Int
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettings(): Flow<AppSettings?>

    @Query("SELECT COUNT(*) FROM app_settings WHERE id = 1")
    suspend fun getSettingsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AppSettings)

    @Update
    suspend fun updateSettings(settings: AppSettings)
}

data class SurahHeader(
    val surahNumber: Int,
    val surahName: String,
    val surahNameArabic: String
)
