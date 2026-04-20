package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/chan808/agolive-realtime/config"
	"github.com/chan808/agolive-realtime/handler"
	"github.com/chan808/agolive-realtime/hub"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/redis/go-redis/v9"
)

func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, nil)))

	cfg := config.Load()

	rdb := redis.NewClient(&redis.Options{
		Addr:     cfg.RedisAddr,
		Password: cfg.RedisPassword,
	})

	if err := rdb.Ping(context.Background()).Err(); err != nil {
		slog.Error("Redis 연결 실패", "err", err)
		os.Exit(1)
	}
	slog.Info("Redis 연결 완료", "addr", cfg.RedisAddr)

	h := hub.New(rdb)
	wsHandler := handler.New(h, rdb, cfg)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /ws/rooms/{roomId}", wsHandler.ServeWS)
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
	mux.Handle("GET /metrics", promhttp.Handler())

	srv := &http.Server{
		Addr:    ":" + cfg.Port,
		Handler: mux,
	}

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		slog.Info("서버 시작", "port", cfg.Port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("서버 오류", "err", err)
			os.Exit(1)
		}
	}()

	<-quit
	slog.Info("서버 종료 중...")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		slog.Error("Graceful shutdown 실패", "err", err)
	}
}
