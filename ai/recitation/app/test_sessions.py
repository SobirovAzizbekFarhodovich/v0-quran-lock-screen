import asyncio
import base64
import struct
import threading

import numpy as np
import pytest
from fastapi import HTTPException

from app import main
from app.main import (
    AnalyzeRequest,
    BYTES_PER_SECOND,
    PriorityGate,
    Transcription,
    analyze,
    dead_letter_fields,
    drop_session,
    evict_idle_sessions,
    find_cut,
    merge_transcriptions,
    sessions,
    settings,
    touch_session,
    trim_encoder_input,
)


@pytest.fixture(autouse=True)
def _clean_sessions():
    for sid in list(sessions):
        drop_session(sid)
    main._gate = None
    yield
    for sid in list(sessions):
        drop_session(sid)
    main._gate = None


class FakeRedis:
    def __init__(self) -> None:
        self.acked: list[str] = []
        self.published: list[dict] = []
        self.dead: list[dict] = []

    async def xack(self, stream, group, message_id):
        self.acked.append(message_id)

    async def set(self, key, value, ex=None):
        pass

    async def publish(self, channel, payload):
        import json

        self.published.append(json.loads(payload))

    async def xadd(self, stream, fields, maxlen=None, approximate=None):
        self.dead.append(fields)


def tone(seconds: float, amplitude: int = 3000) -> bytes:
    n = int(seconds * main.SAMPLE_RATE)
    t = np.arange(n)
    return (amplitude * np.sin(2 * np.pi * 220 * t / main.SAMPLE_RATE)).astype(np.int16).tobytes()


def silence(seconds: float) -> bytes:
    return b"\0" * (int(seconds * main.SAMPLE_RATE) * 2)


@pytest.fixture
def fake_env(monkeypatch):
    """Fake Redis + a fake model that 'transcribes' audio as its length in seconds."""
    fake = FakeRedis()
    calls: list[tuple[int, bool]] = []

    def fake_transcribe(audio: bytes, fast: bool = False) -> Transcription:
        calls.append((len(audio), fast))
        return Transcription(f"{'p' if fast else 'f'}{len(audio) / BYTES_PER_SECOND:.1f}", -0.2, None, 1)

    monkeypatch.setattr(main, "client", fake)
    monkeypatch.setattr(main, "model", object())
    monkeypatch.setattr(main, "transcribe", fake_transcribe)
    return fake, calls


def audio_message(index: int, pcm: bytes, session: str = "s") -> tuple[str, dict[str, str]]:
    return (f"{index}-0", {
        "session_id": session, "user_id": "u", "surah": "67", "ayah": "1",
        "audio_b64": base64.b64encode(pcm).decode(),
    })


def stop_message(index: int, session: str = "s") -> tuple[str, dict[str, str]]:
    return (f"{index}-0", {"session_id": session, "user_id": "u", "surah": "67", "ayah": "1", "event": "stop"})


async def feed(messages, settle: bool = True) -> None:
    for message_id, fields in messages:
        await main.dispatch(message_id, fields)
    if settle:
        await drain()


async def drain() -> None:
    for _ in range(200):
        workers = [state.worker for state in sessions.values() if state.worker and not state.worker.done()]
        if not workers:
            await asyncio.sleep(0)
            if not any(state.worker and not state.worker.done() for state in sessions.values()):
                return
        await asyncio.gather(*workers, return_exceptions=True)


def test_find_cut_lands_in_the_pause() -> None:
    audio = tone(15.0) + silence(0.4) + tone(15.0)
    cut = find_cut(audio, 12 * BYTES_PER_SECOND, 24 * BYTES_PER_SECOND)
    assert 15.0 * BYTES_PER_SECOND <= cut <= 15.4 * BYTES_PER_SECOND
    assert cut % 2 == 0


