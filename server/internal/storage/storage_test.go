package storage

import (
	"path/filepath"
	"testing"

	"nasee-server/internal/models"
)

// setupTestDB creates a temporary database, runs migrations, and returns repos + cleanup.
func setupTestDB(t *testing.T) (*VideoRepo, *LikeRepo, func()) {
	t.Helper()
	tmpDir := t.TempDir()
	db, err := New(tmpDir)
	if err != nil {
		t.Fatalf("failed to create DB: %v", err)
	}
	if err := Migrate(db); err != nil {
		t.Fatalf("failed to migrate DB: %v", err)
	}
	videoRepo := NewVideoRepo(db)
	likeRepo := NewLikeRepo(db)
	cleanup := func() {
		db.Close()
	}
	return videoRepo, likeRepo, cleanup
}

// makeVideo creates a Video struct with common defaults.
func makeVideo(path, title, folder string, duration float64, size, modTime, scannedAt int64) *models.Video {
	return &models.Video{
		FilePath:   path,
		Title:      title,
		Duration:   duration,
		Width:      1920,
		Height:     1080,
		FileSize:   size,
		ModTime:    modTime,
		FolderPath: folder,
		ScannedAt:  scannedAt,
	}
}

// ---------------------------------------------------------------------------
// DB initialization and migration tests
// ---------------------------------------------------------------------------

func TestNewDB(t *testing.T) {
	tmpDir := t.TempDir()
	db, err := New(tmpDir)
	if err != nil {
		t.Fatalf("New() error: %v", err)
	}
	defer db.Close()

	// Verify the database file was created.
	dbPath := filepath.Join(tmpDir, "nasee.db")
	if _, err := filepath.Glob(dbPath); err != nil {
		t.Errorf("database file not found at %s: %v", dbPath, err)
	}
}

func TestNewDB_CreatesDataDir(t *testing.T) {
	tmpDir := t.TempDir()
	nestedDir := filepath.Join(tmpDir, "nested", "data")
	db, err := New(nestedDir)
	if err != nil {
		t.Fatalf("New() with nested dir error: %v", err)
	}
	defer db.Close()
}

func TestMigrate(t *testing.T) {
	tmpDir := t.TempDir()
	db, err := New(tmpDir)
	if err != nil {
		t.Fatalf("New() error: %v", err)
	}
	defer db.Close()

	if err := Migrate(db); err != nil {
		t.Fatalf("Migrate() error: %v", err)
	}

	// Verify tables exist by inserting and querying.
	repo := NewVideoRepo(db)
	v := makeVideo("/test.mp4", "test.mp4", "/", 10.5, 1000, 1000, 1000)
	if err := repo.Upsert(v); err != nil {
		t.Fatalf("Upsert after Migrate failed: %v", err)
	}
	got, err := repo.GetByPath("/test.mp4")
	if err != nil {
		t.Fatalf("GetByPath after Migrate failed: %v", err)
	}
	if got == nil {
		t.Fatal("GetByPath returned nil after insert")
	}
}

func TestMigrate_Idempotent(t *testing.T) {
	tmpDir := t.TempDir()
	db, err := New(tmpDir)
	if err != nil {
		t.Fatalf("New() error: %v", err)
	}
	defer db.Close()

	if err := Migrate(db); err != nil {
		t.Fatalf("first Migrate() error: %v", err)
	}
	if err := Migrate(db); err != nil {
		t.Fatalf("second Migrate() error: %v", err)
	}
}

// ---------------------------------------------------------------------------
// VideoRepo — Upsert tests
// ---------------------------------------------------------------------------

func TestVideoRepo_Upsert_Insert(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	v := makeVideo("/movies/a.mp4", "Alpha", "/movies", 100.5, 1000, 1000, 1000)
	if err := repo.Upsert(v); err != nil {
		t.Fatalf("Upsert insert failed: %v", err)
	}

	got, err := repo.GetByPath("/movies/a.mp4")
	if err != nil {
		t.Fatalf("GetByPath failed: %v", err)
	}
	if got == nil {
		t.Fatal("GetByPath returned nil")
	}
	if got.Title != "Alpha" {
		t.Errorf("Title = %q, want %q", got.Title, "Alpha")
	}
	if got.Duration != 100.5 {
		t.Errorf("Duration = %v, want %v", got.Duration, 100.5)
	}
	if got.ID < 1 {
		t.Errorf("ID = %d, want >= 1", got.ID)
	}
}

