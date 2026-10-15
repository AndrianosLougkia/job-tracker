# Job Tracker

An AI-powered job application tracker, built as a portfolio project. You can:

- track job applications through their stages (wishlist, applied, screening, interview, offer,
  rejected, withdrawn),
- upload resume PDFs, whose text is extracted automatically,
- run an AI analysis of a resume against an application's job description: a match score,
  matching and missing skills, strengths, recommendations and a summary,
- get a live notification when an analysis finishes,
- see a dashboard of your applications by status.

Every account sees only its own data.

---

## Contents

- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Quick start (Docker Compose)](#quick-start-docker-compose)
- [Local development setup](#local-development-setup)
- [Environment variables](#environment-variables)
- [Database (PostgreSQL)](#database-postgresql)
- [Resume storage (MinIO)](#resume-storage-minio)
- [Dashboard cache (Redis)](#dashboard-cache-redis)
- [AI analysis](#ai-analysis)
- [Security and limits](#security-and-limits)
- [Running tests](#running-tests)
- [API documentation](#api-documentation)
- [Deploying to a server](#deploying-to-a-server)
- [Troubleshooting](#troubleshooting)
- [Known limitations](#known-limitations)
- [Future improvements](#future-improvements)

---

## Architecture

The backend is a **modular monolith**: one Spring Boot application split into modules (`auth`,
`user`, `application`, `resume`, `ai`, `analysis`, `notification`, `dashboard`) that talk to
each other only through their services and events. [ARCHITECTURE.md](./ARCHITECTURE.md) has the
full architecture contract, with module rules, the entity model and flow diagrams.

```
Browser ──► frontend (React; Vite dev server or nginx) ──/api──► backend (Spring Boot)
                                                                   │
                     ┌───────────────┬──────────────┬──────────────┼──────────────────┐
                     ▼               ▼              ▼              ▼                  ▼
                PostgreSQL         MinIO          Redis      AI provider        WebSocket (STOMP)
              (all records)   (resume PDFs)  (dashboard     (mock | Ollama |    pushes "analysis
                                              cache, optional) Claude)           finished" to the user
```

- **Requests** are authenticated with a JWT. Every query is scoped to the caller's user ID.
- **Resume upload** stores the PDF in MinIO and its extracted text in PostgreSQL.
- **Analyses** run in the background. `POST /api/analyses` returns `202` straight away; a bounded
  thread pool calls the AI provider; the result is saved and the owner is notified over
  WebSocket.
- **Dashboard statistics** are cached per user in Redis for 5 minutes and cleared whenever that
  user changes an application. Without Redis they come straight from PostgreSQL.

---

## Technology stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Bean Validation |
| Database | PostgreSQL 16, Flyway migrations |
| Cache | Redis 8 through Spring's cache abstraction |
| Object storage | MinIO AIStor (S3-compatible), MinIO Java client |
| AI | Mock provider; Ollama through Spring AI; Claude through the Anthropic Java SDK |
| PDF | Apache PDFBox |
| Realtime | Spring WebSocket + STOMP, `@stomp/stompjs` in the browser |
| API docs | springdoc-openapi (Swagger UI) |
| Frontend | React 18, TypeScript, Vite, React Router 6, Axios |
| Infrastructure | Docker Compose; nginx for the production frontend image |
| Testing | JUnit 5, Mockito, Spring MockMvc, Testcontainers (PostgreSQL, Redis, MinIO) |

---

## Prerequisites

| Needed for | Requirement |
|---|---|
| Running with Docker | Docker Engine with Compose v2 (`docker compose version`); about 3 GB of free RAM |
| Resume storage | A **MinIO AIStor license file**; see [Resume storage](#resume-storage-minio) |
| Backend outside Docker, tests | Java 21 and Maven 3.9+ |
| Frontend outside Docker | Node.js 20+ and npm |
| Integration tests | A running Docker daemon (Testcontainers) |
| Optional AI | Ollama, or an Anthropic API key |

---

## Quick start (Docker Compose)

```bash
# 1. Clone the repository
git clone git@github.com:AndrianosLougkia/job-tracker.git
cd job-tracker

# 2. Create your environment file (the defaults work for local development)
cp .env.example .env

# 3. Put your MinIO AIStor license next to docker-compose.yml
cp /path/to/your/minio.license ./minio.license

# 4. Build and start everything
docker compose up -d --build

# 5. Follow the backend until it reports "Started JobTrackerApplication"
docker compose logs -f backend
```

The first start takes a few minutes, because the backend container downloads its Maven
dependencies and compiles the project. Later starts take well under a minute.

| URL | What |
|---|---|
| http://localhost:3000 | The app. Register an account, then sign in |
| http://localhost:8080/api/swagger-ui.html | API documentation |
| http://localhost:8080/api/actuator/health | Health of the backend and its dependencies |
| http://localhost:9001 | MinIO console (user `minioadmin`, password `minioadmin`) |

`docker compose ps` should show every service as `healthy` (the frontend has no health check).

**Stopping:** `docker compose down` stops and removes the containers but keeps your data in the
`postgres-data` and `minio-data` volumes. `docker compose down -v` also **deletes all data**.

The Compose stack is a **development** setup: the backend runs through `mvn spring-boot:run` and
the frontend through the Vite dev server with live reload of `frontend/src`. For a public server,
see [Deploying to a server](#deploying-to-a-server).

---

## Local development setup

You can run the backend and the frontend on your machine and keep only the infrastructure in
Docker. This gives faster restarts and debugger access.

```bash
# Infrastructure only
docker compose up -d postgres minio redis
```

**Backend** (http://localhost:8080):

```bash
cd backend
# The Compose Redis is published on host port 6380 (6379 is often taken by a host Redis)
REDIS_PORT=6380 mvn spring-boot:run
```

The defaults in `application.yml` point at `localhost` for PostgreSQL (5432) and MinIO (9000), so
no other settings are needed. Set `CACHE_TYPE=none` to run without Redis, and `LOG_LEVEL=DEBUG`
for more detailed logs.

**Frontend** (http://localhost:3000):

```bash
cd frontend
npm ci
npm run dev
```

Vite proxies `/api` (including the `/api/ws` WebSocket) to `http://localhost:8080`. To proxy
somewhere else, set `VITE_API_URL`. `npm run build` type-checks and builds the production bundle
into `frontend/dist`.

---

## Environment variables

Docker Compose reads them from `.env`. When you run the backend outside Docker, export them in
your shell. [.env.example](./.env.example) lists every variable with a comment. All of them have
working development defaults.

| Variable | Default | Description |
|---|---|---|
| **Database** | | |
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | `jobtracker` / `jobtracker` / `jobtracker` | PostgreSQL database and credentials (Compose creates them) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/jobtracker` | JDBC URL; Compose sets it to the `postgres` service |
| `POSTGRES_PORT` | `5432` | Host port for PostgreSQL (bound to 127.0.0.1) |
| **Security** | | |
| `JWT_SECRET` | development value | HMAC key for login tokens, at least 32 characters. **Must be set outside local development**: `openssl rand -hex 32` |
| `JWT_EXPIRATION_MS` | `86400000` (24 h) | How long a login stays valid |
| `LOGIN_MAX_FAILURES` / `LOGIN_LOCKOUT` | `5` / `15m` | Failed logins per email before a temporary lockout |
| **Storage** | | |
| `MINIO_ENDPOINT` | `http://localhost:9000` | MinIO URL; Compose sets it to the `minio` service |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | `minioadmin` / `minioadmin` | MinIO credentials (Compose also uses them as MinIO's root user) |
| `MINIO_BUCKET` | `resumes` | Bucket for resume PDFs, created automatically |
| `MINIO_PORT` / `MINIO_CONSOLE_PORT` | `9000` / `9001` | Host ports (bound to 127.0.0.1) |
| **Cache** | | |
| `CACHE_TYPE` | `redis` | `redis`, or `none` for no cache |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / (empty) | Redis connection; Compose points the backend at its `redis` service |
| `REDIS_TIMEOUT` / `REDIS_CONNECT_TIMEOUT` | `500ms` / `500ms` | How long a request waits for Redis before falling back to PostgreSQL |
| `DASHBOARD_CACHE_TTL` | `5m` | Maximum age of cached dashboard statistics |
| `CACHE_KEY_PREFIX` | `jobtracker:` | Prefix for every key, so a shared Redis can serve several apps |
| `REDIS_HOST_PORT` / `REDIS_MAXMEMORY` | `6380` / `64mb` | Host port and memory cap of the Compose Redis |
| **AI** | | |
| `AI_PROVIDER` | `mock` | `mock`, `ollama` or `claude` |
| `AI_MAX_INPUT_CHARS` | `50000` | Longest resume text or job description sent for analysis |
| `ANALYSIS_DAILY_LIMIT` | `20` | Analyses one user may start per rolling 24 hours; `0` = unlimited |
| `ANALYSIS_EXECUTOR_CORE_SIZE` / `_MAX_SIZE` / `_QUEUE_CAPACITY` | `4` / `8` / `100` | Background analysis threads and queue |
| Ollama and Claude settings | | See [AI analysis](#ai-analysis) |
| **Web** | | |
| `WS_ALLOWED_ORIGINS` | `http://localhost:3000` | Browser origins allowed to open the notification WebSocket (comma-separated) |
| `SERVER_PORT` / `BACKEND_PORT` / `FRONTEND_PORT` | `8080` / `8080` / `3000` | Backend port in the container, host ports for backend and frontend |
| `LOG_LEVEL` | `INFO` | Log level for the application's code. `DEBUG` logs user emails per request; use it locally only |

---

## Database (PostgreSQL)

- **Setup:** nothing to do. Compose creates the database and user from `DB_*`, and the backend
  applies the Flyway migrations in `backend/src/main/resources/db/migration` on startup.
  Hibernate only validates the schema (`ddl-auto: validate`) and never changes it.
- **Migrations:**

  | Version | Content |
  |---|---|
  | V1 | Checks for PostgreSQL 16 or newer |
  | V2 | `users` and `job_applications` |
  | V3 | `resumes` (metadata; the files live in MinIO) |
  | V4 | `analyses`, with the AI result stored as JSONB |
  | V5 | Lower-cases existing emails and enforces lower-case from then on |

  To change the schema, add a new `V<n>__description.sql`. Never edit a migration that has
  already run anywhere, because Flyway checks the checksums.
- **Data:** everything belongs to a user, and deleting a user cascades to their applications,
  resumes and analyses. Data lives in the `postgres-data` volume.
- **Inspect:** `docker exec -it jobtracker-postgres psql -U jobtracker jobtracker`
- **Backup:** `docker exec jobtracker-postgres pg_dump -U jobtracker jobtracker > backup.sql`

---

## Resume storage (MinIO)

Resume PDFs are stored in MinIO, an S3-compatible object store. Only metadata and the extracted
text go into PostgreSQL. The code reaches storage only through the `StoragePort` interface, with
`MinioStorageService` as the implementation.

- **License.** The Compose file uses **MinIO AIStor**
  (`quay.io/minio/aistor/minio`), which refuses to start without a license file. MinIO no longer
  publishes the free community images on Docker Hub or quay.io. Get an AIStor license from
  [min.io](https://min.io) (check the current terms; there is a free tier) and save it as
  `minio.license` in the project root. It is listed in `.gitignore`, so it is never committed;
  keep your own copy.
- **Bucket:** `MINIO_BUCKET` (default `resumes`). It is created on startup, or on the first
  upload if MinIO was not reachable yet.
- **Object keys:** `resumes/{userId}/{uuid}.pdf`. The uploaded filename is shown in the UI but
  never used in a path.
- **Uploads** must be PDFs (content type **and** the `%PDF-` file signature) of at most 10 MB.
  If text extraction fails (e.g. a scanned PDF), the file is still stored, but it cannot be
  analysed.
- **Deleting** a resume removes the database row first, then the object. If MinIO is down at
  that moment, the object is left orphaned; the user never sees it.
- **Failures:** if MinIO is unreachable, uploads answer `503 Storage service is unavailable` and
  nothing is saved. Everything else keeps working. `/actuator/health` then shows
  `"minio": {"status": "DOWN"}`.
- **Console:** http://localhost:9001 (`MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`).

---

## Dashboard cache (Redis)

`GET /api/dashboard/stats` returns the signed-in user's application counts by status, interview
and offer counts, the five most recent applications, and `generatedAt` (when the numbers were
computed). The result is cached per user through Spring's cache abstraction.

| | |
|---|---|
| Cache key | `jobtracker:dashboard:stats:{userId}` (prefix from `CACHE_KEY_PREFIX`) |
| Value | JSON of the response, written with a serializer bound to that type (no class names stored) |
| TTL | 5 minutes (`DASHBOARD_CACHE_TTL`) |
| Invalidated when | The user creates, updates (including status changes) or deletes an application |

**Invalidation rules.** Application writes publish an event, and the user's key is deleted
*after the transaction commits*. Deleting it earlier would let a dashboard request arriving in
between re-cache the old rows. A failed write (e.g. 404) commits nothing and evicts nothing.
Writes by one user never touch another user's key.

**User isolation.** The key is built only from the user ID of the authenticated principal, never
from request input, so no user can read another user's entry.

**Fallback.** Cache errors are logged at WARN and ignored. A failed read counts as a miss, and
the numbers are computed from PostgreSQL. A failed write is simply not cached. If Redis is down
when an eviction runs, the stale entry survives at most until its TTL expires, so data is never
more than 5 minutes stale. Redis is left out of `/actuator/health`, so its absence never marks
the application `DOWN`. Set `CACHE_TYPE=none` to run without any cache.

**Operations.**
- While Redis is unreachable, each dashboard request waits out the timeout on the cache read and
  on the write, about 1 s with the 500 ms defaults. Lower the timeouts, or use `CACHE_TYPE=none`,
  if Redis will be down for long.
- The Compose Redis is a pure cache: `maxmemory 64mb`, `allkeys-lru` eviction, no persistence.
- It has no password and is published on `127.0.0.1:6380` only. Set `REDIS_PASSWORD` for any
  shared Redis.
- Inspect it with `docker exec -it jobtracker-redis redis-cli`, then `KEYS 'jobtracker:*'` and
  `TTL <key>`.

---

## AI analysis

`POST /api/analyses` with `{"applicationId": 1, "resumeId": 2}` compares the resume's extracted
text with the application's job description.

1. The request is checked: both must be yours (`404` otherwise), both must have text within
   `AI_MAX_INPUT_CHARS` (`422`), and you must be within the daily limit (`429`).
2. The analysis is stored as `PENDING` and `202 Accepted` is returned, with `Location:
   /api/analyses/{id}`.
3. A background thread moves it to `PROCESSING`, calls the provider, and stores `COMPLETED` (with
   the result) or `FAILED` (with a safe `errorMessage`).
4. The owner receives a WebSocket notification. The page then fetches the result, or polls if
   the WebSocket is down.

Provider failures never show up as HTTP errors; the analysis simply ends as `FAILED` with a
message such as "Claude rate limit reached; try again shortly". If the queue is full, the
analysis comes back already `FAILED`. Analyses interrupted by a backend restart are marked
`FAILED` on the next start.

The provider is chosen with `AI_PROVIDER`. Each one returns the same JSON shape, which one
parser validates before anything is stored. Ollama and Claude are optional: without them the
application still starts and works, and only analyses fail, with a message saying what to fix.

### Mock (default)

```bash
AI_PROVIDER=mock
```

Runs offline, costs nothing, and is deterministic: it matches a fixed vocabulary of technical
skills, so the same inputs always give the same result. It needs no setup, and all automated
tests use it. Use it for day-to-day development and for demos.

### Ollama (local models, free)

Runs a model on your own machine through [Ollama](https://ollama.com), via Spring AI.

**Option A: Ollama installed on the host**

```bash
ollama serve                 # if it is not already running as a service
ollama pull qwen2.5:3b       # ~2 GB, once
```

```bash
# .env, or exported before mvn spring-boot:run
AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5:3b
```

If the backend runs in Docker and Ollama on the host, use
`OLLAMA_BASE_URL=http://host.docker.internal:11434`. On Linux, Ollama listens only on
`127.0.0.1` by default, so start it with `OLLAMA_HOST=0.0.0.0 ollama serve` to make it
reachable from the container.

**Option B: Ollama in Docker Compose**

```bash
# .env
AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=qwen2.5:3b
```

```bash
docker compose --profile ollama up -d --build
```

The `ollama` profile starts an Ollama container plus a one-shot `ollama-pull` job, which
downloads `OLLAMA_MODEL` into a volume on the first run. Without the profile, neither starts.

| Variable | Default | Description |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` (`http://ollama:11434` in Compose) | Ollama server |
| `OLLAMA_MODEL` | `qwen2.5:3b` | Model tag; must already be pulled |
| `OLLAMA_CONNECT_TIMEOUT` | `5s` | Fails fast when Ollama is not running |
| `OLLAMA_READ_TIMEOUT` | `180s` | Time allowed for the whole answer |
| `OLLAMA_TEMPERATURE` | `0.2` | Low values keep scores stable between runs |
| `OLLAMA_NUM_CTX` | `8192` | Context window in tokens (Ollama's own default of 2048 is too small) |
| `OLLAMA_NUM_PREDICT` | `1024` | Maximum answer length in tokens |

Local models are slow without a GPU (a 3B model needs 30–90 s per analysis on a CPU), use several
GB of RAM, and give less consistent results than Claude. Machines with 8 GB of RAM should stay
with 3B-class models. Inputs too long for `OLLAMA_NUM_CTX` are rejected up front.

### Claude (Anthropic API, paid)

Uses Claude through Anthropic's official Java SDK. **Calls are billed to the Anthropic account
that owns the key**; a Claude.ai subscription does not include API access. Without
`ANTHROPIC_API_KEY`, no request ever leaves the application.

```bash
# .env (never commit this file)
AI_PROVIDER=claude
ANTHROPIC_API_KEY=sk-ant-...
CLAUDE_MODEL=claude-haiku-5-5   # much cheaper than the default; good enough for this task
```

| Variable | Default | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | (empty) | API key from the Anthropic Console |
| `CLAUDE_MODEL` | `claude-opus-5-5` | Model ID; `claude-haiku-5-5` costs far less per analysis |
| `CLAUDE_EFFORT` | `medium` | Reasoning effort: `low`, `medium`, `high`, `xhigh`, `max` |
| `CLAUDE_MAX_TOKENS` | `16000` | Upper bound on the response, including reasoning |
| `CLAUDE_TIMEOUT` | `120s` | Per-attempt request timeout |
| `CLAUDE_MAX_RETRIES` | `2` | Retries for rate limits, overload, server and network errors |

The answer is constrained to the analysis JSON schema with structured outputs. To keep costs
predictable, combine `ANALYSIS_DAILY_LIMIT` with a monthly spend limit in the Anthropic Console.

### Real-time notifications (WebSocket / STOMP)

| | |
|---|---|
| Endpoint | `ws(s)://<host>/api/ws` (plain WebSocket, STOMP 1.2) |
| Authentication | `Authorization: Bearer <jwt>` header on the STOMP `CONNECT` frame (never in the URL) |
| Subscribe to | `/user/queue/analyses`, the only destination clients may subscribe to |
| Client sends | Nothing; `SEND` frames are refused |

```json
{ "analysisId": 42, "applicationId": 7, "status": "COMPLETED", "matchScore": 78,
  "message": "Analysis complete: 78% match" }
```

Spring delivers `/user/queue/analyses` only to the authenticated user's own sessions, so no
client can name another user's queue. The browser keeps one connection, reconnects every 5 s
when it drops, and shows a toast for each notification.

---

## Security and limits

- **Authentication:** stateless JWT (HS256) in the `Authorization` header. Passwords are hashed
  with BCrypt (cost 12). Emails are case-insensitive and stored in lower case.
- **Authorisation:** every query filters by the caller's user ID. Another user's resource answers
  `404`, never `403`, so its existence is not revealed. Tests cover this for every resource type.
- **Brute force:** after `LOGIN_MAX_FAILURES` (5) failed logins for one email within
  `LOGIN_LOCKOUT` (15 min), further attempts get `429` with `Retry-After` until the window ends.
- **AI cost:** at most `ANALYSIS_DAILY_LIMIT` (20) analyses per user per rolling 24 hours;
  beyond that, `429` with `Retry-After`. Concurrent AI calls are capped by the executor size.
- **Input limits:** company and role up to 255 characters, job description up to 50,000, notes up
  to 10,000, passwords 8–72 characters (BCrypt's limit), PDFs up to 10 MB.
- **Errors:** every error has the same JSON shape, and server errors never include internal
  details:

  ```json
  { "timestamp": "2026-10-15T09:30:00Z", "status": 404, "error": "Not Found",
    "message": "Application not found: 99", "path": "/api/applications/99" }
  ```

  Validation errors add `"errors": ["field: message", ...]`.
- **Health:** `/api/actuator/health` is public and shows each component's status (`db`, `minio`,
  `ping`, ...) but no details. Other actuator endpoints are not exposed.
- **Ports:** in Compose, PostgreSQL, MinIO and Redis are published on `127.0.0.1` only.

---

## Running tests

```bash
cd backend
mvn test                       # everything; Docker must be running
mvn test -Dtest=AuthControllerTest   # one class
```

| Kind | Examples | Needs |
|---|---|---|
| Unit | `AnalysisServiceTest`, `ResumeServiceTest`, `AiResponseParserTest`, `LoginAttemptLimiterTest` | Nothing |
| Integration (API, MockMvc) | `AuthControllerTest`, `ApplicationControllerTest`, `AnalysisControllerTest` | Docker (PostgreSQL Testcontainer) |
| Security and isolation | `SecurityIntegrationTest`: every endpoint without a token, expired and forged tokens, cross-user access | Docker |
| Redis | `DashboardCacheIntegrationTest` (hits, misses, TTL, invalidation, isolation), `DashboardWithoutRedisIntegrationTest` (fallback) | Docker (Redis Testcontainer) |
| WebSocket | `AnalysisNotificationIntegrationTest` | Docker |
| MinIO | `MinioStorageIntegrationTest`: real upload, text extraction, delete, health | Docker **and** `minio.license` in the project root; skipped otherwise |
| AI providers | `ClaudeAiProviderTest`, `OllamaAiProviderTest` against local HTTP stubs | Nothing |

The suite never calls a real AI service. One opt-in test runs a real analysis on your Ollama:

```bash
OLLAMA_LIVE_TEST=true OLLAMA_MODEL=qwen2.5:3b mvn test -Dtest=OllamaLiveTest
```

The frontend has no automated tests; `npm run build` type-checks it.

---

## API documentation

Swagger UI: http://localhost:8080/api/swagger-ui.html (OpenAPI JSON at `/api/v3/api-docs`).
Click **Authorize** and paste the `token` from `POST /api/auth/login` to call protected
endpoints.

| Method and path | Description | Success |
|---|---|---|
| `POST /api/auth/register` | Create an account `{email, password}` | 201 |
| `POST /api/auth/login` | Get a JWT `{token, userId, email}` | 200 |
| `GET /api/applications` | Your applications, newest first | 200 |
| `POST /api/applications` | Create `{company, role, status?, jobDescription?, notes?, appliedAt?}` | 201 |
| `GET /api/applications/{id}` | One application | 200 |
| `PATCH /api/applications/{id}` | Partial update; omitted fields unchanged, `""` clears text fields | 200 |
| `DELETE /api/applications/{id}` | Delete, with its analyses | 204 |
| `GET /api/resumes` | Your resumes | 200 |
| `POST /api/resumes` | Upload a PDF (`multipart/form-data`, field `file`) | 201 |
| `GET /api/resumes/{id}` | Resume metadata | 200 |
| `DELETE /api/resumes/{id}` | Delete, with its analyses | 204 |
| `POST /api/analyses` | Start an analysis `{applicationId, resumeId}` | 202 |
| `GET /api/analyses[?applicationId=]` | Your analyses, newest first | 200 |
| `GET /api/analyses/{id}` | One analysis with its result | 200 |
| `GET /api/dashboard/stats` | Counts by status and recent applications (cached) | 200 |
| `GET /api/health` | Simple liveness check | 200 |
| `GET /api/actuator/health` | Health with component statuses | 200 / 503 |

Common errors: `400` validation, `401` missing or invalid token (or wrong credentials at login),
`404` not found or not yours, `405` wrong method, `409` email already registered, `413` file over
10 MB, `422` nothing to analyse, `429` login lockout or daily limit, `503` storage unavailable.

---

## Deploying to a server

The repository ships a development Compose setup. For a small public deployment (e.g. one VPS
for a few users), change the following:

1. **Secrets.** In `.env`, set `JWT_SECRET` (`openssl rand -hex 32`), `DB_PASSWORD`,
   `MINIO_ACCESS_KEY` and `MINIO_SECRET_KEY` to new random values. The backend logs a warning at
   startup while `JWT_SECRET` is still a published placeholder.
2. **Production images.** Build the backend with the `Dockerfile`'s final stage (a plain JRE
   running the JAR as a non-root user) instead of `target: builder` and `mvn spring-boot:run`.
   Build the frontend with `target: prod`, which is nginx serving the built files.
   `frontend/nginx.conf` already proxies `/api` and the `/api/ws` WebSocket to `backend:8080`.
3. **HTTPS.** Put a TLS-terminating reverse proxy in front of the frontend (e.g. Caddy, or nginx
   with Let's Encrypt). The browser then connects the WebSocket over `wss://` automatically, and
   tokens never cross the network in clear text.
4. **WebSocket origin.** Set `WS_ALLOWED_ORIGINS=https://your-domain.example`, or the browser's
   notification connection will be refused.
5. **Ports.** Publish only the HTTPS proxy. Remove the host `ports:` of the backend, PostgreSQL,
   MinIO and Redis; the services reach each other on the Compose network.
6. **AI.** Start with `AI_PROVIDER=mock`. For real analyses, use `AI_PROVIDER=claude` with
   `CLAUDE_MODEL=claude-haiku-5-5`, keep `ANALYSIS_DAILY_LIMIT` low, and set a spend limit in the
   Anthropic Console. Self-hosted Ollama needs far more RAM than a cheap server has.
7. **Small servers.** Set `CACHE_TYPE=none` and drop the `redis` service to save memory; the
   dashboard stays correct without it. Lower `-Xmx` if needed.
8. **Backups.** Back up PostgreSQL (`pg_dump`) and the `minio-data` volume.
9. **Logs.** Keep `LOG_LEVEL=INFO`.

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `minio` keeps restarting; logs mention the license | `minio.license` is missing or invalid. Put a valid AIStor license in the project root. If Docker created a *directory* named `minio.license` (it does when the file was missing), delete it first |
| `backend` stays `starting` for minutes on the first run | It is downloading Maven dependencies and compiling. Watch `docker compose logs -f backend` |
| `Bind for 0.0.0.0:6379 failed` or another "port is already allocated" | Another service uses the port. Change `POSTGRES_PORT`, `MINIO_PORT`, `REDIS_HOST_PORT`, `BACKEND_PORT` or `FRONTEND_PORT` in `.env` |
| Backend fails with `Connection to localhost:5432 refused` | Running outside Docker without PostgreSQL: `docker compose up -d postgres` |
| Backend fails with `Unknown AI provider` | `AI_PROVIDER` must be `mock`, `ollama` or `claude` |
| Flyway: `Migration V5 failed` / `could not create unique index` | Two existing accounts differ only by email case. Delete or rename one, then restart |
| Every page sends you back to the login screen | Your token expired (24 h) or `JWT_SECRET` changed. Sign in again |
| "Too many failed login attempts" | 5 wrong passwords within 15 minutes. Wait for the time shown, or restart the backend |
| Uploads fail with "Storage service is unavailable" | MinIO is down: check `docker compose ps minio` and `/api/actuator/health` |
| "Resume has no extracted text to analyse" | The PDF is scanned or image-only. Upload a PDF with selectable text |
| Analyses fail with "Ollama is not reachable" | Start Ollama, check `OLLAMA_BASE_URL`, and for a host Ollama on Linux use `OLLAMA_HOST=0.0.0.0` |
| Analyses fail with "Claude rejected the API key" | Check `ANTHROPIC_API_KEY`, and that the account has API credit |
| No live notifications; the page polls instead | The WebSocket was refused. Check `WS_ALLOWED_ORIGINS` matches the URL in the browser, and that any proxy forwards `Upgrade` headers |
| Dashboard numbers lag by a few minutes | Redis was down while an application changed; entries expire within `DASHBOARD_CACHE_TTL` (5 min) |
| Integration tests fail with `Could not find a valid Docker environment` | Start Docker. Testcontainers needs it |

---

## Known limitations

- **MinIO license:** resume storage needs a MinIO AIStor license, and there is no license-free
  storage option.
- **Single instance:** background analyses, the WebSocket broker, the login lockout and the queue
  live in the backend's memory. Running two backends would need a shared job queue, an external
  STOMP broker and a shared rate limiter. A restart fails unfinished analyses (they can be run
  again) and resets lockouts.
- **Rate limits are approximate:** two analyses requested at the same instant can both pass the
  daily-limit check, overshooting it by one.
- **Tokens:** no refresh tokens and no server-side logout; a token stays valid until it expires
  (24 h). It is kept in `localStorage`, which an XSS bug could read.
- **Accounts:** no email verification, password reset or account deletion from the UI.
- **Resumes:** text-based PDFs only (no OCR, no Word files); resumes cannot be downloaded again or
  replaced; no per-user storage quota.
- **Applications:** `appliedAt` cannot be cleared once set; lists are not paginated.
- **Frontend:** no automated tests.
- **Development Compose only:** production use needs the changes in
  [Deploying to a server](#deploying-to-a-server).

---

## Future improvements

- A production Compose file (or Helm chart) with built images, HTTPS and secrets management.
- Pluggable storage (local disk or any S3 service) to remove the MinIO license requirement.
- Refresh tokens in HTTP-only cookies, logout, password reset and email verification.
- A persistent job queue and an external broker for horizontal scaling.
- Frontend tests (Vitest + React Testing Library, Playwright end to end).
- Pagination and search for applications; analysis history comparisons across resume versions.
- Resume download, versioning and OCR for scanned PDFs.
- Metrics (Micrometer + Prometheus) and structured JSON logs.
- CI pipeline running the full test suite on every push.

---

## Stage progress

| Stage | Description | Status |
|---|---|---|
| 0 | Architecture contract | Done |
| 1 | Project skeleton and infrastructure | Done |
| 2 | Database, Flyway, domain, CRUD | Done |
| 3 | Authentication and authorisation | Done |
| 4 | React job tracker interface | Done |
| 5 | MinIO, resume upload, PDF extraction | Done |
| 6 | AI abstraction and mock provider | Done |
| 7 | Spring AI, Claude and Ollama | Done |
| 8 | Async analysis and WebSocket notifications | Done |
| 9 | Redis dashboard caching | Done |
| 10 | Testing, hardening, final documentation | Done |
