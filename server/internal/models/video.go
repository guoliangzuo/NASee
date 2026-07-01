package models

// Video represents a video file record stored in the database.
type Video struct {
	ID         int64   `json:"id"`
	FilePath   string  `json:"file_path"`
	Title      string  `json:"title"`
	Duration   float64 `json:"duration"`
	Width      int     `json:"width"`
	Height     int     `json:"height"`
	FileSize   int64   `json:"file_size"`
	ModTime    int64   `json:"mod_time"`
	FolderPath string  `json:"folder_path"`
	ScannedAt  int64   `json:"scanned_at"`
	CreatedAt  int64   `json:"created_at"`
}

// VideoMeta holds metadata extracted by ffprobe.
type VideoMeta struct {
	Duration float64
	Width    int
	Height   int
}

// VideoFilter encapsulates query parameters for listing videos.
type VideoFilter struct {
	Page      int
	PageSize  int
	Folder    string
	Sort      string
	Order     string
	LikedOnly bool
}
