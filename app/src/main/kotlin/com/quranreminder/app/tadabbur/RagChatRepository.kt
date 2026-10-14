package com.quranreminder.app.tadabbur

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.quranreminder.app.BuildConfig
import com.quranreminder.app.auth.SessionException
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.auth.UnauthorizedException
import com.quranreminder.app.ui.theme.AppStrings
import com.quranreminder.app.ui.theme.text
import com.quranreminder.app.util.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/** Source-grounded chat repository with conversational intelligence & RAG fallbacks. */
class RagChatRepository(context: Context) {
    private val appContext = context.applicationContext
    private val tadabburRepo = TadabburRepository(appContext)
    private val session = SessionManager(appContext)
    private val gson = Gson()

    suspend fun answer(message: String, language: String): TadabburAnswer = withContext(Dispatchers.IO) {
        fun text(key: String) = AppStrings.text(language, key)

        val trimmed = message.trim()
        if (trimmed.isBlank()) {
            return@withContext TadabburAnswer(intro = text("chat.empty"), evidence = emptyList())
        }

        val cleanMsg = trimmed.lowercase()

        // Random key mashing is the only message type handled locally. Every
        // meaningful message goes to the server so history and intent routing
        // work like a real chatbot.
        if (isGibberish(cleanMsg)) {
            return@withContext TadabburAnswer(intro = text("chat.gibberish"), evidence = emptyList())
        }

        // Both the server RAG and the Tadabbur fallback need the internet: fail fast
        // with a clear message instead of waiting for HTTP timeouts.
        if (!NetworkStatus.isServerReachable(appContext)) {
            return@withContext TadabburAnswer(intro = text("chat.offline"), evidence = emptyList())
        }

        // A single server route owns normal conversation, emotional support,
        // Islamic RAG, citations and Redis conversation history.
        if (session.isSignedIn) {
            val userName = session.displayName
            val remote = try {
                session.withAuthorization { token -> queryServer(token, trimmed, language, userName) }
            } catch (expired: SessionException) {
                if (expired.code == SessionManager.CODE_SESSION_EXPIRED) {
                    return@withContext TadabburAnswer(intro = text(expired.code), evidence = emptyList())
                }
                null
            } catch (error: Exception) {
                android.util.Log.w("RagChat", "Server RAG failed, falling back", error)
                null
            }
            if (remote != null) {
                return@withContext TadabburAnswer(
                    intro = remote.answer,
                    evidence = remote.sources.map { source -> source.toEvidence(language) }
                )
            }
        }

        // Server erroring or no active session: fall back to the on-device source
        // search + Tadabbur AI, capped so the user never waits more than ~45s.
        // The fallback runs in its own scope because its blocking HTTP calls can't be
        // interrupted; on timeout we stop waiting and let it finish in the background.
        val fallback = fallbackScope.async { tadabburRepo.answer(trimmed, language) }
        withTimeoutOrNull(FALLBACK_TIMEOUT_MS) { fallback.await() }
            ?: run {
                fallback.cancel()
                TadabburAnswer(intro = text("chat.timeout"), evidence = emptyList())
            }
    }

    /** Returns the server answer, null for an empty/unusable reply; throws on transport errors and 401. */
    private fun queryServer(token: String, message: String, language: String, userName: String): RagResponse? {
        val connection = (URL("${BuildConfig.SERVER_BASE_URL}/api/v1/spiritual/query").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 90_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = true
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                val gender = com.quranreminder.app.profile.ProfileRepository.load(appContext)?.gender
                it.write(gson.toJson(RagRequest(message, language, userName, gender)))
            }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) throw UnauthorizedException()
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            if (code !in 200..299 || body.isBlank()) return null
            return gson.fromJson(body, RagResponse::class.java).takeIf { it.answer.isNotBlank() }
        } finally {
            connection.disconnect()
        }
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


    private fun RagSource.toEvidence(language: String): TadabburEvidence {
        val typeValue = metadata.get("type")?.asString.orEmpty()
        val type = if (typeValue == "quran" || metadata.has("surah")) EvidenceType.QURAN else EvidenceType.HADITH
        val title = metadata.get("title")?.asString
            ?: metadata.get("source")?.asString
            ?: AppStrings.text(language, if (type == EvidenceType.QURAN) "chat.source.quran" else "chat.source.hadith")
        return TadabburEvidence(
            type = type,
            title = title,
            text = text,
            source = metadata.get("source")?.asString ?: title,
            score = (score * 100).toInt().coerceIn(0, 100)
        )
    }
}

private const val FALLBACK_TIMEOUT_MS = 45_000L
private val fallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private data class RagRequest(
    val message: String,
    val language: String,
    @com.google.gson.annotations.SerializedName("user_name") val userName: String = "",
    // From the profile; the assistant uses masculine/feminine address. Null = neutral.
    @com.google.gson.annotations.SerializedName("user_gender") val userGender: String? = null
)
private data class RagResponse(val answer: String = "", val sources: List<RagSource> = emptyList())
private data class RagSource(val text: String = "", val score: Double = 0.0, val metadata: JsonObject = JsonObject())
