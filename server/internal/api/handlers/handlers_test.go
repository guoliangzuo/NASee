package handlers_test

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"testing"
	"time"

	"nasee-server/internal/api"
	"nasee-server/internal/api/handlers"
	"nasee-server/internal/media"
	"nasee-server/internal/models"
	"nasee-server/internal/storage"
)

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

// setupHandlerTestEnv creates a temporary DB with 3 test videos and returns repos + cleanup.
func setupHandlerTestEnv(t *testing.T) (*storage.VideoRepo, *storage.LikeRepo, func()) {
	t.Helper()
	tmpDir := t.TempDir()
	db, err := storage.New(tmpDir)
	if err != nil {
		t.Fatalf("failed to create DB: %v", err)
	}
	if err := storage.Migrate(db); err != nil {
		t.Fatalf("failed to migrate DB: %v", err)
	}
	videoRepo := storage.NewVideoRepo(db)
	likeRepo := storage.NewLikeRepo(db)

	// Insert test videos with distinct values for sorting/filtering.
	videos := []*models.Video{
		{FilePath: "/movies/a.mp4", Title: "Alpha", Duration: 100, Width: 1920, Height: 1080, FileSize: 1000, ModTime: 1000, FolderPath: "/movies", ScannedAt: 1000},
		{FilePath: "/movies/b.mp4", Title: "Bravo", Duration: 200, Width: 1920, Height: 1080, FileSize: 2000, ModTime: 2000, FolderPath: "/movies", ScannedAt: 1000},
		{FilePath: "/tv/c.mp4", Title: "Charlie", Duration: 300, Width: 1280, Height: 720, FileSize: 3000, ModTime: 3000, FolderPath: "/tv", ScannedAt: 1000},
	}
	for _, v := range videos {
		if err := videoRepo.Upsert(v); err != nil {
			t.Fatalf("failed to upsert video: %v", err)
		}
	}

	cleanup := func() { db.Close() }
	return videoRepo, likeRepo, cleanup
}

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}

// parseAPIResponse unmarshals the response body into ApiResponse.
func parseAPIResponse(t *testing.T, body []byte) models.ApiResponse {
	t.Helper()
	var resp models.ApiResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		t.Fatalf("failed to parse API response: %v\nbody: %s", err, string(body))
	}
	return resp
}

// parseVideoListData extracts VideoListResponse from ApiResponse.Data.
func parseVideoListData(t *testing.T, resp models.ApiResponse) models.VideoListResponse {
	t.Helper()
	dataBytes, err := json.Marshal(resp.Data)
	if err != nil {
		t.Fatalf("failed to marshal data: %v", err)
	}
	var vlr models.VideoListResponse
	if err := json.Unmarshal(dataBytes, &vlr); err != nil {
		t.Fatalf("failed to unmarshal VideoListResponse: %v", err)
	}
	return vlr
}

// parseLikeResponse extracts LikeResponse from ApiResponse.Data.
func parseLikeResponse(t *testing.T, resp models.ApiResponse) models.LikeResponse {
	t.Helper()
	dataBytes, err := json.Marshal(resp.Data)
	if err != nil {
		t.Fatalf("failed to marshal data: %v", err)
	}
	var lr models.LikeResponse
	if err := json.Unmarshal(dataBytes, &lr); err != nil {
		t.Fatalf("failed to unmarshal LikeResponse: %v", err)
	}
	return lr
}

// ---------------------------------------------------------------------------
// HealthHandler tests
// ---------------------------------------------------------------------------

func TestHealthHandler(t *testing.T) {
	h := &handlers.HealthHandler{Logger: testLogger()}

	req := httptest.NewRequest("GET", "/health", nil)
	rec := httptest.NewRecorder()
	h.Health(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusOK)
	}

	resp := parseAPIResponse(t, rec.Body.Bytes())
	if resp.Code != 0 {
		t.Errorf("code = %d, want 0", resp.Code)
	}
	if resp.Message != "ok" {
		t.Errorf("message = %q, want %q", resp.Message, "ok")
	}
}

