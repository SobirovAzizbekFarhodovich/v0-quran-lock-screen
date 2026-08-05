package com.quranreminder.app.lockscreen

import android.content.Context
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.database.QuranVerse

object LockScreenVerseSelector {
    suspend fun select(context: Context, database: QuranDatabase, forceNew: Boolean = false): QuranVerse? {
        val prefs = context.applicationContext
            .getSharedPreferences(LockScreenWallpaperApplier.PREFS, Context.MODE_PRIVATE)
        val currentHour = LockScreenWallpaperApplier.currentHour()
        val lastHour = prefs.getString(LockScreenWallpaperApplier.KEY_LAST_HOUR, null)

        val isNewHour = forceNew || (lastHour != null && lastHour != currentHour)

        if (isNewHour) {
            // New hour arrived: clear old single-verse pin so hourly rotation continues
            prefs.edit()
                .remove(LockScreenWallpaperApplier.KEY_SELECTED_SURAH)
                .remove(LockScreenWallpaperApplier.KEY_SELECTED_AYAH)
                .apply()
        } else {
            val selectedSurah = prefs.getInt(LockScreenWallpaperApplier.KEY_SELECTED_SURAH, -1)
            val selectedAyah = prefs.getInt(LockScreenWallpaperApplier.KEY_SELECTED_AYAH, -1)
            if (selectedSurah > 0 && selectedAyah > 0) {
                database.quranVerseDao().getVerseOnce(selectedSurah, selectedAyah)?.let { return it }
            }

            if (lastHour == currentHour) {
                val lastSurah = prefs.getInt(LockScreenWallpaperApplier.KEY_LAST_SURAH, -1)
                val lastAyah = prefs.getInt(LockScreenWallpaperApplier.KEY_LAST_AYAH, -1)
                if (lastSurah > 0 && lastAyah > 0) {
                    database.quranVerseDao().getVerseOnce(lastSurah, lastAyah)?.let { return it }
                }
            }
        }

        val selectedCategory = LockScreenWallpaperApplier.getSelectedCategory(context)
        if (selectedCategory != null) {
            val matchingVerses = database.quranVerseDao()
                .getAllVersesOnce()
                .filter { verse -> selectedCategory.matches(verse.translation) }

            if (matchingVerses.isNotEmpty()) {
                val newVerse = matchingVerses.random()
                LockScreenWallpaperApplier.rememberHourlyVerse(context, newVerse)
                return newVerse
            }
        }

        return database.quranVerseDao().getRandomVerse()?.also { newVerse ->
            LockScreenWallpaperApplier.rememberHourlyVerse(context, newVerse)
        }
    }
}

fun LockScreenAyahCategory.matches(text: String): Boolean {
    val normalized = text.lowercase()
    return keywords.any { keyword -> normalized.contains(keyword) }
}
