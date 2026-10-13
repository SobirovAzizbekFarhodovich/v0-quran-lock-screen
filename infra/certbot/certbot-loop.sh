#!/bin/sh
# Entry point of the certbot service: obtains the Let's Encrypt certificate
# for $PUBLIC_HOST plus any $EXTRA_HOSTS (one certificate, HTTP-01 via nginx's /.well-known/acme-challenge/ on :80)
# and renews it. "certonly --keep-until-expiring" is a no-op until the
# certificate is within 30 days of expiry, so running it twice a day is the
# renewal. nginx notices the new file and reloads (40-noorain-https.sh).
#
# The certificate lives in the "letsencrypt" docker volume as
# /etc/letsencrypt/live/$PUBLIC_HOST/, so rsync --delete deploys never touch it.
set -u

INTERVAL="${CERTBOT_INTERVAL:-12h}"
RETRY_INTERVAL="${CERTBOT_RETRY_INTERVAL:-1h}"  # Let's Encrypt allows 5 failed validations/hour

if [ -z "${PUBLIC_HOST:-}" ]; then
  echo "certbot-loop: PUBLIC_HOST is empty; nothing to do" >&2
  exec sleep infinity
fi

if [ -n "${LETSENCRYPT_EMAIL:-}" ]; then
  account="--email $LETSENCRYPT_EMAIL --no-eff-email"
else
  account="--register-unsafely-without-email"
fi

# -d for PUBLIC_HOST first (it names the certificate), then each extra host.
domains="--domain $PUBLIC_HOST"
for host in $(echo "${EXTRA_HOSTS:-}" | tr ',' ' '); do
  [ "$host" = "$PUBLIC_HOST" ] || domains="$domains --domain $host"
done

trap 'exit 0' TERM INT
while :; do
  wait_for="$INTERVAL"
  # shellcheck disable=SC2086
  if ! certbot certonly --webroot --webroot-path /var/www/certbot \
    --cert-name "$PUBLIC_HOST" $domains \
    --keep-until-expiring --non-interactive --agree-tos $account \
    ${CERTBOT_EXTRA_ARGS:-}; then
    wait_for="$RETRY_INTERVAL"
    echo "certbot-loop: certbot failed; retrying in $wait_for" >&2
  fi
  sleep "$wait_for" &
  wait $!
done
