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
from fastapi import FastAPI
from pydantic import BaseModel

try:
    from faster_whisper import WhisperModel
except Exception:  # pragma: no cover - keeps health endpoint usable without model deps
    WhisperModel = None


logger = logging.getLogger("recitation")

STREAM_NAME = "recitation:audio"
DEAD_LETTER_STREAM = "recitation:audio:dead"
GROUP_NAME = "ai-recitation"
RESULT_KEY_PREFIX = "recitation:result:"
RESULT_CHANNEL_PREFIX = "recitation:results:"
RESULT_TTL_SECONDS = 300
SAMPLE_RATE = 16000
SAMPLE_WIDTH_BYTES = 2


@dataclass
class Settings:
    redis_addr: str = os.getenv("REDIS_ADDR", "localhost:6379")
    redis_password: str = os.getenv("REDIS_PASSWORD", "")
    redis_db: int = int(os.getenv("REDIS_DB", "0"))
    whisper_model: str = os.getenv("WHISPER_MODEL", "small")
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


settings = Settings()
client: redis.Redis | None = None
model = None
session_buffers: dict[str, bytearray] = {}
session_last_partial_bytes: dict[str, int] = {}


@dataclass(frozen=True)
class Transcription:
    """The recognition output and the signals used for a conservative score."""

    text: str
    average_log_probability: float | None = None
    no_speech_probability: float | None = None


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
        model = WhisperModel(
            settings.whisper_model,
            device=settings.whisper_device,
            compute_type=settings.whisper_compute,
        )
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
        "status": "ok",
        "model_loaded": model is not None,
        "model_name": settings.whisper_model,
        "buffered_sessions": len(session_buffers),
    }


class AnalyzeRequest(BaseModel):
    audio_b64: str
    language: str = "ar"


class AnalyzeResponse(BaseModel):
    transcript: str
    tajweed: dict
    score: float


@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze(req: AnalyzeRequest) -> AnalyzeResponse:
    """HTTP endpoint for direct audio analysis (alternative to Redis stream)."""
    audio = base64.b64decode(req.audio_b64)
    transcription = transcribe(audio)
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
            messages = await client.xreadgroup(
                GROUP_NAME,
                settings.worker_name,
                {STREAM_NAME: ">"},
                count=4,
                block=5000,
            )
            for _, entries in messages:
                for message_id, fields in entries:
                    await handle_stream_message(message_id, fields)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("recitation consumer loop failed")
            await asyncio.sleep(2)


async def handle_stream_message(message_id: str, fields: dict[str, str]) -> None:
    if client is None:
        return
    try:
        result = await process_chunk(fields)
        if result is not None:
            payload = json.dumps(result, ensure_ascii=False)
            session_id = result["session_id"]
            await client.set(f"{RESULT_KEY_PREFIX}{session_id}", payload, ex=RESULT_TTL_SECONDS)
            await client.publish(f"{RESULT_CHANNEL_PREFIX}{session_id}", payload)
        await client.xack(STREAM_NAME, GROUP_NAME, message_id)
    except Exception as exc:
        failed_fields = {
            **fields,
            "original_id": message_id,
            "error": str(exc),
            "failed_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        }
        await client.xadd(DEAD_LETTER_STREAM, failed_fields)
        await client.xack(STREAM_NAME, GROUP_NAME, message_id)
        logger.exception("failed to process recitation message %s", message_id)


async def process_chunk(fields: dict[str, str]) -> dict | None:
    session_id = fields.get("session_id") or ""
    if not session_id:
        raise ValueError("missing session_id")

    event = fields.get("event", "audio")
    final = event == "stop"
    buffer = session_buffers.setdefault(session_id, bytearray())

    if event == "audio":
        audio = decode_audio(fields.get("audio_b64", ""))
        buffer.extend(audio)
        trim_buffer(buffer)
    elif not final:
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
        transcription = transcribe(bytes(buffer), fast=True)
        if not transcription.text:
            return None
        return {
            "type": "result",
            "status": "in_progress",
            "final": False,
            "session_id": session_id,
            "user_id": fields.get("user_id"),
            "surah": int(fields.get("surah") or 0),
            "ayah": int(fields.get("ayah") or 0),
            "transcript": transcription.text,
            "transcription": transcription.text,
            "errors": [],
            "score": 0,
        }

    transcription = transcribe(bytes(buffer))
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
    if final:
        session_buffers.pop(session_id, None)
        session_last_partial_bytes.pop(session_id, None)
    return result


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


def trim_buffer(buffer: bytearray) -> None:
    max_bytes = max_analysis_bytes()
    if len(buffer) > max_bytes:
        del buffer[: len(buffer) - max_bytes]


def transcribe(audio: bytes, fast: bool = False) -> Transcription:
    """Transcribe raw PCM 16-bit 16kHz mono audio using faster-whisper.

    `fast=True` trades accuracy for latency (lower beam size, no VAD pass) —
    used for the live, non-final partial transcriptions that drive word
    highlighting while the reader is still speaking.
    """
    if model is None or not audio or len(audio) < 1600:
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
            vad_filter=not fast,
            vad_parameters=dict(min_silence_duration_ms=500),
        )
        segment_list = list(segments)
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
