package handlers

import (
	"context"
	"log/slog"
	"net/http"
	"strconv"

	"nasee-server/internal/models"
	"nasee-server/internal/scanner"
	"nasee-server/internal/storage"
)

// VideoHandler handles video listing and folder tree endpoints.
type VideoHandler struct {
	Repo    *storage.VideoRepo
	Scanner *scanner.Scanner
	Logger  *slog.Logger
}

// ListVideos handles GET /api/v1/videos with pagination, filtering, and sorting.
func (h *VideoHandler) ListVideos(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()

	filter := models.VideoFilter{
		Page:      atoiDefault(q.Get("page"), 1),
		PageSize:  atoiDefault(q.Get("page_size"), 20),
		Folder:    q.Get("folder"),
		Sort:      strDefault(q.Get("sort"), "mod_time"),
		Order:     strDefault(q.Get("order"), "desc"),
		LikedOnly: q.Get("liked_only") == "true",
	}

	videos, total, err := h.Repo.List(filter)
	if err != nil {
		h.Logger.Error("failed to list videos", "error", err)
		models.WriteError(w, http.StatusInternalServerError, "failed to list videos")
		return
	}

	models.WriteSuccess(w, models.VideoListResponse{
		Videos:   videos,
		Total:    total,
		Page:     filter.Page,
		PageSize: filter.PageSize,
	})
}

// ListFolders handles GET /api/v1/folders and returns the folder tree.
func (h *VideoHandler) ListFolders(w http.ResponseWriter, r *http.Request) {
	folders, err := h.Repo.GetFolders()
	if err != nil {
		h.Logger.Error("failed to get folders", "error", err)
		models.WriteError(w, http.StatusInternalServerError, "failed to get folders")
		return
	}

	models.WriteSuccess(w, models.FolderListResponse{Folders: folders})
}

// TriggerScan handles POST /api/v1/scan to manually trigger an incremental scan.
func (h *VideoHandler) TriggerScan(w http.ResponseWriter, r *http.Request) {
	if h.Scanner.IsScanning() {
		models.WriteError(w, http.StatusConflict, "scan already in progress")
		return
	}

	go func() {
		if err := h.Scanner.ScanOnce(context.Background()); err != nil {
			h.Logger.Warn("triggered scan failed", "error", err)
		}
	}()

	models.WriteSuccess(w, models.ScanResponse{Status: "scan started"})
}

// atoiDefault parses an integer from a string, returning the default on failure.
func atoiDefault(s string, defaultVal int) int {
	if s == "" {
		return defaultVal
	}
	n, err := strconv.Atoi(s)
	if err != nil {
		return defaultVal
	}
	return n
}

// strDefault returns the string value if non-empty, otherwise the default.
func strDefault(s, defaultVal string) string {
	if s == "" {
		return defaultVal
	}
	return s
}
