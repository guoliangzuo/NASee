package config

import (
	"fmt"
	"os"
	"strconv"
)

// Config holds all application configuration values.
type Config struct {
	Port               int
	MediaDir           string
	DataDir            string
	Password           string
	ScanInterval       int
	FFProbePath        string
	FFProbeConcurrency int
	FFProbeTimeout     int
}

// Load reads configuration from environment variables with sensible defaults.
// Returns an error if required variables (NASEE_MEDIA_DIR, NASEE_PASSWORD) are missing.
func Load() (*Config, error) {
	cfg := &Config{
		Port:               getEnvInt("NASEE_PORT", 8080),
		MediaDir:           getEnv("NASEE_MEDIA_DIR", ""),
		DataDir:            getEnv("NASEE_DATA_DIR", "/data"),
		Password:           getEnv("NASEE_PASSWORD", ""),
		ScanInterval:       getEnvInt("NASEE_SCAN_INTERVAL", 3600),
		FFProbePath:        getEnv("NASEE_FFPROBE_PATH", "ffprobe"),
		FFProbeConcurrency: getEnvInt("NASEE_FFPROBE_CONCURRENCY", 2),
		FFProbeTimeout:     getEnvInt("NASEE_FFPROBE_TIMEOUT", 30),
	}

	if cfg.MediaDir == "" {
		return nil, fmt.Errorf("NASEE_MEDIA_DIR environment variable is required")
	}
	if cfg.Password == "" {
		return nil, fmt.Errorf("NASEE_PASSWORD environment variable is required")
	}

	return cfg, nil
}

// getEnv reads a string environment variable, returning the default if unset.
func getEnv(key, defaultVal string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return defaultVal
}

// getEnvInt reads an integer environment variable, returning the default if unset or invalid.
func getEnvInt(key string, defaultVal int) int {
	if val := os.Getenv(key); val != "" {
		if n, err := strconv.Atoi(val); err == nil {
			return n
		}
	}
	return defaultVal
}
