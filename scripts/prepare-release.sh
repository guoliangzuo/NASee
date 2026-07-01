#!/bin/bash
# =============================================================================
# NASee — Prepare GitHub Release assets
# =============================================================================
# This script is run locally to prepare release assets before publishing.
# The actual build is done by GitHub Actions (see .github/workflows/release.yml).
#
# Usage: ./scripts/prepare-release.sh <version>
# Example: ./scripts/prepare-release.sh v0.1.0
# =============================================================================

set -e

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
    echo "❌ Usage: $0 <version>"
    echo "   Example: $0 v0.1.0"
    exit 1
fi

cd "$(dirname "$0")/.."

echo "=========================================="
echo "  NASee Release Preparation: $VERSION"
echo "=========================================="
echo ""

# Step 1: Verify server builds
echo "📦 Step 1: Verify server build..."
export PATH="/Users/guoliangzuo/.local/go/bin:$PATH"
export GOCACHE="/tmp/go-cache"
export GOMODCACHE="/tmp/go-modcache"
cd server
go build ./cmd/nasee-server && echo "✅ Server build OK" || { echo "❌ Server build failed"; exit 1; }
cd ..

# Step 2: Run server tests
echo ""
echo "🧪 Step 2: Run server tests..."
cd server
go test ./... -count=1 && echo "✅ Tests passed" || { echo "❌ Tests failed"; exit 1; }
cd ..

# Step 3: Prepare release directory
echo ""
echo "📁 Step 3: Prepare release assets..."
RELEASE_DIR="release/$VERSION"
mkdir -p "$RELEASE_DIR"

# Copy docker-compose template
cp deploy/fnos/docker-compose.yml "$RELEASE_DIR/docker-compose.fnos.yml"

# Step 4: Generate checksums for existing files
echo ""
echo "🔐 Step 4: Generate checksums..."
cd "$RELEASE_DIR"
if ls *.apk 1> /dev/null 2>&1; then
    shasum -a 256 *.apk > checksums.txt
    echo "✅ APK checksums generated"
fi
if [ -f docker-compose.fnos.yml ]; then
    shasum -a 256 docker-compose.fnos.yml >> checksums.txt
    echo "✅ Compose checksum generated"
fi
cd ../..

echo ""
echo "=========================================="
echo "  ✅ Release preparation complete!"
echo "=========================================="
echo ""
echo "Release assets in: $RELEASE_DIR/"
ls -la "$RELEASE_DIR/"
echo ""
echo "Next steps:"
echo "  1. Tag the release: git tag $VERSION && git push origin $VERSION"
echo "  2. GitHub Actions will build APK + Docker image automatically"
echo "  3. A draft Release will be created — review and publish it"
echo ""
echo "Required GitHub Secrets for CI:"
echo "  NASEE_KEYSTORE_BASE64    — base64 encoded keystore"
echo "  NASEE_KEYSTORE_PASSWORD  — keystore password"
echo "  NASEE_KEY_ALIAS          — key alias (default: nasee)"
echo "  NASEE_KEY_PASSWORD       — key password"
