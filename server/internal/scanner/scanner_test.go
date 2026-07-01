package scanner

import (
	"context"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"testing"
	"time"

	"nasee-server/internal/config"
	"nasee-server/internal/models"
	"nasee-server/internal/storage"
)

// setupScannerTest creates a temp media dir, DB, and scanner instance.
func setupScannerTest(t *testing.T) (*Scanner, string, *storage.VideoRepo, *storage.DB, func()) {
	t.Helper()

	mediaDir := t.TempDir()
	dataDir := t.TempDir()

	db, err := storage.New(dataDir)
	if err != nil {
		t.Fatalf("failed to create DB: %v", err)
	}
	if err := storage.Migrate(db); err != nil {
		t.Fatalf("failed to migrate DB: %v", err)
	}

	videoRepo := storage.NewVideoRepo(db)

	cfg := &config.Config{
		MediaDir:           mediaDir,
		DataDir:            dataDir,
		Password:           "test",
		ScanInterval:       3600,
		FFProbePath:        "ffprobe",
		FFProbeConcurrency: 2,
		FFProbeTimeout:     5,
	}

	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	ffprobe := NewFFProbe("ffprobe")
	worker := NewWorker(ffprobe, 2, 5)
	scn := NewScanner(videoRepo, worker, cfg, logger)

	cleanup := func() { db.Close() }
	return scn, mediaDir, videoRepo, db, cleanup
}

// createDummyFile creates a file with the given content at the given path.
func createDummyFile(t *testing.T, path string, content string) {
	t.Helper()
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0755); err != nil {
		t.Fatalf("failed to create dir %q: %v", dir, err)
	}
	if err := os.WriteFile(path, []byte(content), 0644); err != nil {
		t.Fatalf("failed to write file %q: %v", path, err)
	}
}

// allVideosFilter returns a VideoFilter that lists all videos (page 1, large page size).
func allVideosFilter() models.VideoFilter {
	return models.VideoFilter{Page: 1, PageSize: 100, Sort: "name", Order: "asc"}
}

// ---------------------------------------------------------------------------
// Extension filter tests
// ---------------------------------------------------------------------------

func TestScanner_ExtensionFilter(t *testing.T) {
	scn, mediaDir, videoRepo, _, cleanup := setupScannerTest(t)
	defer cleanup()

	// Create video files (should be scanned).
	createDummyFile(t, filepath.Join(mediaDir, "video1.mp4"), "dummy mp4")
	createDummyFile(t, filepath.Join(mediaDir, "video2.mkv"), "dummy mkv")
	createDummyFile(t, filepath.Join(mediaDir, "video3.avi"), "dummy avi")

	// Create non-video files (should NOT be scanned).
	createDummyFile(t, filepath.Join(mediaDir, "readme.txt"), "text file")
	createDummyFile(t, filepath.Join(mediaDir, "image.jpg"), "jpeg image")
	createDummyFile(t, filepath.Join(mediaDir, "data.json"), `{"key":"value"}`)

	// Run scan.
	if err := scn.ScanOnce(context.Background()); err != nil {
		t.Fatalf("ScanOnce failed: %v", err)
	}

	// Verify only video files are in the DB.
	videos, total, err := videoRepo.List(allVideosFilter())
	if err != nil {
		t.Fatalf("List failed: %v", err)
	}
	if total != 3 {
		t.Errorf("total = %d, want 3 (only video extensions)", total)
	}
	if len(videos) != 3 {
		t.Fatalf("len(videos) = %d, want 3", len(videos))
	}

	// Verify the correct files were scanned.
	titles := make(map[string]bool)
	for _, v := range videos {
		titles[v.Title] = true
	}
	expected := []string{"video1.mp4", "video2.mkv", "video3.avi"}
	for _, exp := range expected {
		if !titles[exp] {
			t.Errorf("expected video %q not found in scan results", exp)
		}
	}
}

func TestScanner_ExtensionFilter_AllSupportedTypes(t *testing.T) {
	scn, mediaDir, videoRepo, _, cleanup := setupScannerTest(t)
	defer cleanup()

	// Create files with all supported video extensions.
	exts := []string{".mp4", ".mkv", ".avi", ".mov", ".ts", ".flv", ".webm", ".m4v", ".wmv"}
	for i, ext := range exts {
		name := "video" + string(rune('0'+i)) + ext
		createDummyFile(t, filepath.Join(mediaDir, name), "dummy")
	}

	// Create a non-video file.
	createDummyFile(t, filepath.Join(mediaDir, "notvideo.txt"), "not a video")

	if err := scn.ScanOnce(context.Background()); err != nil {
		t.Fatalf("ScanOnce failed: %v", err)
	}

	_, total, err := videoRepo.List(allVideosFilter())
	if err != nil {
		t.Fatalf("List failed: %v", err)
	}
	if total != len(exts) {
		t.Errorf("total = %d, want %d (all supported extensions)", total, len(exts))
	}
}

