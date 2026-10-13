import asyncio
import base64
import binascii
from collections import deque
from contextlib import asynccontextmanager
import heapq
import itertools
import json
import logging
import os
import time
from dataclasses import dataclass, field

import numpy as np
import redis.asyncio as redis
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

try:
    from faster_whisper import WhisperModel
    WHISPER_IMPORT_ERROR: str | None = None
except Exception as exc:  # pragma: no cover - keeps health endpoint usable without model deps
    # Never silent: a missing dependency once left every recitation with an empty result
    # while /healthz still said "ok".
    WhisperModel = None
    WHISPER_IMPORT_ERROR = f"{type(exc).__name__}: {exc}"


logger = logging.getLogger("recitation")

STREAM_NAME = "recitation:audio"
DEAD_LETTER_STREAM = "recitation:audio:dead"
GROUP_NAME = "ai-recitation"
RESULT_KEY_PREFIX = "recitation:result:"
RESULT_CHANNEL_PREFIX = "recitation:results:"
RESULT_TTL_SECONDS = 300
# Dead-letter entries keep only metadata (never the audio payload) and the
# stream is capped so a misbehaving client cannot grow Redis without bound.
DEAD_LETTER_MAXLEN = int(os.getenv("RECITATION_DEAD_LETTER_MAXLEN", "1000"))
SAMPLE_RATE = 16000
SAMPLE_WIDTH_BYTES = 2
BYTES_PER_SECOND = SAMPLE_RATE * SAMPLE_WIDTH_BYTES

# Work classes competing for the CPU, most urgent first: a reader who tapped
# Stop is waiting; a block commit keeps the Stop wait short later; a live
# partial is a nicety that may be skipped under load.
PRIORITY_FINAL = 0
PRIORITY_BLOCK = 1
PRIORITY_PARTIAL = 2


def _env_flag(name: str, default: str) -> bool:
    return os.getenv(name, default).strip().lower() in {"1", "true", "yes", "on"}


