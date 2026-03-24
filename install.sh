#!/usr/bin/env bash
set -euo pipefail

# sing CLI installer — downloads from GitHub Releases (no authentication required).
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/singlr-ai/sing/main/install.sh | bash
#   bash install.sh 0.9.2          # install specific version
#   bash install.sh v0.9.2         # also works with v prefix

GITHUB_REPO="singlr-ai/sing"
INSTALL_DIR="/usr/local/bin"
BINARY="sing"

# --- Colors (disabled if not a TTY) ---
if [ -t 1 ]; then
  BOLD='\033[1m'
  GREEN='\033[0;32m'
  RED='\033[0;31m'
  RESET='\033[0m'
else
  BOLD='' GREEN='' RED='' RESET=''
fi

info()  { echo -e "${BOLD}$1${RESET}"; }
ok()    { echo -e "${GREEN}$1${RESET}"; }
fail()  { echo -e "${RED}$1${RESET}" >&2; exit 1; }

# --- Platform check ---
OS="$(uname -s)"
ARCH="$(uname -m)"

[ "$OS" = "Linux" ] || fail "sing only supports Linux. Detected: $OS"
[ "$ARCH" = "x86_64" ] || fail "sing only supports x86_64 (amd64). Detected: $ARCH"

# --- Resolve version ---
VERSION="${1:-latest}"

if [ "$VERSION" = "latest" ]; then
  info "Fetching latest version..."
  VERSION=$(curl -fsSL "https://api.github.com/repos/${GITHUB_REPO}/releases/latest" 2>/dev/null \
    | grep '"tag_name"' | head -1 | sed 's/.*"tag_name": *"v\?\([^"]*\)".*/\1/')
  [ -n "$VERSION" ] || fail "Could not determine latest version from GitHub."
fi

# Normalize: strip leading 'v' for display, add for URL
case "$VERSION" in
  v*) TAG="$VERSION"; VERSION="${VERSION#v}" ;;
  *)  TAG="v$VERSION" ;;
esac

info "Installing sing $TAG..."

# --- Download binary ---
TMPFILE=$(mktemp)
trap 'rm -f "$TMPFILE"' EXIT

DOWNLOAD_URL="https://github.com/${GITHUB_REPO}/releases/download/${TAG}/sing"
info "Downloading from GitHub Releases..."
HTTP_CODE=$(curl -fsSL -w '%{http_code}' "$DOWNLOAD_URL" -o "$TMPFILE" 2>/dev/null)
[ "$HTTP_CODE" = "200" ] || fail "Download failed (HTTP $HTTP_CODE). Check the version exists: $DOWNLOAD_URL"

# --- Verify checksum ---
info "Verifying checksum..."
CHECKSUM_URL="https://github.com/${GITHUB_REPO}/releases/download/${TAG}/sing.sha256"
EXPECTED=$(curl -fsSL "$CHECKSUM_URL" 2>/dev/null | awk '{print $1}')
if [ -n "$EXPECTED" ]; then
  ACTUAL=$(sha256sum "$TMPFILE" | awk '{print $1}')
  if [ "$EXPECTED" != "$ACTUAL" ]; then
    fail "Checksum mismatch!\n  Expected: $EXPECTED\n  Actual:   $ACTUAL"
  fi
  ok "  Checksum verified."
else
  info "  Checksum not available — skipping verification."
fi

# --- Validate ELF binary (no 'file' command needed — check magic bytes directly) ---
ELF_MAGIC=$(head -c 4 "$TMPFILE" | od -A n -t x1 | tr -d ' \n')
if [ "$ELF_MAGIC" != "7f454c46" ]; then
  fail "Downloaded file is not a valid Linux binary (expected ELF, got something else)."
fi

chmod +x "$TMPFILE"

# --- Install ---
if [ -w "$INSTALL_DIR" ]; then
  mv "$TMPFILE" "$INSTALL_DIR/$BINARY"
else
  info "Installing to $INSTALL_DIR (requires sudo)..."
  sudo mv "$TMPFILE" "$INSTALL_DIR/$BINARY"
fi

# --- Verify ---
ok "Installed sing $TAG to $INSTALL_DIR/$BINARY"
echo
info "Get started:"
info "  sing host init      # provision your server"
info "  sing upgrade        # update to the latest version"
