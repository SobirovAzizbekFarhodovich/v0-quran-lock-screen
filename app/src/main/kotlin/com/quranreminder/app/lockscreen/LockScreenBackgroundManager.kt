package com.quranreminder.app.lockscreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.DrawableRes
import com.quranreminder.app.R
import java.io.File

object LockScreenBackgroundManager {
    data class Sample(
        val id: String,
        val label: String,
        @DrawableRes val resId: Int
    )

    val samples = listOf(
        Sample("mountain", "Tog' tongi", R.drawable.bg_mountain_sunrise),
        Sample("ocean", "Dengiz", R.drawable.bg_ocean_horizon),
        Sample("desert", "Sahro", R.drawable.bg_desert_sunset),
        Sample("forest", "O'rmon", R.drawable.bg_misty_forest),
        Sample("night", "Tun", R.drawable.bg_starry_hills),
        Sample("rain", "Yomg'ir", R.drawable.bg_rainy_window)
    )

    fun setSample(context: Context, sample: Sample) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOURCE, SOURCE_SAMPLE)
            .putString(KEY_SAMPLE_ID, sample.id)
            .remove(KEY_CUSTOM_PATH)
            .apply()
    }

    fun setSystemWallpaper(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOURCE, SOURCE_SYSTEM)
            .remove(KEY_SAMPLE_ID)
            .remove(KEY_CUSTOM_PATH)
            .apply()
    }

    fun setCustomFromUri(context: Context, uri: Uri): Boolean {
        return runCatching {
            val file = File(context.filesDir, CUSTOM_FILE_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SOURCE, SOURCE_CUSTOM)
                .putString(KEY_CUSTOM_PATH, file.absolutePath)
                .remove(KEY_SAMPLE_ID)
                .apply()
            true
        }.getOrDefault(false)
    }

    fun loadSelectedBitmap(context: Context): Bitmap? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_SOURCE, SOURCE_SYSTEM)) {
            SOURCE_CUSTOM -> {
                prefs.getString(KEY_CUSTOM_PATH, null)
                    ?.let { BitmapFactory.decodeFile(it) }
            }
            SOURCE_SAMPLE -> {
                val sampleId = prefs.getString(KEY_SAMPLE_ID, null)
                samples.firstOrNull { it.id == sampleId }?.let { sample ->
                    BitmapFactory.decodeResource(context.resources, sample.resId)
                }
            }
            else -> null
        }
    }

    private const val PREFS = "lock_screen_background"
    private const val KEY_SOURCE = "source"
    private const val KEY_SAMPLE_ID = "sample_id"
    private const val KEY_CUSTOM_PATH = "custom_path"
    private const val SOURCE_SYSTEM = "system"
    private const val SOURCE_SAMPLE = "sample"
    private const val SOURCE_CUSTOM = "custom"
    private const val CUSTOM_FILE_NAME = "lock_screen_custom_background"
}