@dataclass
class Settings:
    redis_addr: str = os.getenv("REDIS_ADDR", "localhost:6379")
    redis_password: str = os.getenv("REDIS_PASSWORD", "")
    redis_db: int = int(os.getenv("REDIS_DB", "0"))
    # A model name ("small") or a directory with a CTranslate2 model. Production uses
    # Tarteel's Quran fine-tune of whisper-base (scripts/convert_tarteel_model.py):
    # on 45 ayahs by three reciters it made 11% word errors against 23-44% for
    # "small", in under half the time and half the memory.
    whisper_model: str = os.getenv("WHISPER_MODEL", "small")
    # Used when WHISPER_MODEL points at a directory that is missing.
    whisper_fallback_model: str = os.getenv("WHISPER_FALLBACK_MODEL", "small")
    # Model for the live (non-final) partials only: Tarteel's whisper-tiny Quran
    # fine-tune, converted like the base model. On growing prefixes it highlighted
    # as many correct words as base (5.3 per partial, 20% vs 19% wrong) in half the
    # time (0.51 s vs 0.96 s), which with 4 readers kept Stop->final at 4 s vs 7 s.
    # Empty, or a directory that is missing or fails to load, means the main model
    # serves partials too.
    partial_model: str = os.getenv(
        "RECITATION_PARTIAL_MODEL", "/root/.cache/huggingface/noorain/whisper-tiny-ar-quran"
    )
    # CTranslate2 threads per model replica (0 = library default, 4). The server's
    # 2 vCPUs are one core with hyper-threading: 1 thread was as fast as 2 for a
    # single transcription and gave the best throughput for simultaneous ones.
    cpu_threads: int = int(os.getenv("WHISPER_CPU_THREADS", "1"))
    # Voice-activity filtering cut reciters' long madds as "silence" and doubled the
    # word errors, so it is off; quiet input is gated by level instead.
    vad_filter: bool = os.getenv("RECITATION_VAD", "0") == "1"
    # RMS (16-bit) under which audio counts as silence and is not transcribed, so
    # Whisper never invents text from room noise. Speech is in the thousands.
    min_speech_rms: float = float(os.getenv("RECITATION_MIN_SPEECH_RMS", "80"))
    # Segments Whisper itself rates as probably not speech are dropped. On room noise and
    # hum the Quran model still "hears" a word (often «وَالْمُؤْمِنِينَ») but with
    # no_speech_prob 0.38-0.49; recitation measured 0.00.
    max_no_speech_prob: float = float(os.getenv("RECITATION_MAX_NO_SPEECH_PROB", "0.3"))
    # Beam width of final-quality decoding (blocks and the Stop result) and of partials.
    beam_size: int = int(os.getenv("RECITATION_BEAM_SIZE", "5"))
    partial_beam_size: int = int(os.getenv("RECITATION_PARTIAL_BEAM_SIZE", "1"))
    # Decoding without timestamp tokens and without conditioning on the previous
    # 30 s window: on 45 ayahs WER 10.6% vs 11.6%, same speed.
    without_timestamps: bool = _env_flag("RECITATION_WITHOUT_TIMESTAMPS", "1")
    condition_on_previous_text: bool = _env_flag("RECITATION_CONDITION_ON_PREVIOUS_TEXT", "0")
    # Decoding budget per second of audio. Recitation measured at most 8.6 tokens/s;
    # without a bound a looping decode ("تَبَارَكَ الَّتَبَارَكَ ...", 227 tokens
    # for 1.5 s) held the CPU for 19 s.
    max_tokens_per_second: float = float(os.getenv("RECITATION_MAX_TOKENS_PER_SECOND", "12"))
    # Whisper pads every input to a 30 s window and the encoder cost grows faster
    # than linearly with it: a 5.5 s ayah spent 1.45 s of its 2.1 s in the encoder.
    # The encoder gets the real audio plus this much of the padding instead.
    # Measured on 45 ayahs: 2-3 s of padding made the model loop (WER 13-150%),
    # 4-8 s matched the full window (10.6-10.8% vs 10.6%) at a third of the time.
    # Negative = Whisper's fixed 30 s window.
    encoder_pad_seconds: float = float(os.getenv("RECITATION_ENCODER_PAD_SECONDS", "6"))
    whisper_device: str = os.getenv("WHISPER_DEVICE", "cpu")
    whisper_compute: str = os.getenv("WHISPER_COMPUTE", "int8")
    worker_name: str = os.getenv("HOSTNAME", "worker-1")
    min_analysis_seconds: float = float(os.getenv("RECITATION_MIN_ANALYSIS_SECONDS", "1.5"))
    # Upper bound of not-yet-committed audio kept per session (memory guard; with
    # block commits the uncommitted tail normally stays under block_max_seconds).
    max_analysis_seconds: float = float(os.getenv("RECITATION_MAX_ANALYSIS_SECONDS", "900"))
    # How often (in seconds of newly buffered audio) a fast, non-final partial
    # transcription is produced so the client can highlight words live, karaoke
    # style, while the reader is still speaking.
    partial_interval_seconds: float = float(os.getenv("RECITATION_PARTIAL_INTERVAL_SECONDS", "1.5"))
    # Long recitations: while the reader is still reciting, completed blocks of
    # audio are transcribed in final quality and committed, so on Stop only the
    # tail remains. A block is cut in a pause between min and max seconds (never
    # mid-word); max stays under Whisper's 30 s window so every block is a single
    # window. On the full Al-Mulk (7-11 min) this made 6.9%/7.8% word errors where
    # transcribing the whole recording at once made 24.6%/32.1% (it skipped
    # phrases) and took 94-101 s after Stop.
    block_commit: bool = _env_flag("RECITATION_BLOCK_COMMIT", "1")
    block_min_seconds: float = float(os.getenv("RECITATION_BLOCK_MIN_SECONDS", "12"))
    block_max_seconds: float = float(os.getenv("RECITATION_BLOCK_MAX_SECONDS", "24"))
    # Without a pause between min and max the cut waits for one up to this length.
    block_hard_max_seconds: float = float(os.getenv("RECITATION_BLOCK_HARD_MAX_SECONDS", "28"))
    # Cut as soon as a clear pause follows block_min seconds (shorter tail at Stop)
    # instead of waiting for block_max seconds of audio.
    block_eager: bool = _env_flag("RECITATION_BLOCK_EAGER", "1")
    # A pause is a 200 ms stretch whose level is under this fraction of the
    # block's median level (and anything under min_speech_rms).
    pause_ratio: float = float(os.getenv("RECITATION_PAUSE_RATIO", "0.1"))
    # Shortest quiet stretch (of the 200 ms-smoothed level) that counts as a pause.
    min_pause_seconds: float = float(os.getenv("RECITATION_MIN_PAUSE_SECONDS", "0.6"))
    # Buffers of sessions whose client disconnected without sending Stop are
    # evicted after this much inactivity, and the number of concurrently
    # buffered sessions is capped.
    session_idle_seconds: float = float(os.getenv("RECITATION_SESSION_IDLE_SECONDS", "300"))
    max_sessions: int = int(os.getenv("RECITATION_MAX_SESSIONS", "64"))
    # Upper bound for the direct /analyze endpoint.
    max_analyze_seconds: float = float(os.getenv("RECITATION_MAX_ANALYZE_SECONDS", "120"))
    # Number of transcriptions allowed to run at once (CPU bound). It is also the
    # number of model replicas (CTranslate2 workers) so they truly run in parallel.
    # With 2 vCPUs, 1 (strict priority: Stop first, then block commits, then
    # partials) beat 2 for 2 and 4 simultaneous readers (Stop->final mean 1.7 s /
    # 3.2 s vs 3.1 s / 4.1 s).
    transcribe_concurrency: int = int(os.getenv("RECITATION_TRANSCRIBE_CONCURRENCY", "1"))


@dataclass(frozen=True)
class Transcription:
    """The recognition output and the signals used for a conservative score."""

    text: str
    average_log_probability: float | None = None
    no_speech_probability: float | None = None
    # Number of segments the average log probability was taken over (for merging).
    segment_count: int = 0


@dataclass
class SessionState:
    """Audio of one recitation session that is not yet committed, and what is."""

    buffer: bytearray = field(default_factory=bytearray)
    # Final-quality transcriptions of the blocks already cut off the buffer.
    committed: list[Transcription] = field(default_factory=list)
    committed_bytes: int = 0
    # Total buffered audio (committed + buffer) at the last live partial.
    last_partial_total: int = 0
    last_seen: float = 0.0
    # Stream messages not yet absorbed, in arrival order: (message_id, fields).
    pending: deque = field(default_factory=deque)
    worker: asyncio.Task | None = None

    @property
    def total_bytes(self) -> int:
        return self.committed_bytes + len(self.buffer)

    def committed_text(self) -> str:
        return " ".join(part.text for part in self.committed if part.text)