// ---------------------------------------------------------------------------
// VideoHandler — ListVideos tests
// ---------------------------------------------------------------------------

func TestVideoHandler_ListVideos_DefaultParams(t *testing.T) {
	videoRepo, _, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	h := &handlers.VideoHandler{Repo: videoRepo, Logger: testLogger()}

	req := httptest.NewRequest("GET", "/api/v1/videos", nil)
	rec := httptest.NewRecorder()
	h.ListVideos(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusOK)
	}

	resp := parseAPIResponse(t, rec.Body.Bytes())
	if resp.Code != 0 {
		t.Errorf("code = %d, want 0", resp.Code)
	}

	vlr := parseVideoListData(t, resp)
	if vlr.Total != 3 {
		t.Errorf("total = %d, want 3", vlr.Total)
	}
	if len(vlr.Videos) != 3 {
		t.Errorf("len(videos) = %d, want 3", len(vlr.Videos))
	}
	// Default sort is mod_time desc → Charlie (3000), Bravo (2000), Alpha (1000).
	if vlr.Videos[0].Title != "Charlie" {
		t.Errorf("videos[0].Title = %q, want %q", vlr.Videos[0].Title, "Charlie")
	}
}

func TestVideoHandler_ListVideos_Pagination(t *testing.T) {
	videoRepo, _, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	h := &handlers.VideoHandler{Repo: videoRepo, Logger: testLogger()}

	// Page 1, size 2.
	req := httptest.NewRequest("GET", "/api/v1/videos?page=1&page_size=2&sort=name&order=asc", nil)
	rec := httptest.NewRecorder()
	h.ListVideos(rec, req)

	vlr := parseVideoListData(t, parseAPIResponse(t, rec.Body.Bytes()))
	if vlr.Total != 3 {
		t.Errorf("total = %d, want 3", vlr.Total)
	}
	if len(vlr.Videos) != 2 {
		t.Fatalf("len(videos) = %d, want 2", len(vlr.Videos))
	}
	if vlr.Videos[0].Title != "Alpha" {
		t.Errorf("videos[0].Title = %q, want %q", vlr.Videos[0].Title, "Alpha")
	}
	if vlr.Videos[1].Title != "Bravo" {
		t.Errorf("videos[1].Title = %q, want %q", vlr.Videos[1].Title, "Bravo")
	}

	// Page 2, size 2.
	req2 := httptest.NewRequest("GET", "/api/v1/videos?page=2&page_size=2&sort=name&order=asc", nil)
	rec2 := httptest.NewRecorder()
	h.ListVideos(rec2, req2)

	vlr2 := parseVideoListData(t, parseAPIResponse(t, rec2.Body.Bytes()))
	if len(vlr2.Videos) != 1 {
		t.Fatalf("len(videos) page 2 = %d, want 1", len(vlr2.Videos))
	}
	if vlr2.Videos[0].Title != "Charlie" {
		t.Errorf("videos[0].Title page 2 = %q, want %q", vlr2.Videos[0].Title, "Charlie")
	}
}

func TestVideoHandler_ListVideos_FolderFilter(t *testing.T) {
	videoRepo, _, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	h := &handlers.VideoHandler{Repo: videoRepo, Logger: testLogger()}

	req := httptest.NewRequest("GET", "/api/v1/videos?folder=/movies&sort=name&order=asc", nil)
	rec := httptest.NewRecorder()
	h.ListVideos(rec, req)

	vlr := parseVideoListData(t, parseAPIResponse(t, rec.Body.Bytes()))
	if vlr.Total != 2 {
		t.Errorf("total = %d, want 2", vlr.Total)
	}
	for _, v := range vlr.Videos {
		if v.Folder != "/movies" {
			t.Errorf("Folder = %q, want %q", v.Folder, "/movies")
		}
	}
}

