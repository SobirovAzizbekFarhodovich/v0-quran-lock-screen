package httpapi

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"math/big"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"
)

type googleTokenInfo struct {
	Audience      string `json:"aud"`
	Subject       string `json:"sub"`
	Email         string `json:"email"`
	EmailVerified string `json:"email_verified"`
	Name          string `json:"name"`
}

type appleJWKSet struct {
	Keys []appleJWK `json:"keys"`
}
type appleJWK struct {
	KeyID    string `json:"kid"`
	KeyType  string `json:"kty"`
	Modulus  string `json:"n"`
	Exponent string `json:"e"`
}

// AppleOAuth validates the identity token against Apple's published JWKS. The
// iOS client obtains that token through Sign in with Apple; no Apple private key
// is ever sent to, or stored in, the mobile app.
func (h *AuthHandler) AppleOAuth(c *gin.Context) {
	if h.appleClientID == "" {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "Apple sign-in is not configured"})
		return
	}
	var req struct {
		IdentityToken string `json:"identity_token" binding:"required"`
		Name          string `json:"name"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "identity_token is required"})
		return
	}
	subject, email, err := verifyAppleIDToken(c.Request.Context(), req.IdentityToken, h.appleClientID)
	if err != nil || subject == "" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid Apple identity"})
		return
	}
	user, err := h.findOrCreateOAuthUser(c, "apple", subject, email, req.Name)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Apple sign-in failed"})
		return
	}
	h.respondWithTokens(c, user)
}

// GoogleOAuth accepts an ID token obtained by the platform Google Sign-In SDK.
// The token is validated by Google's tokeninfo endpoint and its audience must
// match one of the server-side configured client IDs.
func (h *AuthHandler) GoogleOAuth(c *gin.Context) {
	if len(h.googleClientIDs) == 0 {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "Google sign-in is not configured"})
		return
	}
	var req struct {
		IDToken string `json:"id_token" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "id_token is required"})
		return
	}
	info, err := verifyGoogleIDToken(c.Request.Context(), req.IDToken)
	if err != nil || info.Subject == "" || info.Email == "" || !allowedAudience(info.Audience, h.googleClientIDs) || info.EmailVerified != "true" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid Google identity"})
		return
	}
	user, err := h.findOrCreateOAuthUser(c, "google", info.Subject, info.Email, info.Name)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Google sign-in failed"})
		return
	}
	h.respondWithTokens(c, user)
}

func verifyGoogleIDToken(ctx context.Context, token string) (googleTokenInfo, error) {
	client := &http.Client{Timeout: 10 * time.Second}
	endpoint := "https://oauth2.googleapis.com/tokeninfo?id_token=" + url.QueryEscape(token)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return googleTokenInfo{}, err
	}
	resp, err := client.Do(req)
	if err != nil {
		return googleTokenInfo{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return googleTokenInfo{}, fmt.Errorf("google tokeninfo status %d", resp.StatusCode)
	}
	var info googleTokenInfo
	if err := json.NewDecoder(resp.Body).Decode(&info); err != nil {
		return googleTokenInfo{}, err
	}
	return info, nil
}

func allowedAudience(audience string, clientIDs []string) bool {
	for _, clientID := range clientIDs {
		if strings.TrimSpace(clientID) == audience {
			return true
		}
	}
	return false
}

func verifyAppleIDToken(ctx context.Context, rawToken, audience string) (string, string, error) {
	claims := jwt.MapClaims{}
	parser := jwt.NewParser()
	unsigned, _, err := parser.ParseUnverified(rawToken, claims)
	if err != nil {
		return "", "", err
	}
	kid, _ := unsigned.Header["kid"].(string)
	key, err := applePublicKey(ctx, kid)
	if err != nil {
		return "", "", err
	}
	verified := jwt.MapClaims{}
	token, err := jwt.ParseWithClaims(rawToken, verified, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, fmt.Errorf("unexpected Apple signing method")
		}
		return key, nil
	})
	if err != nil || !token.Valid {
		return "", "", fmt.Errorf("invalid Apple token")
	}
	issuer, _ := verified["iss"].(string)
	clientID, _ := verified["aud"].(string)
	subject, _ := verified["sub"].(string)
	email, _ := verified["email"].(string)
	if issuer != "https://appleid.apple.com" || clientID != audience {
		return "", "", fmt.Errorf("invalid Apple issuer or audience")
	}
	return subject, email, nil
}