class PriorityGate:
    """Semaphore that hands a free slot to the most urgent waiter first."""

    def __init__(self, slots: int) -> None:
        self.slots = max(1, slots)
        self.busy = 0
        self._waiters: list[tuple[int, int, asyncio.Future]] = []
        self._seq = itertools.count()

    async def acquire(self, priority: int) -> None:
        if self.busy < self.slots and not self._waiters:
            self.busy += 1
            return
        future = asyncio.get_running_loop().create_future()
        heapq.heappush(self._waiters, (priority, next(self._seq), future))
        try:
            await future
        except asyncio.CancelledError:
            if future.done() and not future.cancelled():
                self.release()  # the slot was handed over just before cancellation
            else:
                self._waiters = [w for w in self._waiters if w[2] is not future]
                heapq.heapify(self._waiters)
            raise

    def release(self) -> None:
        while self._waiters:
            _, _, future = heapq.heappop(self._waiters)
            if not future.done():
                future.set_result(None)  # the slot passes straight to this waiter
                return
        self.busy = max(0, self.busy - 1)

    @asynccontextmanager
    async def slot(self, priority: int):
        await self.acquire(priority)
        try:
            yield
        finally:
            self.release()


settings = Settings()
client: redis.Redis | None = None
model = None
partial_model = None
loaded_model_name: str | None = None
loaded_partial_model_name: str | None = None
sessions: dict[str, SessionState] = {}
_gate: PriorityGate | None = None


def gate() -> PriorityGate:
    global _gate
    if _gate is None:
        _gate = PriorityGate(settings.transcribe_concurrency)
    return _gate


def resolve_model_name() -> str:
    name = settings.whisper_model
    if name.startswith(("/", ".")) and not os.path.isdir(name):
        logger.error(
            "Whisper model directory %s is missing; falling back to %s",
            name,
            settings.whisper_fallback_model,
        )
        return settings.whisper_fallback_model
    return name


def trim_encoder_input(features: np.ndarray, pad_frames: int) -> np.ndarray:
    """Drop the zero padding faster-whisper adds to fill a 30 s window, keeping
    the real audio plus `pad_frames` (10 ms each) of it. Negative = keep all."""
    if pad_frames < 0:
        return features
    frames = features.shape[-1]
    active = np.flatnonzero(np.any(features.reshape(-1, frames) != 0, axis=0))
    if not active.size:
        return features
    keep = int(active[-1]) + 1 + pad_frames
    keep += keep % 2  # the encoder's convolution halves the frame count
    return features[..., : min(frames, keep)] if keep < frames else features


if WhisperModel is not None:

    class TrimmedWhisperModel(WhisperModel):
        """WhisperModel whose encoder sees the audio, not a 30 s padded window.

        faster-whisper zero-pads every window's features to 30 s and the encoder
        cost grows faster than linearly with its input (a 5.5 s ayah: 1.45 s
        padded vs 0.11 s trimmed); CTranslate2 accepts the shorter input.
        """

        def encode(self, features):
            pad = settings.encoder_pad_seconds
            frames = -1 if pad < 0 else int(round(pad * 100))
            return super().encode(trim_encoder_input(features, frames))
else:  # pragma: no cover
    TrimmedWhisperModel = None


def _new_model(name: str):
    return TrimmedWhisperModel(
        name,
        device=settings.whisper_device,
        compute_type=settings.whisper_compute,
        cpu_threads=settings.cpu_threads,
        num_workers=max(1, settings.transcribe_concurrency),
    )


def load_whisper_model():
    global loaded_model_name
    loaded_model_name = resolve_model_name()
    return _new_model(loaded_model_name)


def load_partial_model():
    """The separate partials model, or None to serve partials with the main model."""
    global loaded_partial_model_name
    name = settings.partial_model.strip()
    loaded_partial_model_name = None
    if not name or name == settings.whisper_model:
        return None
    if name.startswith(("/", ".")) and not os.path.isdir(name):
        logger.error("partial model directory %s is missing; partials use the main model", name)
        return None
    try:
        loaded = _new_model(name)
    except Exception:
        logger.exception("partial model %s failed to load; partials use the main model", name)
        return None
    loaded_partial_model_name = name
    return loaded


def is_silence(audio: bytes) -> bool:
    """True when 16-bit PCM is too quiet to hold speech (see Settings.min_speech_rms)."""
    usable = len(audio) - len(audio) % SAMPLE_WIDTH_BYTES
    if usable <= 0:
        return True
    samples = np.frombuffer(audio[:usable], dtype=np.int16).astype(np.float64)
    rms = float(np.sqrt(np.mean(samples * samples)))
    return rms < settings.min_speech_rms


@asynccontextmanager
async def lifespan(app: FastAPI):
    global client, model, partial_model
    host, port = settings.redis_addr.split(":")
    client = redis.Redis(
        host=host,
        port=int(port),
        password=settings.redis_password or None,
        db=settings.redis_db,
        decode_responses=True,
    )
    if WhisperModel is not None:
        model = load_whisper_model()
        partial_model = load_partial_model()
    else:
        logger.error("faster-whisper unavailable, recitation cannot transcribe: %s", WHISPER_IMPORT_ERROR)
    task = asyncio.create_task(consume_audio())
    yield
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        pass
    if client is not None:
        await client.close()


