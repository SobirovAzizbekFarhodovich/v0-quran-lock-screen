package com.quranreminder.app.audio

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's side of the recitation player: builds the ayah playlist, talks to
 * [QuranPlaybackService] through a MediaController, and publishes what is being recited.
 * Call it from the main thread.
 */
object QuranPlayer {
    /** Memorisation help: every ayah three times, or the current ayah until stopped. */
    enum class Repeat { OFF, AYAH_3, AYAH_LOOP }

    data class State(
        val active: Boolean = false,
        val surah: Int? = null,
        /** 0 while the basmala before the surah is recited. */
        val ayah: Int? = null,
        val surahName: String = "",
        val playing: Boolean = false,
        val buffering: Boolean = false,
        val error: Boolean = false,
        val repeat: Repeat = Repeat.OFF,
        val speed: Float = 1f,
        val reciter: Reciter = Reciter.HUSARY
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()

    /** What is loaded, so a change of repeat mode can rebuild the playlist in place. */
    private data class Loaded(val surah: Int, val surahName: String, val ayahCount: Int, val reciter: Reciter, val reciterName: String)
    private var loaded: Loaded? = null
    private lateinit var appContext: Context

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = true, playing = false, buffering = false)
        }
    }

    fun connect(context: Context) {
        if (future != null) return
        appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, QuranPlaybackService::class.java))
        val f = MediaController.Builder(appContext, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: run { future = null; return@addListener }
            controller = c
            c.addListener(listener)
            pending.forEach { it(c) }
            pending.clear()
            sync()
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun withController(context: Context, block: (MediaController) -> Unit) {
        connect(context)
        controller?.let(block) ?: pending.add(block)
    }

    /** Recites [surah] from [fromAyah] to its end (the basmala first when starting at ayah 1). */
    fun play(context: Context, surah: Int, surahName: String, ayahCount: Int, reciterName: String, fromAyah: Int = 1) {
        val reciter = ReciterPrefs.get(context)
        loaded = Loaded(surah, surahName, ayahCount, reciter, reciterName)
        _state.value = _state.value.copy(active = true, surah = surah, ayah = fromAyah, surahName = surahName, error = false, buffering = true, reciter = reciter)
        withController(context) { c -> load(c, fromAyah.coerceIn(1, ayahCount), withBasmala = fromAyah <= 1) }
    }

    private fun load(c: MediaController, fromAyah: Int, withBasmala: Boolean) {
        val l = loaded ?: return
        val repeat = _state.value.repeat
        val items = ArrayList<MediaItem>()
        fun item(ayah: Int, copy: Int): MediaItem {
            val (s, a) = if (ayah == 0) 1 to 1 else l.surah to ayah
            val title = if (ayah == 0) l.surahName else "${l.surahName} · $ayah"
            return MediaItem.Builder()
                .setMediaId("${l.surah}:$ayah:$copy")
                .setUri(AudioStore.uri(appContext, l.reciter, s, a))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(l.reciterName).setAlbumTitle("Noorain").build())
                .build()
        }
        when (repeat) {
            Repeat.AYAH_LOOP -> items += item(fromAyah, 0)
            else -> {
                if (withBasmala && l.surah != 1 && l.surah != 9) items += item(0, 0)
                val times = if (repeat == Repeat.AYAH_3) 3 else 1
                for (a in fromAyah..l.ayahCount) for (k in 0 until times) items += item(a, k)
            }
        }
        c.setMediaItems(items, 0, 0L)
        c.repeatMode = if (repeat == Repeat.AYAH_LOOP) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        c.setPlaybackSpeed(_state.value.speed)
        c.prepare()
        c.play()
    }

    fun toggle(context: Context) = withController(context) { c ->
        when {
            c.isPlaying -> c.pause()
            c.playbackState == Player.STATE_IDLE && loaded != null -> load(c, _state.value.ayah?.coerceAtLeast(1) ?: 1, withBasmala = false)
            else -> c.play()
        }
    }

    /** Skips to the next ayah (past any repeats of the current one). */
    fun next(context: Context) = withController(context) { c ->
        val l = loaded ?: return@withController
        val ayah = _state.value.ayah ?: return@withController
        if (ayah >= l.ayahCount) return@withController
        if (_state.value.repeat == Repeat.AYAH_LOOP) return@withController load(c, ayah + 1, withBasmala = false)
        val index = (c.currentMediaItemIndex + 1 until c.mediaItemCount).firstOrNull { parse(c.getMediaItemAt(it).mediaId)?.second != ayah }
        if (index != null) c.seekTo(index, 0L)
    }

    fun previous(context: Context) = withController(context) { c ->
        val ayah = _state.value.ayah ?: return@withController
        if (ayah <= 1) c.seekTo(0, 0L) else load(c, ayah - 1, withBasmala = false)
    }

    fun setRepeat(context: Context, repeat: Repeat) {
        if (repeat == _state.value.repeat) return
        _state.value = _state.value.copy(repeat = repeat)
        val ayah = _state.value.ayah?.coerceAtLeast(1) ?: return
        withController(context) { c -> if (loaded != null && c.mediaItemCount > 0) load(c, ayah, withBasmala = false) }
    }

    fun cycleSpeed(context: Context) {
        val next = when (_state.value.speed) { 1f -> 0.75f; 0.75f -> 1.25f; else -> 1f }
        _state.value = _state.value.copy(speed = next)
        withController(context) { it.setPlaybackSpeed(next) }
    }

    fun stop(context: Context) = withController(context) { c ->
        c.stop()
        c.clearMediaItems()
        loaded = null
        _state.value = State(repeat = _state.value.repeat, speed = _state.value.speed, reciter = _state.value.reciter)
    }

    private fun parse(mediaId: String?): Pair<Int, Int>? {
        val parts = mediaId?.split(":") ?: return null
        val s = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val a = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return s to a
    }

    private fun sync() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_ENDED) {
            // The surah is finished: close the player.
            c.stop()
            c.clearMediaItems()
            loaded = null
            _state.value = State(repeat = _state.value.repeat, speed = _state.value.speed, reciter = _state.value.reciter)
            return
        }
        val current = parse(c.currentMediaItem?.mediaId)
        val active = c.mediaItemCount > 0 && loaded != null
        _state.value = _state.value.copy(
            active = active,
            surah = current?.first ?: _state.value.surah,
            ayah = current?.second ?: _state.value.ayah,
            playing = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            error = if (c.isPlaying) false else _state.value.error
        )
    }
}
