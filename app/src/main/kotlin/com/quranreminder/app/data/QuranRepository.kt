package com.quranreminder.app.data

import com.quranreminder.app.database.Bookmark
import com.quranreminder.app.database.BookmarkDao
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.database.QuranVerseDao
import com.quranreminder.app.database.Reminder
import com.quranreminder.app.database.ReminderDao
import com.quranreminder.app.database.SurahHeader
import kotlinx.coroutines.flow.Flow

class QuranRepository(
    private val quranVerseDao: QuranVerseDao,
    private val reminderDao: ReminderDao,
    private val bookmarkDao: BookmarkDao
) {
    // Quran Verses
    fun getAllVerses(): Flow<List<QuranVerse>> = quranVerseDao.getAllVerses()

    fun getVersesBySurah(surahNumber: Int): Flow<List<QuranVerse>> = 
        quranVerseDao.getVersesBySurah(surahNumber)

    fun getVerse(surahNumber: Int, ayahNumber: Int): Flow<QuranVerse?> =
        quranVerseDao.getVerse(surahNumber, ayahNumber)

    fun getAllSurahs(): Flow<List<SurahHeader>> = quranVerseDao.getAllSurahs()

    fun searchVerses(query: String): Flow<List<QuranVerse>> = 
        quranVerseDao.searchVerses(query)

    suspend fun insertVerses(verses: List<QuranVerse>) = 
        quranVerseDao.insertVerses(verses)

    suspend fun getVerseCount(): Int = quranVerseDao.getVerseCount()

    suspend fun replaceVerses(verses: List<QuranVerse>) {
        quranVerseDao.deleteAllVerses()
        quranVerseDao.insertVerses(verses)
    }

    suspend fun getRandomVerse(): QuranVerse? = quranVerseDao.getRandomVerse()

    suspend fun getAllSurahNumbers(): List<Int> = quranVerseDao.getAllSurahNumbers()

    // Reminders
    fun getAllReminders(): Flow<List<Reminder>> = reminderDao.getAllReminders()

    fun getEnabledReminders(): Flow<List<Reminder>> = reminderDao.getEnabledReminders()

    fun getReminderById(id: Int): Flow<Reminder?> = reminderDao.getReminderById(id)

    suspend fun insertReminder(reminder: Reminder): Long = 
        reminderDao.insertReminder(reminder)

    suspend fun updateReminder(reminder: Reminder) = 
        reminderDao.updateReminder(reminder)

    suspend fun deleteReminder(reminder: Reminder) = 
        reminderDao.deleteReminder(reminder)

    suspend fun deleteReminderById(id: Int) = 
        reminderDao.deleteReminderById(id)

    // Bookmarks
    fun getAllBookmarks(): Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()

    fun getBookmark(surahNumber: Int, ayahNumber: Int): Flow<Bookmark?> =
        bookmarkDao.getBookmark(surahNumber, ayahNumber)

    suspend fun insertBookmark(bookmark: Bookmark): Long = 
        bookmarkDao.insertBookmark(bookmark)

    suspend fun updateBookmark(bookmark: Bookmark) = 
        bookmarkDao.updateBookmark(bookmark)

    suspend fun deleteBookmark(bookmark: Bookmark) = 
        bookmarkDao.deleteBookmark(bookmark)

    suspend fun isBookmarked(surahNumber: Int, ayahNumber: Int): Boolean =
        bookmarkDao.isBookmarked(surahNumber, ayahNumber) > 0
}
