package storage

import (
	"database/sql"
	"fmt"
)

// LikeRepo provides CRUD operations for like records.
type LikeRepo struct {
	db *DB
}

// NewLikeRepo creates a new LikeRepo bound to the given database.
func NewLikeRepo(db *DB) *LikeRepo {
	return &LikeRepo{db: db}
}

// GetLikeStatus returns true if the given video is liked, false otherwise.
func (r *LikeRepo) GetLikeStatus(videoID int64) (bool, error) {
	var id int64
	err := r.db.QueryRow("SELECT id FROM likes WHERE video_id = ?", videoID).Scan(&id)
	if err == sql.ErrNoRows {
		return false, nil
	}
	if err != nil {
		return false, fmt.Errorf("get like status: %w", err)
	}
	return true, nil
}

// ToggleLike flips the like state for the given video.
// If the video is currently liked, it unlikes it; otherwise it likes it.
func (r *LikeRepo) ToggleLike(videoID int64) (bool, error) {
	liked, err := r.GetLikeStatus(videoID)
	if err != nil {
		return false, err
	}

	if liked {
		if _, err := r.db.Exec("DELETE FROM likes WHERE video_id = ?", videoID); err != nil {
			return false, fmt.Errorf("delete like: %w", err)
		}
		return false, nil
	}

	if _, err := r.db.Exec("INSERT INTO likes (video_id) VALUES (?)", videoID); err != nil {
		return false, fmt.Errorf("insert like: %w", err)
	}
	return true, nil
}
