package scanner

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"nasee-server/internal/config"
	"nasee-server/internal/models"
	"nasee-server/internal/storage"
)

// videoExtensions defines the set of video file extensions to scan.
var videoExtensions = map[string]bool{
	".mp4":  true,
	".mkv":  true,
	".avi":  true,
	".mov":  true,
	".ts":   true,
	".flv":  true,
	".webm": true,
	".m4v":  true,
	".wmv":  true,
}

// Scanner walks the media directory, probes new/changed files via ffprobe,
// upserts them into the database, and removes stale entries.
type Scanner struct {
	repo     *storage.VideoRepo
	worker   *Worker
	cfg      *config.Config
	logger   *slog.Logger
	mu       sync.Mutex
	scanning bool
}

// NewScanner creates a new Scanner instance.
func NewScanner(repo *storage.VideoRepo, worker *Worker, cfg *config.Config, logger *slog.Logger) *Scanner {
	return &Scanner{
		repo:   repo,
		worker: worker,
		cfg:    cfg,
		logger: logger,
	}
}

// Start runs an initial scan and then periodically rescans at the configured interval.
// It blocks until the context is cancelled.
func (s *Scanner) Start(ctx context.Context) {
	// Initial scan on startup.
	if err := s.ScanOnce(ctx); err != nil {
		s.logger.Warn("initial scan failed", "error", err)
	}

	interval := time.Duration(s.cfg.ScanInterval) * time.Second
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			if err := s.ScanOnce(ctx); err != nil {
				s.logger.Warn("periodic scan failed", "error", err)
			}
		case <-ctx.Done():
			s.logger.Info("scanner stopped")
			return
		}
	}
}

// IsScanning returns true if a scan is currently in progress.
func (s *Scanner) IsScanning() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.scanning
}

// ScanOnce performs a single full scan of the media directory.
// Returns an error if a scan is already in progress.
func (s *Scanner) ScanOnce(ctx context.Context) error {
	s.mu.Lock()
	if s.scanning {
		s.mu.Unlock()
		return fmt.Errorf("scan already in progress")
	}
	s.scanning = true
	s.mu.Unlock()

	defer func() {
		s.mu.Lock()
		s.scanning = false
		s.mu.Unlock()
	}()

	s.logger.Info("scan started", "media_dir", s.cfg.MediaDir)
	startTime := time.Now()
	scannedAt := time.Now().Unix()
	foundCount := 0

	err := filepath.Walk(s.cfg.MediaDir, func(fullPath string, info os.FileInfo, err error) error {
		if err != nil {
			s.logger.Warn("walk error, skipping", "path", fullPath, "error", err)
			return nil
		}
		if info.IsDir() {
			return nil
		}

		// Filter by video extension whitelist.
		ext := strings.ToLower(filepath.Ext(fullPath))
		if !videoExtensions[ext] {
			return nil
		}

		// Compute the relative path (stored with leading "/" and forward slashes).
		relPath, err := filepath.Rel(s.cfg.MediaDir, fullPath)
		if err != nil {
			s.logger.Warn("failed to get relative path, skipping", "path", fullPath, "error", err)
			return nil
		}
		relPath = "/" + filepath.ToSlash(relPath)

		foundCount++
		modTime := info.ModTime().Unix()

		// Check if the file already exists in the DB and is unchanged.
		existing, err := s.repo.GetByPath(relPath)
		if err != nil {
			s.logger.Warn("failed to query existing video", "path", relPath, "error", err)
		} else if existing != nil && existing.ModTime == modTime {
			// File unchanged — just update scanned_at.
			existing.ScannedAt = scannedAt
			if err := s.repo.Upsert(existing); err != nil {
				s.logger.Error("failed to update scanned_at", "path", relPath, "error", err)
			}
			return nil
		}

		// File is new or modified — probe with ffprobe.
		meta, probeErr := s.worker.Probe(ctx, fullPath)
		if probeErr != nil {
			s.logger.Warn("ffprobe failed, storing basic info", "path", fullPath, "error", probeErr)
			meta = &models.VideoMeta{}
		}

		folderPath := filepath.ToSlash(filepath.Dir(relPath))

		video := &models.Video{
			FilePath:   relPath,
			Title:      info.Name(),
			Duration:   meta.Duration,
			Width:      meta.Width,
			Height:     meta.Height,
			FileSize:   info.Size(),
			ModTime:    modTime,
			FolderPath: folderPath,
			ScannedAt:  scannedAt,
		}

		if err := s.repo.Upsert(video); err != nil {
			s.logger.Error("failed to upsert video", "path", relPath, "error", err)
		}

		return nil
	})

	if err != nil {
		return fmt.Errorf("walk media directory: %w", err)
	}

	// Remove entries for files that were not seen during this scan.
	if err := s.repo.MarkStale(scannedAt); err != nil {
		s.logger.Error("failed to mark stale videos", "error", err)
	}

	s.logger.Info("scan completed", "duration", time.Since(startTime), "files_found", foundCount)
	return nil
}
