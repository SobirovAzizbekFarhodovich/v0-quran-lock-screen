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
import java.util.Locale

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

        val cleanMsg = trimmed.lowercase(Locale.ROOT)
            .replace('ʼ', '\'')
            .replace('’', '\'')
            .replace('‘', '\'')

        // 1. Check for Gibberish (random key mashing like "saieughoseiurfghnserfig")
        if (isGibberish(cleanMsg)) {
            return@withContext TadabburAnswer(
                intro = "Kechirasiz, yozgan matningizni tushuna olmadim. Iltimos, savolingizni yoki fikringizni aniqroq yozing.",
                evidence = emptyList()
            )
        }

        // 2. Conversational chitchat & greetings handler
        val chitchat = checkChitchat(cleanMsg)
        if (chitchat != null) {
            return@withContext chitchat
        }

        // 3. Check for out-of-scope non-Islamic queries (programming, math, games)
        val outOfScope = checkOutOfScope(cleanMsg)
        if (outOfScope != null) {
            return@withContext outOfScope
        }

        // 4. Attempt remote Go backend RAG query if token exists
        val token = SessionManager(appContext).accessToken
        if (!token.isNullOrBlank()) {
            val remoteResult = runCatching {
                val connection = (URL("${BuildConfig.SERVER_BASE_URL}/api/v1/spiritual/query").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8_000
                    readTimeout = 15_000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $token")
                    doOutput = true
                }
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                    it.write(gson.toJson(RagRequest(message, language)))
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

        // 5. Fallback to device-side RAG search
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

    private fun checkChitchat(msg: String): TadabburAnswer? {
        val isGreeting = msg.contains("salom") || msg.contains("assalom") || msg.contains("assalamu") || 
            msg == "hi" || msg == "hello" || msg.contains("xayrli") || msg.contains("privet") || msg.contains("selam")
        
        val isHowAreYou = msg.contains("qalesan") || msg.contains("qalesiz") || msg.contains("qalaysan") || 
            msg.contains("qalaysiz") || msg.contains("qandaysiz") || msg.contains("qandaysan") || 
            msg.contains("qanaysiz") || msg.contains("qanaysan") || msg.contains("nma gap") || 
            msg.contains("nima gap") || msg.contains("ishlar yaxshimi") || msg.contains("tinchlikmi") || 
            msg.contains("charchamay") || msg.contains("ahvol") || msg.contains("axvol") || 
            msg.contains("yaxshimisiz") || msg.contains("durustmisiz") || msg.contains("kelyapsiz")
        
        val isWhoAreYou = msg.contains("isming") || msg.contains("kimsan") || msg.contains("kimsa") || 
            msg.contains("sen kimsan") || msg.contains("siz kimsiz") || msg.contains("ai misan") || 
            msg.contains("vazifang") || msg.contains("nima qilolaysan") || msg.contains("haqingda")
        
        val isThanks = msg.contains("rahmat") || msg.contains("tashakkur") || msg.contains("jazakallah") || 
            msg.contains("barakalloh") || msg.contains("sog' bo'l") || msg.contains("spasibo") || 
            msg.contains("minnatdor") || msg.contains("rahmad")
        
        val isAffirmation = msg == "ok" || msg == "yaxshi" || msg == "tushundim" || msg == "tushunarli" || 
            msg == "xo'p" || msg == "xop" || msg == "mayli" || msg == "ha"

        val isFarewell = msg.contains("xayr") || msg.contains("ko'rishguncha") || msg.contains("salomat bo'l")

        return when {
            isGreeting -> TadabburAnswer(
                intro = "Assalomu alaykum va rahmatullahi va barakatuh! Men Nur AI islomiy yordamchingizman. Sizga Qur'on oyatlari, sahih hadislar va duolar bo'yicha qanday yordam bera olaman?",
                evidence = emptyList()
            )
            isHowAreYou -> TadabburAnswer(
                intro = "Alhamdulillah, barchasi yaxshi! Alloh taolo kuningizni va umringizni barakali qilsin. Sizga qanday diniy savolingiz bor yoki qaysi mavzu bo'yicha oyat va hadislar izlayapsiz?",
                evidence = emptyList()
            )
            isWhoAreYou -> TadabburAnswer(
                intro = "Men Nur AI — islomiy maslahatchi va yordamchingizman. Vazifam Qur'oni Karim, sahih hadislar hamda duolar to'plamidan sizning savollaringizga mos sharhlar va ishonchli manbalarni topib berishdir.",
                evidence = emptyList()
            )
            isThanks -> TadabburAnswer(
                intro = "Arzimaydi! Jazakallahu xayran. Alloh taolo ilmingizni va imoningizni ziyoda qilsin. Yana savollaringiz bo'lsa, har doim yozishingiz mumkin.",
                evidence = emptyList()
            )
            isAffirmation -> TadabburAnswer(
                intro = "Tushunarli. Qandaydir oyat, hadis yoki duo haqida so'ramoqchi bo'lsangiz, bemalol yozishingiz mumkin!",
                evidence = emptyList()
            )
            isFarewell -> TadabburAnswer(
                intro = "Xayr, Allohning panohida bo'ling! Salomat bo'ling.",
                evidence = emptyList()
            )
            else -> null
        }
    }

    private fun checkOutOfScope(msg: String): TadabburAnswer? {
        val nonIslamicTerms = listOf(
            "python", "java", "javascript", "code", "kod yoz", "programming", "html", "css",
            "bitcoin", "dollar kursi", "crypto", "kripto", "futbol", "fifa", "games", "o'yin",
            "kino", "kino ko'rish", "shaxmat", "kalkulyator"
        )
        if (nonIslamicTerms.any { msg.contains(it) }) {
            return TadabburAnswer(
                intro = "Men Islomiy AI yordamchisiman. Vazifam Qur'on oyatlari, sahih hadislar va duolar bo'yicha yordam berishdir. Iltimos, diniy, ruhiy yoki axloqiy mavzularda savol bering.",
                evidence = emptyList()
            )
        }
        return null
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
