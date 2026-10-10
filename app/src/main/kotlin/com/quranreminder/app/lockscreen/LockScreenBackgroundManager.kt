package com.quranreminder.app.lockscreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.quranreminder.app.BuildConfig
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
        RemoteSample("remote_1", "Sahro kechasi", "${BuildConfig.SERVER_BASE_URL}/backgrounds/bg_remote_1.png"),
        RemoteSample("remote_2", "Yashil o'rmon", "${BuildConfig.SERVER_BASE_URL}/backgrounds/bg_remote_2.png"),
        RemoteSample("remote_3", "Ish stoli", "${BuildConfig.SERVER_BASE_URL}/backgrounds/bg_remote_3.png"),
        RemoteSample("remote_4", "Tog'dagi uycha", "${BuildConfig.SERVER_BASE_URL}/backgrounds/bg_remote_4.png"),
        RemoteSample("remote_5", "Tumanli tog'lar", "${BuildConfig.SERVER_BASE_URL}/backgrounds/bg_remote_5.png"),
        RemoteSample("remote_6", "Uchta kabutar", "${BuildConfig.SERVER_BASE_URL}/backgrounds/bg_remote_6.png"),
        RemoteSample("remote_7", "Somon yo'li va daraxt", "${BuildConfig.SERVER_BASE_URL}/backgrounds/bg_remote_7.png"),
        RemoteSample("remote_8", "Tog'lar va shafaq", "${BuildConfig.SERVER_BASE_URL}/backgrounds/bg_remote_8.png"),
        RemoteSample("remote_9", "Daraxt va oltin shafaq", "${BuildConfig.SERVER_BASE_URL}/backgrounds/bg_remote_9.png"),
        RemoteSample("remote_10", "Kechki dala", "${BuildConfig.SERVER_BASE_URL}/backgrounds/bg_remote_10.png"),
        RemoteSample("remote_11", "Jaynamoz", "${BuildConfig.SERVER_BASE_URL}/backgrounds/bg_remote_11.png")
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
            val file = remoteFile(context, sample.id)
            if (isValidImage(file)) return@withContext true

            // Download into a temp file and only rename it into place once it is complete and
            // decodes as an image, so an interrupted download never looks "downloaded".
            val temp = File(context.filesDir, "${file.name}.part")
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(sample.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext false
                val expected = connection.contentLengthLong
                connection.inputStream.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                if (expected > 0 && temp.length() != expected) return@withContext false
                if (!isValidImage(temp)) return@withContext false
                file.delete()
                temp.renameTo(file).also { BackgroundBitmapCache.invalidate(file.absolutePath) }
            } catch (_: Exception) {
                false
            } finally {
                connection?.disconnect()
                if (temp.exists()) temp.delete()
            }
        }
    }

    fun isRemoteSampleDownloaded(context: Context, sample: RemoteSample): Boolean {
        val file = remoteFile(context, sample.id)
        if (isValidImage(file)) return true
        // Heal partial files left behind by older builds.
        if (file.exists()) {
            file.delete()
            BackgroundBitmapCache.invalidate(file.absolutePath)
        }
        return false
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
        val file = File(context.filesDir, CUSTOM_FILE_NAME)
        val temp = File(context.filesDir, "$CUSTOM_FILE_NAME.part")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            if (!isValidImage(temp)) {
                temp.delete()
                return false
            }
            file.delete()
            // Same path for every gallery pick, so the old image must leave the cache.
            BackgroundBitmapCache.invalidate(file.absolutePath)
            if (!temp.renameTo(file)) return false
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SOURCE, SOURCE_CUSTOM)
                .putString(KEY_CUSTOM_PATH, file.absolutePath)
                .remove(KEY_SAMPLE_ID)
                .apply()
            true
        }.getOrDefault(false).also { if (temp.exists()) temp.delete() }
    }

    fun getSelectedSource(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SOURCE, SOURCE_SYSTEM) ?: SOURCE_SYSTEM
    }

    fun getSelectedSampleId(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SAMPLE_ID, null)
    }

    /** Decodes the selected background downsampled to roughly the screen size. */
    fun loadSelectedBitmap(context: Context): Bitmap? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val (reqWidth, reqHeight) = LockScreenWallpaperRenderer.getRealScreenSize(context)
        return when (prefs.getString(KEY_SOURCE, SOURCE_SYSTEM)) {
            SOURCE_CUSTOM -> {
                prefs.getString(KEY_CUSTOM_PATH, null)
                    ?.let { decodeSampledFile(it, reqWidth, reqHeight) }
            }
            SOURCE_SAMPLE -> {
                val sampleId = prefs.getString(KEY_SAMPLE_ID, null)
                samples.firstOrNull { it.id == sampleId }?.let { sample ->
                    decodeSampledResource(context, sample.resId, reqWidth, reqHeight)
                }
            }
            SOURCE_REMOTE -> {
                val sampleId = prefs.getString(KEY_SAMPLE_ID, null)
                val file = File(context.filesDir, "remote_bg_${sampleId}.png")
                (if (file.exists()) decodeSampledFile(file.absolutePath, reqWidth, reqHeight) else null)
                    ?: decodeSampledResource(context, R.drawable.bg_local_1, reqWidth, reqHeight)
            }
            else -> null
        }
    }

    /** Bundled sample decoded for a [reqWidth] x [reqHeight] target (e.g. a preview card), cached. */
    fun decodeSampleBitmap(context: Context, sample: Sample, reqWidth: Int, reqHeight: Int): Bitmap? =
        decodeSampledResource(context, sample.resId, reqWidth, reqHeight)

    /** Downloaded remote sample decoded for a [reqWidth] x [reqHeight] target, cached; null if not downloaded. */
    fun decodeRemoteSampleBitmap(context: Context, sample: RemoteSample, reqWidth: Int, reqHeight: Int): Bitmap? {
        val file = remoteFile(context, sample.id)
        return if (file.exists()) decodeSampledFile(file.absolutePath, reqWidth, reqHeight) else null
    }

    /** Memory-cached: the same file at the same requested size is decoded only once per process. */
    fun decodeSampledFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? =
        cached(path, reqWidth, reqHeight) { decodeSampledFileUncached(path, reqWidth, reqHeight) }

    private fun decodeSampledFileUncached(path: String, reqWidth: Int, reqHeight: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
            }
            BitmapFactory.decodeFile(path, options)
        }
    }.getOrNull()

    private fun decodeSampledResource(
        context: Context,
        @DrawableRes resId: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? = cached("res:$resId", reqWidth, reqHeight) {
        decodeSampledResourceUncached(context, resId, reqWidth, reqHeight)
    }

    private fun decodeSampledResourceUncached(
        context: Context,
        @DrawableRes resId: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resId, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
        }
        BitmapFactory.decodeResource(context.resources, resId, options)
    }.getOrNull()

    private inline fun cached(source: String, reqWidth: Int, reqHeight: Int, decode: () -> Bitmap?): Bitmap? {
        val key = BackgroundBitmapCache.key(source, reqWidth, reqHeight)
        BackgroundBitmapCache.get(key)?.let { return it }
        return decode()?.also { BackgroundBitmapCache.put(key, it) }
    }

    /** Largest power of two that keeps both dimensions >= the requested size (center-crop safe). */
    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        if (width <= 0 || height <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1
        var sampleSize = 1
        while (width / (sampleSize * 2) >= reqWidth && height / (sampleSize * 2) >= reqHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun remoteFile(context: Context, sampleId: String) =
        File(context.filesDir, "remote_bg_${sampleId}.png")

    private fun isValidImage(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, bounds) }
        return bounds.outWidth > 0 && bounds.outHeight > 0
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
