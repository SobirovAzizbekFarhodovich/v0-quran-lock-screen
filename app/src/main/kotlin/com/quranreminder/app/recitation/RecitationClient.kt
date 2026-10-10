package com.quranreminder.app.recitation

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.quranreminder.app.BuildConfig
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.ui.theme.text
import com.quranreminder.app.util.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

data class RecitationResult(
    val score: Int = 0,
    val transcript: String = "",
    val issues: List<String> = emptyList(),
    val final: Boolean = true
)

/**
 * Streams microphone PCM to `/ws/recitation` and reports live/final results.
 *
 * All callbacks are delivered on the main thread. [strings] is the current UI strings
 * map (e.g. `LocalAppStrings.current`) used for localized error messages.
 */
class RecitationClient(context: Context, private val strings: Map<String, String>) {
    private val appContext = context.applicationContext
    private val session = SessionManager(appContext)
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val lock = Any()
    @Volatile private var socket: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var readerThread: Thread? = null
    @Volatile private var recording = false

    /** Incremented per [start]; callbacks from an older attempt are ignored. */
    @Volatile private var generation = 0
    /** Set once the current attempt has delivered a final result or an error. */
    @Volatile private var settled = false

    /**
     * [onPartial] fires for live, non-final transcripts while the reader is still
     * speaking (drives word-by-word highlighting); [onResult] fires once, with the
     * scored final transcript, after Stop. [onError] fires at most once per attempt.
     */
    fun start(
        surah: Int,
        ayah: Int,
        onPartial: (RecitationResult) -> Unit,
        onResult: (RecitationResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val attempt = synchronized(lock) { ++generation }
        settled = false
        stopAudio()
        socket?.cancel()
        socket = null

        val callbacks = Callbacks(attempt, onPartial, onResult, onError)
        scope.launch {
            // Recitation is server-side: fail fast with a clear message when offline.
            if (!NetworkStatus.isServerReachable(appContext)) {
                callbacks.fail(strings.text("recitation.error.offline"))
                return@launch
            }
            val token = session.validAccessToken()
            if (token.isNullOrBlank()) {
                callbacks.fail(strings.text("auth.error.sessionExpired"))
                return@launch
            }
            if (attempt != generation) return@launch
            openSocket(surah, ayah, token, retriedAuth = false, callbacks)
            if (!startAudio(callbacks)) callbacks.fail(strings.text("recitation.error.mic"))
        }
    }

    /** Stops the microphone and asks the server for the final, scored result. */
    fun stop() {
        stopAudio()
        socket?.send("{\"type\":\"stop\"}")
    }

    fun close() {
        synchronized(lock) { generation++ }
        settled = true
        stopAudio()
        socket?.close(1000, "done")
        socket = null
        scope.cancel()
        http.dispatcher.executorService.shutdown()
    }

    private fun openSocket(surah: Int, ayah: Int, token: String, retriedAuth: Boolean, callbacks: Callbacks) {
        val wsUrl = BuildConfig.SERVER_BASE_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/ws/recitation?surah=$surah&ayah=$ayah"
        val request = Request.Builder().url(wsUrl).header("Authorization", "Bearer $token").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            private fun isCurrent(webSocket: WebSocket) = callbacks.attempt == generation && socket === webSocket

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrent(webSocket)) return
                if (response?.code == 401 && !retriedAuth) {
                    // Access token rejected: refresh once and reconnect, keeping the mic running.
                    scope.launch {
                        val refreshed = session.refresh(failedToken = token)
                        if (refreshed is SessionManager.RefreshOutcome.Refreshed && callbacks.attempt == generation) {
                            openSocket(surah, ayah, refreshed.accessToken, retriedAuth = true, callbacks)
                        } else {
                            stopAudio()
                            callbacks.fail(strings.text("auth.error.sessionExpired"))
                        }
                    }
                    return
                }
                stopAudio()
                callbacks.fail(strings.text("recitation.error.connection"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent(webSocket)) return
                val json = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull() ?: return
                when (json.get("type")?.asString) {
                    "result" -> {
                        val issues = json.getAsJsonArray("errors")?.mapNotNull { item ->
                            if (item.isJsonPrimitive) item.asString else null
                        }.orEmpty()
                        val final = json.get("final")?.asBoolean ?: true
                        val result = RecitationResult(
                            json.get("score")?.asInt ?: 0,
                            json.get("transcript")?.asString.orEmpty(),
                            issues,
                            final
                        )
                        if (final) {
                            stopAudio()
                            callbacks.succeed(result)
                        } else {
                            callbacks.partial(result)
                        }
                    }
                    "error" -> {
                        stopAudio()
                        callbacks.fail(json.get("message")?.asString?.takeIf { it.isNotBlank() }
                            ?: strings.text("recitation.error.generic"))
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (!isCurrent(webSocket)) return
                stopAudio()
                // Server closed before sending a final result.
                callbacks.fail(strings.text("recitation.error.closed"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrent(webSocket)) return
                stopAudio()
                callbacks.fail(strings.text("recitation.error.closed"))
            }
        })
    }

    @SuppressLint("MissingPermission") // RecitationScreen checks RECORD_AUDIO before start().
    private fun startAudio(callbacks: Callbacks): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val record = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, 16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2)
        }.getOrNull() ?: return false
        if (record.state != AudioRecord.STATE_INITIALIZED || runCatching { record.startRecording() }.isFailure) {
            record.release()
            return false
        }
        val thread = Thread({
            val buffer = ByteArray(minBuffer)
            while (recording) {
                val count = record.read(buffer, 0, buffer.size)
                if (count > 0) {
                    socket?.send(ByteString.of(*buffer.copyOf(count)))
                } else if (count < 0) {
                    // ERROR_INVALID_OPERATION / ERROR_DEAD_OBJECT: the recorder is unusable.
                    if (recording) {
                        callbacks.fail(strings.text("recitation.error.mic"))
                        socket?.cancel()
                    }
                    break
                }
            }
        }, "recitation-mic")
        synchronized(lock) {
            if (callbacks.attempt != generation) {
                record.runCatching { stop(); release() }
                return true
            }
            recorder = record
            readerThread = thread
            recording = true
        }
        thread.start()
        return true
    }

    /**
     * Stops capture safely: clear the flag, stop the recorder (which unblocks a pending
     * read), wait for the reader thread to exit, and only then release the recorder.
     */
    private fun stopAudio() {
        val record: AudioRecord?
        val thread: Thread?
        synchronized(lock) {
            recording = false
            record = recorder
            thread = readerThread
            recorder = null
            readerThread = null
        }
        record?.runCatching { stop() }
        if (thread != null && thread !== Thread.currentThread()) {
            runCatching { thread.join(1_000) }
        }
        record?.runCatching { release() }
    }

    private inner class Callbacks(
        val attempt: Int,
        private val onPartial: (RecitationResult) -> Unit,
        private val onResult: (RecitationResult) -> Unit,
        private val onError: (String) -> Unit
    ) {
        private fun deliver(block: () -> Unit) = mainHandler.post {
            if (attempt == generation) block()
        }

        fun partial(result: RecitationResult) {
            if (!settled) deliver { onPartial(result) }
        }

        fun succeed(result: RecitationResult) = settleWith { onResult(result) }

        fun fail(message: String) = settleWith { onError(message) }

        private fun settleWith(block: () -> Unit) {
            synchronized(lock) {
                if (settled || attempt != generation) return
                settled = true
            }
            deliver(block)
        }
    }
}
