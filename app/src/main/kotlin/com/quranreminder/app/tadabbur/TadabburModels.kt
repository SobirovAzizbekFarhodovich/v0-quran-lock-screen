package com.quranreminder.app.tadabbur

data class HadithEntry(
    val id: String,
    val book: String,
    val volume: String = "",
    val chapter: String = "",
    val narrator: String = "",
    val text: String,
    val source: String,
    val reference: String
)

data class TadabburEvidence(
    val type: EvidenceType,
    val title: String,
    val text: String,
    val source: String,
    val narrator: String = "",
    val score: Int,
    val highlights: List<String> = emptyList(),
    val aiRelevance: String = ""
)

data class TadabburAnswer(
    val intro: String,
    val evidence: List<TadabburEvidence>,
    val aiInsight: String = ""
)

data class SourceHighlight(
    val id: String,
    val type: EvidenceType,
    val topics: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
    val highlights: List<String> = emptyList(),
    val semanticText: String = ""
)

enum class EvidenceType(val label: String) {
    QURAN("Qur'on"),
    HADITH("Hadis")
}