def test_find_cut_prefers_the_quietest_pause() -> None:
    audio = tone(13.0) + tone(0.4, amplitude=500) + tone(5.0) + silence(0.4) + tone(10.0)
    cut = find_cut(audio, 12 * BYTES_PER_SECOND, 24 * BYTES_PER_SECOND)
    assert 18.4 * BYTES_PER_SECOND <= cut <= 18.8 * BYTES_PER_SECOND


def test_eager_cut_waits_for_a_pause_after_block_min(monkeypatch) -> None:
    from app.main import next_block_cut

    monkeypatch.setattr(settings, "block_eager", True)
    speech = tone(13.0)
    assert next_block_cut(speech) is None  # past min, but no pause yet
    audio = speech + silence(1.0) + tone(1.0)
    cut = next_block_cut(audio)
    assert cut is not None and 13.0 * BYTES_PER_SECOND <= cut <= 14.0 * BYTES_PER_SECOND
    # A short catch of breath (0.3 s) inside an ayah is not a pause to cut at.
    assert next_block_cut(speech + silence(0.3) + tone(1.0)) is None
    assert next_block_cut(audio, final=True) is None  # on Stop the short rest is the tail
    monkeypatch.setattr(settings, "block_eager", False)
    assert next_block_cut(audio) is None  # lazy: waits for block_max


def test_cut_without_pause_waits_until_hard_max(monkeypatch) -> None:
    from app.main import next_block_cut

    speech = tone(26.0)
    assert next_block_cut(speech) is None
    cut = next_block_cut(tone(28.5))
    assert cut is not None and 12 * BYTES_PER_SECOND <= cut <= 28 * BYTES_PER_SECOND
    assert next_block_cut(speech, final=True) is not None  # Stop: never a tail over block_max


def test_plan_blocks_cuts_long_audio_in_pauses() -> None:
    from app.main import plan_blocks

    audio = b"".join(tone(9.0) + silence(0.6) for _ in range(8))  # 76.8 s
    ends = plan_blocks(audio)
    assert ends and len(audio) - ends[-1] < settings.block_max_seconds * BYTES_PER_SECOND
    for end in ends:
        offset = (end / BYTES_PER_SECOND) % 9.6
        assert 9.0 <= offset <= 9.6  # every cut inside a pause


def test_find_cut_with_short_audio_takes_everything() -> None:
    audio = tone(5.0)
    assert find_cut(audio, 12 * BYTES_PER_SECOND, 24 * BYTES_PER_SECOND) == len(audio)


def test_long_recitation_commits_blocks_and_final_transcribes_only_the_tail(fake_env, monkeypatch) -> None:
    fake, calls = fake_env
    monkeypatch.setattr(settings, "block_min_seconds", 6.0)
    monkeypatch.setattr(settings, "block_max_seconds", 10.0)
    # 4 "ayahs" of 7 s with 1 s pauses, sent in 0.5 s chunks.
    pcm = b"".join(tone(7.0) + silence(1.0) for _ in range(4))
    step = BYTES_PER_SECOND // 2
    chunks = [pcm[i:i + step] for i in range(0, len(pcm), step)]

    async def scenario():
        for index, chunk in enumerate(chunks):
            await feed([audio_message(index, chunk)])
        committed = sessions["s"].committed_bytes
        calls.clear()
        await feed([stop_message(len(chunks))])
        return committed

    committed = asyncio.run(scenario())
    # Blocks were committed while "reciting", each cut in a pause.
    assert committed >= 2 * 8 * BYTES_PER_SECOND
    # After Stop only the tail (less than one block) was transcribed.
    assert [fast for _, fast in calls] == [False]
    assert calls[0][0] < 10 * BYTES_PER_SECOND
    final = fake.published[-1]
    assert final["final"] is True and final["status"] == "completed"
    words = final["transcript"].split()
    assert all(word.startswith("f") for word in words)
    assert sum(float(word[1:]) for word in words) == pytest.approx(len(pcm) / BYTES_PER_SECOND, abs=0.3)
    assert final["audio_seconds"] == pytest.approx(len(pcm) / BYTES_PER_SECOND, abs=0.01)
    assert len(fake.acked) == len(chunks) + 1
    assert "s" not in sessions
    # Live partials were published along the way, beginning with committed text.
    partials = [result for result in fake.published if not result["final"]]
    assert partials and partials[-1]["transcript"].split()[-1].startswith("p")


