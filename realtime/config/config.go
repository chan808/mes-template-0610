package config

import (
	"log"
	"os"
)

type Config struct {
	Port           string
	JWTSecret      string
	RedisAddr      string
	RedisPassword  string
	InternalAPIURL string
	InternalSecret string
}

func Load() *Config {
	return &Config{
		Port:           getEnv("PORT", "8081"),
		JWTSecret:      mustEnv("JWT_SECRET"),
		RedisAddr:      getEnv("REDIS_ADDR", "localhost:6379"),
		RedisPassword:  getEnv("REDIS_PASSWORD", ""),
		InternalAPIURL: getEnv("INTERNAL_API_URL", "http://api:8080"),
		InternalSecret: mustEnv("INTERNAL_SECRET"),
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
		log.Fatalf("required env var %s is not set", key)
	}
	return v
}