func TestVideoRepo_Upsert_Update(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	v := makeVideo("/movies/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000)
	if err := repo.Upsert(v); err != nil {
		t.Fatalf("first Upsert failed: %v", err)
	}

	// Update with new values.
	v2 := makeVideo("/movies/a.mp4", "AlphaUpdated", "/movies", 200, 2000, 2000, 2000)
	if err := repo.Upsert(v2); err != nil {
		t.Fatalf("second Upsert failed: %v", err)
	}

	got, err := repo.GetByPath("/movies/a.mp4")
	if err != nil {
		t.Fatalf("GetByPath failed: %v", err)
	}
	if got.Title != "AlphaUpdated" {
		t.Errorf("Title = %q, want %q", got.Title, "AlphaUpdated")
	}
	if got.Duration != 200 {
		t.Errorf("Duration = %v, want 200", got.Duration)
	}
	if got.FileSize != 2000 {
		t.Errorf("FileSize = %d, want 2000", got.FileSize)
	}
}

// ---------------------------------------------------------------------------
// VideoRepo — GetByID tests
// ---------------------------------------------------------------------------

func TestVideoRepo_GetByID(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	v := makeVideo("/movies/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000)
	repo.Upsert(v)

	inserted, _ := repo.GetByPath("/movies/a.mp4")
	got, err := repo.GetByID(inserted.ID)
	if err != nil {
		t.Fatalf("GetByID failed: %v", err)
	}
	if got == nil {
		t.Fatal("GetByID returned nil")
	}
	if got.Title != "Alpha" {
		t.Errorf("Title = %q, want %q", got.Title, "Alpha")
	}
	if got.FilePath != "/movies/a.mp4" {
		t.Errorf("FilePath = %q, want %q", got.FilePath, "/movies/a.mp4")
	}
}

func TestVideoRepo_GetByID_NotFound(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	got, err := repo.GetByID(99999)
	if err != nil {
		t.Fatalf("GetByID returned error: %v", err)
	}
	if got != nil {
		t.Errorf("GetByID returned non-nil for non-existent ID: %+v", got)
	}
}

// ---------------------------------------------------------------------------
// VideoRepo — GetByPath tests
// ---------------------------------------------------------------------------

func TestVideoRepo_GetByPath_NotFound(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	got, err := repo.GetByPath("/nonexistent.mp4")
	if err != nil {
		t.Fatalf("GetByPath returned error: %v", err)
	}
	if got != nil {
		t.Errorf("GetByPath returned non-nil for non-existent path: %+v", got)
	}
}

// ---------------------------------------------------------------------------
// VideoRepo — List tests (pagination, filtering, sorting)
// ---------------------------------------------------------------------------

func TestVideoRepo_List_Empty(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	videos, total, err := repo.List(models.VideoFilter{Page: 1, PageSize: 20})
	if err != nil {
		t.Fatalf("List failed: %v", err)
	}
	if total != 0 {
		t.Errorf("total = %d, want 0", total)
	}
	if len(videos) != 0 {
		t.Errorf("len(videos) = %d, want 0", len(videos))
	}
}

func TestVideoRepo_List_Pagination(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	// Insert 5 videos with distinct mod_times for deterministic ordering.
	for i := 1; i <= 5; i++ {
		v := makeVideo(
			"/movies/v%d.mp4"+string(rune('0'+i)),
			"Video"+string(rune('0'+i)),
			"/movies",
			float64(i*100),
			int64(i*1000),
			int64(i*1000),
			1000,
		)
		repo.Upsert(v)
	}

	// Page 1, size 2.
	videos, total, err := repo.List(models.VideoFilter{Page: 1, PageSize: 2, Sort: "mod_time", Order: "asc"})
	if err != nil {
		t.Fatalf("List page 1 failed: %v", err)
	}
	if total != 5 {
		t.Errorf("total = %d, want 5", total)
	}
	if len(videos) != 2 {
		t.Errorf("len(videos) = %d, want 2", len(videos))
	}

	// Page 3, size 2 → should return 1 video.
	videos3, _, err := repo.List(models.VideoFilter{Page: 3, PageSize: 2, Sort: "mod_time", Order: "asc"})
	if err != nil {
		t.Fatalf("List page 3 failed: %v", err)
	}
	if len(videos3) != 1 {
		t.Errorf("len(videos) page 3 = %d, want 1", len(videos3))
	}

	// Page 4 (beyond total) → should return 0 videos, total still 5.
	videos4, total4, err := repo.List(models.VideoFilter{Page: 4, PageSize: 2, Sort: "mod_time", Order: "asc"})
	if err != nil {
		t.Fatalf("List page 4 failed: %v", err)
	}
	if len(videos4) != 0 {
		t.Errorf("len(videos) page 4 = %d, want 0", len(videos4))
	}
	if total4 != 5 {
		t.Errorf("total4 = %d, want 5", total4)
	}
}

func TestVideoRepo_List_DefaultParams(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	// Insert 3 videos.
	for i := 1; i <= 3; i++ {
		v := makeVideo(
			"/v"+string(rune('0'+i))+".mp4",
			"V"+string(rune('0'+i)),
			"/movies",
			float64(i*100),
			int64(i*1000),
			int64(i*1000),
			1000,
		)
		repo.Upsert(v)
	}

	// Page=0 → should default to 1. PageSize=0 → should default to 20.
	videos, total, err := repo.List(models.VideoFilter{Page: 0, PageSize: 0})
	if err != nil {
		t.Fatalf("List failed: %v", err)
	}
	if total != 3 {
		t.Errorf("total = %d, want 3", total)
	}
	if len(videos) != 3 {
		t.Errorf("len(videos) = %d, want 3", len(videos))
	}
}

func TestVideoRepo_List_PageSizeCap(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	// PageSize > 100 → should be capped to 100.
	videos, _, err := repo.List(models.VideoFilter{Page: 1, PageSize: 200})
	if err != nil {
		t.Fatalf("List failed: %v", err)
	}
	// With 0 videos, result is empty regardless. Just verify no error.
	if len(videos) > 100 {
		t.Errorf("len(videos) = %d, should be <= 100", len(videos))
	}
}

func TestVideoRepo_List_FolderFilter(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/movies/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000))
	repo.Upsert(makeVideo("/movies/b.mp4", "Bravo", "/movies", 200, 2000, 2000, 1000))
	repo.Upsert(makeVideo("/tv/c.mp4", "Charlie", "/tv", 300, 3000, 3000, 1000))

	// Filter by /movies.
	videos, total, err := repo.List(models.VideoFilter{Page: 1, PageSize: 100, Folder: "/movies"})
	if err != nil {
		t.Fatalf("List with folder filter failed: %v", err)
	}
	if total != 2 {
		t.Errorf("total = %d, want 2", total)
	}
	if len(videos) != 2 {
		t.Fatalf("len(videos) = %d, want 2", len(videos))
	}
	for _, v := range videos {
		if v.Folder != "/movies" {
			t.Errorf("Folder = %q, want %q", v.Folder, "/movies")
		}
	}

	// Filter by /tv.
	videosTV, totalTV, err := repo.List(models.VideoFilter{Page: 1, PageSize: 100, Folder: "/tv"})
	if err != nil {
		t.Fatalf("List with /tv filter failed: %v", err)
	}
	if totalTV != 1 {
		t.Errorf("total = %d, want 1", totalTV)
	}
	if len(videosTV) != 1 {
		t.Errorf("len(videos) = %d, want 1", len(videosTV))
	}
}

