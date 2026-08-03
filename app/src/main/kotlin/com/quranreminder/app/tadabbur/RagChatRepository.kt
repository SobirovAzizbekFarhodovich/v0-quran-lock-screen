package com.quranreminder.app.tadabbur

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.quranreminder.app.BuildConfig
import com.quranreminder.app.auth.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Source-grounded chat used by the Islamic assistant screen. */
class RagChatRepository(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()

    suspend fun answer(message: String, language: String): TadabburAnswer = withContext(Dispatchers.IO) {
        val token = SessionManager(appContext).accessToken ?: error("Avval hisobingizga kiring")
        val connection = (URL("${BuildConfig.SERVER_BASE_URL}/api/v1/spiritual/query").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = true
        }
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
            it.write(gson.toJson(RagRequest(message, language)))
        }
        val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
        check(connection.responseCode in 200..299) { "AI xizmati hozir javob bermadi" }
        val response = gson.fromJson(body, RagResponse::class.java)
        TadabburAnswer(
            intro = response.answer.ifBlank { "Manbalardan javob olinmadi." },
            evidence = response.sources.mapIndexed { index, source -> source.toEvidence(index) }
        )
    }

    private fun RagSource.toEvidence(index: Int): TadabburEvidence {
        val typeValue = metadata.get("type")?.asString.orEmpty()
        val type = if (typeValue == "quran" || metadata.has("surah")) EvidenceType.QURAN else EvidenceType.HADITH
        val title = metadata.get("title")?.asString
            ?: metadata.get("source")?.asString
            ?: if (type == EvidenceType.QURAN) "Qur’oni karim" else "Hadis manbasi"
        return TadabburEvidence(
            type = type,
            title = title,
            text = text,
            source = metadata.get("source")?.asString ?: title,
            score = (score * 100).toInt().coerceIn(0, 100)
        )
    }
}

private data class RagRequest(val message: String, val language: String)
private data class RagResponse(val answer: String = "", val sources: List<RagSource> = emptyList())
private data class RagSource(val text: String = "", val score: Double = 0.0, val metadata: JsonObject = JsonObject())