app = FastAPI(title="Islamic AI Recitation Service", lifespan=lifespan)


@app.get("/health")
@app.get("/healthz")
async def healthz() -> dict[str, str | int | bool | None]:
    return {
        # Without the model every recitation comes back empty: that is not "ok".
        "status": "ok" if model is not None else "degraded",
        "model_loaded": model is not None,
        "model_name": loaded_model_name or settings.whisper_model,
        "partial_model_name": loaded_partial_model_name or loaded_model_name or settings.whisper_model,
        "transcribe_concurrency": gate().slots,
        "buffered_sessions": len(sessions),
        "buffered_bytes": sum(len(state.buffer) for state in sessions.values()),
    }


class AnalyzeRequest(BaseModel):
    audio_b64: str
    language: str = "ar"


class AnalyzeResponse(BaseModel):
    transcript: str
    tajweed: dict
    score: float


def max_analyze_bytes() -> int:
    return int(settings.max_analyze_seconds * BYTES_PER_SECOND)


@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze(req: AnalyzeRequest) -> AnalyzeResponse:
    """HTTP endpoint for direct audio analysis (alternative to Redis stream)."""
    # Reject oversized payloads before decoding (base64 is ~4/3 of the raw size).
    if len(req.audio_b64) > (max_analyze_bytes() * 4) // 3 + 4:
        raise HTTPException(status_code=413, detail="audio is too long")
    try:
        audio = decode_audio(req.audio_b64)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    if len(audio) > max_analyze_bytes():
        raise HTTPException(status_code=413, detail="audio is too long")
    transcription = await transcribe_blocks(audio)
    transcript = transcription.text
    tajweed = check_tajweed(transcript, transcription)
    return AnalyzeResponse(
        transcript=transcript,
        tajweed=tajweed,
        score=tajweed.get("score", 0),
    )


# --- Stream consumption ---------------------------------------------------------
#
# The consumer only routes messages: each session gets its own worker task that
# absorbs that session's messages in order, so sessions are transcribed in
# parallel (bounded by the PriorityGate) and one session's long transcription
# never holds up another session's Stop.


async def consume_audio() -> None:
    if client is None:
        return
    try:
        await client.xgroup_create(STREAM_NAME, GROUP_NAME, id="0", mkstream=True)
    except redis.ResponseError as exc:
        if "BUSYGROUP" not in str(exc):
            raise

    while True:
        try:
            evict_idle_sessions()
            messages = await client.xreadgroup(
                GROUP_NAME,
                settings.worker_name,
                {STREAM_NAME: ">"},
                count=256,
                block=5000,
            )
            for _, entries in messages:
                for message_id, fields in entries:
                    await dispatch(message_id, fields)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("recitation consumer loop failed")
            await asyncio.sleep(2)


async def dispatch(message_id: str, fields: dict[str, str]) -> None:
    """Queue a stream message on its session and make sure a worker runs it."""
    session_id = fields.get("session_id") or ""
    event = fields.get("event", "audio")
    if not session_id or event not in {"audio", "stop"}:
        if not session_id:
            await dead_letter(message_id, fields, ValueError("missing session_id"))
        else:
            await ack(message_id)
        return
    state = touch_session(session_id)  # a Stop without audio still gets a final result
    state.pending.append((message_id, fields))
    if state.worker is None or state.worker.done():
        state.worker = asyncio.create_task(session_worker(session_id, state))


async def session_worker(session_id: str, state: SessionState) -> None:
    # No await between "nothing pending" and returning: dispatch() relies on a
    # finished task meaning nothing is left unprocessed.
    while state.pending:
        try:
            await process_pending(session_id, state)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("recitation worker for session %s failed", session_id)


async def ack(message_id: str) -> None:
    if client is not None:
        await client.xack(STREAM_NAME, GROUP_NAME, message_id)


async def dead_letter(message_id: str, fields: dict[str, str], exc: Exception) -> None:
    logger.error("recitation message %s failed: %s", message_id, exc)
    if client is None:
        return
    try:
        await client.xadd(
            DEAD_LETTER_STREAM,
            dead_letter_fields(message_id, fields, exc),
            maxlen=DEAD_LETTER_MAXLEN,
            approximate=True,
        )
    except Exception:
        logger.exception("failed to dead-letter recitation message %s", message_id)
    await ack(message_id)


async def publish(result: dict) -> None:
    if client is None:
        return
    payload = json.dumps(result, ensure_ascii=False)
    session_id = result["session_id"]
    await client.set(f"{RESULT_KEY_PREFIX}{session_id}", payload, ex=RESULT_TTL_SECONDS)
    await client.publish(f"{RESULT_CHANNEL_PREFIX}{session_id}", payload)


