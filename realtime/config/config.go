package config

import (
	"log"
	"os"

	"github.com/joho/godotenv"
)

type Config struct {
	Port           string
	JWTSecret      string
	RedisAddr      string
	RedisPassword  string
	InternalAPIURL string
	InternalSecret string
	AllowedOrigin  string
}

func Load() *Config {
	// 로컬: .env 자동 로드. Docker/CI: 파일 없으면 조용히 무시, 기존 env 덮어쓰지 않음
	_ = godotenv.Load()

	return &Config{
		Port:           getEnv("PORT", "8081"),
		JWTSecret:      mustEnv("JWT_SECRET"),
		RedisAddr:      getEnv("REDIS_ADDR", "localhost:6379"),
		RedisPassword:  getEnv("REDIS_PASSWORD", ""),
		InternalAPIURL: getEnv("INTERNAL_API_URL", "http://localhost:8080"),
		InternalSecret: mustEnv("INTERNAL_SECRET"),
		AllowedOrigin:  getEnv("CORS_ALLOWED_ORIGIN", "http://localhost:3000"),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func mustEnv(key string) string {
	v := os.Getenv(key)
	if v == "" {
		log.Fatalf("필수 환경변수 %s 가 설정되지 않았습니다", key)
	}
	return v
}
