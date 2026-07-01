package storage

import (
	"database/sql"
	"fmt"
	"sort"
	"strings"

	"nasee-server/internal/models"
)

// VideoRepo provides CRUD operations for video records.
type VideoRepo struct {
	db *DB
}

// NewVideoRepo creates a new VideoRepo bound to the given database.
func NewVideoRepo(db *DB) *VideoRepo {
	return &VideoRepo{db: db}
}

// sortFields maps API sort parameter values to actual column names.
var sortFields = map[string]string{
	"name":      "title",
	"mod_time":  "mod_time",
	"file_size": "file_size",
	"duration":  "duration",
}

// List returns a paginated, filtered, and sorted list of videos with like status.
func (r *VideoRepo) List(filter models.VideoFilter) ([]models.VideoDTO, int, error) {
	// Normalize pagination parameters.
	if filter.Page < 1 {
		filter.Page = 1
	}
	if filter.PageSize < 1 {
		filter.PageSize = 20
	}
	if filter.PageSize > 100 {
		filter.PageSize = 100
	}

	// Validate sort field against whitelist.
	sortCol, ok := sortFields[filter.Sort]
	if !ok {
		sortCol = "mod_time"
	}
	order := "DESC"
	if strings.ToLower(filter.Order) == "asc" {
		order = "ASC"
	}

	// Build WHERE clause dynamically.
	where := "WHERE 1=1"
	args := []interface{}{}
	if filter.Folder != "" {
		where += " AND v.folder_path = ?"
		args = append(args, filter.Folder)
	}
	if filter.LikedOnly {
		where += " AND l.id IS NOT NULL"
	}

	// Count total matching records.
	var total int
	countQuery := fmt.Sprintf(
		"SELECT COUNT(*) FROM videos v LEFT JOIN likes l ON v.id = l.video_id %s",
		where,
	)
	if err := r.db.QueryRow(countQuery, args...).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("count videos: %w", err)
	}

	// Query videos with pagination.
	offset := (filter.Page - 1) * filter.PageSize
	query := fmt.Sprintf(`
		SELECT v.id, v.title, v.duration, v.file_size, v.file_path, v.folder_path,
		       v.width, v.height, v.mod_time,
		       CASE WHEN l.id IS NOT NULL THEN 1 ELSE 0 END AS liked
		FROM videos v
		LEFT JOIN likes l ON v.id = l.video_id
		%s
		ORDER BY v.%s %s
		LIMIT ? OFFSET ?`,
		where, sortCol, order,
	)
	args = append(args, filter.PageSize, offset)

	rows, err := r.db.Query(query, args...)
	if err != nil {
		return nil, 0, fmt.Errorf("query videos: %w", err)
	}
	defer rows.Close()

	videos := make([]models.VideoDTO, 0, filter.PageSize)
	for rows.Next() {
		var v models.VideoDTO
		var liked int
		if err := rows.Scan(
			&v.ID, &v.Title, &v.Duration, &v.Size, &v.Path, &v.Folder,
			&v.Width, &v.Height, &v.ModTime, &liked,
		); err != nil {
			return nil, 0, fmt.Errorf("scan video row: %w", err)
		}
		v.Liked = liked == 1
		v.StreamURL = fmt.Sprintf("/api/v1/videos/%d/stream", v.ID)
		videos = append(videos, v)
	}

	if err := rows.Err(); err != nil {
		return nil, 0, fmt.Errorf("iterate video rows: %w", err)
	}

	return videos, total, nil
}

// GetByID retrieves a single video by its primary key.
func (r *VideoRepo) GetByID(id int64) (*models.Video, error) {
	row := r.db.QueryRow(`
		SELECT id, file_path, title, duration, width, height,
		       file_size, mod_time, folder_path, scanned_at, created_at
		FROM videos WHERE id = ?`, id)

	var v models.Video
	if err := row.Scan(
		&v.ID, &v.FilePath, &v.Title, &v.Duration, &v.Width, &v.Height,
		&v.FileSize, &v.ModTime, &v.FolderPath, &v.ScannedAt, &v.CreatedAt,
	); err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("get video by id: %w", err)
	}
	return &v, nil
}

// GetByPath retrieves a single video by its file path.
func (r *VideoRepo) GetByPath(path string) (*models.Video, error) {
	row := r.db.QueryRow(`
		SELECT id, file_path, title, duration, width, height,
		       file_size, mod_time, folder_path, scanned_at, created_at
		FROM videos WHERE file_path = ?`, path)

	var v models.Video
	if err := row.Scan(
		&v.ID, &v.FilePath, &v.Title, &v.Duration, &v.Width, &v.Height,
		&v.FileSize, &v.ModTime, &v.FolderPath, &v.ScannedAt, &v.CreatedAt,
	); err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("get video by path: %w", err)
	}
	return &v, nil
}