async def absorb_audio(session_id: str, state: SessionState) -> tuple[dict[str, str] | None, tuple | None]:
    """Buffer every queued audio message up to the first Stop.

    Returns (fields of the newest absorbed audio message, the Stop message left
    at the head of the queue or None). Nothing is transcribed here.
    """
    latest = None
    while state.pending:
        message_id, fields = state.pending[0]
        if fields.get("event", "audio") == "stop":
            return latest, state.pending[0]
        state.pending.popleft()
        try:
            audio = decode_audio(fields.get("audio_b64", ""))
        except ValueError as exc:
            await dead_letter(message_id, fields, exc)
            continue
        state.buffer.extend(audio)
        trim_buffer(state)
        state.last_seen = time.monotonic()
        latest = fields
        await ack(message_id)
    return latest, None


async def process_pending(session_id: str, state: SessionState) -> None:
    latest, stop = await absorb_audio(session_id, state)
    if stop is None:
        # Partial first (the reader is watching the highlight), then the block
        # commit, which only shortens the wait after Stop.
        result = await live_partial(session_id, state, latest or {})
        if result is not None:
            await publish(result)
        if not any(queued.get("event") == "stop" for _, queued in state.pending):
            # (With a Stop queued the final path commits at final priority.)
            await commit_blocks(state)
        return

    message_id, fields = state.pending.popleft()
    try:
        await commit_blocks(state, priority=PRIORITY_FINAL)
        result = await final_result(session_id, state, fields)
        await publish(result)
        await ack(message_id)
    except Exception as exc:
        await dead_letter(message_id, fields, exc)
    finally:
        if sessions.get(session_id) is state:
            sessions.pop(session_id, None)
        # Anything queued after the Stop belongs to a new session with this id.
        if state.pending:
            new_state = touch_session(session_id)
            new_state.pending.extend(state.pending)
            state.pending.clear()
            if new_state.worker is None or new_state.worker.done():
                new_state.worker = asyncio.create_task(session_worker(session_id, new_state))


def base_result(session_id: str, fields: dict[str, str], state: SessionState) -> dict:
    return {
        "type": "result",
        "session_id": session_id,
        "user_id": fields.get("user_id"),
        "surah": int(fields.get("surah") or 0),
        "ayah": int(fields.get("ayah") or 0),
        "audio_seconds": round(state.total_bytes / BYTES_PER_SECOND, 2),
    }


async def live_partial(session_id: str, state: SessionState, fields: dict[str, str]) -> dict | None:
    """Fast, non-final transcript so the client can highlight words while the
    reader is still speaking: final-quality committed blocks + a fast pass over
    the uncommitted tail (never more than one Whisper window)."""
    if state.total_bytes < min_analysis_bytes():
        return None
    if state.total_bytes - state.last_partial_total < partial_interval_bytes():
        return None
    async with gate().slot(PRIORITY_PARTIAL):
        # Waiting for the CPU may have taken a while: take the freshest audio,
        # and skip the partial when the Stop is already queued.
        newer, stop = await absorb_audio(session_id, state)
        fields = newer or fields
        if stop is not None:
            return None
        state.last_partial_total = state.total_bytes
        tail = bytes(state.buffer)
        tail_text = (await asyncio.to_thread(transcribe, tail, True)).text if tail else ""
    text = " ".join(part for part in (state.committed_text(), tail_text) if part).strip()
    if not text:
        return None
    return {
        **base_result(session_id, fields, state),
        "status": "in_progress",
        "final": False,
        "transcript": text,
        "transcription": text,
        "errors": [],
        "score": 0,
    }


async def commit_blocks(state: SessionState, priority: int = PRIORITY_BLOCK) -> None:
    """Transcribe (final quality) and commit every complete block in the buffer."""
    if not settings.block_commit:
        return
    final = priority == PRIORITY_FINAL
    while (cut := next_block_cut(state.buffer, final=final)) is not None:
        block = bytes(state.buffer[:cut])
        try:
            transcription = await transcribe_async(block, priority=priority)
        except Exception:
            # Never retry a failing block forever: its words are lost, the rest is not.
            logger.exception("block transcription failed; committing it empty")
            transcription = Transcription("")
        state.committed.append(transcription)
        state.committed_bytes += cut
        del state.buffer[:cut]


async def final_result(session_id: str, state: SessionState, fields: dict[str, str]) -> dict:
    tail = await transcribe_async(bytes(state.buffer), priority=PRIORITY_FINAL)
    transcription = merge_transcriptions([*state.committed, tail])
    transcript = transcription.text
    tajweed = check_tajweed(transcript, transcription)
    return {
        **base_result(session_id, fields, state),
        "status": "completed",
        "final": True,
        "transcript": transcript,
        "transcription": transcript,
        "tajweed": tajweed,
        "errors": tajweed.get("issues", []),
        "score": tajweed.get("score", 0),
    }


async def transcribe_blocks(audio: bytes, priority: int = PRIORITY_FINAL) -> Transcription:
    """Final-quality transcription of audio of any length, block by block (cut in
    pauses exactly as during a live session)."""
    parts = []
    start = 0
    for end in plan_blocks(audio):
        parts.append(await transcribe_async(audio[start:end], priority=priority))
        start = end
    parts.append(await transcribe_async(audio[start:], priority=priority))
    return merge_transcriptions(parts)