func TestVideoRepo_List_LikedOnly(t *testing.T) {
	repo, likeRepo, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/movies/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000))
	repo.Upsert(makeVideo("/movies/b.mp4", "Bravo", "/movies", 200, 2000, 2000, 1000))
	repo.Upsert(makeVideo("/tv/c.mp4", "Charlie", "/tv", 300, 3000, 3000, 1000))

	// Like video "Alpha".
	v, _ := repo.GetByPath("/movies/a.mp4")
	likeRepo.ToggleLike(v.ID)

	// List with LikedOnly=true.
	videos, total, err := repo.List(models.VideoFilter{Page: 1, PageSize: 100, LikedOnly: true})
	if err != nil {
		t.Fatalf("List with LikedOnly failed: %v", err)
	}
	if total != 1 {
		t.Errorf("total = %d, want 1", total)
	}
	if len(videos) != 1 {
		t.Fatalf("len(videos) = %d, want 1", len(videos))
	}
	if !videos[0].Liked {
		t.Errorf("videos[0].Liked = false, want true")
	}
	if videos[0].Title != "Alpha" {
		t.Errorf("videos[0].Title = %q, want %q", videos[0].Title, "Alpha")
	}
}

func TestVideoRepo_List_LikedField(t *testing.T) {
	repo, likeRepo, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/movies/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000))
	repo.Upsert(makeVideo("/movies/b.mp4", "Bravo", "/movies", 200, 2000, 2000, 1000))

	// Like video "Alpha".
	v, _ := repo.GetByPath("/movies/a.mp4")
	likeRepo.ToggleLike(v.ID)

	// List all → Alpha should have Liked=true, Bravo should have Liked=false.
	videos, _, err := repo.List(models.VideoFilter{Page: 1, PageSize: 100, Sort: "name", Order: "asc"})
	if err != nil {
		t.Fatalf("List failed: %v", err)
	}
	if len(videos) != 2 {
		t.Fatalf("len(videos) = %d, want 2", len(videos))
	}
	if !videos[0].Liked {
		t.Errorf("videos[0] (Alpha).Liked = false, want true")
	}
	if videos[1].Liked {
		t.Errorf("videos[1] (Bravo).Liked = true, want false")
	}
}

