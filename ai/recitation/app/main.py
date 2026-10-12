import asyncio
import base64
import binascii
from contextlib import asynccontextmanager
import json
import logging
import os
import tempfile
import time
from dataclasses import dataclass

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
    whisper_device: str = os.getenv("WHISPER_DEVICE", "cpu")
    whisper_compute: str = os.getenv("WHISPER_COMPUTE", "int8")
    worker_name: str = os.getenv("HOSTNAME", "worker-1")
    min_analysis_seconds: float = float(os.getenv("RECITATION_MIN_ANALYSIS_SECONDS", "1.5"))
    # A complete surah can take several minutes.  Analysis is intentionally
    # performed only after the reader taps Stop, not after a short silence.
    max_analysis_seconds: float = float(os.getenv("RECITATION_MAX_ANALYSIS_SECONDS", "900"))
    # How often (in seconds of newly buffered audio) a fast, non-final partial
    # transcription is produced so the client can highlight words live, karaoke
    # style, while the reader is still speaking.
    partial_interval_seconds: float = float(os.getenv("RECITATION_PARTIAL_INTERVAL_SECONDS", "1.5"))
    # Partials never re-transcribe the whole buffer: only the most recent
    # window is transcribed on each tick; older audio is transcribed once, in
    # window-sized blocks, and its text is kept as a committed prefix.  The
    # full buffer is transcribed only on Stop (the scored final result).
    partial_window_seconds: float = float(os.getenv("RECITATION_PARTIAL_WINDOW_SECONDS", "30"))
    # Buffers of sessions whose client disconnected without sending Stop are
    # evicted after this much inactivity, and the number of concurrently
    # buffered sessions is capped (each may hold up to max_analysis_seconds).
    session_idle_seconds: float = float(os.getenv("RECITATION_SESSION_IDLE_SECONDS", "300"))
    max_sessions: int = int(os.getenv("RECITATION_MAX_SESSIONS", "64"))
    # Upper bound for the direct /analyze endpoint.
    max_analyze_seconds: float = float(os.getenv("RECITATION_MAX_ANALYZE_SECONDS", "120"))
    # Number of transcriptions allowed to run at once (CPU bound).
    transcribe_concurrency: int = int(os.getenv("RECITATION_TRANSCRIBE_CONCURRENCY", "1"))


settings = Settings()
client: redis.Redis | None = None
model = None
loaded_model_name: str | None = None
session_buffers: dict[str, bytearray] = {}
session_last_partial_bytes: dict[str, int] = {}
# Per-session committed partial transcript: (bytes already transcribed, text).
session_committed: dict[str, tuple[int, str]] = {}
session_last_seen: dict[str, float] = {}
_transcribe_semaphore: asyncio.Semaphore | None = None


@dataclass(frozen=True)
class Transcription:
    """The recognition output and the signals used for a conservative score."""

    text: str
    average_log_probability: float | None = None
    no_speech_probability: float | None = None


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


def load_whisper_model():
    global loaded_model_name
    loaded_model_name = resolve_model_name()
    return WhisperModel(
        loaded_model_name,
        device=settings.whisper_device,
        compute_type=settings.whisper_compute,
    )


def is_silence(audio: bytes) -> bool:
    """True when 16-bit PCM is too quiet to hold speech (see Settings.min_speech_rms)."""
    usable = len(audio) - len(audio) % SAMPLE_WIDTH_BYTES
    if usable <= 0:
        return True
    try:
        import numpy as np

        samples = np.frombuffer(audio[:usable], dtype=np.int16).astype(np.float64)
        rms = float(np.sqrt(np.mean(samples * samples)))
    except ImportError:  # pragma: no cover - numpy ships with faster-whisper
        samples = memoryview(audio[:usable]).cast("h")
        rms = (sum(sample * sample for sample in samples) / len(samples)) ** 0.5
    return rms < settings.min_speech_rms