def plan_blocks(audio: bytes, step_seconds: float = 1.0) -> list[int]:
    """Block ends for already-recorded audio, as a live session would cut them
    (the audio is replayed in steps of `step_seconds`, then Stop)."""
    if not settings.block_commit:
        return []
    ends: list[int] = []
    start = 0
    step = max(SAMPLE_WIDTH_BYTES, _seconds_to_bytes(step_seconds))
    arrived = 0
    while arrived < len(audio):
        arrived = min(len(audio), arrived + step)
        while (cut := next_block_cut(audio[start:arrived], final=False)) is not None:
            start += cut
            ends.append(start)
    while (cut := next_block_cut(audio[start:], final=True)) is not None:
        start += cut
        ends.append(start)
    return ends


def merge_transcriptions(parts: list[Transcription]) -> Transcription:
    """One transcription out of consecutive blocks (text joined, confidence pooled)."""
    text = " ".join(part.text for part in parts if part.text).strip()
    scored = [part for part in parts if part.average_log_probability is not None]
    weights = [max(1, part.segment_count) for part in scored]
    average = (
        sum(part.average_log_probability * weight for part, weight in zip(scored, weights)) / sum(weights)
        if scored else None
    )
    no_speech = [part.no_speech_probability for part in parts if part.no_speech_probability is not None]
    return Transcription(
        text=text,
        average_log_probability=average,
        no_speech_probability=max(no_speech) if no_speech else None,
        segment_count=sum(part.segment_count for part in parts),
    )


PAUSE_EDGE_BYTES = 300 * BYTES_PER_SECOND // 1000  # keep 300 ms after a cut point


def next_block_cut(audio: bytes | bytearray, final: bool = False) -> int | None:
    """Where to cut the next block off the uncommitted audio, or None to wait.

    Live (final=False): with block_eager, at the first clear pause after
    block_min seconds; otherwise once block_max seconds are buffered, at the
    quietest point in [min, max]. Without a pause it waits for one up to
    block_hard_max seconds, then cuts at the quietest point anyway. On Stop
    (final=True) only audio beyond block_max is cut off (the rest is the tail).
    """
    if not settings.block_commit:
        return None
    n = len(audio)
    lo, hi, hard = block_min_bytes(), block_max_bytes(), block_hard_max_bytes()
    edge = PAUSE_EDGE_BYTES
    if n < hi:
        if final or not settings.block_eager or n < lo + edge:
            return None
        cut, quiet = find_pause(audio, lo, n - edge)
        return cut if quiet else None
    cut, quiet = find_pause(audio, lo, hi)
    if quiet:
        return cut
    if not final and n < hard:
        cut, quiet = find_pause(audio, lo, n - edge)
        return cut if quiet else None
    return find_pause(audio, lo, min(n, hard))[0]


def find_cut(audio: bytes | bytearray, min_bytes: int, max_bytes: int) -> int:
    """Byte offset in [min_bytes, max_bytes] at the quietest point of the audio."""
    return find_pause(audio, min_bytes, max_bytes)[0]