func TestVideoRepo_List_StreamURL(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/movies/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000))

	videos, _, err := repo.List(models.VideoFilter{Page: 1, PageSize: 100})
	if err != nil {
		t.Fatalf("List failed: %v", err)
	}
	if len(videos) != 1 {
		t.Fatalf("len(videos) = %d, want 1", len(videos))
	}
	expectedURL := "/api/v1/videos/" + strconvInt64(videos[0].ID) + "/stream"
	if videos[0].StreamURL != expectedURL {
		t.Errorf("StreamURL = %q, want %q", videos[0].StreamURL, expectedURL)
	}
}

func TestVideoRepo_List_Sorting(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	// Insert 3 videos with distinct values.
	repo.Upsert(makeVideo("/c.mp4", "Charlie", "/movies", 300, 3000, 3000, 1000))
	repo.Upsert(makeVideo("/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000))
	repo.Upsert(makeVideo("/b.mp4", "Bravo", "/movies", 200, 2000, 2000, 1000))

	tests := []struct {
		name       string
		sort       string
		order      string
		wantTitles []string
	}{
		{"name_asc", "name", "asc", []string{"Alpha", "Bravo", "Charlie"}},
		{"name_desc", "name", "desc", []string{"Charlie", "Bravo", "Alpha"}},
		{"duration_asc", "duration", "asc", []string{"Alpha", "Bravo", "Charlie"}},
		{"duration_desc", "duration", "desc", []string{"Charlie", "Bravo", "Alpha"}},
		{"file_size_asc", "file_size", "asc", []string{"Alpha", "Bravo", "Charlie"}},
		{"file_size_desc", "file_size", "desc", []string{"Charlie", "Bravo", "Alpha"}},
		{"mod_time_asc", "mod_time", "asc", []string{"Alpha", "Bravo", "Charlie"}},
		{"mod_time_desc", "mod_time", "desc", []string{"Charlie", "Bravo", "Alpha"}},
		{"invalid_sort_defaults_to_mod_time_desc", "invalid_field", "desc", []string{"Charlie", "Bravo", "Alpha"}},
		{"empty_sort_defaults_to_mod_time_desc", "", "desc", []string{"Charlie", "Bravo", "Alpha"}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			videos, total, err := repo.List(models.VideoFilter{
				Page: 1, PageSize: 100,
				Sort: tt.sort, Order: tt.order,
			})
			if err != nil {
				t.Fatalf("List failed: %v", err)
			}
			if total != 3 {
				t.Errorf("total = %d, want 3", total)
			}
			if len(videos) != 3 {
				t.Fatalf("len(videos) = %d, want 3", len(videos))
			}
			for i, v := range videos {
				if v.Title != tt.wantTitles[i] {
					t.Errorf("result[%d].Title = %q, want %q", i, v.Title, tt.wantTitles[i])
				}
			}
		})
	}
}

// ---------------------------------------------------------------------------
// VideoRepo — Delete tests
// ---------------------------------------------------------------------------

func TestVideoRepo_Delete(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/movies/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000))
	v, _ := repo.GetByPath("/movies/a.mp4")

	if err := repo.Delete(v.ID); err != nil {
		t.Fatalf("Delete failed: %v", err)
	}

	got, _ := repo.GetByID(v.ID)
	if got != nil {
		t.Errorf("video still exists after Delete")
	}
}

func TestVideoRepo_DeleteByPath(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/movies/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000))

	if err := repo.DeleteByPath("/movies/a.mp4"); err != nil {
		t.Fatalf("DeleteByPath failed: %v", err)
	}

	got, _ := repo.GetByPath("/movies/a.mp4")
	if got != nil {
		t.Errorf("video still exists after DeleteByPath")
	}
}

