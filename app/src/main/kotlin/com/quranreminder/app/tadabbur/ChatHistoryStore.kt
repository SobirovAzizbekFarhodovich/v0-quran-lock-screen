package com.quranreminder.app.tadabbur

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** One chat turn as the UI renders it: a user question or an assistant answer. */
sealed interface StoredChatMessage {
    data class User(val text: String) : StoredChatMessage
    data class Assistant(val answer: TadabburAnswer) : StoredChatMessage
}

/**
 * Keeps the assistant conversation in a small JSON file so reopening the chat shows
 * past answers (with their sources) instantly and offline. Plain file instead of Room
 * on purpose: the history is a rolling window, not queryable data.
 */
class ChatHistoryStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val gson = Gson()

    suspend fun load(): List<StoredChatMessage> = withContext(Dispatchers.IO) {
        lock.withLock {
            // A snapshot not yet flushed to disk is newer than the file.
            pending.get()?.let { return@withLock it }
            if (!file.exists()) return@withLock emptyList()
            runCatching {
                file.bufferedReader(Charsets.UTF_8).use { gson.fromJson(it, HistoryFile::class.java) }
                    ?.messages.orEmpty()
                    .mapNotNull { it.toMessage() }
                    .takeLast(MAX_MESSAGES)
            }.getOrElse { error ->
                // A corrupt file must never break the chat screen: start fresh.
                android.util.Log.w("ChatHistory", "Dropping unreadable chat history", error)
                file.delete()
                emptyList()
            }
        }
    }

    /**
     * Saves [messages] in the background. Rapid calls collapse: each write takes the
     * newest pending snapshot, so an older list can never overwrite a newer one.
     */
    fun persist(messages: List<StoredChatMessage>) {
        pending.set(messages.takeLast(MAX_MESSAGES))
        storeScope.launch {
            lock.withLock {
                val snapshot = pending.getAndSet(null) ?: return@withLock
                write(HistoryFile(snapshot.map { it.toEntry() }))
            }
        }
    }

    /** Drops the local history; a write queued before this is discarded too. */
    fun clear() {
        pending.set(null)
        storeScope.launch {
            lock.withLock {
                pending.set(null)
                file.delete()
            }
        }
    }

    private fun write(payload: HistoryFile) {
        // Write to a temp file then rename so a crash mid-write keeps the old history.
        val temp = File(file.parentFile, "$FILE_NAME.part")
        runCatching {
            temp.bufferedWriter(Charsets.UTF_8).use { gson.toJson(payload, it) }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }.onFailure { error ->
            android.util.Log.w("ChatHistory", "Failed to save chat history", error)
            temp.delete()
        }
    }

    private fun StoredChatMessage.toEntry(): HistoryEntry = when (this) {
        is StoredChatMessage.User -> HistoryEntry(role = ROLE_USER, text = text)
        is StoredChatMessage.Assistant -> HistoryEntry(
            role = ROLE_ASSISTANT,
            text = answer.intro,
            aiInsight = answer.aiInsight,
            evidence = answer.evidence.map {
                HistoryEvidence(it.type.name, it.title, it.text, it.source, it.narrator, it.score, it.highlights, it.aiRelevance)
            }
        )
    }

    // Gson bypasses Kotlin defaults, so every field is read as nullable and defaulted here.
    private fun HistoryEntry.toMessage(): StoredChatMessage? = when (role) {
        ROLE_USER -> text?.takeIf { it.isNotBlank() }?.let { StoredChatMessage.User(it) }
        ROLE_ASSISTANT -> StoredChatMessage.Assistant(
            TadabburAnswer(
                intro = text.orEmpty(),
                evidence = evidence.orEmpty().map { e ->
                    TadabburEvidence(
                        type = EvidenceType.entries.firstOrNull { it.name == e.type } ?: EvidenceType.HADITH,
                        title = e.title.orEmpty(),
                        text = e.text.orEmpty(),
                        source = e.source.orEmpty(),
                        narrator = e.narrator.orEmpty(),
                        score = e.score ?: 0,
                        highlights = e.highlights.orEmpty(),
                        aiRelevance = e.aiRelevance.orEmpty()
                    )
                },
                aiInsight = aiInsight.orEmpty()
            )
        )
        else -> null
    }

    companion object {
        private const val FILE_NAME = "tadabbur_chat_history.json"
        private const val MAX_MESSAGES = 100
        private const val ROLE_USER = "user"
        private const val ROLE_ASSISTANT = "assistant"

        // Process-wide: writes outlive the chat screen (leaving it must not lose the
        // last answer) and saves from a recreated screen can't interleave.
        private val lock = Mutex()
        private val pending = AtomicReference<List<StoredChatMessage>?>(null)
        private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

// On-disk shapes are public so R8's `com.quranreminder.app.**` keep rule preserves
// their field names across app updates.
data class HistoryFile(val messages: List<HistoryEntry>? = null)

data class HistoryEntry(
    val role: String? = null,
    val text: String? = null,
    val aiInsight: String? = null,
    val evidence: List<HistoryEvidence>? = null
)

data class HistoryEvidence(
    val type: String? = null,
    val title: String? = null,
    val text: String? = null,
    val source: String? = null,
    val narrator: String? = null,
    val score: Int? = null,
    val highlights: List<String>? = null,
    val aiRelevance: String? = null
)
