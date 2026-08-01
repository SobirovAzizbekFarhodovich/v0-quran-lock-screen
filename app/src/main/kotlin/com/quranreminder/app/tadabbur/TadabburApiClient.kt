package com.quranreminder.app.tadabbur

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.quranreminder.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tadabbur AI API client.
 * To'g'ridan-to'g'ri OpenRouter API orqali Qwen AI modelini chaqiradi.
 * 
 * Bu client har qanday telefonda ishlaydi — lokal server kerak emas.
 * Faqat internet kerak.
 */
object TadabburApiClient {
    // SERVER_BASE_URL release build vaqtida almashtiriladi. Hozircha u
    // vaqtinchalik HTTP serverga, keyin esa HTTPS domain'ga ko'rsatiladi.
    private val backendChatUrl = "${BuildConfig.SERVER_BASE_URL}/api/v1/chat/completions"
    private const val QWEN_MODEL = "qwen/qwen3-8b"
    private const val TIMEOUT_MS = 60_000
    private const val MAX_RETRIES = 2
    private val gson = Gson()

    // System prompt — AI'ning asosiy vazifasi va qoidalari
    private const val SYSTEM_PROMPT = """Siz Qur'on va Hadis bo'yicha bilimdon, juda mehribon va tushunuvchi islomiy ruhiy maslahatchisiz. Foydalanuvchi sizga o'zining jismoniy, ruhiy yoki hayotiy qiyinchiligini yozadi. Biz sizga foydalanuvchining so'zlariga mos tushuvchi Qur'on va Hadis dalillarini taqdim etamiz.

Sizning yagona vazifangiz — foydalanuvchining holatini tahlil qilib, unga aynan biz bergan dalillar asosida taskin beruvchi shaxsiy maslahat tayyorlash. O'zingizdan fatvo chiqarmang, shariat hukmlarini o'zgartirmang yoki yo'q dalillarni to'qimang. Faqat berilgan dalillardan foydalaning.

Javobingizni SOF O'ZBEK ADABIY TILIDA, grammatik va imlo xatolarisiz, juda tabiiy, samimiy va ravon tilda yozing. G'alati, noto'g'ri yoki g'aliz tarjima qilingan so'zlardan umuman foydalanmang. Yaratganni "Alloh taolo" yoki "Robbimiz" deb nomlang.

Quyidagi tuzilmaga qat'iy rioya qilgan holda, FAQAT TOZA JSON formatida javob bering. Markdown, ` yoki qo'shimcha so'zlar ISHLATMANG! Hech qanday HTML teglarsiz sof JSON bo'lsin.

{
  "analysis": {
    "topics": ["mos mavzu1", "mos mavzu2"],
    "mood": "foydalanuvchining kayfiyati",
    "keywords": ["kalit_soz1", "kalit_soz2"]
  },
  "intro": "2-3 gaplik mehribon kirish so'z",
  "aiInsight": "3-5 gaplik shaxsiy maslahat",
  "evidence_notes": {
    "1": "1-dalil uchun 1 gaplik izoh",
    "2": "2-dalil uchun 1 gaplik izoh"
  }
}"""

    val isAiAvailable: Boolean
        get() = true // Endi har doim Go backend orqali ishlaydi

    // ====== Data models ======

    private data class ChatMessage(
        val role: String,
        val content: String
    )

    private data class ChatRequest(
        val model: String = QWEN_MODEL,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.3,
        @SerializedName("max_tokens")
        val maxTokens: Int = 2000,
        @SerializedName("include_reasoning")
        val includeReasoning: Boolean = false
    )

    private data class ChatResponse(
        val choices: List<Choice>? = null,
        val error: ErrorDetail? = null
    )

    private data class Choice(val message: MessageContent? = null)
    private data class MessageContent(val content: String? = null)
    private data class ErrorDetail(val message: String? = null)

    // ====== AI Analysis models ======

    data class QwenAnalysis(
        val topics: List<String> = emptyList(),
        val moods: List<String> = emptyList(),
        val keywords: List<String> = emptyList(),
        val userState: String = ""
    )

    data class PersonalResponse(
        val intro: String = "",
        val aiInsight: String = "",
        @SerializedName("evidence_notes")
        val evidenceNotes: Map<String, String> = emptyMap()
    )

    // ====== Birlashtirilgan AI javob modeli ======

    data class UnifiedAiResponse(
        val analysis: AnalysisSection? = null,
        val intro: String = "",
        val aiInsight: String = "",
        @SerializedName("evidence_notes")
        val evidenceNotes: Map<String, String> = emptyMap()
    )

    data class AnalysisSection(
        val topics: List<String> = emptyList(),
        val mood: String = "",
        val keywords: List<String> = emptyList()
    )

    // ====== Core API call ======

    private suspend fun callQwen(
        messages: List<ChatMessage>
    ): String? = withContext(Dispatchers.IO) {
        if (!isAiAvailable) {
            android.util.Log.w("TadabburAI", "AI mavjud emas")
            return@withContext null
        }

        // Retry mexanizmi — tarmoq xatolarida qayta urinadi
        repeat(MAX_RETRIES) { attempt ->
            try {
                val result = executeApiCall(messages)
                if (result != null) return@withContext result
                
                // Null natija — retry qilish
                if (attempt < MAX_RETRIES - 1) {
                    android.util.Log.w("TadabburAI", "Retry ${attempt + 1}/$MAX_RETRIES...")
                    delay(1000L * (attempt + 1))
                }
            } catch (e: Exception) {
                android.util.Log.e("TadabburAI", "Qwen API call failed (attempt ${attempt + 1})", e)
                if (attempt < MAX_RETRIES - 1) {
                    delay(1000L * (attempt + 1))
                }
            }
        }
        null
    }