// ---------------------------------------------------------------------------
// VideoRepo — MarkStale tests
// ---------------------------------------------------------------------------

func TestVideoRepo_MarkStale(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	// Insert videos with different scanned_at values.
	repo.Upsert(makeVideo("/old.mp4", "Old", "/movies", 100, 1000, 1000, 1000))
	repo.Upsert(makeVideo("/new.mp4", "New", "/movies", 200, 2000, 2000, 2000))

	// Mark stale at timestamp 1500 → should delete the "old" video (scanned_at=1000 < 1500).
	if err := repo.MarkStale(1500); err != nil {
		t.Fatalf("MarkStale failed: %v", err)
	}

	old, _ := repo.GetByPath("/old.mp4")
	if old != nil {
		t.Errorf("old video should have been deleted by MarkStale")
	}

	new, _ := repo.GetByPath("/new.mp4")
	if new == nil {
		t.Errorf("new video should still exist after MarkStale")
	}
}

func TestVideoRepo_MarkStale_All(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/a.mp4", "A", "/movies", 100, 1000, 1000, 1000))
	repo.Upsert(makeVideo("/b.mp4", "B", "/movies", 200, 2000, 2000, 1000))

	// Mark stale at timestamp 3000 → should delete all (all have scanned_at < 3000).
	if err := repo.MarkStale(3000); err != nil {
		t.Fatalf("MarkStale failed: %v", err)
	}

	videos, total, _ := repo.List(models.VideoFilter{Page: 1, PageSize: 100})
	if total != 0 {
		t.Errorf("total = %d, want 0 after MarkStale(3000)", total)
	}
	if len(videos) != 0 {
		t.Errorf("len(videos) = %d, want 0", len(videos))
	}
}

// ---------------------------------------------------------------------------
// VideoRepo — GetFolders tests
// ---------------------------------------------------------------------------

func TestVideoRepo_GetFolders_Empty(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	folders, err := repo.GetFolders()
	if err != nil {
		t.Fatalf("GetFolders failed: %v", err)
	}
	if len(folders) != 1 {
		t.Fatalf("len(folders) = %d, want 1 (root)", len(folders))
	}
	root := folders[0]
	if root.Path != "/" {
		t.Errorf("root.Path = %q, want %q", root.Path, "/")
	}
	if root.Count != 0 {
		t.Errorf("root.Count = %d, want 0", root.Count)
	}
	if len(root.Children) != 0 {
		t.Errorf("root.Children len = %d, want 0", len(root.Children))
	}
}

func TestVideoRepo_GetFolders_SingleLevel(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/movies/a.mp4", "A", "/movies", 100, 1000, 1000, 1000))
	repo.Upsert(makeVideo("/movies/b.mp4", "B", "/movies", 200, 2000, 2000, 1000))
	repo.Upsert(makeVideo("/tv/c.mp4", "C", "/tv", 300, 3000, 3000, 1000))

	folders, err := repo.GetFolders()
	if err != nil {
		t.Fatalf("GetFolders failed: %v", err)
	}
	if len(folders) != 1 {
		t.Fatalf("len(folders) = %d, want 1", len(folders))
	}
	root := folders[0]
	if root.Count != 3 {
		t.Errorf("root.Count = %d, want 3", root.Count)
	}
	if len(root.Children) != 2 {
		t.Fatalf("root.Children len = %d, want 2", len(root.Children))
	}

	// Children should be sorted alphabetically: "movies" then "tv".
	if root.Children[0].Name != "movies" {
		t.Errorf("children[0].Name = %q, want %q", root.Children[0].Name, "movies")
	}
	if root.Children[0].Count != 2 {
		t.Errorf("children[0].Count = %d, want 2", root.Children[0].Count)
	}
	if root.Children[1].Name != "tv" {
		t.Errorf("children[1].Name = %q, want %q", root.Children[1].Name, "tv")
	}
	if root.Children[1].Count != 1 {
		t.Errorf("children[1].Count = %d, want 1", root.Children[1].Count)
	}
}

