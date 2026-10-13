package com.quranreminder.app.audio

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A reciter of the whole Quran, recorded ayah by ayah (everyayah.com), so the reader can
 * light up the ayah being recited and repeat a single ayah for memorisation.
 */
enum class Reciter(val id: String, private val folder: String) {
    HUSARY("husary", "Husary_64kbps"),
    ALAFASY("alafasy", "Alafasy_64kbps");

    fun url(surah: Int, ayah: Int): String = "https://everyayah.com/data/$folder/${fileName(surah, ayah)}"

    /** String keys of the reciter's name and a one-line description (Strings.kt). */
    val nameKey get() = "audio.reciter.$id"
    val descKey get() = "audio.reciter.$id.desc"

    companion object {
        fun fileName(surah: Int, ayah: Int) = "%03d%03d.mp3".format(surah, ayah)
        fun fromId(id: String?): Reciter = entries.firstOrNull { it.id == id } ?: HUSARY
    }
}

/** The chosen reciter (Husary by default: the clearest tartil for learners). */
object ReciterPrefs {
    private const val PREFS = "quran_audio"
    private const val KEY = "reciter"
    private val state = MutableStateFlow<Reciter?>(null)

    fun flow(context: Context): StateFlow<Reciter?> {
        if (state.value == null) state.value = get(context)
        return state.asStateFlow()
    }

    fun get(context: Context): Reciter =
        Reciter.fromId(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun set(context: Context, reciter: Reciter) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, reciter.id).apply()
        state.value = reciter
    }
}

/**
 * Recitations saved on the phone for listening offline: files/audio/<reciter>/<sssaaa>.mp3.
 * The basmala recited before a surah is ayah 1:1 of the same reciter.
 */
object AudioStore {
    private fun dir(context: Context, reciter: Reciter) = File(context.filesDir, "audio/${reciter.id}")

    fun file(context: Context, reciter: Reciter, surah: Int, ayah: Int) = File(dir(context, reciter), Reciter.fileName(surah, ayah))

    /** The local file if it was downloaded, otherwise the stream URL. */
    fun uri(context: Context, reciter: Reciter, surah: Int, ayah: Int): String {
        val local = file(context, reciter, surah, ayah)
        return if (local.isFile && local.length() > 0) android.net.Uri.fromFile(local).toString() else reciter.url(surah, ayah)
    }

    /** Files a surah needs: its ayahs, plus the basmala before every surah except 1 and 9. */
    fun surahFiles(surah: Int, ayahCount: Int): List<Pair<Int, Int>> =
        (if (surah != 1 && surah != 9) listOf(1 to 1) else emptyList()) + (1..ayahCount).map { surah to it }

    fun isSurahDownloaded(context: Context, reciter: Reciter, surah: Int, ayahCount: Int): Boolean =
        ayahCount > 0 && surahFiles(surah, ayahCount).all { (s, a) -> file(context, reciter, s, a).let { it.isFile && it.length() > 0 } }

    fun usedBytes(context: Context): Long =
        File(context.filesDir, "audio").walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun deleteAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(SurahDownloadWorker.TAG)
        File(context.filesDir, "audio").deleteRecursively()
    }

    fun downloadWorkName(reciter: Reciter, surah: Int) = "audio-${reciter.id}-$surah"

    fun download(context: Context, reciter: Reciter, surah: Int, ayahCount: Int) {
        val request = OneTimeWorkRequestBuilder<SurahDownloadWorker>()
            .setInputData(workDataOf("reciter" to reciter.id, "surah" to surah, "count" to ayahCount))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(SurahDownloadWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(downloadWorkName(reciter, surah), ExistingWorkPolicy.KEEP, request)
    }
}

/** Downloads one surah's ayah files for one reciter; progress is "done"/"total". */
class SurahDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val reciter = Reciter.fromId(inputData.getString("reciter"))
        val surah = inputData.getInt("surah", 0)
        val count = inputData.getInt("count", 0)
        if (surah !in 1..114 || count <= 0) return@withContext Result.failure()
        val files = AudioStore.surahFiles(surah, count)
        var done = 0
        for ((s, a) in files) {
            val target = AudioStore.file(applicationContext, reciter, s, a)
            if (!(target.isFile && target.length() > 0)) {
                target.parentFile?.mkdirs()
                val tmp = File(target.path + ".part")
                val ok = runCatching {
                    client.newCall(Request.Builder().url(reciter.url(s, a)).build()).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        tmp.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
                    }
                    tmp.renameTo(target)
                }.getOrDefault(false)
                if (!ok) {
                    tmp.delete()
                    return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            }
            done++
            setProgress(Data.Builder().putInt("done", done).putInt("total", files.size).build())
        }
        Result.success()
    }

    companion object {
        const val TAG = "quran-audio-download"
        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
