#!/usr/bin/env bash

set -Eeuo pipefail

# This small server entry point is installed once as /usr/local/bin/hsm-deploy.
# Keep the release logic in Git so every run uses the freshly fetched version.
REPO="/opt/hanseo-mate/backend/source"

cd "$REPO"
git fetch origin main
git reset --hard origin/main

if [ ! -f "$REPO/scripts/deploy-release.sh" ]; then
    echo "Release script is missing from main: scripts/deploy-release.sh" >&2
    exit 1
fi

exec bash "$REPO/scripts/deploy-release.sh"