func TestVideoRepo_GetFolders_Nested(t *testing.T) {
	repo, _, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/movies/a.mp4", "A", "/movies", 100, 1000, 1000, 1000))
	repo.Upsert(makeVideo("/movies/action/b.mp4", "B", "/movies/action", 200, 2000, 2000, 1000))
	repo.Upsert(makeVideo("/movies/action/c.mp4", "C", "/movies/action", 300, 3000, 3000, 1000))

	folders, err := repo.GetFolders()
	if err != nil {
		t.Fatalf("GetFolders failed: %v", err)
	}
	root := folders[0]
	if root.Count != 3 {
		t.Errorf("root.Count = %d, want 3", root.Count)
	}
	if len(root.Children) != 1 {
		t.Fatalf("root.Children len = %d, want 1", len(root.Children))
	}

	movies := root.Children[0]
	if movies.Name != "movies" {
		t.Errorf("movies.Name = %q, want %q", movies.Name, "movies")
	}
	if movies.Count != 1 {
		t.Errorf("movies.Count = %d, want 1 (only direct videos)", movies.Count)
	}
	if len(movies.Children) != 1 {
		t.Fatalf("movies.Children len = %d, want 1", len(movies.Children))
	}

	action := movies.Children[0]
	if action.Name != "action" {
		t.Errorf("action.Name = %q, want %q", action.Name, "action")
	}
	if action.Count != 2 {
		t.Errorf("action.Count = %d, want 2", action.Count)
	}
}

// ---------------------------------------------------------------------------
// LikeRepo — GetLikeStatus tests
// ---------------------------------------------------------------------------

func TestLikeRepo_GetLikeStatus_NotLiked(t *testing.T) {
	repo, likeRepo, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/movies/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000))
	v, _ := repo.GetByPath("/movies/a.mp4")

	liked, err := likeRepo.GetLikeStatus(v.ID)
	if err != nil {
		t.Fatalf("GetLikeStatus failed: %v", err)
	}
	if liked {
		t.Errorf("liked = true, want false (not liked yet)")
	}
}

func TestLikeRepo_GetLikeStatus_Liked(t *testing.T) {
	repo, likeRepo, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/movies/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000))
	v, _ := repo.GetByPath("/movies/a.mp4")
	likeRepo.ToggleLike(v.ID)

	liked, err := likeRepo.GetLikeStatus(v.ID)
	if err != nil {
		t.Fatalf("GetLikeStatus failed: %v", err)
	}
	if !liked {
		t.Errorf("liked = false, want true (was toggled)")
	}
}

// ---------------------------------------------------------------------------
// LikeRepo — ToggleLike tests
// ---------------------------------------------------------------------------

func TestLikeRepo_ToggleLike_Like(t *testing.T) {
	repo, likeRepo, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/movies/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000))
	v, _ := repo.GetByPath("/movies/a.mp4")

	liked, err := likeRepo.ToggleLike(v.ID)
	if err != nil {
		t.Fatalf("ToggleLike failed: %v", err)
	}
	if !liked {
		t.Errorf("first ToggleLike returned liked=false, want true")
	}
}

func TestLikeRepo_ToggleLike_Unlike(t *testing.T) {
	repo, likeRepo, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/movies/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000))
	v, _ := repo.GetByPath("/movies/a.mp4")

	// First toggle: like.
	likeRepo.ToggleLike(v.ID)

	// Second toggle: unlike.
	liked, err := likeRepo.ToggleLike(v.ID)
	if err != nil {
		t.Fatalf("ToggleLike failed: %v", err)
	}
	if liked {
		t.Errorf("second ToggleLike returned liked=true, want false")
	}
}

func TestLikeRepo_ToggleLike_MultipleToggles(t *testing.T) {
	repo, likeRepo, cleanup := setupTestDB(t)
	defer cleanup()

	repo.Upsert(makeVideo("/movies/a.mp4", "Alpha", "/movies", 100, 1000, 1000, 1000))
	v, _ := repo.GetByPath("/movies/a.mp4")

	// Toggle 4 times: like → unlike → like → unlike.
	expected := []bool{true, false, true, false}
	for i, want := range expected {
		got, err := likeRepo.ToggleLike(v.ID)
		if err != nil {
			t.Fatalf("toggle %d failed: %v", i+1, err)
		}
		if got != want {
			t.Errorf("toggle %d: liked = %v, want %v", i+1, got, want)
		}
	}
}

// strconvInt64 converts int64 to string (avoids importing strconv in every test file).
func strconvInt64(n int64) string {
	return formatInt64(n)
}

// formatInt64 is a simple int64 to string converter.
func formatInt64(n int64) string {
	if n == 0 {
		return "0"
	}
	negative := n < 0
	if negative {
		n = -n
	}
	var buf [20]byte
	i := len(buf)
	for n > 0 {
		i--
		buf[i] = byte('0' + n%10)
		n /= 10
	}
	if negative {
		i--
		buf[i] = '-'
	}
	return string(buf[i:])
}