@asynccontextmanager
async def lifespan(app: FastAPI):
    global client, model
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
async def healthz() -> dict[str, str | int | bool]:
    return {
        # Without the model every recitation comes back empty: that is not "ok".
        "status": "ok" if model is not None else "degraded",
        "model_loaded": model is not None,
        "model_name": loaded_model_name or settings.whisper_model,
        "buffered_sessions": len(session_buffers),
        "buffered_bytes": sum(len(buffer) for buffer in session_buffers.values()),
    }


class AnalyzeRequest(BaseModel):
    audio_b64: str
    language: str = "ar"


class AnalyzeResponse(BaseModel):
    transcript: str
    tajweed: dict
    score: float


def max_analyze_bytes() -> int:
    return int(settings.max_analyze_seconds * SAMPLE_RATE * SAMPLE_WIDTH_BYTES)


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
    transcription = await transcribe_async(audio)
    transcript = transcription.text
    tajweed = check_tajweed(transcript, transcription)
    return AnalyzeResponse(
        transcript=transcript,
        tajweed=tajweed,
        score=tajweed.get("score", 0),
    )


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
                # Take the whole backlog at once so it can be coalesced (see handle_batch).
                count=256,
                block=5000,
            )
            for _, entries in messages:
                await handle_batch(entries)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("recitation consumer loop failed")
            await asyncio.sleep(2)


def partial_worthy(entries: list[tuple[str, dict[str, str]]]) -> set[int]:
    """Indexes of the audio entries that still deserve a live (partial) transcript.

    A transcription costs seconds on the server's CPUs whatever the audio length
    (Whisper pads to 30 s), so one per 1.5 s of speech piled up: live highlighting
    never kept pace and the final result waited behind stale partials (~17 s after
    Stop for a 5 s ayah). Within a batch only each session's latest audio entry is
    transcribed live, and none when that session's Stop is already queued.
    """
    stopping: set[str] = set()
    last_audio: dict[str, int] = {}
    for index, (_, fields) in enumerate(entries):
        session_id = fields.get("session_id") or ""
        event = fields.get("event", "audio")
        if event == "stop":
            stopping.add(session_id)
        elif event == "audio":
            last_audio[session_id] = index
    return {index for session_id, index in last_audio.items() if session_id not in stopping}


async def handle_batch(entries: list[tuple[str, dict[str, str]]]) -> None:
    worthy = partial_worthy(entries)
    for index, (message_id, fields) in enumerate(entries):
        await handle_stream_message(message_id, fields, allow_partial=index in worthy)


async def handle_stream_message(message_id: str, fields: dict[str, str], allow_partial: bool = True) -> None:
    if client is None:
        return
    try:
        result = await process_chunk(fields, allow_partial=allow_partial)
        if result is not None:
            payload = json.dumps(result, ensure_ascii=False)
            session_id = result["session_id"]
            await client.set(f"{RESULT_KEY_PREFIX}{session_id}", payload, ex=RESULT_TTL_SECONDS)
            await client.publish(f"{RESULT_CHANNEL_PREFIX}{session_id}", payload)
        await client.xack(STREAM_NAME, GROUP_NAME, message_id)
    except Exception as exc:
        logger.exception("failed to process recitation message %s", message_id)
        try:
            await client.xadd(
                DEAD_LETTER_STREAM,
                dead_letter_fields(message_id, fields, exc),
                maxlen=DEAD_LETTER_MAXLEN,
                approximate=True,
            )
        except Exception:
            logger.exception("failed to dead-letter recitation message %s", message_id)
        await client.xack(STREAM_NAME, GROUP_NAME, message_id)


