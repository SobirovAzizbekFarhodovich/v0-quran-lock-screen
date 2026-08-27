package com.quranreminder.app.tadabbur

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.quranreminder.app.BuildConfig
import com.quranreminder.app.auth.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Source-grounded chat repository with conversational intelligence & RAG fallbacks. */
class RagChatRepository(context: Context) {
    private val appContext = context.applicationContext
    private val tadabburRepo = TadabburRepository(appContext)
    private val gson = Gson()

    suspend fun answer(message: String, language: String): TadabburAnswer = withContext(Dispatchers.IO) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) {
            return@withContext TadabburAnswer(
                intro = "Iltimos, savolingizni yozing.",
                evidence = emptyList()
            )
        }

        val cleanMsg = trimmed.lowercase()

        // Random key mashing is the only message type handled locally. Every
        // meaningful message goes to the server so history and intent routing
        // work like a real chatbot.
        if (isGibberish(cleanMsg)) {
            return@withContext TadabburAnswer(
                intro = "Kechirasiz, yozgan matningizni tushuna olmadim. Iltimos, savolingizni yoki fikringizni aniqroq yozing.",
                evidence = emptyList()
            )
        }

        // A single server route owns normal conversation, emotional support,
        // Islamic RAG, citations and Redis conversation history.
        val token = SessionManager(appContext).accessToken
        if (!token.isNullOrBlank()) {
            val userName = SessionManager(appContext).displayName
            val remoteResult = runCatching {
                val connection = (URL("${BuildConfig.SERVER_BASE_URL}/api/v1/spiritual/query").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8_000
                    readTimeout = 90_000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $token")
                    doOutput = true
                }
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                    it.write(gson.toJson(RagRequest(message, language, userName)))
                }
                val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
                if (connection.responseCode in 200..299 && body.isNotBlank()) {
                    val response = gson.fromJson(body, RagResponse::class.java)
                    if (response.answer.isNotBlank()) {
                        TadabburAnswer(
                            intro = response.answer,
                            evidence = response.sources.mapIndexed { index, source -> source.toEvidence(index) }
                        )
                    } else null
                } else null
            }.getOrNull()

            if (remoteResult != null) {
                return@withContext remoteResult
            }
        }

        // Server unreachable, erroring, or no active session: fall back to the
        // on-device RAG search instead of blaming "no internet" when that may
        // not be the actual cause.
        return@withContext tadabburRepo.answer(message)
    }

    private fun isGibberish(msg: String): Boolean {
        if (msg.length < 3) return false
        
        val words = msg.split(" ").filter { it.isNotBlank() }
        
        for (word in words) {
            if (word.length >= 10) {
                val vowelCount = word.count { it in "aeiouoy'ʻʼ" }
                val ratio = vowelCount.toFloat() / word.length
                
                if (ratio < 0.22f || ratio > 0.75f) return true
                if (Regex("[bcfghjklmnpqrstvwxz]{5,}").containsMatchIn(word)) return true
            }
            
            if (word.contains("asdf") || word.contains("sdfg") || word.contains("dfgh") ||
                word.contains("fghj") || word.contains("ghjk") || word.contains("hjkl") ||
                word.contains("qwerty") || word.contains("zxcv") || word.contains("1234") ||
                word.contains("rfgh") || word.contains("serf") || word.contains("iurg")
            ) {
                return true
            }
        }
        
        return false
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

private data class RagRequest(val message: String, val language: String, @com.google.gson.annotations.SerializedName("user_name") val userName: String = "")
private data class RagResponse(val answer: String = "", val sources: List<RagSource> = emptyList())
private data class RagSource(val text: String = "", val score: Double = 0.0, val metadata: JsonObject = JsonObject())