def find_pause(audio: bytes | bytearray, min_bytes: int, max_bytes: int) -> tuple[int, bool]:
    """(Byte offset in [min_bytes, max_bytes] at the quietest point, is it a pause).

    Energy is taken over 20 ms frames and smoothed over 200 ms so a cut lands in
    a pause (between words or ayahs), not in a short stop consonant; within a
    flat silent stretch the middle is chosen.
    """
    frame = SAMPLE_RATE // 50  # 20 ms
    frame_bytes = frame * SAMPLE_WIDTH_BYTES
    smoothing = 10  # frames (200 ms)
    # Look a little past max_bytes so the smoothing near the end sees real audio.
    usable = min(len(audio), max_bytes + smoothing * frame_bytes)
    usable -= usable % frame_bytes
    lo = -(-min_bytes // frame_bytes)
    hi = min(max_bytes, len(audio)) // frame_bytes
    if hi <= lo:
        cut = min(len(audio), max_bytes)
        return cut - cut % SAMPLE_WIDTH_BYTES, False
    samples = np.frombuffer(bytes(audio[:usable]), dtype=np.int16).astype(np.float32)
    energy = (samples.reshape(-1, frame) ** 2).mean(axis=1)
    padded = np.pad(energy, (smoothing // 2, smoothing - smoothing // 2 - 1), mode="edge")
    smooth = np.convolve(padded, np.ones(smoothing, dtype=np.float32) / smoothing, mode="valid")
    window = smooth[lo:hi]
    typical = float(np.sqrt(np.median(smooth[:hi])))
    threshold = max(settings.pause_ratio * typical, settings.min_speech_rms) ** 2
    # Prefer the longest pause (a breath between ayahs over a short catch).
    start, end = _longest_run(window <= threshold)
    if end - start + 1 >= max(1, int(settings.min_pause_seconds * 50)):
        return (lo + (start + end) // 2) * frame_bytes, True
    # No pause: the quietest point (middle of the first quietest stretch).
    best = float(window.min())
    start, end = _longest_run(window <= best + max(1.0, best * 0.05), first=True)
    return (lo + (start + end) // 2) * frame_bytes, False


def _longest_run(mask: np.ndarray, first: bool = False) -> tuple[int, int]:
    """(start, end) of the longest run of True (the first run with first=True); (0, -1) if none."""
    best = (0, -1)
    index = 0
    n = len(mask)
    while index < n:
        if not mask[index]:
            index += 1
            continue
        run_start = index
        while index + 1 < n and mask[index + 1]:
            index += 1
        if first:
            return run_start, index
        if index - run_start > best[1] - best[0]:
            best = (run_start, index)
        index += 1
    return best


def dead_letter_fields(message_id: str, fields: dict[str, str], exc: Exception) -> dict[str, str]:
    """Metadata-only copy of a failed message (the audio payload is dropped)."""
    kept = {key: value for key, value in fields.items() if key != "audio_b64"}
    kept["audio_b64_length"] = str(len(fields.get("audio_b64") or ""))
    kept["original_id"] = message_id
    kept["error"] = str(exc)[:500]
    kept["failed_at"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    return kept


def touch_session(session_id: str, now: float | None = None) -> SessionState:
    """Return the session state, creating it (within the session cap) if needed."""
    now = time.monotonic() if now is None else now
    state = sessions.get(session_id)
    if state is None:
        evict_idle_sessions(now)
        while len(sessions) >= max(1, settings.max_sessions):
            oldest = min(sessions, key=lambda sid: sessions[sid].last_seen)
            logger.warning("evicting recitation session %s: session cap reached", oldest)
            drop_session(oldest)
        state = sessions[session_id] = SessionState()
    state.last_seen = now
    return state


def drop_session(session_id: str) -> None:
    state = sessions.pop(session_id, None)
    if state is not None and state.worker is not None and not state.worker.done():
        state.worker.cancel()


def evict_idle_sessions(now: float | None = None) -> int:
    """Drop buffers of sessions that went silent (client disconnected without Stop)."""
    now = time.monotonic() if now is None else now
    cutoff = now - settings.session_idle_seconds
    stale = [sid for sid, state in list(sessions.items()) if state.last_seen < cutoff]
    for sid in stale:
        logger.info("evicting idle recitation session %s", sid)
        drop_session(sid)
    return len(stale)


def decode_audio(audio_b64: str) -> bytes:
    try:
        audio = base64.b64decode(audio_b64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError("invalid base64 audio") from exc
    if len(audio) % SAMPLE_WIDTH_BYTES != 0:
        raise ValueError("invalid PCM16 audio byte length")
    return audio


def _seconds_to_bytes(seconds: float) -> int:
    value = int(seconds * BYTES_PER_SECOND)
    return value - value % SAMPLE_WIDTH_BYTES


def min_analysis_bytes() -> int:
    return _seconds_to_bytes(settings.min_analysis_seconds)


def max_analysis_bytes() -> int:
    return _seconds_to_bytes(settings.max_analysis_seconds)


def partial_interval_bytes() -> int:
    return _seconds_to_bytes(settings.partial_interval_seconds)


def block_max_bytes() -> int:
    return max(_seconds_to_bytes(settings.block_max_seconds), SAMPLE_RATE)


def block_hard_max_bytes() -> int:
    return max(_seconds_to_bytes(settings.block_hard_max_seconds), block_max_bytes())


def block_min_bytes() -> int:
    return min(_seconds_to_bytes(settings.block_min_seconds), block_max_bytes())


def trim_buffer(state: SessionState) -> int:
    """Drop the oldest uncommitted audio beyond the limit; return bytes removed."""
    max_bytes = max_analysis_bytes()
    if len(state.buffer) > max_bytes:
        removed = len(state.buffer) - max_bytes
        del state.buffer[:removed]
        return removed
    return 0


async def transcribe_async(audio: bytes, fast: bool = False, priority: int = PRIORITY_FINAL) -> Transcription:
    """Run the blocking Whisper transcription off the event loop, one CPU slot each."""
    if model is None or not audio or len(audio) < 1600:
        return Transcription("")
    async with gate().slot(PRIORITY_PARTIAL if fast else priority):
        return await asyncio.to_thread(transcribe, audio, fast)


def token_budget(audio_bytes: int) -> int:
    """Most tokens one Whisper window of this audio may decode (prompt fits in 448)."""
    seconds = min(30.0, audio_bytes / BYTES_PER_SECOND)
    return max(16, min(400, int(seconds * settings.max_tokens_per_second) + 16))


def transcribe(audio: bytes, fast: bool = False) -> Transcription:
    """Transcribe raw PCM 16-bit 16kHz mono audio using faster-whisper.

    `fast=True` is the live partial: the partials model (if configured) and a
    narrow beam. The audio goes to the model as a float array (no temp WAV file
    and no ffmpeg decode).
    """
    active = (partial_model or model) if fast else model
    if active is None or not audio or len(audio) < 1600 or is_silence(audio):
        return Transcription("")

    usable = len(audio) - len(audio) % SAMPLE_WIDTH_BYTES
    samples = np.frombuffer(audio[:usable], dtype=np.int16).astype(np.float32) / 32768.0
    segments, info = active.transcribe(
        samples,
        language="ar",
        task="transcribe",
        beam_size=max(1, settings.partial_beam_size if fast else settings.beam_size),
        without_timestamps=settings.without_timestamps,
        condition_on_previous_text=settings.condition_on_previous_text,
        vad_filter=settings.vad_filter and not fast,
        vad_parameters=dict(min_silence_duration_ms=500),
        max_new_tokens=token_budget(usable),
        # A live partial is never retried at higher temperatures: the fallback
        # re-decoded a 9-18 s prefix up to five times (13-31 s). Finals keep it.
        **({"temperature": 0.0} if fast else {}),
    )
    segment_list = [
        segment for segment in segments
        if getattr(segment, "no_speech_prob", 0.0) <= settings.max_no_speech_prob
    ]
    parts = [segment.text for segment in segment_list]
    probabilities = [
        segment.avg_logprob for segment in segment_list
        if getattr(segment, "avg_logprob", None) is not None
    ]
    average_log_probability = (
        sum(probabilities) / len(probabilities) if probabilities else None
    )
    return Transcription(
        text=" ".join(parts).strip(),
        average_log_probability=average_log_probability,
        no_speech_probability=getattr(info, "no_speech_prob", None),
        segment_count=len(probabilities),
    )


# --- Tajweed Rules Engine ---

# Arabic letter sets for tajweed rule detection
_HALQ_LETTERS = set("ءهعحغخ")
_IDGHAM_LETTERS = set("يرملون")
_IKHFA_LETTERS = set("تثجدذزسشصضطظفقك")
_MADD_LETTERS = set("اوي")
_NUN_SAKIN = "نْ"
_TANWIN_MARKS = set("\u064B\u064C\u064D")  # fathatan, dammatan, kasratan
_SUKUN = "\u0652"
_SHADDA = "\u0651"


def next_pronounced_letter(text: str, start: int) -> tuple[str, int] | None:
    """Find the next Arabic base letter, ignoring spaces and vowel marks."""
    for index in range(start, len(text)):
        char = text[index]
        if char.isspace() or char in _TANWIN_MARKS or char in {_SUKUN, _SHADDA, "\u064e", "\u064f", "\u0650"}:
            continue
        return char, index
    return None


def check_tajweed(text: str, transcription: Transcription | None = None) -> dict:
    """Return a conservative *recognition* score and textual tajweed opportunities.

    A transcript cannot prove pronunciation, duration, or makhraj.  The previous
    implementation claimed a 100-point tajweed score whenever text existed.  We
    now report text-only findings separately and score only recognition confidence
    until forced alignment against the selected ayah is available.
    """
    if not text:
        return {"score": 0, "issues": [], "rules_checked": 0}

    issues = []
    rules_checked = 0
    opportunities = []

    # 1. Ghunna — Nun or Mim with Shadda
    rules_checked += 1
    for i, ch in enumerate(text):
        if ch in "نم" and i + 1 < len(text) and text[i + 1] == _SHADDA:
            opportunities.append({"rule": "ghunna", "position": i})

    # 2. Idgham — Nun sakin/tanwin followed by idgham letters (يرملون)
    rules_checked += 1
    for i in range(len(text) - 1):
        if text[i] == "ن" and i + 1 < len(text) and text[i + 1] == _SUKUN:
            next_letter = next_pronounced_letter(text, i + 2)
            if next_letter and next_letter[0] in _IDGHAM_LETTERS:
                opportunities.append({"rule": "idgham", "position": i})

    # 3. Ikhfa — Nun sakin/tanwin followed by ikhfa letters (15 letters)
    rules_checked += 1
    for i in range(len(text) - 1):
        if text[i] == "ن" and i + 1 < len(text) and text[i + 1] == _SUKUN:
            next_letter = next_pronounced_letter(text, i + 2)
            if next_letter and next_letter[0] in _IKHFA_LETTERS:
                opportunities.append({"rule": "ikhfa", "position": i})

    # 4. Iqlab — Nun sakin/tanwin followed by Ba
    rules_checked += 1
    for i in range(len(text) - 1):
        if text[i] == "ن" and i + 1 < len(text) and text[i + 1] == _SUKUN:
            next_letter = next_pronounced_letter(text, i + 2)
            if next_letter and next_letter[0] == "ب":
                opportunities.append({"rule": "iqlab", "position": i})

    # 5. Izhar — Nun sakin/tanwin followed by halq letters
    rules_checked += 1
    for i in range(len(text) - 1):
        if text[i] == "ن" and i + 1 < len(text) and text[i + 1] == _SUKUN:
            next_letter = next_pronounced_letter(text, i + 2)
            if next_letter and next_letter[0] in _HALQ_LETTERS:
                opportunities.append({"rule": "izhar", "position": i})

    # 6. Madd — Long vowels
    rules_checked += 1
    madd_count = 0
    for i in range(len(text) - 1):
        if text[i] in _MADD_LETTERS and i + 1 < len(text):
            if text[i + 1] == _SUKUN or text[i + 1] in _MADD_LETTERS:
                madd_count += 1

    # avg_logprob is normally <= 0.  -0.2 is strong recognition; -1.5 is weak.
    # This intentionally caps text-only output at 85: it is not a tajweed grade.
    confidence = 0.0
    if transcription and transcription.average_log_probability is not None:
        confidence = min(1.0, max(0.0, (transcription.average_log_probability + 1.5) / 1.3))
    if transcription and transcription.no_speech_probability is not None:
        confidence *= max(0.0, 1.0 - transcription.no_speech_probability)
    score = round(35 + 50 * confidence, 1) if text else 0.0

    return {
        "score": score,
        "issues": issues,
        "rules_checked": rules_checked,
        "madd_points": madd_count,
        "opportunities": opportunities,
        "analysis_status": "text_only_not_tajweed_verified",
        "recognition_confidence": round(confidence, 3),
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=8001)