func applePublicKey(ctx context.Context, keyID string) (*rsa.PublicKey, error) {
	client := &http.Client{Timeout: 10 * time.Second}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://appleid.apple.com/auth/keys", nil)
	if err != nil {
		return nil, err
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("Apple key endpoint status %d", resp.StatusCode)
	}
	var set appleJWKSet
	if err := json.NewDecoder(resp.Body).Decode(&set); err != nil {
		return nil, err
	}
	for _, jwk := range set.Keys {
		if jwk.KeyID != keyID || jwk.KeyType != "RSA" {
			continue
		}
		n, err := base64.RawURLEncoding.DecodeString(jwk.Modulus)
		if err != nil {
			return nil, err
		}
		e, err := base64.RawURLEncoding.DecodeString(jwk.Exponent)
		if err != nil {
			return nil, err
		}
		exponent := new(big.Int).SetBytes(e).Int64()
		if exponent < 3 || exponent > int64(^uint(0)>>1) {
			return nil, fmt.Errorf("invalid Apple public exponent")
		}
		return &rsa.PublicKey{N: new(big.Int).SetBytes(n), E: int(exponent)}, nil
	}
	return nil, fmt.Errorf("Apple key %q not found", keyID)
}

func (h *AuthHandler) findOrCreateOAuthUser(c *gin.Context, provider, subject, email, name string) (userDTO, error) {
	var user userDTO
	err := h.db.QueryRow(c.Request.Context(), `SELECT u.id::text, u.name, u.email, u.language FROM oauth_identities oi JOIN users u ON u.id = oi.user_id WHERE oi.provider = $1 AND oi.provider_subject = $2`, provider, subject).Scan(&user.ID, &user.Name, &user.Email, &user.Language)
	if err == nil {
		return user, nil
	}
	if email == "" {
		return user, fmt.Errorf("provider did not return an email for a new account")
	}
	tx, err := h.db.Begin(c.Request.Context())
	if err != nil {
		return user, err
	}
	defer tx.Rollback(c.Request.Context())
	if name == "" {
		name = strings.Split(email, "@")[0]
	}
	err = tx.QueryRow(c.Request.Context(), `SELECT id::text, name, email, language FROM users WHERE email = lower($1) FOR UPDATE`, email).Scan(&user.ID, &user.Name, &user.Email, &user.Language)
	if err != nil {
		raw := make([]byte, 32)
		if _, err = rand.Read(raw); err != nil {
			return user, err
		}
		hash, hashErr := bcrypt.GenerateFromPassword([]byte(hex.EncodeToString(raw)), 12)
		if hashErr != nil {
			return user, hashErr
		}
		err = tx.QueryRow(c.Request.Context(), `INSERT INTO users (name, email, password_hash, language) VALUES ($1, lower($2), $3, '') RETURNING id::text, name, email, language`, name, email, string(hash)).Scan(&user.ID, &user.Name, &user.Email, &user.Language)
		if err != nil {
			return user, err
		}
	}
	if _, err = tx.Exec(c.Request.Context(), `INSERT INTO oauth_identities (user_id, provider, provider_subject) VALUES ($1, $2, $3) ON CONFLICT (provider, provider_subject) DO NOTHING`, user.ID, provider, subject); err != nil {
		return user, err
	}
	if err = tx.Commit(c.Request.Context()); err != nil {
		return user, err
	}
	return user, nil
}
