package com.quranreminder.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quran_verses")
data class QuranVerse(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val surahNumber: Int,
    val surahName: String,
    val surahNameArabic: String,
    val ayahNumber: Int,
    val ayahText: String,
    val ayahTextArabic: String,
    val translation: String, // Uzbek translation (Sadik Muhammad Yusuf)
    val transliteration: String = ""
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val surahNumber: Int,
    val ayahNumber: Int,
    val reminderText: String = "",
    val hourOfDay: Int = 8,
    val minuteOfHour: Int = 0,
    val isEnabled: Boolean = true,
    val daysOfWeek: String = "0,1,2,3,4,5,6", // "0" = Sunday, comma-separated
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val surahNumber: Int,
    val ayahNumber: Int,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    val notificationsEnabled: Boolean = true,
    val vibrateOnReminder: Boolean = true,
    val soundEnabled: Boolean = true,
    val fontSize: Int = 16,
    val darkMode: Boolean = false,
    val lockScreenNotifications: Boolean = true
)
