package com.quranreminder.app.tadabbur

import android.content.Context
import com.quranreminder.app.database.QuranDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class TadabburRepository(private val context: Context) {
    private val appContext = context.applicationContext

    // AI uchun mavjud topic nomlari
    private val validTopicLabels: List<String>
        get() = topicProfiles.map { it.label }

    suspend fun answer(query: String): TadabburAnswer = withContext(Dispatchers.IO) {
        // 1. Lokal topic detection (har doim ishlaydi)
        val normalizedQuery = normalize(query)
        val queryTokens = tokenize(query)
        val matchedProfiles = matchProfiles(normalizedQuery, queryTokens)
        val profiles = matchedProfiles.map { it.profile }

        // 2. AI bilan tahlil qilish (agar mavjud bo'lsa)
        val aiAnalysis = if (TadabburApiClient.isAiAvailable) {
            try {
                TadabburApiClient.analyzeUserQuery(query, validTopicLabels)
            } catch (e: Exception) {
                android.util.Log.w("TadabburAI", "AI analysis failed, using local", e)
                null
            }
        } else null

        // AI topiclarni lokal profillar bilan birlashtirish
        val aiMatchedProfiles = if (aiAnalysis != null && aiAnalysis.topics.isNotEmpty()) {
            val aiTopics = aiAnalysis.topics.map { normalize(it) }.toSet()
            topicProfiles.filter { normalize(it.label) in aiTopics }
        } else emptyList()

        val effectiveProfiles = if (aiMatchedProfiles.isNotEmpty()) {
            (profiles + aiMatchedProfiles).distinctBy { it.label }.take(3)
        } else profiles

        val effectiveTokens = if (aiAnalysis != null && aiAnalysis.keywords.isNotEmpty()) {
            (queryTokens + aiAnalysis.keywords.map { normalize(it) }.filter { it.length >= 3 }.toSet())
        } else queryTokens

        val sharedTerms = (effectiveTokens + effectiveProfiles.flatMap { it.sharedTerms }).toSet()
        val quranTerms = (sharedTerms + effectiveProfiles.flatMap { it.quranTerms }).toSet()
        val hadithTerms = (sharedTerms + effectiveProfiles.flatMap { it.hadithTerms }).toSet()
        val queryMeaning = buildQueryMeaningText(normalizedQuery, effectiveTokens, effectiveProfiles)
        val quranAnchorBoosts = (matchedProfiles + aiMatchedProfiles.map { ProfileMatch(it, 50) })
            .distinctBy { it.profile.label }
            .flatMap { match -> match.profile.quranAnchors.map { it to (90 + match.score) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, scores) -> scores.maxOrNull() ?: 0 }

        if (effectiveTokens.isEmpty() && effectiveProfiles.isEmpty()) {
            return@withContext TadabburAnswer(
                intro = "Holatingizni yozing. Javob faqat Qur'on va hadis manbalaridan topilgan dalillarga tayangan holda beriladi.",
                evidence = emptyList()
            )
        }

        val database = QuranDatabase.getDatabase(appContext)
        val verses = database.quranVerseDao().getAllVersesOnce()
        val hadiths = HadithDataLoader.load(appContext)
        val sourceHighlights = SourceHighlightLoader.load(appContext)

        val quranEvidence = verses
            .mapNotNull { verse ->
                val reference = "${verse.surahNumber}:${verse.ayahNumber}"
                val sourceId = "quran_${verse.surahNumber}_${verse.ayahNumber}"
                val metadata = sourceHighlights[sourceId]
                val sourceText = "${verse.surahName} ${verse.translation}"
                if (!isSourceAllowedForProfiles(
                        type = EvidenceType.QURAN,
                        reference = reference,
                        text = sourceText,
                        metadata = metadata,
                        profiles = effectiveProfiles
                    )
                ) {
                    return@mapNotNull null
                }
                val score = scoreDocument(
                    terms = quranTerms,
                    queryTokens = effectiveTokens,
                    normalizedQuery = normalizedQuery,
                    queryMeaning = queryMeaning,
                    text = sourceText,
                    metadata = metadata,
                    matchedProfiles = effectiveProfiles,
                    boost = quranAnchorBoosts[reference] ?: 0
                )
                if (score < minimumScore(effectiveProfiles)) {
                    null
                } else {
                    TadabburEvidence(
                        type = EvidenceType.QURAN,
                        title = "${verse.surahName} ${verse.surahNumber}:${verse.ayahNumber}",
                        text = verse.translation.compactForChat(),
                        source = "Qur'oni karim, ${verse.surahName} ${verse.surahNumber}:${verse.ayahNumber}. Muhammad Sodiq Muhammad Yusuf tarjimasi.",
                        score = score,
                        highlights = metadata?.displayHighlights(effectiveProfiles).orEmpty()
                    )
                }
            }
            .sortedByDescending { it.score }
            .take(4)

        val hadithEvidence = hadiths
            .mapNotNull { hadith ->
                val sourceId = "hadith_${hadith.id}"
                val metadata = sourceHighlights[sourceId]
                val sourceText = "${hadith.book} ${hadith.chapter} ${hadith.narrator} ${hadith.text}"
                if (!isSourceAllowedForProfiles(
                        type = EvidenceType.HADITH,
                        reference = null,
                        text = sourceText,
                        metadata = metadata,
                        profiles = effectiveProfiles
                    )
                ) {
                    return@mapNotNull null
                }
                val score = scoreDocument(
                    terms = hadithTerms,
                    queryTokens = effectiveTokens,
                    normalizedQuery = normalizedQuery,
                    queryMeaning = queryMeaning,
                    text = sourceText,
                    metadata = metadata,
                    matchedProfiles = effectiveProfiles
                )
                if (score < minimumScore(effectiveProfiles)) {
                    null
                } else {
                    TadabburEvidence(
                        type = EvidenceType.HADITH,
                        title = hadith.reference.ifBlank { hadith.book },
                        text = hadith.text.compactForChat(),
                        source = hadith.source,
                        narrator = hadith.narrator,
                        score = score,
                        highlights = metadata?.displayHighlights(effectiveProfiles).orEmpty()
                    )
                }
            }
            .sortedByDescending { it.score }
            .take(4)

        val evidence = quranEvidence.take(3) + hadithEvidence.take(3)

        if (evidence.isEmpty()) {
            return@withContext TadabburAnswer(
                intro = "Bu holat bo'yicha indekslangan Qur'on va hadis manbalaridan aniq mos dalil topilmadi. Boshqa so'zlar bilan qayta yozing.",
                evidence = emptyList()
            )
        }

        // 3. AI bilan shaxsiy javob yaratish (agar AI mavjud bo'lsa)
        val aiResponse = if (aiAnalysis != null && TadabburApiClient.isAiAvailable) {
            try {
                val evidenceSummary = evidence.take(6).mapIndexed { i, ev ->
                    val shortText = if (ev.text.length > 300) ev.text.take(300) + "..." else ev.text
                    "${i + 1}. [${ev.type.label}] ${ev.title}: \"$shortText\""
                }.joinToString("\n")
                TadabburApiClient.generatePersonalResponse(query, aiAnalysis, evidenceSummary)
            } catch (e: Exception) {
                android.util.Log.w("TadabburAI", "AI personal response failed", e)
                null
            }
        } else null

        // AI bilan boyitilgan evidence
        val enrichedEvidence = if (aiResponse != null && aiResponse.evidenceNotes.isNotEmpty()) {
            evidence.mapIndexed { i, ev ->
                val note = aiResponse.evidenceNotes[(i + 1).toString()] ?: ""
                ev.copy(aiRelevance = note)
            }
        } else evidence

        val intro = aiResponse?.intro?.takeIf { it.isNotBlank() } ?: run {
            val labels = effectiveProfiles.take(2).joinToString(", ") { it.label }
            if (labels.isBlank()) {
                "Holatingizga mos dalillar quyida. Javob faqat indekslangan Qur'on oyatlari va hadis matnlaridan tuzildi; manbalardan tashqari xulosa yoki fatvo berilmaydi."
            } else {
                "Men yozganlaringizni \"$labels\" holatiga yaqin deb tushundim. Quyidagi dalillar faqat indekslangan Qur'on oyatlari va hadis matnlaridan tanlandi."
            }
        }

        TadabburAnswer(
            intro = intro,
            evidence = enrichedEvidence,
            aiInsight = aiResponse?.aiInsight ?: ""
        )
    }

    private fun matchProfiles(normalizedQuery: String, queryTokens: Set<String>): List<ProfileMatch> {
        if (queryTokens.isEmpty()) return emptyList()
        return topicProfiles
            .mapNotNull { profile ->
                val profileTokens = profile.matchTerms
                val overlap = queryTokens.sumOf { token ->
                    profileTokens.maxOfOrNull { candidate -> semanticTokenScore(token, candidate) } ?: 0
                }
                val phraseHits = profile.phrases.count { phrase -> normalizedQuery.contains(phrase) } * 10
                val score = overlap + phraseHits
                if (score >= 11) ProfileMatch(profile, score) else null
            }
            .sortedByDescending { it.score }
            .take(3)
    }

    private fun minimumScore(profiles: List<TopicProfile>): Int {
        return if (profiles.isEmpty()) 1 else 10
    }

    private fun isSourceAllowedForProfiles(
        type: EvidenceType,
        reference: String?,
        text: String,
        metadata: SourceHighlight?,
        profiles: List<TopicProfile>
    ): Boolean {
        if (profiles.isEmpty()) return true
        val normalizedText = normalize(text)
        return profiles.any { profile ->
            val anchoredVerse = type == EvidenceType.QURAN &&
                reference != null &&
                reference in profile.quranAnchors
            if (anchoredVerse) return@any true

            val metadataTopicMatches = metadata?.topics
                ?.any { topic -> normalize(topic) == normalize(profile.label) } == true
            val strictTermMatches = hasStrictEvidenceTerm(profile, normalizedText, type)
            // Allow through if EITHER metadata matches OR strict terms match
            // (previously required BOTH, which filtered out too many relevant hadiths)
            metadataTopicMatches || strictTermMatches
        }
    }

    private fun hasStrictEvidenceTerm(
        profile: TopicProfile,
        normalizedText: String,
        type: EvidenceType
    ): Boolean {
        val strictTerms = if (type == EvidenceType.HADITH && profile.strictHadithTerms.isNotEmpty()) {
            profile.strictHadithTerms
        } else {
            profile.strictEvidenceTerms
        }
        if (strictTerms.isEmpty()) return true
        return strictTerms.any { term ->
            val normalizedTerm = normalize(term)
            normalizedTerm.isNotBlank() && normalizedText.contains(normalizedTerm)
        }
    }

    private fun scoreDocument(
        terms: Set<String>,
        queryTokens: Set<String>,
        normalizedQuery: String,
        queryMeaning: String,
        text: String,
        metadata: SourceHighlight?,
        matchedProfiles: List<TopicProfile>,
        boost: Int = 0
    ): Int {
        val normalized = normalize(text)
        val words = normalized.split(" ").filter { it.isNotBlank() }.toSet()
        var total = boost

        val semanticText = metadata?.semanticText.orEmpty()
        if (metadata != null && semanticText.isNotBlank()) {
            val semanticWords = semanticText.split(" ").filter { it.isNotBlank() }.toSet()
            terms.forEach { term ->
                // Semantic terms get even more weight now
                total += termScore(term, semanticText, semanticWords) * 3
            }
            queryTokens.forEach { token ->
                total += semanticWords.maxOfOrNull { word -> semanticTokenScore(token, word) } ?: 0
            }
            total += meaningSimilarityScore(queryMeaning, semanticText)
            total += profileMetadataBoost(metadata, matchedProfiles)
        }

        terms.forEach { term ->
            total += termScore(term, normalized, words)
        }
        queryTokens.forEach { token ->
            total += words.maxOfOrNull { word -> semanticTokenScore(token, word) / 2 } ?: 0
        }
        
        // Add significant boost for strict evidence term matches
        if (matchedProfiles.isNotEmpty()) {
            val hasStrictMatch = matchedProfiles.any { profile -> 
                hasStrictEvidenceTerm(profile, normalized, if (metadata?.id?.startsWith("hadith") == true) EvidenceType.HADITH else EvidenceType.QURAN)
            }
            if (hasStrictMatch) total += 150
        }

        if (normalizedQuery.length >= 10) {
            // Boost direct phrase matching — content-meaning overlap is the most
            // important signal, especially when profiles are matched.
            val phraseScore = phraseSimilarityScore(normalizedQuery, normalized)
            total += if (matchedProfiles.isNotEmpty()) phraseScore * 5 else phraseScore * 2
        }
        return total
    }

    private fun buildQueryMeaningText(
        normalizedQuery: String,
        queryTokens: Set<String>,
        profiles: List<TopicProfile>
    ): String {
        return listOf(
            normalizedQuery,
            queryTokens.joinToString(" "),
            profiles.joinToString(" ") { profile ->
                listOf(
                    profile.label,
                    profile.moods.joinToString(" "),
                    profile.actions.joinToString(" "),
                    profile.quranTerms.joinToString(" "),
                    profile.hadithTerms.joinToString(" ")
                ).joinToString(" ")
            }
        ).joinToString(" ").trim()
    }

    private fun profileMetadataBoost(metadata: SourceHighlight, profiles: List<TopicProfile>): Int {
        if (profiles.isEmpty()) return 0
        val sourceMeaning = (
            metadata.topics + metadata.moods + metadata.actions + metadata.highlights
        ).joinToString(" ") { normalize(it) }
        return profiles.sumOf { profile ->
            var score = 0
            val normalizedLabel = normalize(profile.label)
            if (metadata.topics.any { normalize(it) == normalizedLabel }) {
                score += 120 // Increased from 80
            }
            profile.moods.forEach { mood ->
                if (sourceMeaning.contains(normalize(mood))) score += 30 // Increased from 20
            }
            profile.actions.forEach { action ->
                if (sourceMeaning.contains(normalize(action))) score += 25 // Increased from 15
            }
            score
        }
    }

    private fun meaningSimilarityScore(queryMeaning: String, sourceMeaning: String): Int {
        if (queryMeaning.length < 6 || sourceMeaning.length < 6) return 0
        val queryTokens = queryMeaning.split(" ").filter { it.length >= 4 }.toSet()
        val sourceTokens = sourceMeaning.split(" ").filter { it.length >= 4 }.toSet()
        if (queryTokens.isEmpty() || sourceTokens.isEmpty()) return 0
        val semanticHits = queryTokens.sumOf { queryToken ->
            sourceTokens.maxOfOrNull { sourceToken -> semanticTokenScore(queryToken, sourceToken) } ?: 0
        }
        return semanticHits / 2 + phraseSimilarityScore(queryMeaning, sourceMeaning)
    }

    private fun termScore(term: String, normalized: String, words: Set<String>): Int {
        if (term.length < 3) return 0
        val exactWeight = if (term.length >= 7) 12 else if (term.length >= 5) 8 else 4
        if (term in words) return exactWeight
        if (words.any { word -> word.startsWith(term) || term.startsWith(word) }) {
            return exactWeight / 2
        }
        return if (normalized.contains(term)) 2 else 0
    }

    private fun semanticTokenScore(a: String, b: String): Int {
        if (a.length < 3 || b.length < 3) return 0
        if (a == b) return 15 // Increased from 12
        
        // Specific disambiguation for 'tush' (dream vs fall/depressed)
        if (a == "tush" && (b.startsWith("tushkun") || b.startsWith("tushga"))) return 0
        if (b == "tush" && (a.startsWith("tushkun") || a.startsWith("tushga"))) return 0
        
        if (a.startsWith(b) || b.startsWith(a)) {
            // Only give points if the prefix is significant or world is long
            // Prevents "tush" matching "tushgan" incorrectly
            return if (a.length >= 5 && b.length >= 5) 7 else 0
        }
        if (a.length >= 5 && b.length >= 5) {
            val similarity = diceSimilarity(a, b)
            if (similarity >= 0.85) return 8 // Tightened threshold
            if (similarity >= 0.72) return 4
        }
        return 0
    }

    private fun phraseSimilarityScore(query: String, document: String): Int {
        val queryGrams = charTrigrams(query)
        if (queryGrams.isEmpty()) return 0
        val shortDoc = document.take(2200)
        val hits = queryGrams.count { gram -> shortDoc.contains(gram) }
        val ratio = hits.toFloat() / queryGrams.size
        return when {
            ratio >= 0.55f -> 10
            ratio >= 0.38f -> 6
            ratio >= 0.25f -> 3
            else -> 0
        }
    }

    private fun diceSimilarity(a: String, b: String): Float {
        val aBigrams = charBigrams(a)
        val bBigrams = charBigrams(b)
        if (aBigrams.isEmpty() || bBigrams.isEmpty()) return 0f
        val bMutable = bBigrams.toMutableList()
        var overlap = 0
        aBigrams.forEach { gram ->
            val index = bMutable.indexOf(gram)
            if (index >= 0) {
                overlap++
                bMutable.removeAt(index)
            }
        }
        return (2f * overlap) / (aBigrams.size + bBigrams.size)
    }

    private fun charBigrams(value: String): List<String> {
        val compact = value.replace(" ", "")
        if (compact.length < 2) return emptyList()
        return compact.windowed(2)
    }

    private fun charTrigrams(value: String): Set<String> {
        val compact = value.replace(" ", "")
        if (compact.length < 3) return emptySet()
        return compact.windowed(3).toSet()
    }

    private fun tokenize(value: String): Set<String> {
        return normalize(value)
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
    }

    private fun normalize(value: String): String {
        val prepared = value
            .lowercase(Locale.ROOT)
            .replace('ʼ', '\'')
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('`', '\'')
            .replace("g'", "g")
            .replace("o'", "o")
            .replace("ʻ", "")
        return transliterateUzbekCyrillic(prepared)
            .replace(Regex("[^a-z0-9а-яёғқўҳ' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun transliterateUzbekCyrillic(value: String): String {
        val builder = StringBuilder(value.length)
        value.forEach { char ->
            builder.append(cyrillicToLatin[char] ?: char)
        }
        return builder.toString()
    }

    private fun String.compactForChat(): String {
        return replace(Regex("\\s+"), " ").trim()
    }

    private fun SourceHighlight.displayHighlights(profiles: List<TopicProfile>): List<String> {
        val values = if (profiles.isEmpty()) {
            topics + moods + actions + highlights
        } else {
            val profileLabels = profiles.map { normalize(it.label) }.toSet()
            val strictTerms = profiles
                .flatMap { it.strictEvidenceTerms + it.strictHadithTerms + it.moods + it.actions }
                .map { normalize(it) }
                .filter { it.isNotBlank() }
            val matchedTopics = topics.filter { normalize(it) in profileLabels }
            val matchedHighlights = highlights.filter { highlight ->
                val normalizedHighlight = normalize(highlight)
                strictTerms.any { term ->
                    normalizedHighlight.contains(term) || term.contains(normalizedHighlight)
                }
            }
            matchedTopics + matchedHighlights
        }
        return values
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
    }

    private companion object {
        val stopWords = setOf(
            "men", "meni", "menga", "sen", "siz", "bilan", "uchun", "ham", "edi",
            "ekan", "qilib", "qildi", "deb", "bir", "shu", "bu", "u", "va", "yoki",
            "lekin", "agar", "juda", "nima", "qanday", "bugun", "hozir"
        )

        data class TopicProfile(
            val label: String,
            val triggers: Set<String>,
            val phrases: Set<String> = emptySet(),
            val moods: Set<String> = emptySet(),
            val actions: Set<String> = emptySet(),
            val sharedTerms: Set<String>,
            val quranTerms: Set<String>,
            val hadithTerms: Set<String>,
            val strictEvidenceTerms: Set<String> = emptySet(),
            val strictHadithTerms: Set<String> = emptySet(),
            val quranAnchors: Set<String>
        ) {
            val matchTerms: Set<String> = triggers +
                phrases.flatMap { it.split(" ") } +
                moods.flatMap { it.split(" ") } +
                actions.flatMap { it.split(" ") }
        }

        data class ProfileMatch(
            val profile: TopicProfile,
            val score: Int
        )

        val topicProfiles = listOf(
            TopicProfile(
                label = "sabr va taskin",
                triggers = setOf("sabr", "qiyin", "sinov", "dard", "xafa", "siqil", "siqilyap", "siqilyapman", "siqilvomman", "siqildim", "musibat", "qaygu", "gam", "tushkun", "charchadim", "ezildim", "yiglagim", "ogir", "muammo", "qiynalyapman"),
                phrases = setOf("konglim siqilyapti", "konglim siqildi", "hayotim ogir", "juda charchadim", "muammolar kop", "ichim siqilyapti", "ichim siqildi", "dilim ogriyapti"),
                moods = setOf("siqilish", "qaygu", "tushkunlik", "charchoq", "musibat"),
                actions = setOf("sabr qilish", "Allohni zikr qilish", "duo qilish"),
                sharedTerms = setOf("sabr", "musibat", "xafa", "qaygu", "dard", "rahmat", "ajr"),
                quranTerms = setOf("sabr", "sabrli", "musibat", "rahmat", "qalb", "zikr", "xotirjam", "noumid", "yordam"),
                hadithTerms = setOf("sabr", "musibat", "xafa", "qayg", "dard", "ajr", "rahmat", "duo"),
                strictEvidenceTerms = setOf("musibat", "sabr toqat", "sabr-toqat", "qaygu", "gam", "dard", "orom", "xotirjam", "qiyinchilik", "osonlik", "zikr", "rahmatidan noumid"),
                strictHadithTerms = setOf("musibat", "sabr toqat", "sabr-toqat", "qaygu", "gam", "dard", "yig", "inna", "qaytuvchimiz"),
                quranAnchors = setOf("2:45", "2:153", "2:155", "2:156", "2:157", "13:28", "39:53", "94:5", "94:6")
            ),
            TopicProfile(
                label = "yomon tush va qorquv",
                triggers = setOf("tush", "yomon", "qorqinchli", "qorqdim", "vahima", "bosinqiradim", "uyqu", "shayton", "panoh"),
                phrases = setOf("yomon tush", "qorqinchli tush", "tushimda yomon", "tush korib qorqdim", "tush ko'rib qorqdim", "bosinqirib uygondim", "shaytondan panoh"),
                moods = setOf("qorquv", "vahima", "yomon tush", "uyqudagi bezovtalik"),
                actions = setOf("Allohdan panoh sorash", "tushni hammaga aytmaslik", "namoz va duo qilish"),
                sharedTerms = setOf("tush", "uyqu", "qorq", "vahima", "shayton", "panoh"),
                quranTerms = setOf("panoh", "yomonlik", "vasvasa", "shayton", "qorq"),
                hadithTerms = setOf("tush", "uyqu", "shayton", "panoh", "chap", "tufla", "aytmasin", "qorq"),
                strictEvidenceTerms = setOf("panoh", "yomonlik", "vasvasa", "shayton", "tush", "qorq"),
                strictHadithTerms = setOf("yomon tush", "qorqinchli tush", "shaytondan panoh", "tushini aytmasin", "uyqusida qorq", "tushida qorq", "vahimali tush", "chap tomoniga tuf", "chap tomonga tuf", "uch marta tuf"),
                quranAnchors = setOf("113:1", "113:2", "113:3", "113:4", "113:5", "114:1", "114:2", "114:3", "114:4", "114:5", "114:6")
            ),
            TopicProfile(
                label = "charchoq va yengillik",
                triggers = setOf("charchadim", "charchoq", "toldim", "holdan", "holsiz", "uyqum", "dam", "malol", "qiynaldim", "haddan"),
                phrases = setOf("juda charchadim", "holdan toydim", "ibodat malol", "qurbim yetmayapti", "uyqum kelyapti", "dam ololmayapman"),
                moods = setOf("charchoq", "malol kelish", "haddan oshish", "holsizlik"),
                actions = setOf("qurb yetadigan amal", "tanaga haqqini berish", "dam olish"),
                sharedTerms = setOf("charchoq", "uyqu", "dam", "qiyin", "malol", "haqqi"),
                quranTerms = setOf("qiyinchilik", "osonlik", "sabr", "yengillik"),
                hadithTerms = setOf("qurb", "malol", "tanang", "koz", "uyqu", "yotingiz", "haqqi"),
                strictEvidenceTerms = setOf("qiyinchilik", "osonlik", "malol", "qurb", "tanang", "uyqu", "haqqi"),
                strictHadithTerms = setOf("qurbingiz yetadigan", "malol kelmasa", "tanangning haqqi", "kozingning haqqi", "uyquga ham yot", "yotingiz", "haddan oshirib"),
                quranAnchors = setOf("94:5", "94:6", "2:286")
            ),
            TopicProfile(
                label = "tavba va mag'firat",
                triggers = setOf("tavba", "gunoh", "kechir", "afsus", "magfirat"),
                phrases = setOf("gunoh qilib qoydim", "ozimdan uyalaman", "alloh kechiradimi", "tavba qilmoqchiman"),
                moods = setOf("afsus", "uyalish", "pushaymonlik"),
                actions = setOf("tavba qilish", "magfirat sorash"),
                sharedTerms = setOf("tavba", "gunoh", "kechir", "magfirat", "rahmat"),
                quranTerms = setOf("tavba", "gunoh", "magfirat", "rahmat", "kechir", "zulm"),
                hadithTerms = setOf("tavba", "gunoh", "magfirat", "kechir", "rahmat"),
                strictEvidenceTerms = setOf("tavba", "gunoh", "magfirat", "kechir", "rahmat", "zulm", "pushaymon"),
                quranAnchors = setOf("3:135", "25:70", "39:53", "66:8")
            ),
            TopicProfile(
                label = "umid va xavotir",
                triggers = setOf("umid", "noumid", "yolgiz", "qorq", "xavotir", "vahima", "bezovta", "havotir", "panic", "panika", "tashvish", "kelajak", "qorqyapman"),
                phrases = setOf("hech kimim yoq", "kelajagimdan qorqaman", "xavotir bosyapti", "umidim qolmadi", "yolgiz his qilyapman"),
                moods = setOf("xavotir", "qorquv", "yolgizlik", "noumidlik"),
                actions = setOf("Allohga tavakkal qilish", "umidni saqlash"),
                sharedTerms = setOf("umid", "rahmat", "qorq", "xavotir", "qalb"),
                quranTerms = setOf("umid", "rahmat", "qorq", "xavf", "mahzun", "qalb", "xotirjam"),
                hadithTerms = setOf("umid", "rahmat", "qorq", "xavf", "qalb", "duo"),
                strictEvidenceTerms = setOf("umid", "noumid", "qorq", "xavf", "xavotir", "mahzun", "xotirjam", "rahmat"),
                quranAnchors = setOf("2:112", "2:277", "3:139", "13:28", "39:53")
            ),
            TopicProfile(
                label = "rizq va ish",
                triggers = setOf("rizq", "pul", "ish", "qarz", "halol", "kambagal", "kambagallik", "oylik", "topolmayapman", "ishsiz", "biznes", "savdo"),
                phrases = setOf("pulim yoq", "ishim yurishmayapti", "qarzim bor", "halol pul topish", "rizqim kamaydi"),
                moods = setOf("moliyaviy tashvish", "ishsizlik", "qarz"),
                actions = setOf("halol kasb", "tavakkal", "sadaqa"),
                sharedTerms = setOf("rizq", "halol", "sadaqa", "mol", "kasb"),
                quranTerms = setOf("rizq", "halol", "sadaqa", "taqvo", "tavakkal"),
                hadithTerms = setOf("rizq", "halol", "sadaqa", "mol", "kasb", "qo'l"),
                strictEvidenceTerms = setOf("rizq", "halol", "sadaqa", "mol", "kasb", "taqvo", "tavakkal", "qarz", "boy", "kambagal"),
                quranAnchors = setOf("11:6", "29:60", "65:2", "65:3")
            ),
            TopicProfile(
                label = "ota-ona va oila",
                triggers = setOf("ota", "ona", "otaona", "farzand", "onam", "otam", "oilam", "akam", "ukam", "singlim", "opam", "qarindosh"),
                phrases = setOf("ota onam bilan", "onam xafa", "otam bilan urishdim", "oilam bilan kelisha olmayapman"),
                moods = setOf("oilaviy kelishmovchilik", "ota ona bilan muammo"),
                actions = setOf("ehson qilish", "qarindoshlikni saqlash", "muloyim gapirish"),
                sharedTerms = setOf("ota", "ona", "yaxshilik", "qarindosh", "farzand"),
                quranTerms = setOf("ota", "ona", "yaxshilik", "ehson", "rahm", "qarindosh"),
                hadithTerms = setOf("ota", "ona", "yaxshilik", "qarindosh", "rahm"),
                strictEvidenceTerms = setOf("ota", "ona", "farzand", "qarindosh", "ehson", "yaxshilik", "oila"),
                quranAnchors = setOf("17:23", "17:24", "31:14", "46:15")
            ),
            TopicProfile(
                label = "namoz va ibodat",
                triggers = setOf("namoz", "ibodat", "dangasa", "masjid", "qazo", "bomdod", "qilolmayapman", "oqimayapman", "erinchoq"),
                phrases = setOf("namozga dangasalik", "namoz oqiy olmayapman", "ibodatda sustman", "bomdodga turolmayman"),
                moods = setOf("ibodatda sustlik", "dangasalikka berilish"),
                actions = setOf("namozni qoim qilish", "zikr qilish"),
                sharedTerms = setOf("namoz", "ibodat", "iymon", "masjid"),
                quranTerms = setOf("namoz", "ibodat", "sabr", "zikr", "fahsh"),
                hadithTerms = setOf("namoz", "ibodat", "iymon", "masjid", "jamoat"),
                strictEvidenceTerms = setOf("namoz", "ibodat", "masjid", "jamoat", "sajda", "ruku", "bomdod", "fahsh"),
                quranAnchors = setOf("2:45", "2:153", "20:14", "29:45")
            ),
            TopicProfile(
                label = "g'azab va kelishmovchilik",
                triggers = setOf("gazab", "jahl", "kechirolmayapman", "alam", "urish", "asab", "jahlim", "tortishuv", "rashk", "yomonkoraman"),
                phrases = setOf("jahlim chiqyapti", "kechira olmayapman", "asabim buzildi", "urishib qoldim"),
                moods = setOf("jahl", "gazab", "alam", "kechirolmaslik"),
                actions = setOf("afv qilish", "tilni tiyish", "sabr qilish"),
                sharedTerms = setOf("gazab", "jahl", "kechir", "sabr", "til"),
                quranTerms = setOf("gazab", "kechir", "afv", "sabr", "yaxshilik"),
                hadithTerms = setOf("gazab", "jahl", "kechir", "til", "sabr"),
                strictEvidenceTerms = setOf("gazab", "jahl", "kechir", "afv", "til", "haqorat", "urish", "yaxshilik"),
                quranAnchors = setOf("3:134", "7:199", "41:34", "42:40")
            ),
            TopicProfile(
                label = "kasallik va shifo",
                triggers = setOf("kasal", "ogriyapti", "shifo", "davo", "doktor", "xastalik", "bemor", "soglik", "operatsiya", "dori"),
                phrases = setOf("kasal bolib qoldim", "sogligim yomon", "dardimga shifo", "bemor yotibman"),
                moods = setOf("kasallik", "dard", "bemorlik"),
                actions = setOf("shifo sorash", "sabr qilish", "bemorni ziyorat qilish"),
                sharedTerms = setOf("kasal", "shifo", "dard", "sabr", "rahmat"),
                quranTerms = setOf("shifo", "rahmat", "sabr", "dard", "zarar"),
                hadithTerms = setOf("kasal", "bemor", "shifo", "dard", "sabr", "ziyorat"),
                strictEvidenceTerms = setOf("kasal", "bemor", "shifo", "dard", "ziyorat", "zarar"),
                quranAnchors = setOf("17:82", "21:83", "21:84", "26:80", "41:44")
            ),
            TopicProfile(
                label = "o'lim va qayg'u",
                triggers = setOf("olim", "vafot", "janoza", "qabr", "yiglayapman", "yoqotdim", "judolik", "motam", "aza"),
                phrases = setOf("yaqinim vafot etdi", "onam vafot etdi", "otam vafot etdi", "kimnidir yoqotdim", "olimdan qorqaman"),
                moods = setOf("judolik", "motam", "qaygu", "olimdan qorqish"),
                actions = setOf("sabr qilish", "oxiratni eslash", "duo qilish"),
                sharedTerms = setOf("olim", "vafot", "qabr", "sabr", "musibat", "qaygu"),
                quranTerms = setOf("olim", "sabr", "musibat", "qaytuvchi", "rahmat", "xavf"),
                hadithTerms = setOf("vafot", "janoza", "qabr", "musibat", "sabr", "yig"),
                strictEvidenceTerms = setOf("olim", "vafot", "janoza", "qabr", "musibat", "qaygu", "yig", "judolik"),
                quranAnchors = setOf("2:156", "2:157", "3:185", "21:35", "29:57")
            ),
            TopicProfile(
                label = "nikoh va munosabat",
                triggers = setOf("nikoh", "xotin", "erim", "ayolim", "turmush", "sevgi", "ajrash", "uylan", "rashk", "munosabat"),
                phrases = setOf("xotinim bilan", "erim bilan", "turmushim ogir", "ajrashmoqchiman", "uylanmoqchiman"),
                moods = setOf("turmushdagi muammo", "rashk", "ajrashish xavotiri"),
                actions = setOf("yaxshilik bilan muomala", "sabr", "adolat"),
                sharedTerms = setOf("nikoh", "xotin", "yaxshilik", "rahm", "sabr"),
                quranTerms = setOf("juft", "nikoh", "mawadda", "rahmat", "yaxshilik", "adolat"),
                hadithTerms = setOf("xotin", "ayol", "nikoh", "yaxshi", "rahm", "oila"),
                strictEvidenceTerms = setOf("nikoh", "xotin", "ayol", "er", "juft", "turmush", "yaxshilik", "adolat", "oila"),
                quranAnchors = setOf("4:19", "30:21", "65:2")
            ),
            TopicProfile(
                label = "harom istak va nafs",
                triggers = setOf("nafs", "shahvat", "zino", "harom", "porn", "lust", "qarayapman", "istak", "vasvasa"),
                phrases = setOf("harom narsaga qarayapman", "nafsimni yenga olmayapman", "zinodan qorqaman", "shahvat kuchli"),
                moods = setOf("vasvasa", "shahvat", "nafs bilan kurash"),
                actions = setOf("kozni tiyish", "poklanish", "namozga yopishish"),
                sharedTerms = setOf("nafs", "harom", "zino", "sabr", "qorq"),
                quranTerms = setOf("zino", "fahsh", "ko'z", "pok", "taqvo", "sabr"),
                hadithTerms = setOf("zino", "nafs", "ko'z", "fitna", "pok", "nikoh"),
                strictEvidenceTerms = setOf("nafs", "shahvat", "zino", "harom", "fahsh", "koz", "pok", "taqvo", "fitna", "nikoh"),
                quranAnchors = setOf("17:32", "23:5", "23:6", "24:30", "24:31", "29:45")
            ),
            TopicProfile(
                label = "zulm va adolatsizlik",
                triggers = setOf("zulm", "adolatsiz", "haqsizlik", "haqim", "aldashdi", "xiyonat", "mazlum", "tuhmat"),
                phrases = setOf("menga zulm qilishdi", "haqimni yeyishdi", "adolatsizlik boldi", "meni aldashdi"),
                moods = setOf("haqsizlik", "mazlumlik", "xiyonat", "tuhmat"),
                actions = setOf("adolatni tutish", "sabr", "haqni talab qilish"),
                sharedTerms = setOf("zulm", "haq", "sabr", "adolat", "kechir"),
                quranTerms = setOf("zulm", "zolim", "haq", "sabr", "adolat", "yordam"),
                hadithTerms = setOf("zulm", "haq", "mazlum", "adolat", "sabr", "tuhmat"),
                strictEvidenceTerms = setOf("zulm", "zolim", "haq", "mazlum", "adolat", "tuhmat", "xiyonat", "haqsizlik"),
                quranAnchors = setOf("3:160", "4:135", "14:42", "16:90", "42:40")
            ),
            TopicProfile(
                label = "hasad va solishtirish",
                triggers = setOf("hasad", "rashk", "solishtiraman", "boshqalar", "omad", "instagram", "boyroq", "chiroyliroq"),
                phrases = setOf("ozimni boshqalar bilan solishtiraman", "hasad qilyapman", "boshqalarga havas qilyapman"),
                moods = setOf("hasad", "rashk", "ozini solishtirish"),
                actions = setOf("shukr qilish", "nematni tan olish"),
                sharedTerms = setOf("hasad", "ne'mat", "shukr", "qalb", "rizq"),
                quranTerms = setOf("hasad", "ne'mat", "fazl", "shukr", "rizq"),
                hadithTerms = setOf("hasad", "ne'mat", "shukr", "rizq", "qalb"),
                strictEvidenceTerms = setOf("hasad", "rashk", "nemat", "shukr", "rizq", "fazl"),
                quranAnchors = setOf("4:32", "14:7", "113:5")
            ),
            TopicProfile(
                label = "ilm va o'qish",
                triggers = setOf("ilm", "oqish", "dars", "imtihon", "universitet", "ustoz", "talaba", "organish", "bilim"),
                phrases = setOf("oqishim qiyin", "imtihondan qorqyapman", "ilm olish", "dars qilolmayapman"),
                moods = setOf("imtihon xavotiri", "oqishdagi qiyinchilik"),
                actions = setOf("ilm talab qilish", "duo qilish", "sabr"),
                sharedTerms = setOf("ilm", "oqish", "sabr", "ustoz", "foyda"),
                quranTerms = setOf("ilm", "bilim", "hikmat", "duo", "ziyoda"),
                hadithTerms = setOf("ilm", "organ", "ustoz", "sahoba", "foyda"),
                strictEvidenceTerms = setOf("ilm", "oqish", "dars", "imtihon", "ustoz", "talaba", "bilim", "hikmat", "ziyoda"),
                quranAnchors = setOf("20:114", "39:9", "58:11", "96:1")
            ),
            TopicProfile(
                label = "qaror va maslahat",
                triggers = setOf("qaror", "tanlov", "ikkilan", "maslahat", "istikhor", "bilmayapman", "qaysi", "boshlaymi"),
                phrases = setOf("qanday qaror qilay", "qaysi yolni tanlay", "ikkilanib qoldim", "maslahat kerak"),
                moods = setOf("ikkilanish", "tanlov oldida qolish"),
                actions = setOf("maslahat qilish", "istixora", "tavakkal"),
                sharedTerms = setOf("qaror", "maslahat", "tavakkal", "duo", "yol"),
                quranTerms = setOf("maslahat", "tavakkal", "hidoyat", "yol", "duo"),
                hadithTerms = setOf("istikhor", "maslahat", "duo", "ish", "tavakkal"),
                strictEvidenceTerms = setOf("qaror", "tanlov", "maslahat", "istixora", "istikhor", "duo", "ish", "tavakkal", "hidoyat", "yol"),
                quranAnchors = setOf("3:159", "2:216", "65:3", "1:6")
            ),
            TopicProfile(
                label = "shukr va qanoat",
                triggers = setOf("shukr", "qanoat", "noshukur", "yetmayapti", "baxtsiz", "ne'mat", "norozi"),
                phrases = setOf("hech narsadan rozi emasman", "shukr qilolmayapman", "ozimni baxtsiz his qilaman"),
                moods = setOf("norozi bolish", "qanoatsizlik", "baxtsizlik"),
                actions = setOf("shukr qilish", "qanoat qilish"),
                sharedTerms = setOf("shukr", "ne'mat", "qanoat", "rizq", "sabr"),
                quranTerms = setOf("shukr", "ne'mat", "ziyoda", "rizq", "fazl"),
                hadithTerms = setOf("shukr", "ne'mat", "qanoat", "rizq", "boylik"),
                strictEvidenceTerms = setOf("shukr", "nemat", "qanoat", "rizq", "ziyoda", "fazl", "boylik"),
                quranAnchors = setOf("14:7", "16:18", "55:13", "93:11")
            ),
            TopicProfile(
                label = "til va g'iybat",
                triggers = setOf("giybat", "gapirdim", "tuhmat", "yolgon", "sir", "til", "haqorat", "sokdim"),
                phrases = setOf("giybat qilib qoydim", "yolgon gapirdim", "birovni haqorat qildim", "tilimni tiya olmayapman"),
                moods = setOf("giybatdan pushaymonlik", "tilni tiya olmaslik"),
                actions = setOf("tilni tiyish", "rost gapirish", "tavba"),
                sharedTerms = setOf("til", "giybat", "yolgon", "haq", "tavba"),
                quranTerms = setOf("giybat", "gumon", "yolgon", "til", "haq"),
                hadithTerms = setOf("til", "yolgon", "giybat", "haqorat", "musulmon"),
                strictEvidenceTerms = setOf("til", "giybat", "yolgon", "haqorat", "gumon", "haq", "musulmon", "tuhmat"),
                quranAnchors = setOf("49:11", "49:12", "33:70", "33:71")
            )
        )

        val cyrillicToLatin = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "yo", 'ж' to "j", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "x", 'ц' to "s", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sh", 'ъ' to "", 'ь' to "", 'э' to "e",
            'ю' to "yu", 'я' to "ya", 'ў' to "o", 'қ' to "q", 'ғ' to "g",
            'ҳ' to "h"
        )
    }
}
