package com.quranreminder.app.lockscreen

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.FileProvider
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.notification.AppLanguage
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
                val cropHint = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                // Any phone can opt in to the ayah on both screens (Honor/Huawei also need it
                // in dark mode, where their keyguard shows the *home* wallpaper).
                val flags = if (isApplyToHomeEnabled(context)) {
                    WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
                } else {
                    WallpaperManager.FLAG_LOCK
                }
                wallpaperManager.setBitmap(bitmap, cropHint, true, flags)
            } else {
                wallpaperManager.setBitmap(bitmap)
            }

            if (rememberForHour) {
                rememberHourlyVerse(context, verse)
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_LAST_APPLIED_HOUR, renderSignature(context, verse)).apply()
            }
        }
    }

    /**
     * Identifies what is on the lock screen: hour, verse and its current text, language, font
     * and background. Stored under [KEY_LAST_APPLIED_HOUR] (name kept for compatibility); the
     * hourly worker skips work only when the signature is unchanged.
     */
    fun renderSignature(context: Context, verse: QuranVerse): String = listOf(
        currentHour(),
        "${verse.surahNumber}:${verse.ayahNumber}",
        (verse.surahName + verse.translation).hashCode().toString(),
        getLockScreenLanguage(context),
        getLockScreenFont(context).id,
        LockScreenBackgroundManager.getSelectedSource(context),
        LockScreenBackgroundManager.getSelectedSampleId(context).orEmpty(),
        if (isApplyToHomeEnabled(context)) "home" else ""
    ).joinToString("|")

    /**
     * On by default: the ayah goes on the home screen too, so it shows on every phone (Honor/
     * Huawei show the home wallpaper on the lock screen in dark mode). Users can turn it off
     * to keep their own home wallpaper and put the ayah on the lock screen only.
     */
    fun isApplyToHomeEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_APPLY_TO_HOME, true)

    fun setApplyToHome(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_APPLY_TO_HOME, enabled).apply()
    }

    /**
     * True on Honor/Huawei (MagicOS/EMUI) devices while dark mode is on: their keyguard then
     * shows a dimmed copy of the home wallpaper instead of the separate lock wallpaper.
     */
    fun lockScreenShowsHomeInDarkMode(context: Context): Boolean {
        val vendor = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase(Locale.ROOT)
        if ("honor" !in vendor && "huawei" !in vendor) return false
        val nightMask = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun saveSelectedVerse(context: Context, verse: QuranVerse) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SELECTED_SURAH, verse.surahNumber)
            .putInt(KEY_SELECTED_AYAH, verse.ayahNumber)
            .remove(KEY_LAST_HOUR)
            .remove(KEY_LAST_APPLIED_HOUR)
            .apply()
    }

    fun clearSelectedVerse(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SELECTED_SURAH)
            .remove(KEY_SELECTED_AYAH)
            .remove(KEY_LAST_HOUR)
            .remove(KEY_LAST_APPLIED_HOUR)
            .apply()
    }

    fun setSelectedCategory(context: Context, category: LockScreenAyahCategory?) {
        val editor = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_HOUR)
            .remove(KEY_LAST_APPLIED_HOUR)
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
            .remove(KEY_LAST_APPLIED_HOUR)
            .apply()
    }

    fun getLockScreenFont(context: Context): LockScreenFontFamily {
        val id = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LOCKSCREEN_FONT, LockScreenFontFamily.STANDART.id)
        return LockScreenFontFamily.fromId(id)
    }

    fun setLockScreenFont(context: Context, font: LockScreenFontFamily) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCKSCREEN_FONT, font.id)
            .remove(KEY_LAST_HOUR)
            .remove(KEY_LAST_APPLIED_HOUR)
            .apply()
    }

    fun rememberHourlyVerse(context: Context, verse: QuranVerse) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_HOUR, currentHour())
            .putInt(KEY_LAST_SURAH, verse.surahNumber)
            .putInt(KEY_LAST_AYAH, verse.ayahNumber)
            .apply()
    }

    fun currentHour(): String = SimpleDateFormat("yyyy-MM-dd-HH", Locale.US).format(Date())

    /**
     * Renders and shares the verse image. Throws [java.io.IOException] / [SecurityException]
     * (or [android.content.ActivityNotFoundException]) so callers can show a localized error.
     */
    suspend fun shareImage(context: Context, verse: QuranVerse) {
        val language = AppLanguage.current(context)
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
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserTitle = AppLanguage.text(language, "lockPreview.shareChooser")
            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    const val PREFS = "daily_ayah_wallpaper"
    const val KEY_LAST_HOUR = "last_hour"
    const val KEY_LAST_SURAH = "last_surah"
    const val KEY_LAST_AYAH = "last_ayah"
    const val KEY_LAST_APPLIED_HOUR = "last_applied_hour"
    const val KEY_SELECTED_SURAH = "selected_surah"
    const val KEY_SELECTED_AYAH = "selected_ayah"
    const val KEY_SELECTED_CATEGORY = "selected_category"
    const val KEY_LOCKSCREEN_LANGUAGE = "lockscreen_language"
    const val KEY_LOCKSCREEN_FONT = "lockscreen_font"
    const val KEY_APPLY_TO_HOME = "apply_to_home"
}

/**
 * Lettering for the lock-screen translation: real bundled fonts that cover Uzbek Latin and
 * Cyrillic in full (Ққ Ғғ Ўў Ҳҳ ʻ ʼ). The Arabic is always Amiri Quran.
 */