def dead_letter_fields(message_id: str, fields: dict[str, str], exc: Exception) -> dict[str, str]:
    """Metadata-only copy of a failed message (the audio payload is dropped)."""
    kept = {key: value for key, value in fields.items() if key != "audio_b64"}
    kept["audio_b64_length"] = str(len(fields.get("audio_b64") or ""))
    kept["original_id"] = message_id
    kept["error"] = str(exc)[:500]
    kept["failed_at"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    return kept


def touch_session(session_id: str, now: float | None = None) -> bytearray:
    """Return the session buffer, creating it (within the session cap) if needed."""
    now = time.monotonic() if now is None else now
    buffer = session_buffers.get(session_id)
    if buffer is None:
        evict_idle_sessions(now)
        while len(session_buffers) >= max(1, settings.max_sessions):
            oldest = min(session_buffers, key=lambda sid: session_last_seen.get(sid, 0.0))
            logger.warning("evicting recitation session %s: session cap reached", oldest)
            drop_session(oldest)
        buffer = session_buffers[session_id] = bytearray()
    session_last_seen[session_id] = now
    return buffer


def drop_session(session_id: str) -> None:
    session_buffers.pop(session_id, None)
    session_last_partial_bytes.pop(session_id, None)
    session_committed.pop(session_id, None)
    session_last_seen.pop(session_id, None)


def evict_idle_sessions(now: float | None = None) -> int:
    """Drop buffers of sessions that went silent (client disconnected without Stop)."""
    now = time.monotonic() if now is None else now
    cutoff = now - settings.session_idle_seconds
    stale = [
        sid for sid in list(session_buffers)
        if session_last_seen.get(sid, 0.0) < cutoff
    ]
    for sid in stale:
        logger.info("evicting idle recitation session %s", sid)
        drop_session(sid)
    # Keep the auxiliary dicts consistent with the buffers.
    for orphan in [sid for sid in list(session_last_seen) if sid not in session_buffers]:
        session_last_seen.pop(orphan, None)
    return len(stale)


async def process_chunk(fields: dict[str, str], allow_partial: bool = True) -> dict | None:
    session_id = fields.get("session_id") or ""
    if not session_id:
        raise ValueError("missing session_id")

    event = fields.get("event", "audio")
    final = event == "stop"
    if event == "audio":
        audio = decode_audio(fields.get("audio_b64", ""))
        buffer = touch_session(session_id)
        buffer.extend(audio)
        removed = trim_buffer(buffer)
        if removed:
            shift_session_offsets(session_id, removed)
    elif not final:
        return None
    else:
        buffer = session_buffers.get(session_id, bytearray())

    if not final and not allow_partial:
        # Newer audio or the Stop for this session is already queued: just buffer it.
        return None

    if not final:
        # Fast, non-final transcription so the client can highlight words
        # live while the reader is still speaking (karaoke-style progress),
        # without paying for the full tajweed analysis on every chunk.
        last_bytes = session_last_partial_bytes.get(session_id, 0)
        if len(buffer) < min_analysis_bytes():
            return None
        if len(buffer) - last_bytes < partial_interval_bytes():
            return None
        session_last_partial_bytes[session_id] = len(buffer)
        text = await partial_transcript(session_id, buffer)
        if not text:
            return None
        return {
            "type": "result",
            "status": "in_progress",
            "final": False,
            "session_id": session_id,
            "user_id": fields.get("user_id"),
            "surah": int(fields.get("surah") or 0),
            "ayah": int(fields.get("ayah") or 0),
            "transcript": text,
            "transcription": text,
            "errors": [],
            "score": 0,
        }

    try:
        transcription = await transcribe_async(bytes(buffer))
    finally:
        if final:
            drop_session(session_id)
    transcript = transcription.text
    tajweed = check_tajweed(transcript, transcription)
    result = {
        "type": "result",
        "status": "completed",
        "final": final,
        "session_id": session_id,
        "user_id": fields.get("user_id"),
        "surah": int(fields.get("surah") or 0),
        "ayah": int(fields.get("ayah") or 0),
        "transcript": transcript,
        "transcription": transcript,
        "tajweed": tajweed,
        "errors": tajweed.get("issues", []),
        "score": tajweed.get("score", 0),
    }
    return result


def partial_window_bytes() -> int:
    window = int(settings.partial_window_seconds * SAMPLE_RATE * SAMPLE_WIDTH_BYTES)
    window -= window % SAMPLE_WIDTH_BYTES
    return max(window, partial_interval_bytes(), SAMPLE_WIDTH_BYTES)


async def partial_transcript(session_id: str, buffer: bytearray) -> str:
    """Live transcript whose cost is bounded by the partial window.

    Audio older than the window is transcribed exactly once, block by block,
    and kept as a committed prefix so the client still receives the transcript
    from the beginning of the recitation (it aligns words from the start).
    """
    window = partial_window_bytes()
    committed_bytes, committed_text = session_committed.get(session_id, (0, ""))
    while len(buffer) - committed_bytes > window:
        block = bytes(buffer[committed_bytes : committed_bytes + window])
        block_text = (await transcribe_async(block, fast=True)).text
        committed_bytes += len(block)
        committed_text = " ".join(part for part in (committed_text, block_text) if part)
    session_committed[session_id] = (committed_bytes, committed_text)
    tail = bytes(buffer[committed_bytes:])
    tail_text = (await transcribe_async(tail, fast=True)).text if tail else ""
    return " ".join(part for part in (committed_text, tail_text) if part).strip()


def shift_session_offsets(session_id: str, removed: int) -> None:
    """Adjust byte offsets after the oldest audio was trimmed from the buffer."""
    if session_id in session_last_partial_bytes:
        session_last_partial_bytes[session_id] = max(0, session_last_partial_bytes[session_id] - removed)
    if session_id in session_committed:
        committed_bytes, committed_text = session_committed[session_id]
        session_committed[session_id] = (max(0, committed_bytes - removed), committed_text)


def decode_audio(audio_b64: str) -> bytes:
    try:
        audio = base64.b64decode(audio_b64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError("invalid base64 audio") from exc
    if len(audio) % SAMPLE_WIDTH_BYTES != 0:
        raise ValueError("invalid PCM16 audio byte length")
    return audio


def min_analysis_bytes() -> int:
    return int(settings.min_analysis_seconds * SAMPLE_RATE * SAMPLE_WIDTH_BYTES)


def max_analysis_bytes() -> int:
    return int(settings.max_analysis_seconds * SAMPLE_RATE * SAMPLE_WIDTH_BYTES)


def partial_interval_bytes() -> int:
    return int(settings.partial_interval_seconds * SAMPLE_RATE * SAMPLE_WIDTH_BYTES)


def trim_buffer(buffer: bytearray) -> int:
    """Drop the oldest audio beyond the analysis limit; return bytes removed."""
    max_bytes = max_analysis_bytes()
    if len(buffer) > max_bytes:
        removed = len(buffer) - max_bytes
        del buffer[:removed]
        return removed
    return 0


async def transcribe_async(audio: bytes, fast: bool = False) -> Transcription:
    """Run the blocking Whisper transcription off the event loop."""
    global _transcribe_semaphore
    if model is None or not audio or len(audio) < 1600:
        return Transcription("")
    if _transcribe_semaphore is None:
        _transcribe_semaphore = asyncio.Semaphore(max(1, settings.transcribe_concurrency))
    async with _transcribe_semaphore:
        return await asyncio.to_thread(transcribe, audio, fast)


def transcribe(audio: bytes, fast: bool = False) -> Transcription:
    """Transcribe raw PCM 16-bit 16kHz mono audio using faster-whisper.

    `fast=True` trades accuracy for latency (lower beam size, no VAD pass) —
    used for the live, non-final partial transcriptions that drive word
    highlighting while the reader is still speaking.
    """
    if model is None or not audio or len(audio) < 1600 or is_silence(audio):
        return Transcription("")

    # Write to temp WAV file for faster-whisper.
    import wave

    with tempfile.NamedTemporaryFile(suffix=".wav", delete=True) as tmp:
        with wave.open(tmp.name, "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(SAMPLE_WIDTH_BYTES)
            wf.setframerate(SAMPLE_RATE)
            wf.writeframes(audio)

        segments, info = model.transcribe(
            tmp.name,
            language="ar",
            task="transcribe",
            beam_size=1 if fast else 5,
            vad_filter=settings.vad_filter and not fast,
            vad_parameters=dict(min_silence_duration_ms=500),
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
