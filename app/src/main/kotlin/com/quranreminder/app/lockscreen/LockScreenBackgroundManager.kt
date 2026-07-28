package com.quranreminder.app.lockscreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.DrawableRes
import com.quranreminder.app.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LockScreenBackgroundManager {
    data class Sample(
        val id: String,
        val label: String,
        @DrawableRes val resId: Int
    )

    data class RemoteSample(
        val id: String,
        val label: String,
        val url: String
    )

    val samples = listOf(
        Sample("local_1", "Manzara 1", R.drawable.bg_local_1),
        Sample("local_2", "Manzara 2", R.drawable.bg_local_2),
        Sample("local_3", "Manzara 3", R.drawable.bg_local_3)
    )

    val remoteSamples = listOf(
        RemoteSample("remote_1", "Sahro kechasi", "http://18.197.97.234/backgrounds/bg_remote_1.png"),
        RemoteSample("remote_2", "Yashil o'rmon", "http://18.197.97.234/backgrounds/bg_remote_2.png"),
        RemoteSample("remote_3", "Ish stoli", "http://18.197.97.234/backgrounds/bg_remote_3.png"),
        RemoteSample("remote_4", "Tog'dagi uycha", "http://18.197.97.234/backgrounds/bg_remote_4.png"),
        RemoteSample("remote_5", "Tumanli tog'lar", "http://18.197.97.234/backgrounds/bg_remote_5.png"),
        RemoteSample("remote_6", "Uchta kabutar", "http://18.197.97.234/backgrounds/bg_remote_6.png"),
        RemoteSample("remote_7", "Somon yo'li va daraxt", "http://18.197.97.234/backgrounds/bg_remote_7.png"),
        RemoteSample("remote_8", "Tog'lar va shafaq", "http://18.197.97.234/backgrounds/bg_remote_8.png"),
        RemoteSample("remote_9", "Daraxt va oltin shafaq", "http://18.197.97.234/backgrounds/bg_remote_9.png"),
        RemoteSample("remote_10", "Kechki dala", "http://18.197.97.234/backgrounds/bg_remote_10.png"),
        RemoteSample("remote_11", "Jaynamoz", "http://18.197.97.234/backgrounds/bg_remote_11.png")
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

    fun setRemoteSample(context: Context, sample: RemoteSample) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOURCE, SOURCE_REMOTE)
            .putString(KEY_SAMPLE_ID, sample.id)
            .remove(KEY_CUSTOM_PATH)
            .apply()
    }

    suspend fun downloadRemoteSample(context: Context, sample: RemoteSample): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File(context.filesDir, "remote_bg_${sample.id}.png")
                if (file.exists()) return@withContext true
                
                val url = URL(sample.url)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
        }
    }

    fun isRemoteSampleDownloaded(context: Context, sample: RemoteSample): Boolean {
        return File(context.filesDir, "remote_bg_${sample.id}.png").exists()
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

    fun getSelectedSource(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SOURCE, SOURCE_SYSTEM) ?: SOURCE_SYSTEM
    }

    fun getSelectedSampleId(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SAMPLE_ID, null)
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
            SOURCE_REMOTE -> {
                val sampleId = prefs.getString(KEY_SAMPLE_ID, null)
                val file = File(context.filesDir, "remote_bg_${sampleId}.png")
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else {
                    BitmapFactory.decodeResource(context.resources, R.drawable.bg_local_1)
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
    private const val SOURCE_REMOTE = "remote"
    private const val CUSTOM_FILE_NAME = "lock_screen_custom_background"
}