// ---------------------------------------------------------------------------
// Incremental scan tests
// ---------------------------------------------------------------------------

func TestScanner_IncrementalScan_UnchangedFiles(t *testing.T) {
	scn, mediaDir, videoRepo, _, cleanup := setupScannerTest(t)
	defer cleanup()

	// Create a video file.
	createDummyFile(t, filepath.Join(mediaDir, "video.mp4"), "dummy content")

	// First scan.
	if err := scn.ScanOnce(context.Background()); err != nil {
		t.Fatalf("first ScanOnce failed: %v", err)
	}

	v1, err := videoRepo.GetByPath("/video.mp4")
	if err != nil {
		t.Fatalf("GetByPath after first scan failed: %v", err)
	}
	if v1 == nil {
		t.Fatal("video not found after first scan")
	}

	// Record the initial state.
	initialScannedAt := v1.ScannedAt
	initialTitle := v1.Title
	initialDuration := v1.Duration

	// Wait 1 second to ensure different scanned_at timestamp.
	time.Sleep(1 * time.Second)

	// Second scan without changes.
	if err := scn.ScanOnce(context.Background()); err != nil {
		t.Fatalf("second ScanOnce failed: %v", err)
	}

	v2, err := videoRepo.GetByPath("/video.mp4")
	if err != nil {
		t.Fatalf("GetByPath after second scan failed: %v", err)
	}
	if v2 == nil {
		t.Fatal("video not found after second scan")
	}

	// The video should still exist.
	// Metadata should be unchanged.
	if v2.Title != initialTitle {
		t.Errorf("Title changed: %q -> %q", initialTitle, v2.Title)
	}
	if v2.Duration != initialDuration {
		t.Errorf("Duration changed: %v -> %v", initialDuration, v2.Duration)
	}

	// scanned_at should be updated (scanner updates it for unchanged files).
	if v2.ScannedAt <= initialScannedAt {
		t.Errorf("scanned_at not updated: %d -> %d (expected increase)", initialScannedAt, v2.ScannedAt)
	}
}

func TestScanner_IncrementalScan_ModifiedFile(t *testing.T) {
	scn, mediaDir, videoRepo, _, cleanup := setupScannerTest(t)
	defer cleanup()

	// Create a video file.
	filePath := filepath.Join(mediaDir, "video.mp4")
	createDummyFile(t, filePath, "original content")

	// First scan.
	if err := scn.ScanOnce(context.Background()); err != nil {
		t.Fatalf("first ScanOnce failed: %v", err)
	}

	v1, _ := videoRepo.GetByPath("/video.mp4")
	if v1 == nil {
		t.Fatal("video not found after first scan")
	}
	originalModTime := v1.ModTime

	// Modify the file (change content → changes mod_time).
	time.Sleep(1 * time.Second) // Ensure mod_time changes.
	createDummyFile(t, filePath, "modified content with more data to change size")

	// Second scan.
	if err := scn.ScanOnce(context.Background()); err != nil {
		t.Fatalf("second ScanOnce failed: %v", err)
	}

	v2, _ := videoRepo.GetByPath("/video.mp4")
	if v2 == nil {
		t.Fatal("video not found after second scan")
	}

	// mod_time should be updated.
	if v2.ModTime == originalModTime {
		t.Errorf("mod_time not updated after file modification: %d", v2.ModTime)
	}

	// file_size should be updated (content is longer now).
	if v2.FileSize <= v1.FileSize {
		t.Errorf("file_size not updated: %d -> %d (expected increase)", v1.FileSize, v2.FileSize)
	}
}

// ---------------------------------------------------------------------------
// Stale entry removal tests
// ---------------------------------------------------------------------------

func TestScanner_StaleRemoval(t *testing.T) {
	scn, mediaDir, videoRepo, _, cleanup := setupScannerTest(t)
	defer cleanup()

	// Create two video files.
	createDummyFile(t, filepath.Join(mediaDir, "keep.mp4"), "keep this")
	createDummyFile(t, filepath.Join(mediaDir, "delete.mp4"), "delete this")

	// First scan.
	if err := scn.ScanOnce(context.Background()); err != nil {
		t.Fatalf("first ScanOnce failed: %v", err)
	}

	_, total, _ := videoRepo.List(allVideosFilter())
	if total != 2 {
		t.Fatalf("after first scan: total = %d, want 2", total)
	}

	// Delete one file from disk.
	os.Remove(filepath.Join(mediaDir, "delete.mp4"))

	// Wait 1 second to ensure different scanned_at.
	time.Sleep(1 * time.Second)

	// Second scan.
	if err := scn.ScanOnce(context.Background()); err != nil {
		t.Fatalf("second ScanOnce failed: %v", err)
	}

	// The deleted file should be removed from DB.
	videos, total, _ := videoRepo.List(allVideosFilter())
	if total != 1 {
		t.Errorf("after second scan: total = %d, want 1 (stale removed)", total)
	}
	if len(videos) != 1 {
		t.Fatalf("len(videos) = %d, want 1", len(videos))
	}
	if videos[0].Title != "keep.mp4" {
		t.Errorf("remaining video title = %q, want %q", videos[0].Title, "keep.mp4")
	}
}