func TestVideoHandler_ListVideos_LikedOnly(t *testing.T) {
	videoRepo, likeRepo, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	// Like "Alpha".
	v, _ := videoRepo.GetByPath("/movies/a.mp4")
	likeRepo.ToggleLike(v.ID)

	h := &handlers.VideoHandler{Repo: videoRepo, Logger: testLogger()}

	req := httptest.NewRequest("GET", "/api/v1/videos?liked_only=true", nil)
	rec := httptest.NewRecorder()
	h.ListVideos(rec, req)

	vlr := parseVideoListData(t, parseAPIResponse(t, rec.Body.Bytes()))
	if vlr.Total != 1 {
		t.Errorf("total = %d, want 1", vlr.Total)
	}
	if len(vlr.Videos) != 1 {
		t.Fatalf("len(videos) = %d, want 1", len(vlr.Videos))
	}
	if !vlr.Videos[0].Liked {
		t.Errorf("videos[0].Liked = false, want true")
	}
}

func TestVideoHandler_ListVideos_SortByName(t *testing.T) {
	videoRepo, _, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	h := &handlers.VideoHandler{Repo: videoRepo, Logger: testLogger()}

	// Asc.
	req := httptest.NewRequest("GET", "/api/v1/videos?sort=name&order=asc", nil)
	rec := httptest.NewRecorder()
	h.ListVideos(rec, req)
	vlr := parseVideoListData(t, parseAPIResponse(t, rec.Body.Bytes()))

	want := []string{"Alpha", "Bravo", "Charlie"}
	for i, w := range want {
		if vlr.Videos[i].Title != w {
			t.Errorf("asc videos[%d].Title = %q, want %q", i, vlr.Videos[i].Title, w)
		}
	}

	// Desc.
	req2 := httptest.NewRequest("GET", "/api/v1/videos?sort=name&order=desc", nil)
	rec2 := httptest.NewRecorder()
	h.ListVideos(rec2, req2)
	vlr2 := parseVideoListData(t, parseAPIResponse(t, rec2.Body.Bytes()))

	wantDesc := []string{"Charlie", "Bravo", "Alpha"}
	for i, w := range wantDesc {
		if vlr2.Videos[i].Title != w {
			t.Errorf("desc videos[%d].Title = %q, want %q", i, vlr2.Videos[i].Title, w)
		}
	}
}

func TestVideoHandler_ListVideos_SortByDuration(t *testing.T) {
	videoRepo, _, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	h := &handlers.VideoHandler{Repo: videoRepo, Logger: testLogger()}

	// Asc.
	req := httptest.NewRequest("GET", "/api/v1/videos?sort=duration&order=asc", nil)
	rec := httptest.NewRecorder()
	h.ListVideos(rec, req)
	vlr := parseVideoListData(t, parseAPIResponse(t, rec.Body.Bytes()))

	want := []float64{100, 200, 300}
	for i, w := range want {
		if vlr.Videos[i].Duration != w {
			t.Errorf("asc videos[%d].Duration = %v, want %v", i, vlr.Videos[i].Duration, w)
		}
	}
}

func TestVideoHandler_ListVideos_StreamURL(t *testing.T) {
	videoRepo, _, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	h := &handlers.VideoHandler{Repo: videoRepo, Logger: testLogger()}

	req := httptest.NewRequest("GET", "/api/v1/videos", nil)
	rec := httptest.NewRecorder()
	h.ListVideos(rec, req)

	vlr := parseVideoListData(t, parseAPIResponse(t, rec.Body.Bytes()))
	for _, v := range vlr.Videos {
		expected := "/api/v1/videos/" + strconv.FormatInt(v.ID, 10) + "/stream"
		if v.StreamURL != expected {
			t.Errorf("StreamURL = %q, want %q", v.StreamURL, expected)
		}
	}
}

// ---------------------------------------------------------------------------
// VideoHandler — ListFolders tests
// ---------------------------------------------------------------------------

