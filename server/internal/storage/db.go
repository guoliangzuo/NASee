package storage

import (
	"database/sql"
	"fmt"
	"os"
	"path/filepath"

	_ "modernc.org/sqlite" // Pure-Go SQLite driver, registers as "sqlite"
)

// DB wraps a sql.DB connection to SQLite.
type DB struct {
	*sql.DB
}

// New opens a SQLite database at <dataDir>/nasee.db with WAL mode enabled.
// The data directory is created if it does not exist.
func New(dataDir string) (*DB, error) {
	if err := os.MkdirAll(dataDir, 0755); err != nil {
		return nil, fmt.Errorf("create data directory %q: %w", dataDir, err)
	}

	dbPath := filepath.Join(dataDir, "nasee.db")
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		return nil, fmt.Errorf("open database %q: %w", dbPath, err)
	}

	// Enable WAL mode and set busy timeout for better concurrent access.
	pragmas := []string{
		"PRAGMA journal_mode=WAL",
		"PRAGMA busy_timeout=5000",
		"PRAGMA foreign_keys=ON",
	}
	for _, p := range pragmas {
		if _, err := db.Exec(p); err != nil {
			db.Close()
			return nil, fmt.Errorf("exec pragma %q: %w", p, err)
		}
	}

	return &DB{db}, nil
}

// Close closes the underlying database connection.
func (db *DB) Close() error {
	return db.DB.Close()
}
