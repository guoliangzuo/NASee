#!/bin/bash
# =============================================================================
# NASee — Generate Android release keystore
# =============================================================================
# Usage: ./scripts/generate-keystore.sh
#
# Generates a release keystore for signing the NASee Android APK.
# The keystore is saved to android/keystore/nasee-release.jks
# This file must NOT be committed to git (already in .gitignore).
#
# For CI/CD, encode the keystore as base64 and store as GitHub Secret:
#   NASEE_KEYSTORE_BASE64
#   NASEE_KEYSTORE_PASSWORD
#   NASEE_KEY_ALIAS
#   NASEE_KEY_PASSWORD
# =============================================================================

set -e

KEYSTORE_DIR="android/keystore"
KEYSTORE_FILE="$KEYSTORE_DIR/nasee-release.jks"
KEY_ALIAS="nasee"
KEYSTORE_PASSWORD="nasee123"
KEY_PASSWORD="nasee123"
VALIDITY=10000  # ~27 years

# Navigate to project root
cd "$(dirname "$0")/.."

mkdir -p "$KEYSTORE_DIR"

if [ -f "$KEYSTORE_FILE" ]; then
    echo "⚠️  Keystore already exists: $KEYSTORE_FILE"
    echo "   To regenerate, delete it first: rm $KEYSTORE_FILE"
    exit 1
fi

echo "Generating release keystore..."
echo "  File: $KEYSTORE_FILE"
echo "  Alias: $KEY_ALIAS"
echo "  Validity: $VALIDITY days"
echo ""

# Check if keytool is available
if ! command -v keytool &> /dev/null; then
    echo "❌ keytool not found. Please install JDK first:"
    echo "   brew install openjdk"
    echo "   or download from https://adoptium.net/"
    exit 1
fi

keytool -genkeypair \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity "$VALIDITY" \
    -keystore "$KEYSTORE_FILE" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=NASee, OU=NASee, O=NASee, L=NA, ST=NA, C=CN"

echo ""
echo "✅ Keystore generated successfully!"
echo ""
echo "To use for CI/CD, encode as base64:"
echo "  base64 -i $KEYSTORE_FILE | pbcopy  # macOS"
echo "  base64 -w 0 $KEYSTORE_FILE         # Linux"
echo ""
echo "Then add these GitHub Secrets to your repository:"
echo "  NASEE_KEYSTORE_BASE64    — base64 encoded keystore"
echo "  NASEE_KEYSTORE_PASSWORD  — $KEYSTORE_PASSWORD"
echo "  NASEE_KEY_ALIAS          — $KEY_ALIAS"
echo "  NASEE_KEY_PASSWORD       — $KEY_PASSWORD"
echo ""
echo "⚠️  Keep this keystore safe! You need the same keystore for all future updates."
