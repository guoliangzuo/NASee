package handlers

import (
	"log/slog"
	"net/http"

	"nasee-server/internal/models"
)

// HealthHandler handles health check requests.
type HealthHandler struct {
	Logger *slog.Logger
}

// Health returns a simple health status. This endpoint does not require authentication.
func (h *HealthHandler) Health(w http.ResponseWriter, r *http.Request) {
	models.WriteSuccess(w, map[string]string{"status": "ok"})
}
