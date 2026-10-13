#!/usr/bin/env bash
# Switch the public HTTPS hostname to a real domain, in one command.
#
#   cd /opt/noorain && ./infra/scripts/enable-domain.sh [primary] [extra ...]
#   (defaults: api.noorain.uz, extras noorain.uz www.noorain.uz)
#
# 1. requires the primary name's A record to point at this server;
#    extras are included only if they already do (re-run later to add them),
# 2. keeps the current PUBLIC_HOST (e.g. the sslip.io name) in the certificate
#    so the old URL keeps working,
# 3. checks the ACME path end to end and does a Let's Encrypt staging dry run,
# 4. issues one certificate for all names (lineage named after the primary),
# 5. sets PUBLIC_HOST / EXTRA_HOSTS in .env (backup .env.bak-<timestamp>) and
#    recreates nginx + certbot, then checks https://<primary>/healthz.
#
# Env: SERVER_IP (default: detected public IP), DOCKER (default: "sudo docker").
set -euo pipefail

cd "$(dirname "$0")/../.."
[ -f docker-compose.yml ] && [ -f .env ] || { echo "run this inside /opt/noorain (needs docker-compose.yml and .env)" >&2; exit 1; }

PRIMARY="${1:-api.noorain.uz}"
shift || true
if [ "$#" -gt 0 ]; then EXTRAS=("$@"); else EXTRAS=(noorain.uz www.noorain.uz); fi
DOCKER="${DOCKER:-sudo docker}"
COMPOSE="$DOCKER compose"
SERVER_IP="${SERVER_IP:-$(curl -fsS --max-time 10 https://checkip.amazonaws.com | tr -d '[:space:]')}"

resolve_a() {
  if command -v dig >/dev/null 2>&1; then
    dig +short A "$1" @1.1.1.1 | grep -E '^[0-9.]+$' || true
  else
    getent ahostsv4 "$1" | awk '{print $1}' | sort -u || true
  fi
}

points_here() {
  local ips
  ips=$(resolve_a "$1")
  [ -n "$ips" ] && [ "$(echo "$ips" | sort -u)" = "$SERVER_IP" ]
}

current_env() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- || true; }

echo "server IP: $SERVER_IP"
if ! points_here "$PRIMARY"; then
  echo "error: $PRIMARY resolves to '$(resolve_a "$PRIMARY" | xargs)', not $SERVER_IP." >&2
  echo "Add an A record $PRIMARY -> $SERVER_IP, wait for it to propagate, then re-run." >&2
  exit 1
fi

names=("$PRIMARY")
add_name() {
  local n
  for n in "${names[@]}"; do [ "$n" = "$1" ] && return 0; done
  names+=("$1")
}
for extra in "${EXTRAS[@]}"; do
  if points_here "$extra"; then add_name "$extra"; else echo "skipping $extra (does not resolve to $SERVER_IP yet)"; fi
done
old_host=$(current_env PUBLIC_HOST)
old_host=${old_host:-3-73-130-154.sslip.io}
if [ "$old_host" != "$PRIMARY" ] && points_here "$old_host"; then add_name "$old_host"; fi
for old_extra in $(current_env EXTRA_HOSTS | tr ',' ' '); do
  if points_here "$old_extra"; then add_name "$old_extra"; fi
done
echo "certificate names: ${names[*]}"

# End-to-end check of the HTTP-01 path through nginx for every name.
token="preflight-$(date +%s)"
$COMPOSE run --rm --no-deps --entrypoint sh certbot -c \
  "mkdir -p /var/www/certbot/.well-known/acme-challenge && echo $token > /var/www/certbot/.well-known/acme-challenge/$token"
for n in "${names[@]}"; do
  got=$(curl -fsS --max-time 10 "http://$n/.well-known/acme-challenge/$token" || true)
  if [ "$got" != "$token" ]; then
    echo "error: http://$n/.well-known/acme-challenge/ is not served by this nginx (port 80 closed or wrong DNS?)" >&2
    exit 1
  fi
done
$COMPOSE run --rm --no-deps --entrypoint sh certbot -c "rm -f /var/www/certbot/.well-known/acme-challenge/$token"

domain_args=()
for n in "${names[@]}"; do domain_args+=(--domain "$n"); done
certbot_run() {
  $COMPOSE run --rm --no-deps --entrypoint certbot certbot certonly \
    --webroot --webroot-path /var/www/certbot --cert-name "$PRIMARY" "${domain_args[@]}" \
    --non-interactive --agree-tos --register-unsafely-without-email "$@"
}
echo "== staging dry run"
certbot_run --dry-run
echo "== issuing certificate"
certbot_run --keep-until-expiring --expand

extras_csv=$(IFS=,; echo "${names[*]:1}")
cp -p .env ".env.bak-$(date +%Y%m%d%H%M%S)"
set_env() {
  if grep -qE "^$1=" .env; then
    sed -i -E "s|^$1=.*|$1=$2|" .env
  else
    printf '%s=%s\n' "$1" "$2" >> .env
  fi
}
set_env PUBLIC_HOST "$PRIMARY"
set_env EXTRA_HOSTS "$extras_csv"
echo "set PUBLIC_HOST=$PRIMARY EXTRA_HOSTS=$extras_csv in .env"

$COMPOSE up -d --no-deps nginx certbot
sleep 3
echo "== https://$PRIMARY/healthz"
curl -fsS --max-time 10 --resolve "$PRIMARY:443:127.0.0.1" "https://$PRIMARY/healthz"
echo
cat <<EOF
Done. Follow-ups:
  - GitHub > Settings > Secrets and variables > Actions > Variables:
    HEALTH_URL=https://$PRIMARY/healthz/deep  (health monitor target)
  - app: build with -PSERVER_BASE_URL=https://$PRIMARY
  - .env: consider PUBLIC_BASE_URL / CORS_ORIGINS = https://$PRIMARY, then recreate api-gateway
EOF