func TestVideoHandler_ListFolders(t *testing.T) {
	videoRepo, _, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	h := &handlers.VideoHandler{Repo: videoRepo, Logger: testLogger()}

	req := httptest.NewRequest("GET", "/api/v1/folders", nil)
	rec := httptest.NewRecorder()
	h.ListFolders(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusOK)
	}

	resp := parseAPIResponse(t, rec.Body.Bytes())
	if resp.Code != 0 {
		t.Errorf("code = %d, want 0", resp.Code)
	}

	dataBytes, _ := json.Marshal(resp.Data)
	var flr models.FolderListResponse
	json.Unmarshal(dataBytes, &flr)

	if len(flr.Folders) != 1 {
		t.Fatalf("len(folders) = %d, want 1", len(flr.Folders))
	}
	root := flr.Folders[0]
	if root.Path != "/" {
		t.Errorf("root.Path = %q, want %q", root.Path, "/")
	}
	if root.Count != 3 {
		t.Errorf("root.Count = %d, want 3", root.Count)
	}
	if len(root.Children) != 2 {
		t.Errorf("root.Children len = %d, want 2", len(root.Children))
	}
}

// ---------------------------------------------------------------------------
// LikeHandler — GetLikeStatus tests
// ---------------------------------------------------------------------------

func TestLikeHandler_GetLikeStatus(t *testing.T) {
	videoRepo, likeRepo, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	v, _ := videoRepo.GetByPath("/movies/a.mp4")
	likeRepo.ToggleLike(v.ID)

	h := &handlers.LikeHandler{LikeRepo: likeRepo, VideoRepo: videoRepo, Logger: testLogger()}

	req := httptest.NewRequest("GET", "/api/v1/videos/"+strconv.FormatInt(v.ID, 10)+"/like", nil)
	req.SetPathValue("id", strconv.FormatInt(v.ID, 10))
	rec := httptest.NewRecorder()
	h.GetLikeStatus(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusOK)
	}

	lr := parseLikeResponse(t, parseAPIResponse(t, rec.Body.Bytes()))
	if !lr.Liked {
		t.Errorf("liked = false, want true")
	}
}

func TestLikeHandler_GetLikeStatus_NotLiked(t *testing.T) {
	videoRepo, likeRepo, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	v, _ := videoRepo.GetByPath("/movies/a.mp4")

	h := &handlers.LikeHandler{LikeRepo: likeRepo, VideoRepo: videoRepo, Logger: testLogger()}

	req := httptest.NewRequest("GET", "/api/v1/videos/"+strconv.FormatInt(v.ID, 10)+"/like", nil)
	req.SetPathValue("id", strconv.FormatInt(v.ID, 10))
	rec := httptest.NewRecorder()
	h.GetLikeStatus(rec, req)

	lr := parseLikeResponse(t, parseAPIResponse(t, rec.Body.Bytes()))
	if lr.Liked {
		t.Errorf("liked = true, want false")
	}
}

