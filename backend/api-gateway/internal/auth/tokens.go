package auth

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

type Claims struct {
	UserID string `json:"uid"`
	Email  string `json:"email"`
	Type   string `json:"typ"`
	jwt.RegisteredClaims
}

type TokenService struct {
	secret     []byte
	accessTTL  time.Duration
	refreshTTL time.Duration
}

type TokenPair struct {
	AccessToken  string
	RefreshToken string
	RefreshHash  string
	RefreshExp   time.Time
}

func NewTokenService(secret string, accessTTL, refreshTTL time.Duration) *TokenService {
	return &TokenService{secret: []byte(secret), accessTTL: accessTTL, refreshTTL: refreshTTL}
}

func (s *TokenService) Issue(userID, email string) (TokenPair, error) {
	accessExp := time.Now().Add(s.accessTTL)
	refreshExp := time.Now().Add(s.refreshTTL)
	access, err := s.sign(userID, email, "access", accessExp, "")
	if err != nil {
		return TokenPair{}, err
	}
	refreshID := uuid.NewString()
	refresh, err := s.sign(userID, email, "refresh", refreshExp, refreshID)
	if err != nil {
		return TokenPair{}, err
	}
	return TokenPair{AccessToken: access, RefreshToken: refresh, RefreshHash: HashToken(refresh), RefreshExp: refreshExp}, nil
}

func (s *TokenService) Parse(tokenValue string) (*Claims, error) {
	token, err := jwt.ParseWithClaims(tokenValue, &Claims{}, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, errors.New("unexpected signing method")
		}
		return s.secret, nil
	})
	if err != nil {
		return nil, err
	}
	claims, ok := token.Claims.(*Claims)
	if !ok || !token.Valid {
		return nil, errors.New("invalid token")
	}
	return claims, nil
}

func (s *TokenService) sign(userID, email, typ string, exp time.Time, id string) (string, error) {
	if id == "" {
		id = uuid.NewString()
	}
	return jwt.NewWithClaims(jwt.SigningMethodHS256, Claims{
		UserID: userID,
		Email:  email,
		Type:   typ,
		RegisteredClaims: jwt.RegisteredClaims{
			ID:        id,
			Subject:   userID,
			ExpiresAt: jwt.NewNumericDate(exp),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
	}).SignedString(s.secret)
}

func HashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}
