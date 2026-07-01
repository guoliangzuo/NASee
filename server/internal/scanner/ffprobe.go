package scanner

import (
	"context"
	"encoding/json"
	"fmt"
	"os/exec"
	"strconv"

	"nasee-server/internal/models"
)

// FFProbe wraps the external ffprobe binary for extracting video metadata.
type FFProbe struct {
	path string
}

// ffprobeOutput mirrors the JSON structure produced by ffprobe.
type ffprobeOutput struct {
	Format struct {
		Duration string `json:"duration"`
	} `json:"format"`
	Streams []struct {
		CodecType string `json:"codec_type"`
		Width     int    `json:"width"`
		Height    int    `json:"height"`
	} `json:"streams"`
}

// NewFFProbe creates a new FFProbe wrapper using the given binary path.
func NewFFProbe(path string) *FFProbe {
	return &FFProbe{path: path}
}

// Probe executes ffprobe on the given file and returns parsed metadata.
// The context controls timeout and cancellation.
func (f *FFProbe) Probe(ctx context.Context, filePath string) (*models.VideoMeta, error) {
	cmd := exec.CommandContext(ctx, f.path,
		"-v", "quiet",
		"-print_format", "json",
		"-show_format",
		"-show_streams",
		filePath,
	)

	output, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("ffprobe command failed for %q: %w", filePath, err)
	}

	var result ffprobeOutput
	if err := json.Unmarshal(output, &result); err != nil {
		return nil, fmt.Errorf("parse ffprobe output for %q: %w", filePath, err)
	}

	meta := &models.VideoMeta{}

	// Parse duration string (e.g. "120.500000") to float64.
	if result.Format.Duration != "" {
		meta.Duration, _ = strconv.ParseFloat(result.Format.Duration, 64)
	}

	// Find the first video stream to extract dimensions.
	for _, stream := range result.Streams {
		if stream.CodecType == "video" {
			meta.Width = stream.Width
			meta.Height = stream.Height
			break
		}
	}

	return meta, nil
}
