package com.quranreminder.app.recitation

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.quranreminder.app.BuildConfig
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.auth.UnauthorizedException
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.database.RecitationResultEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Backs the on-device recitation history up to the server and restores it on a new phone,
 * so the streak and the review list survive a reinstall. Offline it simply waits for the
 * next sync; rows carry a client id, so nothing is ever stored twice.
 */
object RecitationSync {
    private const val PREFS = "recitation_sync"
    private const val KEY_SINCE = "restored_since"
    private val mutex = Mutex()
    private val gson = Gson()
    private val json = "application/json".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private data class Row(
        @SerializedName("client_id") val clientId: String,
        val surah: Int,
        val ayah: Int,
        @SerializedName("word_count") val wordCount: Int,
        @SerializedName("correct_count") val correctCount: Int,
        @SerializedName("wrong_count") val wrongCount: Int,
        @SerializedName("missed_count") val missedCount: Int,
        val mode: String,
        @SerializedName("created_at") val createdAt: Long,
        @SerializedName("local_day") val localDay: Long
    )

    private data class Page(
        val results: List<Row>? = null,
        @SerializedName("next_since") val nextSince: Long? = null,
        @SerializedName("has_more") val hasMore: Boolean? = null
    )

    /** Uploads what is not backed up yet, then pulls rows from other devices. Never throws. */
    suspend fun syncNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val session = SessionManager(context.applicationContext)
                if (!session.isSignedIn) return@runCatching false
                val dao = QuranDatabase.getDatabase(context).recitationHistoryDao()
                // Upload in batches.
                while (true) {
                    val pending = dao.unsynced()
                    if (pending.isEmpty()) break
                    // Rows the server rejects (bad data) are marked too: retrying them forever
                    // would block every later row.
                    session.withAuthorization { token -> post(token, pending) }
                    dao.markSynced(pending.map { it.clientId })
                    if (pending.size < 500) break
                }
                // Restore what this device has not seen.
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                var since = prefs.getLong(KEY_SINCE, 0L)
                repeat(50) {
                    val page = session.withAuthorization { token -> get(token, since) }
                    val rows = page.results.orEmpty()
                    if (rows.isNotEmpty()) dao.insertRestored(rows.map { it.toEntity() })
                    val next = page.nextSince ?: rows.maxOfOrNull { it.createdAt } ?: since
                    if (rows.isEmpty() || next <= since || page.hasMore == false) {
                        if (next > since) since = next
                        prefs.edit().putLong(KEY_SINCE, since).apply()
                        return@runCatching true
                    }
                    since = next
                    prefs.edit().putLong(KEY_SINCE, since).apply()
                }
                true
            }.getOrElse { false }
        }
    }

    private fun Row.toEntity() = RecitationResultEntity(
        surahNumber = surah, ayahNumber = ayah, wordCount = wordCount, correctCount = correctCount,
        wrongCount = wrongCount, missedCount = missedCount, mode = mode, createdAt = createdAt,
        day = localDay, clientId = clientId, synced = true
    )

    private fun RecitationResultEntity.toRow() = Row(
        clientId, surahNumber, ayahNumber, wordCount, correctCount, wrongCount, missedCount, mode, createdAt, day
    )

    private fun post(token: String, rows: List<RecitationResultEntity>) {
        val body = gson.toJson(mapOf("results" to rows.map { it.toRow() })).toRequestBody(json)
        execute(Request.Builder().url("${BuildConfig.SERVER_BASE_URL}/api/v1/recitation/results").post(body), token)
    }

    private fun get(token: String, since: Long): Page {
        val text = execute(
            Request.Builder().url("${BuildConfig.SERVER_BASE_URL}/api/v1/recitation/results?since=$since&limit=500").get(),
            token
        )
        return gson.fromJson(text, Page::class.java) ?: Page()
    }

    private fun execute(builder: Request.Builder, token: String): String {
        http.newCall(builder.header("Authorization", "Bearer $token").build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) throw UnauthorizedException()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${runCatching { gson.fromJson(text, JsonObject::class.java)?.get("error") }.getOrNull()}")
            return text
        }
    }
}
