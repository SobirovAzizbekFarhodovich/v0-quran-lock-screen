package ws

import "testing"

func TestNormalizeResultPayload(t *testing.T) {
	payload := `{"session_id":"abc","transcript":"الحمد لله","tajweed":{"score":87.5}}`

	result, score, transcript, err := normalizeResultPayload("fallback", payload)
	if err != nil {
		t.Fatalf("normalizeResultPayload returned error: %v", err)
	}

	if result["type"] != recitationResultReady {
		t.Fatalf("type = %v, want %q", result["type"], recitationResultReady)
	}
	if result["status"] != "completed" {
		t.Fatalf("status = %v, want completed", result["status"])
	}
	if result["session_id"] != "abc" {
		t.Fatalf("session_id = %v, want abc", result["session_id"])
	}
	if transcript != "الحمد لله" {
		t.Fatalf("transcript = %q", transcript)
	}
	if score != 87.5 {
		t.Fatalf("score = %v, want 87.5", score)
	}
}

func TestNormalizeResultPayloadFallbackSessionAndScore(t *testing.T) {
	payload := `{"transcription":"test","score":91}`

	result, score, transcript, err := normalizeResultPayload("fallback", payload)
	if err != nil {
		t.Fatalf("normalizeResultPayload returned error: %v", err)
	}

	if result["session_id"] != "fallback" {
		t.Fatalf("session_id = %v, want fallback", result["session_id"])
	}
	if transcript != "test" {
		t.Fatalf("transcript = %q", transcript)
	}
	if score != 91 {
		t.Fatalf("score = %v, want 91", score)
	}
}
