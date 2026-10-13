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

# Every name on the certificate: PUBLIC_HOST plus EXTRA_HOSTS (comma or space separated).
ALL_NAMES=$(echo "${PUBLIC_HOST:-} ${EXTRA_HOSTS:-}" | tr ',' ' ' | xargs)
# The website's names get their own server (the static site); the rest serve the API.
SITE_HOSTS="${SITE_HOSTS:-noorain.uz www.noorain.uz}"
SERVER_NAMES=""
SITE_NAMES=""
for name in $ALL_NAMES; do
  case " $SITE_HOSTS " in
    *" $name "*) SITE_NAMES="$SITE_NAMES $name" ;;
    *) SERVER_NAMES="$SERVER_NAMES $name" ;;
  esac
done
SERVER_NAMES=$(echo "$SERVER_NAMES" | xargs)
SITE_NAMES=$(echo "$SITE_NAMES" | xargs)
export SERVER_NAMES SITE_NAMES
SITE_TEMPLATE=/etc/nginx/noorain-templates/site-https.conf.template
REDIRECT_TEMPLATE=/etc/nginx/noorain-templates/site-redirect.conf.template
SITE_OUT="$OUT_DIR/site.conf"
REDIRECT_DIR=/etc/nginx/site-https.d

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
  mkdir -p "$OUT_DIR" "$REDIRECT_DIR"
  if [ -n "$(cert_fingerprint)" ]; then
    envsubst '${PUBLIC_HOST} ${SERVER_NAMES}' < "$TEMPLATE" > "$OUT.tmp"
    mv "$OUT.tmp" "$OUT"
    log "HTTPS enabled for $SERVER_NAMES"
    if [ -n "$SITE_NAMES" ]; then
      envsubst '${PUBLIC_HOST} ${SITE_NAMES}' < "$SITE_TEMPLATE" > "$SITE_OUT.tmp"
      mv "$SITE_OUT.tmp" "$SITE_OUT"
      cp "$REDIRECT_TEMPLATE" "$REDIRECT_DIR/redirect.conf"
      log "HTTPS enabled for the website: $SITE_NAMES"
    else
      rm -f "$SITE_OUT" "$REDIRECT_DIR/redirect.conf"
    fi
  else
    rm -f "$OUT" "$SITE_OUT" "$REDIRECT_DIR/redirect.conf"
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
