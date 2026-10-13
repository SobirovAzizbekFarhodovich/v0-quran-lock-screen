# HTTPS, health checks and monitoring

## How HTTPS works

- `PUBLIC_HOST` (compose default `3-73-130-154.sslip.io`, a hostname that
  sslip.io resolves to the server IP) names the HTTPS server and its
  certificate. `EXTRA_HOSTS` (comma separated, optional) adds more names to the
  same certificate. Both can be set in the server `.env`.
- The `certbot` service (`infra/certbot/certbot-loop.sh`) gets the Let's
  Encrypt certificate over HTTP-01 and re-runs every 12 hours; it renews when
  the certificate is close to expiry (retries after 1 hour on failure). nginx serves
  `/.well-known/acme-challenge/` on port 80 from the `certbot_www` volume.
- Certificates live in the `letsencrypt` docker volume
  (`/etc/letsencrypt/live/<PUBLIC_HOST>/`), so `rsync --delete` deploys can't
  remove them.
- `infra/nginx/40-noorain-https.sh` runs when nginx starts. It renders
  `infra/nginx/templates/https.conf.template` only if the certificate exists,
  so nginx can start before the first certificate is issued. Every 60 seconds
  it checks the certificate and reloads nginx when it changes.
- The HTTP (:80) and HTTPS (:443) servers share the same locations
  (`infra/nginx/snippets/app-locations.conf`), including `/ws/recitation`
  (WebSocket upgrade, 900s read timeout). The HTTPS server uses TLS 1.2/1.3
  (Mozilla intermediate profile) and sends HSTS. Plain HTTP still serves the
  API because installed app builds use `http://3.73.130.154`.

The Lightsail instance firewall (console > instance > Networking > IPv4
firewall) must allow **HTTPS, TCP 443**.

## Switching to a real domain

Point the domain's A record(s) at `3.73.130.154`, then on the server:

```bash
cd /opt/noorain && ./infra/scripts/enable-domain.sh   # api.noorain.uz + noorain.uz, www.noorain.uz
```

The script requires the primary name to resolve to the server. It includes
the extra names only if they already resolve (run it again later to add them),
and it keeps the old sslip.io name in the certificate. Before issuing, it
checks the challenge path and runs a staging dry run. It then issues the
certificate, sets `PUBLIC_HOST`/`EXTRA_HOSTS` in `.env` (and backs up the old
file to `.env.bak-<timestamp>`), recreates nginx and certbot, and checks
`https://<domain>/healthz`. Doing it by hand takes one line in `.env`:
`PUBLIC_HOST=api.noorain.uz`, then
`sudo docker compose up -d --no-deps nginx certbot`.

After the switch, set the GitHub Actions variable `HEALTH_URL` to
`https://api.noorain.uz/healthz/deep`, and build the app with
`-PSERVER_BASE_URL=https://api.noorain.uz`.

## Health

- `GET /healthz` is a cheap liveness check (the gateway process is up).
- `GET /healthz/deep` checks postgres, redis, ai-rag (`status` ok) and
  ai-recitation (`model_loaded` true). It returns status and latency for each
  component, and HTTP 503 if any of them is degraded.
- `.github/workflows/cd-backend.yml` fails the deploy if deep health is still
  degraded about 4 minutes after the restart.
- `.github/workflows/health-monitor.yml` calls deep health every 15 minutes
  (and on manual dispatch). It also fails when the certificate has less than
  14 days left. GitHub emails failed scheduled runs to the person who last
  edited the workflow's cron line.
