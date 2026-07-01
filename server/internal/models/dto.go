package models

import (
	"encoding/json"
	"net/http"
)

// ApiResponse is the standard envelope for all API responses.
type ApiResponse struct {
	Code    int         `json:"code"`
	Data    interface{} `json:"data"`
	Message string      `json:"message"`
}

// VideoDTO is the video representation returned by the API.
type VideoDTO struct {
	ID        int64   `json:"id"`
	Title     string  `json:"title"`
	Duration  float64 `json:"duration"`
	Size      int64   `json:"size"`
	Path      string  `json:"path"`
	Folder    string  `json:"folder"`
	Width     int     `json:"width"`
	Height    int     `json:"height"`
	Liked     bool    `json:"liked"`
	StreamURL string  `json:"stream_url"`
	ModTime   int64   `json:"mod_time"`
}

// VideoListResponse is the paginated video list response payload.
type VideoListResponse struct {
	Videos   []VideoDTO `json:"videos"`
	Total    int        `json:"total"`
	Page     int        `json:"page"`
	PageSize int        `json:"page_size"`
}

// FolderNode represents a node in the folder tree.
type FolderNode struct {
	Path     string       `json:"path"`
	Name     string       `json:"name"`
	Count    int          `json:"count"`
	Children []FolderNode `json:"children"`
}

// FolderListResponse is the folder tree response payload.
type FolderListResponse struct {
	Folders []FolderNode `json:"folders"`
}

// ScanResponse is the response for the scan trigger endpoint.
type ScanResponse struct {
	Status string `json:"status"`
}

// WriteJSON encodes data as JSON and writes it with the given HTTP status.
func WriteJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

// WriteSuccess writes a standard success response (code 0, message "ok").
func WriteSuccess(w http.ResponseWriter, data interface{}) {
	WriteJSON(w, http.StatusOK, ApiResponse{Code: 0, Data: data, Message: "ok"})
}

// WriteError writes a standard error response with the given status and message.
func WriteError(w http.ResponseWriter, status int, message string) {
	WriteJSON(w, status, ApiResponse{Code: status, Data: nil, Message: message})
}