func TestLikeHandler_GetLikeStatus_InvalidID(t *testing.T) {
	videoRepo, likeRepo, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	h := &handlers.LikeHandler{LikeRepo: likeRepo, VideoRepo: videoRepo, Logger: testLogger()}

	req := httptest.NewRequest("GET", "/api/v1/videos/abc/like", nil)
	req.SetPathValue("id", "abc")
	rec := httptest.NewRecorder()
	h.GetLikeStatus(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
}

// ---------------------------------------------------------------------------
// LikeHandler — ToggleLike tests
// ---------------------------------------------------------------------------

func TestLikeHandler_ToggleLike(t *testing.T) {
	videoRepo, likeRepo, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	v, _ := videoRepo.GetByPath("/movies/a.mp4")
	h := &handlers.LikeHandler{LikeRepo: likeRepo, VideoRepo: videoRepo, Logger: testLogger()}
	idStr := strconv.FormatInt(v.ID, 10)

	// First toggle: like.
	req := httptest.NewRequest("POST", "/api/v1/videos/"+idStr+"/like", nil)
	req.SetPathValue("id", idStr)
	rec := httptest.NewRecorder()
	h.ToggleLike(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("first toggle status = %d, want %d", rec.Code, http.StatusOK)
	}
	lr := parseLikeResponse(t, parseAPIResponse(t, rec.Body.Bytes()))
	if !lr.Liked {
		t.Errorf("first toggle: liked = false, want true")
	}

	// Second toggle: unlike.
	req2 := httptest.NewRequest("POST", "/api/v1/videos/"+idStr+"/like", nil)
	req2.SetPathValue("id", idStr)
	rec2 := httptest.NewRecorder()
	h.ToggleLike(rec2, req2)

	lr2 := parseLikeResponse(t, parseAPIResponse(t, rec2.Body.Bytes()))
	if lr2.Liked {
		t.Errorf("second toggle: liked = true, want false")
	}

	// Third toggle: like again.
	req3 := httptest.NewRequest("POST", "/api/v1/videos/"+idStr+"/like", nil)
	req3.SetPathValue("id", idStr)
	rec3 := httptest.NewRecorder()
	h.ToggleLike(rec3, req3)

	lr3 := parseLikeResponse(t, parseAPIResponse(t, rec3.Body.Bytes()))
	if !lr3.Liked {
		t.Errorf("third toggle: liked = false, want true")
	}
}

func TestLikeHandler_ToggleLike_InvalidID(t *testing.T) {
	videoRepo, likeRepo, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	h := &handlers.LikeHandler{LikeRepo: likeRepo, VideoRepo: videoRepo, Logger: testLogger()}

	req := httptest.NewRequest("POST", "/api/v1/videos/abc/like", nil)
	req.SetPathValue("id", "abc")
	rec := httptest.NewRecorder()
	h.ToggleLike(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
}

// ---------------------------------------------------------------------------
// LikeHandler — ListLiked tests
// ---------------------------------------------------------------------------

func TestLikeHandler_ListLiked(t *testing.T) {
	videoRepo, likeRepo, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	// Like Alpha and Charlie.
	vAlpha, _ := videoRepo.GetByPath("/movies/a.mp4")
	likeRepo.ToggleLike(vAlpha.ID)
	vCharlie, _ := videoRepo.GetByPath("/tv/c.mp4")
	likeRepo.ToggleLike(vCharlie.ID)

	h := &handlers.LikeHandler{LikeRepo: likeRepo, VideoRepo: videoRepo, Logger: testLogger()}

	req := httptest.NewRequest("GET", "/api/v1/videos/liked?page=1&page_size=20", nil)
	rec := httptest.NewRecorder()
	h.ListLiked(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusOK)
	}

	vlr := parseVideoListData(t, parseAPIResponse(t, rec.Body.Bytes()))
	if vlr.Total != 2 {
		t.Errorf("total = %d, want 2", vlr.Total)
	}
	if len(vlr.Videos) != 2 {
		t.Fatalf("len(videos) = %d, want 2", len(vlr.Videos))
	}
	for _, v := range vlr.Videos {
		if !v.Liked {
			t.Errorf("video %q: Liked = false, want true", v.Title)
		}
	}
}

func TestLikeHandler_ListLiked_Empty(t *testing.T) {
	videoRepo, likeRepo, cleanup := setupHandlerTestEnv(t)
	defer cleanup()

	h := &handlers.LikeHandler{LikeRepo: likeRepo, VideoRepo: videoRepo, Logger: testLogger()}

	req := httptest.NewRequest("GET", "/api/v1/videos/liked", nil)
	rec := httptest.NewRecorder()
	h.ListLiked(rec, req)

	vlr := parseVideoListData(t, parseAPIResponse(t, rec.Body.Bytes()))
	if vlr.Total != 0 {
		t.Errorf("total = %d, want 0", vlr.Total)
	}
	if len(vlr.Videos) != 0 {
		t.Errorf("len(videos) = %d, want 0", len(vlr.Videos))
	}
}

// ---------------------------------------------------------------------------
// StreamHandler tests
// ---------------------------------------------------------------------------

// setupStreamTest creates a temp media dir with a real file and a DB with a video record.
func setupStreamTest(t *testing.T) (*handlers.StreamHandler, *storage.VideoRepo, int64, func()) {
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

	// Create a real file with known content (2048 bytes).
	fileContent := make([]byte, 2048)
	for i := range fileContent {
		fileContent[i] = 'A'
	}
	realPath := filepath.Join(mediaDir, "test.mp4")
	if err := os.WriteFile(realPath, fileContent, 0644); err != nil {
		t.Fatalf("failed to write test file: %v", err)
	}

	// Insert video record.
	video := &models.Video{
		FilePath:   "/test.mp4",
		Title:      "test.mp4",
		Duration:   10.5,
		Width:      1920,
		Height:     1080,
		FileSize:   2048,
		ModTime:    time.Now().Unix(),
		FolderPath: "/",
		ScannedAt:  time.Now().Unix(),
	}
	if err := videoRepo.Upsert(video); err != nil {
		t.Fatalf("failed to upsert video: %v", err)
	}

	inserted, _ := videoRepo.GetByPath("/test.mp4")
	streamSvc := media.NewStreamService(mediaDir)

	h := &handlers.StreamHandler{
		Repo:      videoRepo,
		StreamSvc: streamSvc,
		Logger:    testLogger(),
	}

	cleanup := func() { db.Close() }
	return h, videoRepo, inserted.ID, cleanup
}

func TestStreamHandler_NotFound(t *testing.T) {
	h, _, _, cleanup := setupStreamTest(t)
	defer cleanup()

	req := httptest.NewRequest("GET", "/api/v1/videos/99999/stream", nil)
	req.SetPathValue("id", "99999")
	rec := httptest.NewRecorder()
	h.Stream(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusNotFound)
	}
}

