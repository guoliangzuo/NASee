package models

// LikeResponse is the API response for like-related operations.
type LikeResponse struct {
	VideoID int64 `json:"video_id"`
	Liked   bool  `json:"liked"`
}
