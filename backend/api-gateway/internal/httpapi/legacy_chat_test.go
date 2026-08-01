package httpapi

import "testing"

func TestLegacyQuestionExtractsUserText(t *testing.T) {
	prompt := "Foydalanuvchining holati: \"Ko'nglim siqilyapti\"\nTahlil: x"
	if got := legacyQuestion(prompt); got != "Ko'nglim siqilyapti" {
		t.Fatalf("legacyQuestion() = %q", got)
	}
}

func TestLegacyQuestionKeepsOrdinaryPrompt(t *testing.T) {
	prompt := "oddiy so'rov"
	if got := legacyQuestion(prompt); got != prompt {
		t.Fatalf("legacyQuestion() = %q", got)
	}
}