func TestStreamHandler_InvalidID(t *testing.T) {
	h, _, _, cleanup := setupStreamTest(t)
	defer cleanup()

	req := httptest.NewRequest("GET", "/api/v1/videos/abc/stream", nil)
	req.SetPathValue("id", "abc")
	rec := httptest.NewRecorder()
	h.Stream(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
}

func TestStreamHandler_FullFile(t *testing.T) {
	h, _, videoID, cleanup := setupStreamTest(t)
	defer cleanup()

	idStr := strconv.FormatInt(videoID, 10)
	req := httptest.NewRequest("GET", "/api/v1/videos/"+idStr+"/stream", nil)
	req.SetPathValue("id", idStr)
	rec := httptest.NewRecorder()
	h.Stream(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusOK)
	}

	// Verify Content-Type.
	ct := rec.Header().Get("Content-Type")
	if ct != "video/mp4" {
		t.Errorf("Content-Type = %q, want %q", ct, "video/mp4")
	}

	// Verify Content-Length.
	cl := rec.Header().Get("Content-Length")
	if cl != "2048" {
		t.Errorf("Content-Length = %q, want %q", cl, "2048")
	}

	// Verify body length.
	body := rec.Body.Bytes()
	if len(body) != 2048 {
		t.Errorf("body length = %d, want 2048", len(body))
	}
}

func TestStreamHandler_RangeRequest(t *testing.T) {
	h, _, videoID, cleanup := setupStreamTest(t)
	defer cleanup()

	idStr := strconv.FormatInt(videoID, 10)
	req := httptest.NewRequest("GET", "/api/v1/videos/"+idStr+"/stream", nil)
	req.SetPathValue("id", idStr)
	req.Header.Set("Range", "bytes=0-1023")
	rec := httptest.NewRecorder()
	h.Stream(rec, req)

	if rec.Code != http.StatusPartialContent {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusPartialContent)
	}

	// Verify Content-Range.
	cr := rec.Header().Get("Content-Range")
	expectedCR := "bytes 0-1023/2048"
	if cr != expectedCR {
		t.Errorf("Content-Range = %q, want %q", cr, expectedCR)
	}

	// Verify Content-Length.
	cl := rec.Header().Get("Content-Length")
	if cl != "1024" {
		t.Errorf("Content-Length = %q, want %q", cl, "1024")
	}

	// Verify body length.
	body := rec.Body.Bytes()
	if len(body) != 1024 {
		t.Errorf("body length = %d, want 1024", len(body))
	}

	// Verify Accept-Ranges.
	ar := rec.Header().Get("Accept-Ranges")
	if ar != "bytes" {
		t.Errorf("Accept-Ranges = %q, want %q", ar, "bytes")
	}
}