    private fun executeApiCall(
        messages: List<ChatMessage>
    ): String? {
        val request = ChatRequest(
            messages = messages,
            includeReasoning = false
        )

        val url = URL(backendChatUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("HTTP-Referer", "https://quran-reminder.app")
            setRequestProperty("X-Title", "Quran Reminder Tadabbur AI")
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
        }

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(gson.toJson(request))
            writer.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown"
            android.util.Log.e("TadabburAI", "OpenRouter error $responseCode: $errorText")
            // 401/403 = API key noto'g'ri, retry qilmaslik kerak
            if (responseCode == 401 || responseCode == 403) {
                throw RuntimeException("API key noto'g'ri yoki amal qilish muddati tugagan")
            }
            return null
        }

        val responseText = connection.inputStream.bufferedReader().use { it.readText() }
        android.util.Log.d("TadabburAI", "Raw API response length: ${responseText.length}")
        val response = gson.fromJson(responseText, ChatResponse::class.java)

        // API xatolikni javob ichida qaytarishi mumkin
        if (response.error != null) {
            android.util.Log.e("TadabburAI", "API error: ${response.error.message}")
            return null
        }

        val content = response.choices?.firstOrNull()?.message?.content
        if (content.isNullOrBlank()) {
            android.util.Log.w("TadabburAI", "Bo'sh javob qaytdi. Raw: ${responseText.take(500)}")
            return null
        }

        android.util.Log.d("TadabburAI", "AI content: ${content.take(200)}...")

        // Qwen3 modellar <think>...</think> teg ichida reasoning qiladi
        // Biz faqat asosiy javobni olamiz
        if (content.contains("</think>")) {
            val afterThink = content.substringAfterLast("</think>").trim()
            return afterThink.ifBlank { content }
        }

        return content
    }

    // ====== Eski API lar (backward compatibility uchun) ======

    suspend fun analyzeUserQuery(query: String, validTopics: List<String>): QwenAnalysis? {
        val topicsList = validTopics.joinToString("\n") { "- $it" }
        val prompt = """/no_think
Siz islomiy ruhiy maslahatchi sifatida foydalanuvchining holatini tahlil qilasiz.

Foydalanuvchi yozdi: "$query"

Bu insonnning ruhiy, jismoniy yoki hayotiy holatini tahlil qiling. Quyidagi ro'yxatdan ENG MOS mavzularni tanlang:
$topicsList

FAQAT quyidagi JSON formatida javob bering (boshqa hech narsa qo'shmang):
{
  "topics": ["eng mos mavzu1", "mos mavzu2"],
  "moods": ["foydalanuvchining holati1", "holati2"],
  "keywords": ["kalit_soz1", "kalit_soz2", "kalit_soz3"],
  "userState": "foydalanuvchining holatini qisqacha tavsifi"
}"""

        val content = callQwen(
            listOf(ChatMessage("user", prompt))
        ) ?: return null

        return try {
            val jsonStr = extractJson(content) ?: return null
            gson.fromJson(jsonStr, QwenAnalysis::class.java)
        } catch (e: Exception) {
            android.util.Log.e("TadabburAI", "Failed to parse analysis: $content", e)
            null
        }
    }

    suspend fun generatePersonalResponse(
        query: String,
        analysis: QwenAnalysis,
        evidenceSummary: String
    ): PersonalResponse? {
        val userPrompt = """Foydalanuvchining holati: "$query"
Tahlil: ${analysis.userState}
Mavzular: ${analysis.topics.joinToString(", ")}

Topilgan dalillar:
$evidenceSummary"""

        val content = callQwen(
            listOf(
                ChatMessage("system", SYSTEM_PROMPT),
                ChatMessage("user", userPrompt)
            )
        ) ?: return null

        return try {
            val jsonStr = extractJson(content) ?: return null
            // Avval UnifiedAiResponse sifatida parse qilishga urinamiz
            val unified = gson.fromJson(jsonStr, UnifiedAiResponse::class.java)
            PersonalResponse(
                intro = unified.intro,
                aiInsight = unified.aiInsight,
                evidenceNotes = unified.evidenceNotes
            )
        } catch (e: Exception) {
            android.util.Log.e("TadabburAI", "Failed to parse personal response: $content", e)
            null
        }
    }

    // ====== Yordamchi funksiyalar ======

    private fun extractJson(content: String): String? {
        // Markdown code block ichidagi JSON ni olish
        val codeBlockMatch = Regex("```(?:json)?\\s*\\n?(\\{[\\s\\S]*?\\})\\s*\\n?```").find(content)
        if (codeBlockMatch != null) {
            return codeBlockMatch.groupValues[1]
        }
        // Oddiy JSON ni olish
        val jsonMatch = Regex("\\{[\\s\\S]*\\}").find(content)?.value
        if (jsonMatch != null) {
            // JSON to'g'riligini tekshirish
            try {
                gson.fromJson(jsonMatch, Any::class.java)
                return jsonMatch
            } catch (_: Exception) { }
        }
        return null
    }
}
