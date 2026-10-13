#!/bin/sh
# Runs from the official nginx image's /docker-entrypoint.d before nginx starts.
#
# Renders the HTTPS server for $PUBLIC_HOST (+ $EXTRA_HOSTS) only when its Let's Encrypt
# certificate exists, so nginx can boot (and answer the ACME challenge on :80)
# before the first issuance. A background watcher then re-renders and reloads
# nginx whenever the certificate appears or is renewed by the certbot service.
set -eu

TEMPLATE=/etc/nginx/noorain-templates/https.conf.template
OUT_DIR=/etc/nginx/https.d
OUT="$OUT_DIR/https.conf"
CHECK_INTERVAL="${CERT_CHECK_INTERVAL:-60}"

# server_name list: PUBLIC_HOST plus EXTRA_HOSTS (comma or space separated).
SERVER_NAMES=$(echo "${PUBLIC_HOST:-} ${EXTRA_HOSTS:-}" | tr ',' ' ' | xargs)
export SERVER_NAMES

log() { echo "40-noorain-https: $*"; }

cert_dir() { echo "/etc/letsencrypt/live/${PUBLIC_HOST:-}"; }

# Prints a fingerprint of the current certificate, or nothing when missing.
cert_fingerprint() {
  dir=$(cert_dir)
  if [ -n "${PUBLIC_HOST:-}" ] && [ -s "$dir/fullchain.pem" ] && [ -s "$dir/privkey.pem" ]; then
    md5sum "$dir/fullchain.pem" | cut -d' ' -f1
  fi
}

render() {
  mkdir -p "$OUT_DIR"
  if [ -n "$(cert_fingerprint)" ]; then
    envsubst '${PUBLIC_HOST} ${SERVER_NAMES}' < "$TEMPLATE" > "$OUT.tmp"
    mv "$OUT.tmp" "$OUT"
    log "HTTPS enabled for $SERVER_NAMES"
  else
    rm -f "$OUT"
    log "no certificate for '${PUBLIC_HOST:-<unset>}' yet; serving HTTP only"
  fi
}

watch_certificate() {
  set +e  # a transient failure must not kill the watcher
  last=$(cert_fingerprint)
  while :; do
    sleep "$CHECK_INTERVAL"
    current=$(cert_fingerprint)
    [ "$current" = "$last" ] && continue
    render
    if nginx -t -q; then
      nginx -s reload && log "reloaded nginx for new certificate"
      last=$current
    else
      rm -f "$OUT"  # never leave a config behind that breaks the next start
      log "config test failed after certificate change; keeping old config"
    fi
  done
}

render
watch_certificate &
