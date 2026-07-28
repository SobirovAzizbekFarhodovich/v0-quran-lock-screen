package com.quranreminder.app.lockscreen

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.FileProvider
import com.quranreminder.app.database.QuranVerse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LockScreenWallpaperApplier {
    suspend fun apply(context: Context, verse: QuranVerse, rememberForHour: Boolean = true) {
        withContext(Dispatchers.IO) {
            val bitmap = LockScreenWallpaperRenderer.render(context.applicationContext, verse)
            val wallpaperManager = WallpaperManager.getInstance(context.applicationContext)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(
                    bitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_LOCK
                )
            } else {
                wallpaperManager.setBitmap(bitmap)
            }

            if (rememberForHour) {
                val currentHour = SimpleDateFormat("yyyy-MM-dd-HH", Locale.US).format(Date())
                context.applicationContext
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_HOUR, currentHour)
                    .putInt(KEY_LAST_SURAH, verse.surahNumber)
                    .putInt(KEY_LAST_AYAH, verse.ayahNumber)
                    .apply()
            }
        }
    }

    fun saveSelectedVerse(context: Context, verse: QuranVerse) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SELECTED_SURAH, verse.surahNumber)
            .putInt(KEY_SELECTED_AYAH, verse.ayahNumber)
            .remove(KEY_LAST_HOUR)
            .apply()
    }

    fun clearSelectedVerse(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SELECTED_SURAH)
            .remove(KEY_SELECTED_AYAH)
            .remove(KEY_LAST_HOUR)
            .apply()
    }

    fun setSelectedCategory(context: Context, category: LockScreenAyahCategory?) {
        val editor = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_HOUR)
        if (category == null) {
            editor.remove(KEY_SELECTED_CATEGORY)
        } else {
            editor.putString(KEY_SELECTED_CATEGORY, category.id)
        }
        editor.apply()
    }

    fun getSelectedCategory(context: Context): LockScreenAyahCategory? {
        val id = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_CATEGORY, null)
        return LockScreenAyahCategory.entries.firstOrNull { it.id == id }
    }

    fun getLockScreenLanguage(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LOCKSCREEN_LANGUAGE, "uz_cyrillic") ?: "uz_cyrillic"
    }

    fun setLockScreenLanguage(context: Context, language: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCKSCREEN_LANGUAGE, language)
            .remove(KEY_LAST_HOUR)
            .apply()
    }

    suspend fun shareImage(context: Context, verse: QuranVerse) {
        val uri = withContext(Dispatchers.IO) {
            val bitmap = LockScreenWallpaperRenderer.render(context.applicationContext, verse)
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(dir, "quran-lock-screen-${verse.surahNumber}-${verse.ayahNumber}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Oyat rasmini ulashish"))
        }
    }

    const val PREFS = "daily_ayah_wallpaper"
    const val KEY_LAST_HOUR = "last_hour"
    const val KEY_LAST_SURAH = "last_surah"
    const val KEY_LAST_AYAH = "last_ayah"
    const val KEY_SELECTED_SURAH = "selected_surah"
    const val KEY_SELECTED_AYAH = "selected_ayah"
    const val KEY_SELECTED_CATEGORY = "selected_category"
    const val KEY_LOCKSCREEN_LANGUAGE = "lockscreen_language"
}

enum class LockScreenAyahCategory(
    val id: String,
    val label: String,
    val keywords: List<String>
) {
    SABR("sabr", "Sabr", listOf("сабр", "сабрли", "сабр қил", "чида")),
    TAVBA("tavba", "Tavba", listOf("тавба", "мағфират", "кечир", "гуноҳ", "раҳмат")),
    UMID("umid", "Umid", listOf("умид", "ноумид", "раҳмат", "нусрат", "ёрдам")),
    RIZQ("rizq", "Rizq", listOf("ризқ", "неъмат", "берур", "бой", "камбағал")),
    OTA_ONA("ota_ona", "Ota-ona", listOf("ота", "она", "ота-она", "волидайн")),
    NAMOZ("namoz", "Namoz", listOf("намоз", "салот", "сажда", "рукуъ"))
}
