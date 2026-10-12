import asyncio
import base64

import pytest
from fastapi import HTTPException

from app import main
from app.main import (
    AnalyzeRequest,
    Transcription,
    analyze,
    dead_letter_fields,
    drop_session,
    evict_idle_sessions,
    partial_transcript,
    session_buffers,
    session_committed,
    session_last_seen,
    settings,
    touch_session,
)


@pytest.fixture(autouse=True)
def _clean_sessions():
    for sid in list(session_buffers):
        drop_session(sid)
    yield
    for sid in list(session_buffers):
        drop_session(sid)


def test_idle_sessions_are_evicted() -> None:
    touch_session("old", now=0.0)
    touch_session("fresh", now=5.0)
    evicted = evict_idle_sessions(now=settings.session_idle_seconds + 3)
    assert evicted == 1
    assert "old" not in session_buffers
    assert "old" not in session_last_seen
    assert "fresh" in session_buffers


def test_session_cap_evicts_least_recent(monkeypatch) -> None:
    monkeypatch.setattr(settings, "max_sessions", 2)
    touch_session("a", now=100.0)
    touch_session("b", now=101.0)
    touch_session("c", now=102.0)
    assert set(session_buffers) == {"b", "c"}


def test_dead_letter_drops_audio_payload() -> None:
    fields = {"session_id": "s", "audio_b64": "A" * 1000, "event": "audio"}
    kept = dead_letter_fields("1-0", fields, ValueError("boom"))
    assert "audio_b64" not in kept
    assert kept["audio_b64_length"] == "1000"
    assert kept["error"] == "boom"


def test_partial_transcribes_only_recent_window(monkeypatch) -> None:
    calls: list[int] = []

    async def fake_transcribe(audio: bytes, fast: bool = False) -> Transcription:
        calls.append(len(audio))
        return Transcription(f"w{len(calls)}")

    monkeypatch.setattr(main, "transcribe_async", fake_transcribe)
    monkeypatch.setattr(settings, "partial_window_seconds", 1.0)
    window = main.partial_window_bytes()
    buffer = touch_session("s", now=1.0)
    buffer.extend(b"\0" * (window * 2 + 100))

    text = asyncio.run(partial_transcript("s", buffer))
    # Two committed blocks plus the short tail; nothing larger than a window.
    assert calls == [window, window, 100]
    assert text == "w1 w2 w3"

    calls.clear()
    buffer.extend(b"\0" * 200)
    text = asyncio.run(partial_transcript("s", buffer))
    assert calls == [300]  # committed audio is not re-transcribed
    assert text == "w1 w2 w1"
    assert session_committed["s"][0] == window * 2


def test_analyze_rejects_invalid_base64() -> None:
    with pytest.raises(HTTPException) as err:
        asyncio.run(analyze(AnalyzeRequest(audio_b64="not base64!!")))
    assert err.value.status_code == 400


def test_analyze_rejects_oversized_audio(monkeypatch) -> None:
    monkeypatch.setattr(settings, "max_analyze_seconds", 0.01)
    audio = base64.b64encode(b"\0" * 4000).decode()
    with pytest.raises(HTTPException) as err:
        asyncio.run(analyze(AnalyzeRequest(audio_b64=audio)))
    assert err.value.status_code == 413


def test_partials_are_coalesced_per_batch() -> None:
    from app.main import partial_worthy

    def audio(session: str) -> tuple[str, dict[str, str]]:
        return ("id", {"session_id": session, "event": "audio", "audio_b64": ""})

    entries = [audio("a"), audio("b"), audio("a"), audio("b"), ("id", {"session_id": "b", "event": "stop"})]
    # Only a's latest chunk is transcribed live; b is stopping, so it gets none.
    assert partial_worthy(entries) == {2}


def test_silence_is_not_transcribed() -> None:
    from app.main import is_silence
    import struct

    quiet = struct.pack("<" + "h" * 1600, *([20, -20] * 800))
    speech = struct.pack("<" + "h" * 1600, *([2000, -2000] * 800))
    assert is_silence(quiet)
    assert not is_silence(speech)
    assert is_silence(b"")


def test_missing_model_directory_falls_back(monkeypatch) -> None:
    from app import main as m

    monkeypatch.setattr(m.settings, "whisper_model", "/nonexistent/model")
    monkeypatch.setattr(m.settings, "whisper_fallback_model", "small")
    assert m.resolve_model_name() == "small"
