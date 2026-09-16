#!/usr/bin/env bash
set -euo pipefail

# ONE-TIME PocketForge beta signing setup.
# Run this from an authenticated GitHub Codespace in this repository.
# Do NOT rerun it after users install the first stable-signed beta: rotating this key
# would intentionally make Android reject future in-place beta updates.

for cmd in gh keytool openssl base64; do
  command -v "$cmd" >/dev/null 2>&1 || {
    echo "Missing required command: $cmd" >&2
    exit 1
  }
done

gh auth status >/dev/null 2>&1 || {
  echo "GitHub CLI is not authenticated. Open this repository in a GitHub Codespace and run the script there." >&2
  exit 1
}

REPO="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"
if [[ -z "$REPO" ]]; then
  echo "Could not determine the GitHub repository." >&2
  exit 1
fi

EXISTING="$(gh secret list --repo "$REPO" 2>/dev/null || true)"
if grep -q '^POCKETFORGE_BETA_KEYSTORE_B64[[:space:]]' <<<"$EXISTING" || \
   grep -q '^POCKETFORGE_BETA_KEY_PASSWORD[[:space:]]' <<<"$EXISTING"; then
  echo "PocketForge beta signing secrets already exist for $REPO."
  echo "Refusing to rotate the key. Keeping the same key is what makes in-app updates work."
  exit 0
fi

TMP_DIR="$(mktemp -d)"
KEYSTORE="$TMP_DIR/pocketforge-beta.p12"
PASSWORD="$(openssl rand -hex 32)"
trap 'rm -rf "$TMP_DIR"; unset PASSWORD KEYSTORE_B64' EXIT

keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -storetype PKCS12 \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -alias pocketforge-beta \
  -keyalg RSA \
  -keysize 3072 \
  -validity 3650 \
  -dname "CN=PocketForge Beta,O=PocketForge,C=US" \
  >/dev/null 2>&1

if base64 --help 2>/dev/null | grep -q -- '-w'; then
  KEYSTORE_B64="$(base64 -w 0 "$KEYSTORE")"
else
  KEYSTORE_B64="$(base64 < "$KEYSTORE" | tr -d '\n')"
fi

printf '%s' "$KEYSTORE_B64" | gh secret set POCKETFORGE_BETA_KEYSTORE_B64 --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set POCKETFORGE_BETA_KEY_PASSWORD --repo "$REPO"

unset KEYSTORE_B64 PASSWORD
rm -rf "$TMP_DIR"
trap - EXIT

echo "Stable private beta signing is now locked for $REPO."
echo "Do not delete or replace POCKETFORGE_BETA_KEYSTORE_B64 or POCKETFORGE_BETA_KEY_PASSWORD during beta testing."
echo "Starting a fresh signed PocketForge build now…"
gh workflow run android.yml --repo "$REPO" --ref main

echo "Done. Watch Actions for the new build. The first stable-signed beta must be installed once manually; later betas can update in-app."