enum class LockScreenFontFamily(
    val id: String,
    @androidx.annotation.FontRes val fontRes: Int,
    /** Cormorant is drawn small for its size; scale it up to read like the others. */
    val sizeScale: Float = 1f
) {
    STANDART("standart", com.quranreminder.app.R.font.golos_text),
    KLASSIK("klassik", com.quranreminder.app.R.font.lock_lora),
    NAFIS("nafis", com.quranreminder.app.R.font.lock_cormorant, 1.14f),
    YUMSHOQ("yumshoq", com.quranreminder.app.R.font.lock_nunito);

    val composeFontFamily: androidx.compose.ui.text.font.FontFamily by lazy {
        androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(fontRes))
    }

    fun typeface(context: Context): android.graphics.Typeface =
        androidx.core.content.res.ResourcesCompat.getFont(context, fontRes) ?: android.graphics.Typeface.DEFAULT

    companion object {
        fun fromId(id: String?): LockScreenFontFamily = when (id) {
            // The former Unicode "fancy" styles map to the nearest real font.
            "fancy_bold_italic", "fancy_italic" -> NAFIS
            "fancy_bold", "default" -> STANDART
            else -> entries.firstOrNull { it.id == id } ?: STANDART
        }
    }
}


/**
 * Lock-screen verse categories. Matching is primarily by a curated, language-independent list
 * of surah:ayah references (translation wording differs per language and translator, so
 * keywords alone are unreliable). Keywords for every supported language — uz_cyrillic,
 * uz_latin, ru, en — are only a fallback when none of the curated verses are in Room.
 */
enum class LockScreenAyahCategory(
    val id: String,
    val label: String,
    val references: List<Pair<Int, Int>>,
    val keywords: List<String>
) {
    SABR(
        "sabr", "Sabr",
        refs(
            "2:45 2:153 2:155 2:156 2:157 2:177 2:249 3:142 3:146 3:186 3:200 8:46 11:11 11:115 " +
                "12:18 12:83 13:22 13:24 16:96 16:126 16:127 20:130 28:54 29:59 31:17 39:10 40:55 " +
                "41:35 42:43 46:35 50:39 70:5 73:10 76:12 76:24 90:17 103:3"
        ),
        listOf("сабр", "чида", "sabr", "chida", "терпе", "терпели", "стойк", "patien", "persever", "steadfast")
    ),
    TAVBA(
        "tavba", "Tavba",
        refs(
            "2:37 2:160 2:222 3:16 3:89 3:133 3:135 3:136 4:17 4:106 4:110 5:39 5:74 6:54 7:23 " +
                "7:153 9:104 9:118 11:3 11:52 11:61 11:90 16:119 20:82 24:31 25:70 25:71 28:16 " +
                "39:53 39:54 40:3 40:7 42:25 66:8 71:10 110:3"
        ),
        listOf(
            "тавба", "мағфират", "кечир", "гуноҳ", "tavba", "mag'firat", "magʻfirat", "kechir", "gunoh",
            "покая", "покаян", "прощ", "грех", "repent", "forgiv", "sins"
        )
    ),
    UMID(
        "umid", "Umid",
        refs(
            "2:186 2:214 2:216 2:286 3:139 3:173 9:40 12:87 13:28 15:56 21:87 21:88 29:69 39:53 " +
                "40:60 65:2 65:3 65:7 93:3 93:4 93:5 94:5 94:6"
        ),
        listOf("умид", "ноумид", "нажот", "umid", "noumid", "najot", "надежд", "отчаива", "отчая", "hope", "despair")
    ),
    RIZQ(
        "rizq", "Rizq",
        refs(
            "2:172 2:212 2:261 2:268 3:37 11:6 13:26 14:7 16:112 17:30 20:132 29:60 29:62 34:36 " +
                "34:39 35:3 42:12 42:19 42:27 51:22 51:58 62:10 65:2 65:3 67:15 71:10 71:11 71:12"
        ),
        listOf("ризқ", "неъмат", "rizq", "ne'mat", "neʼmat", "удел", "пропитани", "provision", "sustenance")
    ),
    OTA_ONA(
        "ota_ona", "Ota-ona",
        refs(
            "2:83 2:180 2:215 4:36 6:151 14:41 17:23 17:24 17:25 19:14 19:32 27:19 29:8 31:14 " +
                "31:15 46:15 46:17 71:28"
        ),
        listOf("ота-она", "волидайн", "ota-ona", "volidayn", "родител", "parent")
    ),
    NAMOZ(
        "namoz", "Namoz",
        refs(
            "2:3 2:43 2:45 2:110 2:153 2:238 2:239 4:103 5:55 8:3 9:71 11:114 14:31 14:40 17:78 " +
                "17:79 20:14 20:132 22:41 23:1 23:2 23:9 24:56 29:45 31:17 62:9 70:23 70:34 98:5 " +
                "107:4 107:5 108:2"
        ),
        listOf("намоз", "сажда", "namoz", "sajda", "намаз", "молитв", "prayer", "prostrat")
    )
}

private fun refs(spec: String): List<Pair<Int, Int>> =
    spec.trim().split(Regex("\\s+")).map { ref ->
        val (surah, ayah) = ref.split(":")
        surah.toInt() to ayah.toInt()
    }
