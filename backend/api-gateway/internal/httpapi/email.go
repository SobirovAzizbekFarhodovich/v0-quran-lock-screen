package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"
)

type EmailSender interface {
	Send(ctx context.Context, to, subject, html string) error
}

type ResendEmailSender struct {
	apiKey string
	from   string
	client *http.Client
}

func NewResendEmailSender(apiKey, from string) EmailSender {
	return &ResendEmailSender{apiKey: apiKey, from: from, client: &http.Client{Timeout: 10 * time.Second}}
}

func (s *ResendEmailSender) Send(ctx context.Context, to, subject, html string) error {
	if s.apiKey == "" || s.from == "" {
		return fmt.Errorf("email delivery is not configured")
	}
	payload, err := json.Marshal(map[string]string{"from": s.from, "to": to, "subject": subject, "html": html})
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, "https://api.resend.com/emails", bytes.NewReader(payload))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+s.apiKey)
	req.Header.Set("Content-Type", "application/json")
	resp, err := s.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("email provider returned status %d", resp.StatusCode)
	}
	return nil
}
