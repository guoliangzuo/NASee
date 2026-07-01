package main

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"nasee-server/internal/api"
	"nasee-server/internal/config"
	"nasee-server/internal/media"
	"nasee-server/internal/scanner"
	"nasee-server/internal/storage"
)

func main() {
	// Initialize structured logger.
	logger := slog.New(slog.NewTextHandler(os.Stdout, nil))
	slog.SetDefault(logger)

	// Load configuration from environment variables.
	cfg, err := config.Load()
	if err != nil {
		logger.Error("failed to load configuration", "error", err)
		os.Exit(1)
	}
	logger.Info("configuration loaded",
		"port", cfg.Port,
		"media_dir", cfg.MediaDir,
		"data_dir", cfg.DataDir,
		"scan_interval", cfg.ScanInterval,
		"ffprobe_concurrency", cfg.FFProbeConcurrency,
	)

	// Initialize SQLite database with WAL mode.
	db, err := storage.New(cfg.DataDir)
	if err != nil {
		logger.Error("failed to initialize database", "error", err)
		os.Exit(1)
	}
	defer db.Close()
	logger.Info("database initialized", "path", cfg.DataDir)

	// Run database migrations.
	if err := storage.Migrate(db); err != nil {
		logger.Error("failed to migrate database", "error", err)
		os.Exit(1)
	}
	logger.Info("database migrations completed")

	// Initialize repositories.
	videoRepo := storage.NewVideoRepo(db)
	likeRepo := storage.NewLikeRepo(db)

	// Initialize scanner with ffprobe worker.
	ffprobe := scanner.NewFFProbe(cfg.FFProbePath)
	worker := scanner.NewWorker(ffprobe, cfg.FFProbeConcurrency, cfg.FFProbeTimeout)
	scn := scanner.NewScanner(videoRepo, worker, cfg, logger)

	// Initialize media stream service.
	streamSvc := media.NewStreamService(cfg.MediaDir)

	// Create a cancellable context for background services.
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Start the scanner in a background goroutine.
	go scn.Start(ctx)

	// Build the HTTP router with all dependencies injected.
	router := api.NewRouter(cfg, videoRepo, likeRepo, scn, streamSvc, logger)

	// Configure the HTTP server.
	srv := &http.Server{
		Addr:         fmt.Sprintf(":%d", cfg.Port),
		Handler:      router.Handler(),
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 0, // No write timeout to support long video streams.
		IdleTimeout:  120 * time.Second,
	}

	// Start the HTTP server in a goroutine.
	go func() {
		logger.Info("NASee server starting", "addr", srv.Addr)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Error("server error", "error", err)
			os.Exit(1)
		}
	}()

	// Wait for interrupt or termination signal for graceful shutdown.
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	sig := <-sigChan
	logger.Info("received shutdown signal, shutting down...", "signal", sig.String())

	// Cancel background scanner.
	cancel()

	// Gracefully shutdown the HTTP server with a 10-second deadline.
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer shutdownCancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("server forced to shutdown", "error", err)
	}

	logger.Info("NASee server stopped")
}