// Upsert inserts a new video or updates an existing one (matched by file_path).
// The primary key and created_at are preserved on update.
func (r *VideoRepo) Upsert(v *models.Video) error {
	_, err := r.db.Exec(`
		INSERT INTO videos (file_path, title, duration, width, height,
		                    file_size, mod_time, folder_path, scanned_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(file_path) DO UPDATE SET
			title      = excluded.title,
			duration   = excluded.duration,
			width      = excluded.width,
			height     = excluded.height,
			file_size  = excluded.file_size,
			mod_time   = excluded.mod_time,
			folder_path = excluded.folder_path,
			scanned_at = excluded.scanned_at`,
		v.FilePath, v.Title, v.Duration, v.Width, v.Height,
		v.FileSize, v.ModTime, v.FolderPath, v.ScannedAt,
	)
	if err != nil {
		return fmt.Errorf("upsert video: %w", err)
	}
	return nil
}

// Delete removes a video record by its primary key.
func (r *VideoRepo) Delete(id int64) error {
	_, err := r.db.Exec("DELETE FROM videos WHERE id = ?", id)
	if err != nil {
		return fmt.Errorf("delete video: %w", err)
	}
	return nil
}

// DeleteByPath removes a video record by its file path.
func (r *VideoRepo) DeleteByPath(path string) error {
	_, err := r.db.Exec("DELETE FROM videos WHERE file_path = ?", path)
	if err != nil {
		return fmt.Errorf("delete video by path: %w", err)
	}
	return nil
}

// MarkStale deletes all video records whose scanned_at is older than the given timestamp.
// This is used to remove entries for files that no longer exist on disk.
func (r *VideoRepo) MarkStale(scannedAt int64) error {
	_, err := r.db.Exec("DELETE FROM videos WHERE scanned_at < ?", scannedAt)
	if err != nil {
		return fmt.Errorf("mark stale videos: %w", err)
	}
	return nil
}

// GetFolders returns the folder tree built from all distinct folder_path values in the database.
// The root node "/" has the total video count; each subfolder's count is the number of videos
// directly in that folder (not including subfolders).
func (r *VideoRepo) GetFolders() ([]models.FolderNode, error) {
	rows, err := r.db.Query("SELECT folder_path, COUNT(*) FROM videos GROUP BY folder_path")
	if err != nil {
		return nil, fmt.Errorf("query folders: %w", err)
	}
	defer rows.Close()

	folderCounts := make(map[string]int)
	total := 0
	for rows.Next() {
		var path string
		var count int
		if err := rows.Scan(&path, &count); err != nil {
			return nil, fmt.Errorf("scan folder row: %w", err)
		}
		folderCounts[path] = count
		total += count
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate folder rows: %w", err)
	}

	return buildFolderTree(folderCounts, total), nil
}

// buildFolderTree constructs a nested FolderNode tree from a flat map of folder paths to counts.
func buildFolderTree(folderCounts map[string]int, total int) []models.FolderNode {
	type tempNode struct {
		path     string
		name     string
		count    int
		children map[string]*tempNode
	}

	root := &tempNode{
		path:     "/",
		name:     "全部视频",
		count:    total,
		children: make(map[string]*tempNode),
	}
	allNodes := map[string]*tempNode{"/": root}

	// Sort paths for deterministic tree construction.
	paths := make([]string, 0, len(folderCounts))
	for p := range folderCounts {
		paths = append(paths, p)
	}
	sort.Strings(paths)

	for _, p := range paths {
		// Split the folder path into segments. e.g. "/movies/action" -> ["movies", "action"]
		trimmed := strings.Trim(p, "/")
		if trimmed == "" {
			continue
		}
		parts := strings.Split(trimmed, "/")
		currentPath := ""
		parent := root

		for _, part := range parts {
			currentPath = currentPath + "/" + part
			node, exists := allNodes[currentPath]
			if !exists {
				node = &tempNode{
					path:     currentPath,
					name:     part,
					children: make(map[string]*tempNode),
				}
				allNodes[currentPath] = node
				parent.children[part] = node
			}
			parent = node
		}
		allNodes[p].count = folderCounts[p]
	}

	// Recursively convert the temp tree into FolderNode values.
	var convert func(n *tempNode) models.FolderNode
	convert = func(n *tempNode) models.FolderNode {
		childNames := make([]string, 0, len(n.children))
		for name := range n.children {
			childNames = append(childNames, name)
		}
		sort.Strings(childNames)

		children := make([]models.FolderNode, 0, len(childNames))
		for _, name := range childNames {
			children = append(children, convert(n.children[name]))
		}
		return models.FolderNode{
			Path:     n.path,
			Name:     n.name,
			Count:    n.count,
			Children: children,
		}
	}

	return []models.FolderNode{convert(root)}
}
