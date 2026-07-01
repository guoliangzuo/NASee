package handlers

import (
	"log/slog"
	"net/http"

	"nasee-server/internal/media"
	"nasee-server/internal/models"
	"nasee-server/internal/storage"
)

// StreamHandler handles video streaming with HTTP Range support.
type StreamHandler struct {
	Repo      *storage.VideoRepo
	StreamSvc *media.StreamService
	Logger    *slog.Logger
}

// Stream handles GET /api/v1/videos/{id}/stream.
// Uses http.ServeContent for automatic Range/206/seek support.
func (h *StreamHandler) Stream(w http.ResponseWriter, r *http.Request) {
	id, ok := parseVideoID(w, r)
	if !ok {
		return
	}

	video, err := h.Repo.GetByID(id)
	if err != nil {
		h.Logger.Error("failed to get video", "id", id, "error", err)
		models.WriteError(w, http.StatusInternalServerError, "failed to get video")
		return
	}
	if video == nil {
		models.WriteError(w, http.StatusNotFound, "video not found")
		return
	}

	if err := h.StreamSvc.Serve(w, r, video); err != nil {
		h.Logger.Error("failed to stream video", "id", id, "path", video.FilePath, "error", err)
		// If headers haven't been written yet, send an error response.
		// If ServeContent already started writing, the connection will just drop.
		models.WriteError(w, http.StatusInternalServerError, "failed to stream video")
	}
}
