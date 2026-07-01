package handlers

import (
	"log/slog"
	"net/http"
	"strconv"

	"nasee-server/internal/models"
	"nasee-server/internal/storage"
)

// LikeHandler handles like-related endpoints.
type LikeHandler struct {
	LikeRepo  *storage.LikeRepo
	VideoRepo *storage.VideoRepo
	Logger    *slog.Logger
}

// GetLikeStatus handles GET /api/v1/videos/{id}/like.
func (h *LikeHandler) GetLikeStatus(w http.ResponseWriter, r *http.Request) {
	id, ok := parseVideoID(w, r)
	if !ok {
		return
	}

	liked, err := h.LikeRepo.GetLikeStatus(id)
	if err != nil {
		h.Logger.Error("failed to get like status", "video_id", id, "error", err)
		models.WriteError(w, http.StatusInternalServerError, "failed to get like status")
		return
	}

	models.WriteSuccess(w, models.LikeResponse{VideoID: id, Liked: liked})
}

// ToggleLike handles POST /api/v1/videos/{id}/like.
func (h *LikeHandler) ToggleLike(w http.ResponseWriter, r *http.Request) {
	id, ok := parseVideoID(w, r)
	if !ok {
		return
	}

	liked, err := h.LikeRepo.ToggleLike(id)
	if err != nil {
		h.Logger.Error("failed to toggle like", "video_id", id, "error", err)
		models.WriteError(w, http.StatusInternalServerError, "failed to toggle like")
		return
	}

	models.WriteSuccess(w, models.LikeResponse{VideoID: id, Liked: liked})
}

// ListLiked handles GET /api/v1/videos/liked with pagination.
func (h *LikeHandler) ListLiked(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()

	filter := models.VideoFilter{
		Page:      atoiDefault(q.Get("page"), 1),
		PageSize:  atoiDefault(q.Get("page_size"), 20),
		Sort:      "mod_time",
		Order:     "desc",
		LikedOnly: true,
	}

	videos, total, err := h.VideoRepo.List(filter)
	if err != nil {
		h.Logger.Error("failed to list liked videos", "error", err)
		models.WriteError(w, http.StatusInternalServerError, "failed to list liked videos")
		return
	}

	models.WriteSuccess(w, models.VideoListResponse{
		Videos:   videos,
		Total:    total,
		Page:     filter.Page,
		PageSize: filter.PageSize,
	})
}

// parseVideoID extracts and validates the {id} path parameter.
func parseVideoID(w http.ResponseWriter, r *http.Request) (int64, bool) {
	idStr := r.PathValue("id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		models.WriteError(w, http.StatusBadRequest, "invalid video id")
		return 0, false
	}
	if id < 1 {
		models.WriteError(w, http.StatusBadRequest, "invalid video id")
		return 0, false
	}
	return id, true
}
