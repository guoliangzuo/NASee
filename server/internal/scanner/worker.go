package scanner

import (
	"context"
	"fmt"
	"time"

	"nasee-server/internal/models"
)

// Worker limits concurrent ffprobe invocations using a counting semaphore
// and enforces a per-file timeout.
type Worker struct {
	ffprobe *FFProbe
	sem     chan struct{}
	timeout time.Duration
}

// NewWorker creates a new Worker with the given concurrency limit and timeout.
func NewWorker(ffprobe *FFProbe, concurrency int, timeoutSecs int) *Worker {
	if concurrency < 1 {
		concurrency = 1
	}
	return &Worker{
		ffprobe: ffprobe,
		sem:     make(chan struct{}, concurrency),
		timeout: time.Duration(timeoutSecs) * time.Second,
	}
}

// Probe acquires a concurrency slot, calls ffprobe with a timeout, and returns metadata.
// The outer context may also cancel the operation.
func (w *Worker) Probe(ctx context.Context, filePath string) (*models.VideoMeta, error) {
	// Acquire semaphore slot (blocks if at capacity).
	select {
	case w.sem <- struct{}{}:
		defer func() { <-w.sem }()
	case <-ctx.Done():
		return nil, fmt.Errorf("waiting for probe slot: %w", ctx.Err())
	}

	// Create a child context with the per-file timeout.
	probeCtx, cancel := context.WithTimeout(ctx, w.timeout)
	defer cancel()

	return w.ffprobe.Probe(probeCtx, filePath)
}
