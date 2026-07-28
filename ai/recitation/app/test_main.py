from app.main import Transcription, check_tajweed


def test_empty_transcription_has_no_score() -> None:
    result = check_tajweed("")
    assert result["score"] == 0


def test_text_only_score_is_not_a_perfect_tajweed_grade() -> None:
    result = check_tajweed(
        "مِنْ رَبِّهِمْ",
        Transcription("مِنْ رَبِّهِمْ", average_log_probability=-0.2, no_speech_probability=0.0),
    )
    assert result["score"] == 85.0
    assert result["analysis_status"] == "text_only_not_tajweed_verified"
    assert any(item["rule"] == "idgham" for item in result["opportunities"])


def test_poor_recognition_receives_a_lower_score() -> None:
    strong = check_tajweed("قُلْ هُوَ اللَّهُ", Transcription("x", -0.2, 0.0))
    weak = check_tajweed("قُلْ هُوَ اللَّهُ", Transcription("x", -1.4, 0.8))
    assert weak["score"] < strong["score"]