def test_queued_stop_skips_partials(fake_env) -> None:
    fake, calls = fake_env
    messages = [audio_message(i, tone(0.5)) for i in range(10)] + [stop_message(10)]
    asyncio.run(feed(messages))
    assert [fast for _, fast in calls] == [False]
    assert len(fake.published) == 1 and fake.published[0]["final"] is True


def test_partials_are_coalesced(fake_env) -> None:
    fake, calls = fake_env
    # Ten chunks arrive before the worker runs: one partial over all of them.
    asyncio.run(feed([audio_message(i, tone(0.5)) for i in range(10)]))
    assert calls == [(10 * BYTES_PER_SECOND // 2, True)]
    assert fake.published[0]["final"] is False
    assert fake.published[0]["audio_seconds"] == 5.0


def test_stop_without_audio_returns_empty_final(fake_env) -> None:
    fake, calls = fake_env
    asyncio.run(feed([stop_message(1)]))
    assert fake.published[0]["final"] is True
    assert fake.published[0]["transcript"] == ""
    assert calls == []


def test_invalid_audio_is_dead_lettered(fake_env) -> None:
    fake, _ = fake_env
    bad = ("1-0", {"session_id": "s", "audio_b64": "###"})
    asyncio.run(feed([bad]))
    assert fake.dead and fake.dead[0]["original_id"] == "1-0"
    assert "1-0" in fake.acked


def test_sessions_are_transcribed_in_parallel(fake_env, monkeypatch) -> None:
    fake, _ = fake_env
    monkeypatch.setattr(settings, "transcribe_concurrency", 2)
    running = []
    peak = []
    lock = threading.Lock()
    release = threading.Event()

    def slow_transcribe(audio: bytes, fast: bool = False) -> Transcription:
        with lock:
            running.append(1)
            peak.append(len(running))
        release.wait(0.2)
        with lock:
            running.pop()
        return Transcription("x")

    monkeypatch.setattr(main, "transcribe", slow_transcribe)
    messages = []
    for session in ("a", "b"):
        messages.append(audio_message(1, tone(2.0), session))
        messages.append(stop_message(2, session))
    asyncio.run(feed(messages))
    assert max(peak) == 2
    assert sorted(result["session_id"] for result in fake.published) == ["a", "b"]


def test_priority_gate_serves_most_urgent_first() -> None:
    order: list[str] = []

    async def scenario():
        gate = PriorityGate(1)
        await gate.acquire(main.PRIORITY_PARTIAL)

        async def waiter(name: str, priority: int):
            async with gate.slot(priority):
                order.append(name)

        tasks = [
            asyncio.create_task(waiter("partial", main.PRIORITY_PARTIAL)),
            asyncio.create_task(waiter("block", main.PRIORITY_BLOCK)),
            asyncio.create_task(waiter("final", main.PRIORITY_FINAL)),
        ]
        await asyncio.sleep(0)
        gate.release()
        await asyncio.gather(*tasks)
        assert gate.busy == 0

    asyncio.run(scenario())
    assert order == ["final", "block", "partial"]


def test_priority_gate_cancelled_waiter_does_not_leak_a_slot() -> None:
    async def scenario():
        gate = PriorityGate(1)
        await gate.acquire(0)
        task = asyncio.create_task(gate.acquire(1))
        await asyncio.sleep(0)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        gate.release()
        assert gate.busy == 0
        await asyncio.wait_for(gate.acquire(2), 1)

    asyncio.run(scenario())


def test_merge_transcriptions_pools_confidence() -> None:
    merged = merge_transcriptions([
        Transcription("a b", -0.2, None, 3),
        Transcription("", None, None, 0),
        Transcription("c", -1.0, 0.1, 1),
    ])
    assert merged.text == "a b c"
    assert merged.average_log_probability == pytest.approx((-0.2 * 3 - 1.0) / 4)
    assert merged.no_speech_probability == 0.1
    assert merged.segment_count == 4


def test_encoder_input_drops_the_zero_padding() -> None:
    features = np.zeros((80, 3000), dtype=np.float32)
    features[:, :553] = -0.5  # 5.53 s of audio, then faster-whisper's zero padding
    assert trim_encoder_input(features, 100).shape == (80, 654)
    assert trim_encoder_input(features, 0).shape == (80, 554)  # even frame count
    assert trim_encoder_input(features, -1).shape == (80, 3000)
    batched = features[None]
    assert trim_encoder_input(batched, 100).shape == (1, 80, 654)
    full = np.full((80, 3000), -0.5, dtype=np.float32)
    assert trim_encoder_input(full, 100).shape == (80, 3000)
    assert trim_encoder_input(np.zeros((80, 3000), dtype=np.float32), 100).shape == (80, 3000)


def test_idle_sessions_are_evicted() -> None:
    touch_session("old", now=0.0)
    touch_session("fresh", now=5.0)
    evicted = evict_idle_sessions(now=settings.session_idle_seconds + 3)
    assert evicted == 1
    assert "old" not in sessions
    assert "fresh" in sessions


def test_session_cap_evicts_least_recent(monkeypatch) -> None:
    monkeypatch.setattr(settings, "max_sessions", 2)
    touch_session("a", now=100.0)
    touch_session("b", now=101.0)
    touch_session("c", now=102.0)
    assert set(sessions) == {"b", "c"}


def test_dead_letter_drops_audio_payload() -> None:
    fields = {"session_id": "s", "audio_b64": "A" * 1000, "event": "audio"}
    kept = dead_letter_fields("1-0", fields, ValueError("boom"))
    assert "audio_b64" not in kept
    assert kept["audio_b64_length"] == "1000"
    assert kept["error"] == "boom"


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


def test_analyze_splits_long_audio_into_blocks(fake_env, monkeypatch) -> None:
    _, calls = fake_env
    monkeypatch.setattr(settings, "block_min_seconds", 6.0)
    monkeypatch.setattr(settings, "block_max_seconds", 10.0)
    pcm = b"".join(tone(7.0) + silence(0.5) for _ in range(3))
    response = asyncio.run(analyze(AnalyzeRequest(audio_b64=base64.b64encode(pcm).decode())))
    assert all(length <= 10 * BYTES_PER_SECOND for length, _ in calls)
    assert len(calls) >= 3
    assert len(response.transcript.split()) == len(calls)


def test_silence_is_not_transcribed() -> None:
    from app.main import is_silence

    quiet = struct.pack("<" + "h" * 1600, *([20, -20] * 800))
    speech = struct.pack("<" + "h" * 1600, *([2000, -2000] * 800))
    assert is_silence(quiet)
    assert not is_silence(speech)
    assert is_silence(b"")


def test_missing_model_directory_falls_back(monkeypatch) -> None:
    monkeypatch.setattr(main.settings, "whisper_model", "/nonexistent/model")
    monkeypatch.setattr(main.settings, "whisper_fallback_model", "small")
    assert main.resolve_model_name() == "small"


def test_missing_partial_model_falls_back_to_main_model(monkeypatch) -> None:
    monkeypatch.setattr(main.settings, "partial_model", "/nonexistent/tiny")
    assert main.load_partial_model() is None
    assert main.loaded_partial_model_name is None
    monkeypatch.setattr(main.settings, "partial_model", "")
    assert main.load_partial_model() is None


def test_token_budget_bounds_decoding() -> None:
    from app.main import token_budget

    assert token_budget(int(1.5 * BYTES_PER_SECOND)) == 34
    assert token_budget(10 * BYTES_PER_SECOND) == 136
    assert token_budget(600 * BYTES_PER_SECOND) == 376  # per 30 s window, prompt still fits
