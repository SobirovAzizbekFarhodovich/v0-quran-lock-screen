package httpapi

import (
	"bytes"
	"errors"
	"io"
	"log"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"

	"islamic-ai/backend/api-gateway/internal/auth"
	"islamic-ai/backend/api-gateway/internal/metrics"
	"islamic-ai/backend/api-gateway/internal/rag"
)

type RAGHandler struct {
	client *rag.Client
}

func NewRAGHandler(client *rag.Client) *RAGHandler {
	return &RAGHandler{client: client}
}

func (h *RAGHandler) Query(c *gin.Context) {
	start := time.Now()
	var req rag.QueryRequest
	raw, _ := io.ReadAll(io.LimitReader(c.Request.Body, 1<<20))
	c.Request.Body = io.NopCloser(bytes.NewReader(raw))
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("DEBUG spiritual/query bad request: err=%v body=%s", err, string(raw))
		metrics.RAGQueryDuration.WithLabelValues("bad_request").Observe(time.Since(start).Seconds())
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	req.UserID = c.GetString(auth.UserIDKey)
	result, err := h.client.Query(c.Request.Context(), req)
	if err != nil {
		if errors.Is(err, rag.ErrCircuitOpen) {
			metrics.RAGQueryDuration.WithLabelValues("circuit_open").Observe(time.Since(start).Seconds())
			c.JSON(http.StatusServiceUnavailable, gin.H{"error": "rag service temporarily unavailable"})
			return
		}
		var statusErr *rag.StatusError
		if errors.As(err, &statusErr) && statusErr.IsClientError() {
			metrics.RAGQueryDuration.WithLabelValues("bad_request").Observe(time.Since(start).Seconds())
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid query"})
			return
		}
		metrics.RAGQueryDuration.WithLabelValues("error").Observe(time.Since(start).Seconds())
		c.JSON(http.StatusBadGateway, gin.H{"error": "rag service unavailable"})
		return
	}
	metrics.RAGQueryDuration.WithLabelValues("ok").Observe(time.Since(start).Seconds())
	c.JSON(http.StatusOK, result)
}
