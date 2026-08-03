# Temporary HTTP deployment

This guide is for short-term testing on `http://18.195.218.23` before a domain
and HTTPS certificate are available. Do not share the app widely in this mode:
passwords, access tokens and recitation audio are not encrypted over HTTP.

## 1. Prepare the server

On an Ubuntu/Debian server, install Docker and clone or copy this repository:

```bash
sudo apt update
sudo apt install -y git docker.io docker-compose-plugin
sudo systemctl enable --now docker
sudo mkdir -p /opt/quran-reminder
sudo chown "$USER":"$USER" /opt/quran-reminder
git clone <REPOSITORY_URL> /opt/quran-reminder
cd /opt/quran-reminder
```

Only TCP ports `22` (SSH) and `80` need to be reachable from the internet.
The Compose configuration keeps PostgreSQL, Redis, ChromaDB, MinIO, the API
gateway and RAG service private to the Docker network. Grafana and Prometheus
bind to `127.0.0.1` only.

## 2. Create the environment file

```bash
cp .env.example .env
openssl rand -hex 32
openssl rand -base64 36
```

Put the generated values in `.env`. This minimal temporary configuration is
enough for the app:

```dotenv
APP_ENV=staging
PUBLIC_BASE_URL=http://18.195.218.23
PASSWORD_RESET_URL=islamicai:///reset-password

POSTGRES_PASSWORD=<long-random-password>
DATABASE_URL=postgres://islamic_ai:<long-random-password>@postgres:5432/islamic_ai?sslmode=disable
REDIS_PASSWORD=<another-long-random-password>
JWT_SECRET=<at-least-32-random-characters>

CORS_ORIGINS=http://18.195.218.23
GEMINI_API_KEY=<optional-but-needed-for-AI>
# Or use OPENROUTER_API_KEY=<key> instead.
```

`APP_ENV=staging` intentionally avoids the production HTTPS validation while
this is a temporary HTTP deployment. Do not commit `.env`.

## 3. Start and verify

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f ai-rag-init ai-rag api-gateway nginx
```

The first RAG initialization can take several minutes because it builds the
ChromaDB index. Then test from the server or another machine:

```bash
curl http://18.195.218.23/healthz
curl -s http://18.195.218.23/api/quran/verses | head -c 200
curl -I http://18.195.218.23/backgrounds/bg_remote_1.png
```

Expected results are an `{"status":"ok"}` health response, JSON beginning
with `{"verses":`, and `HTTP/1.1 200 OK` for the image.

## 4. Install the Android test APK

The Kotlin app uses `http://18.195.218.23` by default. Build it with:

```bash
./gradlew :app:assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` on the phone. On its first
run it downloads the complete 6,236-verse Uzbek translation to its offline
database. The repository does not contain a full Arabic-text corpus, so the
app deliberately hides the Arabic line for server-loaded verses instead of
showing incorrect text. Lock-screen translations, reminders, search, remote
backgrounds and Tadabbur AI are available.

## Later: move to HTTPS

When a domain is available, point its DNS A record to the server, add a TLS
certificate and switch the public values to HTTPS:

```dotenv
APP_ENV=production
PUBLIC_BASE_URL=https://api.example.com
PASSWORD_RESET_URL=islamicai:///reset-password
CORS_ORIGINS=https://api.example.com
```

Build the Android APK with the new URL, without changing source code:

```bash
./gradlew :app:assembleRelease -PSERVER_BASE_URL=https://api.example.com
```

The same URL must be configured in the Flutter client before its release build.
