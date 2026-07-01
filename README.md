# NASee

A self-hosted video streaming server with a TikTok-style vertical swipe Android client, designed for NAS (Network Attached Storage) devices.

## Features

- **Vertical swipe video player** — TikTok/Reels-style fullscreen video browsing with smooth seek
- **HTTP Range streaming** — Supports `206 Partial Content` for instant playback and seeking
- **Auto media scanning** — Uses `ffprobe` to extract metadata (duration, resolution) with incremental updates
- **Folder navigation** — Browse videos by folder with a tree view
- **Like / favorite** — Mark videos as favorites with instant toggle
- **Single-binary server** — Pure Go (no CGO), ships as one binary + SQLite database
- **FNOS / Docker ready** — One-command deployment on 飞牛 OS or any Docker host

## Architecture

```
NASee/
├── server/          # Go HTTP server (net/http + SQLite + ffprobe)
│   ├── cmd/nasee-server/   # Entry point
│   └── internal/
│       ├── api/            # HTTP handlers, middleware, router
│       ├── config/         # Environment-based configuration
│       ├── media/          # Video streaming (http.ServeContent)
│       ├── models/         # Data structures and DTOs
│       ├── scanner/        # Directory scanner + ffprobe worker
│       └── storage/        # SQLite database layer
├── android/         # Jetpack Compose Android client
└── deploy/fnos/     # Docker Compose deployment template
```

## Quick Start

### Option 1: Docker Compose (Recommended)

1. Create a `docker-compose.yml` file (see `deploy/fnos/docker-compose.yml` for template):

```yaml
version: "3.8"
services:
  nasee:
    image: ghcr.io/<account>/nasee-server:latest
    container_name: nasee
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      - NASEE_MEDIA_DIR=/media
      - NASEE_DATA_DIR=/data
      - NASEE_PASSWORD=your-password-here
    volumes:
      - /path/to/your/videos:/media:ro
      - /path/to/data:/data
```

2. Start the server:

```bash
docker compose up -d
```

3. Verify it's running:

```bash
curl http://localhost:8080/health
# {"code":0,"data":{"status":"ok"},"message":"ok"}
```

### Option 2: 飞牛 OS (fnOS) 一键部署

1. 下载 Release 附件中的 `docker-compose.fnos.yml`
2. 在飞牛 OS 的 Docker 管理器中导入/粘贴 Compose 文件
3. 修改三个必改项：
   - `NASEE_PASSWORD` — 你的访问密码
   - 媒体目录挂载路径 — 你的视频文件夹路径（`/path/to/your/videos:/media:ro`）
   - 数据目录挂载路径 — SQLite 存储路径（`/path/to/data:/data`）
4. 启动容器，App 输入 NAS 地址和密码即可连接

### Option 3: Build from Source

```bash
cd server
go build -o nasee-server ./cmd/nasee-server
NASEE_MEDIA_DIR=/path/to/videos \
NASEE_DATA_DIR=/path/to/data \
NASEE_PASSWORD=your-password \
./nasee-server
```

### Connect the Android App

1. Install the NASee APK on your Android device
2. Enter your server address (e.g., `http://192.168.1.100:8080`) and password
3. Start swiping through your video collection

## Configuration

All configuration is via environment variables:

| Variable | Default | Required | Description |
|----------|---------|----------|-------------|
| `NASEE_PORT` | `8080` | No | HTTP server port |
| `NASEE_MEDIA_DIR` | — | **Yes** | Path to your video directory |
| `NASEE_DATA_DIR` | `/data` | No | Path for SQLite database storage |
| `NASEE_PASSWORD` | — | **Yes** | Access password (sent via `X-NASee-Key` header) |
| `NASEE_SCAN_INTERVAL` | `3600` | No | Auto-scan interval in seconds |
| `NASEE_FFPROBE_PATH` | `ffprobe` | No | Path to ffprobe binary |
| `NASEE_FFPROBE_CONCURRENCY` | `2` | No | Max concurrent ffprobe processes |
| `NASEE_FFPROBE_TIMEOUT` | `30` | No | Per-file ffprobe timeout in seconds |

## API Reference

All `/api/v1/*` endpoints require the `X-NASee-Key: <password>` header.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check (no auth) |
| GET | `/api/v1/videos` | List videos (pagination, filtering, sorting) |
| GET | `/api/v1/videos/{id}/stream` | Stream video (HTTP Range) |
| GET | `/api/v1/folders` | Folder tree |
| GET | `/api/v1/videos/{id}/like` | Get like status |
| POST | `/api/v1/videos/{id}/like` | Toggle like |
| GET | `/api/v1/videos/liked` | List liked videos |
| POST | `/api/v1/scan` | Trigger manual scan |

## Supported Video Formats

`.mp4` `.mkv` `.avi` `.mov` `.ts` `.flv` `.webm` `.m4v` `.wmv`

## License

MIT

## Development

### Server (Go)

```bash
cd server
go build ./cmd/nasee-server    # Build
go test ./...                  # Run tests
go run ./cmd/nasee-server      # Run directly
```

### Android (Kotlin)

```bash
cd android
./gradlew assembleDebug        # Debug APK
./gradlew assembleRelease      # Release APK (requires keystore)
```

### Release Keystore

Generate a release keystore for signing APKs:

```bash
./scripts/generate-keystore.sh
```

For CI/CD, add these GitHub Secrets:
- `NASEE_KEYSTORE_BASE64` — base64 encoded keystore
- `NASEE_KEYSTORE_PASSWORD` — keystore password
- `NASEE_KEY_ALIAS` — key alias
- `NASEE_KEY_PASSWORD` — key password

### Publishing a Release

```bash
git tag v0.1.0
git push origin v0.1.0
```

GitHub Actions will automatically:
1. Build server Docker image and push to GHCR
2. Build signed release APK and generate checksums
3. Create a draft GitHub Release with all assets
