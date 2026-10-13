package com.quranreminder.app.recitation

import android.content.Context
import com.google.gson.Gson
import com.quranreminder.app.BuildConfig
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.auth.UnauthorizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends a recitation the user says was judged wrongly: the recording (only with their
 * consent, from the dialog) and what was expected and heard. These real phone recordings
 * are how the recognizer gets measured and improved beyond studio test audio.
 */
object RecitationReporter {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun send(
        context: Context,
        pcm: ByteArray,
        surah: Int,
        ayah: Int,
        expected: String,
        transcript: String,
        alignment: String,
        note: String
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val meta = Gson().toJson(
                mapOf(
                    "surah" to surah,
                    "ayah" to ayah,
                    "expected_text" to expected,
                    "transcript" to transcript,
                    "alignment" to alignment,
                    "app_version" to BuildConfig.VERSION_NAME,
                    "note" to note.take(500)
                )
            )
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("meta", meta)
                .addFormDataPart("audio", "recitation.pcm", pcm.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            SessionManager(context.applicationContext).withAuthorization { token ->
                val request = Request.Builder()
                    .url("${BuildConfig.SERVER_BASE_URL}/api/v1/recitation/reports")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                http.newCall(request).execute().use { response ->
                    if (response.code == 401) throw UnauthorizedException()
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                }
            }
            true
        }.getOrElse { false }
    }
}
