package com.quranreminder.app.recitation

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.quranreminder.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

data class RecitationResult(val score: Int = 0, val transcript: String = "", val issues: List<String> = emptyList())

class RecitationClient(private val token: String) {
    private val gson = Gson()
    private val http = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var recorder: AudioRecord? = null
    @Volatile private var recording = false

    fun start(surah: Int, ayah: Int, onResult: (RecitationResult) -> Unit, onError: (String) -> Unit) {
        val wsUrl = BuildConfig.SERVER_BASE_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/ws/recitation?surah=$surah&ayah=$ayah"
        socket = http.newWebSocket(
            Request.Builder().url(wsUrl).header("Authorization", "Bearer $token").build(),
            object : WebSocketListener() {
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = onError("Ulanishda xatolik: ${t.message}")
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val json = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull() ?: return
                    if (json.get("type")?.asString == "result") {
                        val issues = json.getAsJsonArray("errors")?.mapNotNull { item ->
                            if (item.isJsonPrimitive) item.asString else null
                        }.orEmpty()
                        onResult(RecitationResult(json.get("score")?.asInt ?: 0, json.get("transcript")?.asString.orEmpty(), issues))
                    } else if (json.get("type")?.asString == "error") onError(json.get("message")?.asString ?: "Tilovat xatosi")
                }
            }
        )
        val minBuffer = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            onError("Mikrofonni tayyorlab bo‘lmadi")
            return
        }
        recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2)
        recorder?.startRecording()
        recording = true
        Thread {
            val buffer = ByteArray(minBuffer)
            while (recording) {
                val count = recorder?.read(buffer, 0, buffer.size) ?: -1
                if (count > 0) socket?.send(ByteString.of(*buffer.copyOf(count)))
            }
        }.start()
    }

    fun stop() {
        recording = false
        recorder?.runCatching { stop(); release() }
        recorder = null
        socket?.send("{\"type\":\"stop\"}")
    }

    fun close() {
        stop()
        socket?.close(1000, "done")
        http.dispatcher.executorService.shutdown()
    }
}
