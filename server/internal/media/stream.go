package media

import (
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"nasee-server/internal/models"
)

// contentTypes maps video file extensions to their MIME types.
var contentTypes = map[string]string{
	".mp4":  "video/mp4",
	".mkv":  "video/x-matroska",
	".avi":  "video/x-msvideo",
	".mov":  "video/quicktime",
	".ts":   "video/mp2t",
	".flv":  "video/x-flv",
	".webm": "video/webm",
	".m4v":  "video/x-m4v",
	".wmv":  "video/x-ms-wmv",
}

// StreamService handles HTTP video streaming with Range request support.
type StreamService struct {
	mediaDir string
}

// NewStreamService creates a new StreamService for the given media directory.
func NewStreamService(mediaDir string) *StreamService {
	return &StreamService{mediaDir: mediaDir}
}

// Serve streams a video file using http.ServeContent, which handles
// Range requests, 206 Partial Content, Content-Range, and seeking.
func (s *StreamService) Serve(w http.ResponseWriter, r *http.Request, video *models.Video) error {
	// Construct the full file path.
	fullPath := filepath.Join(s.mediaDir, filepath.FromSlash(video.FilePath))

	// Security: prevent directory traversal by ensuring the resolved path
	// stays within the media directory.
	cleanPath := filepath.Clean(fullPath)
	cleanMediaDir := filepath.Clean(s.mediaDir)
	if !strings.HasPrefix(cleanPath, cleanMediaDir+string(filepath.Separator)) && cleanPath != cleanMediaDir {
		return fmt.Errorf("path traversal detected: %s", video.FilePath)
	}

	// Open the file for reading.
	file, err := os.Open(cleanPath)
	if err != nil {
		return fmt.Errorf("open file %q: %w", cleanPath, err)
	}
	defer file.Close()

	// Stat the file to get modification time for cache headers.
	stat, err := file.Stat()
	if err != nil {
		return fmt.Errorf("stat file %q: %w", cleanPath, err)
	}

	// Set Content-Type based on file extension.
	contentType := getContentType(video.FilePath)
	w.Header().Set("Content-Type", contentType)

	// http.ServeContent handles Range requests, 206 responses,
	// Content-Range headers, and conditional requests automatically.
	http.ServeContent(w, r, video.Title, stat.ModTime(), file)
	return nil
}

// getContentType returns the MIME type for a given file extension.
// Falls back to application/octet-stream for unknown types.
func getContentType(filePath string) string {
	ext := strings.ToLower(filepath.Ext(filePath))
	if ct, ok := contentTypes[ext]; ok {
		return ct
	}
	return "application/octet-stream"
}
