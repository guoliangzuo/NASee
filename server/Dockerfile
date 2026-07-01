# =============================================================================
# Build stage — compile the Go binary with CGO disabled for static linking.
# =============================================================================
FROM golang:1.22-alpine AS builder

WORKDIR /build

# Copy module definition files first to leverage Docker layer caching.
COPY go.mod go.sum ./
RUN go mod download

# Copy the rest of the source code.
COPY . .

# Build a statically-linked binary (no CGO required for modernc.org/sqlite).
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o nasee-server ./cmd/nasee-server

# =============================================================================
# Runtime stage — minimal Alpine image with ffmpeg for ffprobe.
# =============================================================================
FROM alpine:3.20

# Install ffmpeg (provides ffprobe) and CA certificates for HTTPS.
RUN apk add --no-cache ffmpeg ca-certificates tzdata

WORKDIR /app

# Copy the compiled binary from the builder stage.
COPY --from=builder /build/nasee-server .

# Create default directories for media and data.
RUN mkdir -p /media /data

EXPOSE 8080

# Health check polls the /health endpoint.
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/health || exit 1

ENTRYPOINT ["./nasee-server"]