func TestStreamHandler_RangeRequest_Middle(t *testing.T) {
	h, _, videoID, cleanup := setupStreamTest(t)
	defer cleanup()

	idStr := strconv.FormatInt(videoID, 10)
	req := httptest.NewRequest("GET", "/api/v1/videos/"+idStr+"/stream", nil)
	req.SetPathValue("id", idStr)
	req.Header.Set("Range", "bytes=512-1023")
	rec := httptest.NewRecorder()
	h.Stream(rec, req)

	if rec.Code != http.StatusPartialContent {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusPartialContent)
	}

	cr := rec.Header().Get("Content-Range")
	expectedCR := "bytes 512-1023/2048"
	if cr != expectedCR {
		t.Errorf("Content-Range = %q, want %q", cr, expectedCR)
	}

	body := rec.Body.Bytes()
	if len(body) != 512 {
		t.Errorf("body length = %d, want 512", len(body))
	}
}

// ---------------------------------------------------------------------------
// AuthMiddleware tests
// ---------------------------------------------------------------------------

func TestAuthMiddleware_NoKey(t *testing.T) {
	password := "secretpass"
	auth := api.AuthMiddleware(password)

	nextHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		models.WriteSuccess(w, map[string]string{"status": "ok"})
	})
	wrapped := auth(nextHandler)

	req := httptest.NewRequest("GET", "/api/v1/videos", nil)
	rec := httptest.NewRecorder()
	wrapped.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusUnauthorized)
	}

	resp := parseAPIResponse(t, rec.Body.Bytes())
	if resp.Code != http.StatusUnauthorized {
		t.Errorf("code = %d, want %d", resp.Code, http.StatusUnauthorized)
	}
}

func TestAuthMiddleware_WrongKey(t *testing.T) {
	password := "secretpass"
	auth := api.AuthMiddleware(password)

	nextHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		models.WriteSuccess(w, map[string]string{"status": "ok"})
	})
	wrapped := auth(nextHandler)

	req := httptest.NewRequest("GET", "/api/v1/videos", nil)
	req.Header.Set("X-NASee-Key", "wrongpassword")
	rec := httptest.NewRecorder()
	wrapped.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusUnauthorized)
	}
}

func TestAuthMiddleware_CorrectKey(t *testing.T) {
	password := "secretpass"
	auth := api.AuthMiddleware(password)

	nextHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		models.WriteSuccess(w, map[string]string{"status": "ok"})
	})
	wrapped := auth(nextHandler)

	req := httptest.NewRequest("GET", "/api/v1/videos", nil)
	req.Header.Set("X-NASee-Key", password)
	rec := httptest.NewRecorder()
	wrapped.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusOK)
	}

	resp := parseAPIResponse(t, rec.Body.Bytes())
	if resp.Code != 0 {
		t.Errorf("code = %d, want 0", resp.Code)
	}
}

func TestAuthMiddleware_EmptyKey(t *testing.T) {
	password := "secretpass"
	auth := api.AuthMiddleware(password)

	nextHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		models.WriteSuccess(w, map[string]string{"status": "ok"})
	})
	wrapped := auth(nextHandler)

	req := httptest.NewRequest("GET", "/api/v1/videos", nil)
	req.Header.Set("X-NASee-Key", "")
	rec := httptest.NewRecorder()
	wrapped.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusUnauthorized)
	}
}