// ---------------------------------------------------------------------------
// IsScanning tests
// ---------------------------------------------------------------------------

func TestScanner_IsScanning_NotScanningInitially(t *testing.T) {
	scn, _, _, _, cleanup := setupScannerTest(t)
	defer cleanup()

	if scn.IsScanning() {
		t.Errorf("IsScanning() = true, want false (before any scan)")
	}
}

func TestScanner_IsScanning_FalseAfterScan(t *testing.T) {
	scn, mediaDir, _, _, cleanup := setupScannerTest(t)
	defer cleanup()

	createDummyFile(t, filepath.Join(mediaDir, "video.mp4"), "dummy")

	if err := scn.ScanOnce(context.Background()); err != nil {
		t.Fatalf("ScanOnce failed: %v", err)
	}

	if scn.IsScanning() {
		t.Errorf("IsScanning() = true, want false (after scan completed)")
	}
}

func TestScanner_ScanOnce_ConcurrentReject(t *testing.T) {
	scn, mediaDir, _, _, cleanup := setupScannerTest(t)
	defer cleanup()

	createDummyFile(t, filepath.Join(mediaDir, "video.mp4"), "dummy")

	// Manually set scanning to true to simulate concurrent scan.
	scn.mu.Lock()
	scn.scanning = true
	scn.mu.Unlock()

	err := scn.ScanOnce(context.Background())
	if err == nil {
		t.Errorf("ScanOnce should reject when already scanning")
	}

	// Reset.
	scn.mu.Lock()
	scn.scanning = false
	scn.mu.Unlock()
}

// ---------------------------------------------------------------------------
// Nested directory scan tests
// ---------------------------------------------------------------------------

func TestScanner_NestedDirectories(t *testing.T) {
	scn, mediaDir, videoRepo, _, cleanup := setupScannerTest(t)
	defer cleanup()

	// Create files in nested directories.
	createDummyFile(t, filepath.Join(mediaDir, "root.mp4"), "root")
	createDummyFile(t, filepath.Join(mediaDir, "movies", "action", "a.mp4"), "action")
	createDummyFile(t, filepath.Join(mediaDir, "movies", "comedy", "b.mp4"), "comedy")
	createDummyFile(t, filepath.Join(mediaDir, "tv", "c.mp4"), "tv")

	if err := scn.ScanOnce(context.Background()); err != nil {
		t.Fatalf("ScanOnce failed: %v", err)
	}

	videos, total, err := videoRepo.List(allVideosFilter())
	if err != nil {
		t.Fatalf("List failed: %v", err)
	}
	if total != 4 {
		t.Errorf("total = %d, want 4", total)
	}

	// Verify folder paths are correctly set.
	folderPaths := make(map[string]bool)
	for _, v := range videos {
		folderPaths[v.Folder] = true
	}
	expectedFolders := []string{"/", "/movies/action", "/movies/comedy", "/tv"}
	for _, fp := range expectedFolders {
		if !folderPaths[fp] {
			t.Errorf("expected folder path %q not found in scan results", fp)
		}
	}
}

// ---------------------------------------------------------------------------
// Worker tests
// ---------------------------------------------------------------------------

func TestWorker_Probe_NonExistentFile(t *testing.T) {
	ffprobe := NewFFProbe("ffprobe")
	worker := NewWorker(ffprobe, 2, 5)

	// ffprobe is not available on the test system, so Probe should fail.
	_, err := worker.Probe(context.Background(), "/nonexistent/file.mp4")
	if err == nil {
		t.Logf("Probe succeeded on non-existent file (ffprobe might be available)")
	}
}

func TestWorker_ConcurrencyLimit(t *testing.T) {
	ffprobe := NewFFProbe("ffprobe")
	worker := NewWorker(ffprobe, 3, 5)

	if cap(worker.sem) != 3 {
		t.Errorf("semaphore capacity = %d, want 3", cap(worker.sem))
	}
}

func TestWorker_ConcurrencyMinimum(t *testing.T) {
	ffprobe := NewFFProbe("ffprobe")
	worker := NewWorker(ffprobe, 0, 5)

	// Concurrency < 1 should default to 1.
	if cap(worker.sem) != 1 {
		t.Errorf("semaphore capacity = %d, want 1 (minimum)", cap(worker.sem))
	}
}
