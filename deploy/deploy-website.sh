#!/usr/bin/env bash
# Builds the noorain.uz website (the Nur_website repo: Vite + React) and copies the static
# build to the server, where nginx serves it for noorain.uz and www.noorain.uz.
#
#   deploy/deploy-website.sh [path-to-Nur_website]   (default: ~/Nur_website_folder)
#
# The files land in /opt/noorain/site, which the backend CD rsync excludes, so backend
# deploys never remove them. No nginx restart is needed: it serves the new files at once.
set -euo pipefail

SITE_SRC="${1:-$HOME/Nur_website_folder}"
SERVER="${NOORAIN_SERVER:-ubuntu@3.73.130.154}"
KEY="${NOORAIN_SSH_KEY:-$HOME/.ssh/noorain-lightsail.pem}"

cd "$SITE_SRC"
npm ci --silent
npm run build
test -s dist/index.html

ssh -i "$KEY" "$SERVER" 'mkdir -p /opt/noorain/site'
rsync -az --delete -e "ssh -i $KEY" dist/ "$SERVER:/opt/noorain/site/"
echo "deployed $(git -C "$SITE_SRC" rev-parse --short HEAD 2>/dev/null || echo build) to /opt/noorain/site"
