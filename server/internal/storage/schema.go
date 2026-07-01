package storage

// schemaSQL contains the DDL statements for creating all tables and indexes.
const schemaSQL = `
CREATE TABLE IF NOT EXISTS videos (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    file_path   TEXT    NOT NULL UNIQUE,
    title       TEXT    NOT NULL,
    duration    REAL    NOT NULL DEFAULT 0,
    width       INTEGER NOT NULL DEFAULT 0,
    height      INTEGER NOT NULL DEFAULT 0,
    file_size   INTEGER NOT NULL DEFAULT 0,
    mod_time    INTEGER NOT NULL DEFAULT 0,
    folder_path TEXT    NOT NULL DEFAULT '',
    scanned_at  INTEGER NOT NULL DEFAULT 0,
    created_at  INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_videos_folder ON videos(folder_path);
CREATE INDEX IF NOT EXISTS idx_videos_mod_time ON videos(mod_time);

CREATE TABLE IF NOT EXISTS likes (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    video_id  INTEGER NOT NULL UNIQUE,
    liked_at  INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
    FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_likes_video_id ON likes(video_id);
`

// Migrate executes the schema DDL to create tables and indexes if they do not exist.
func Migrate(db *DB) error {
	_, err := db.Exec(schemaSQL)
	if err != nil {
		return err
	}
	return nil
}
