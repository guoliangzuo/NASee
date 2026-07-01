package api

import (
	"log/slog"
	"net/http"

	"nasee-server/internal/api/handlers"
	"nasee-server/internal/config"
	"nasee-server/internal/media"
	"nasee-server/internal/scanner"
	"nasee-server/internal/storage"
)

// Router holds all dependencies needed to build the HTTP handler tree.
type Router struct {
	cfg       *config.Config
	videoRepo *storage.VideoRepo
	likeRepo  *storage.LikeRepo
	scn       *scanner.Scanner
	streamSvc *media.StreamService
	logger    *slog.Logger
}

// NewRouter creates a new Router with the given dependencies.
func NewRouter(
	cfg *config.Config,
	videoRepo *storage.VideoRepo,
	likeRepo *storage.LikeRepo,
	scn *scanner.Scanner,
	streamSvc *media.StreamService,
	logger *slog.Logger,
) *Router {
	return &Router{
		cfg:       cfg,
		videoRepo: videoRepo,
		likeRepo:  likeRepo,
		scn:       scn,
		streamSvc: streamSvc,
		logger:    logger,
	}
}

// Handler builds and returns the root http.Handler with all routes and middleware registered.
func (rt *Router) Handler() http.Handler {
	mux := http.NewServeMux()

	// Construct handler instances with injected dependencies.
	healthH := &handlers.HealthHandler{Logger: rt.logger}
	videoH := &handlers.VideoHandler{Repo: rt.videoRepo, Scanner: rt.scn, Logger: rt.logger}
	likeH := &handlers.LikeHandler{LikeRepo: rt.likeRepo, VideoRepo: rt.videoRepo, Logger: rt.logger}
	streamH := &handlers.StreamHandler{Repo: rt.videoRepo, StreamSvc: rt.streamSvc, Logger: rt.logger}

	// Health endpoint — no authentication required.
	mux.HandleFunc("GET /health", healthH.Health)

	// Auth middleware wraps all /api/v1/* endpoints.
	auth := AuthMiddleware(rt.cfg.Password)

	// API v1 routes.
	mux.Handle("GET /api/v1/videos", auth(http.HandlerFunc(videoH.ListVideos)))
	mux.Handle("GET /api/v1/videos/{id}/stream", auth(http.HandlerFunc(streamH.Stream)))
	mux.Handle("GET /api/v1/folders", auth(http.HandlerFunc(videoH.ListFolders)))
	mux.Handle("GET /api/v1/videos/{id}/like", auth(http.HandlerFunc(likeH.GetLikeStatus)))
	mux.Handle("POST /api/v1/videos/{id}/like", auth(http.HandlerFunc(likeH.ToggleLike)))
	mux.Handle("GET /api/v1/videos/liked", auth(http.HandlerFunc(likeH.ListLiked)))
	mux.Handle("POST /api/v1/scan", auth(http.HandlerFunc(videoH.TriggerScan)))

	// Apply global middleware: logging (outermost) wraps CORS wraps mux.
	return LoggingMiddleware(rt.logger)(CORSMiddleware()(mux))
}
