#!/usr/bin/env bash
# Push the server/ tree to a Heroku container-stack app.
# Usage: bash scripts/heroku-deploy.sh [app-name]
set -euo pipefail

APP_NAME="${1:-serlok}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEPLOY_DIR="${TMPDIR:-/tmp}/serlok-heroku-deploy-$$"
HEROKU_BIN="${HEROKU_BIN:-}"

if [[ -z "$HEROKU_BIN" ]]; then
  if command -v heroku >/dev/null 2>&1; then
    HEROKU_BIN="$(command -v heroku)"
  elif [[ -x "/c/Program Files/heroku/bin/heroku.cmd" ]]; then
    HEROKU_BIN="/c/Program Files/heroku/bin/heroku.cmd"
  else
    echo "heroku CLI not found. Install it or set HEROKU_BIN." >&2
    exit 1
  fi
fi

if ! command -v bun >/dev/null 2>&1; then
  echo "bun not found. Needed to write build-info.json." >&2
  exit 1
fi

if [[ ! -f "$SERVER_DIR/heroku.yml" || ! -f "$SERVER_DIR/Dockerfile" ]]; then
  echo "Missing heroku.yml or Dockerfile in $SERVER_DIR" >&2
  exit 1
fi

cleanup() {
  rm -rf "$DEPLOY_DIR"
}
trap cleanup EXIT

mkdir -p "$DEPLOY_DIR"
cp -a "$SERVER_DIR/." "$DEPLOY_DIR/"
bun "$SERVER_DIR/scripts/write-build-info.ts" "$DEPLOY_DIR/build-info.json"

rm -rf \
  "$DEPLOY_DIR/node_modules" \
  "$DEPLOY_DIR/coverage" \
  "$DEPLOY_DIR/log" \
  "$DEPLOY_DIR/tmp" \
  "$DEPLOY_DIR/data" \
  "$DEPLOY_DIR/.bundle" \
  "$DEPLOY_DIR/.git"

find "$DEPLOY_DIR" -maxdepth 1 -name '.env*' ! -name '.env.example' -delete

cd "$DEPLOY_DIR"
git init -b main >/dev/null
git add .
git add -f build-info.json
git -c user.email="deploy@serlok.local" -c user.name="Serlok Deploy" \
  commit -m "deploy: serlok server to heroku" >/dev/null

git remote add heroku "https://git.heroku.com/${APP_NAME}.git"

echo "Pushing server tree to Heroku app '${APP_NAME}' (container build)..."
GIT_TERMINAL_PROMPT=0 git push heroku main --force

"$HEROKU_BIN" ps:scale web=1 -a "$APP_NAME"
echo "Deploy finished. Check: heroku logs -a ${APP_NAME} -t"
