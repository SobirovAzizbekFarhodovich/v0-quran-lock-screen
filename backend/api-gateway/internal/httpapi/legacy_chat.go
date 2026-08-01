package httpapi

import (
	"encoding/json"
	"net/http"
	"regexp"
	"strings"

	"github.com/gin-gonic/gin"

	"islamic-ai/backend/api-gateway/internal/rag"
)

// LegacyChatHandler is a deliberately small compatibility adapter for the
// already-published Kotlin client.  It is not a public OpenAI-compatible API:
// it accepts only the application's two Tadabbur prompt shapes and delegates
// the advice response to the source-grounded RAG service.
type LegacyChatHandler struct {
	rag *rag.Client
}

func NewLegacyChatHandler(client *rag.Client) *LegacyChatHandler {
	return &LegacyChatHandler{rag: client}
}

type legacyChatRequest struct {
	Messages []struct {
		Role    string `json:"role"`
		Content string `json:"content"`
	} `json:"messages" binding:"required,min=1"`
}

func (h *LegacyChatHandler) Complete(c *gin.Context) {
	var req legacyChatRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": gin.H{"message": "messages are required"}})
		return
	}

	prompt := req.Messages[len(req.Messages)-1].Content
	if strings.Contains(prompt, `"moods"`) && strings.Contains(prompt, "Foydalanuvchi yozdi:") {
		// Topic selection remains local in the Kotlin app.  Returning a valid
		// empty response preserves that fallback while allowing the next call
		// to obtain an evidence-based answer from RAG.
		h.respond(c, `{"topics":[],"moods":[],"keywords":[],"userState":""}`)
		return
	}

	question := legacyQuestion(prompt)
	result, err := h.rag.Query(c.Request.Context(), rag.QueryRequest{
		Message:  question,
		Language: "uz_latin",
	})
	if err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": gin.H{"message": "tadabbur service is unavailable"}})
		return
	}

	content := map[string]interface{}{
		"intro":          "Quyidagi tavsiya ishonchli manbalardan topilgan dalillarga asoslanadi.",
		"aiInsight":      result.Answer,
		"evidence_notes": map[string]string{},
	}
	h.respond(c, content)
}

func (h *LegacyChatHandler) respond(c *gin.Context, content interface{}) {
	encoded, err := json.Marshal(content)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": gin.H{"message": "response encoding failed"}})
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"choices": []gin.H{{"message": gin.H{"content": string(encoded)}}},
	})
}

var tadabburQuestionPattern = regexp.MustCompile(`Foydalanuvchining holati:\s*"([^"]+)"`)

func legacyQuestion(prompt string) string {
	if match := tadabburQuestionPattern.FindStringSubmatch(prompt); len(match) == 2 {
		return match[1]
	}
	return prompt
}
