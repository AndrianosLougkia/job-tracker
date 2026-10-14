# Job Tracker

An AI-powered job application tracker built as a portfolio project.
Track applications, upload resumes, and receive AI-driven resume-versus-job-description analysis.


---

## Architecture

See [ARCHITECTURE.md](./ARCHITECTURE.md) for the full architecture contract including
module structure, entity model, flow diagrams, and configuration strategy.

**Summary:**
- Modular monolith backend (Spring Boot 3.5 / Java 21)
- React 18 + Vite frontend
- PostgreSQL primary store with Flyway migrations
- Redis dashboard cache (Stage 9)
- MinIO file storage for resume PDFs (Stage 5)
- Three AI providers: Mock (default), Ollama (local models), Claude (Anthropic API)
- Asynchronous analysis with WebSocket notifications (Stage 8)

---

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA |
| Database | PostgreSQL 16, Flyway |
| Cache | Redis 7 |
| Object storage | MinIO |
| AI | Mock provider, Spring AI (Ollama), Anthropic Java SDK (Claude) |
| PDF | Apache PDFBox |
| Realtime | Spring WebSocket + STOMP |
| API docs | springdoc-openapi (Swagger UI) |
| Frontend | React 18, Vite, React Router 6 |
| Infrastructure | Docker Compose |
| Testing | JUnit 5, Mockito, Testcontainers |

---

## Prerequisites

- Docker and Docker Compose (v2+)
- Java 21 (for running backend outside Docker)
- Node.js 20+ (for running frontend outside Docker)
- Maven 3.9+ (for running backend outside Docker)

---

## Quick Start (Docker Compose)

```bash
# 1. Clone the repository
git clone <repo-url>
cd job-tracker

# 2. Create your environment file
cp .env.example .env
# Edit .env if you want custom passwords (defaults work for local dev)

# 3. Start all services
docker compose up --build

# 4. Open the application
open http://localhost:3000          # Frontend
open http://localhost:8080/api/health    # Backend health
open http://localhost:8080/api/swagger-ui.html  # API docs
```

---

## Running Services Individually

### Backend

```bash
cd backend

# Requires a running PostgreSQL instance.
# Either start only postgres via Docker Compose:
docker compose up postgres -d

# Then run the backend locally:
mvn spring-boot:run

# Or with a specific profile:
mvn spring-boot:run -Dspring-boot.run.profiles=docker
```

### Frontend

```bash
cd frontend
npm install
npm run dev
# Available at http://localhost:3000
```

---

## Environment Variables

See [.env.example](./.env.example) for the full list with descriptions.

Key variables for Stage 1:

| Variable | Default | Description |
|---|---|---|
| `DB_NAME` | `jobtracker` | PostgreSQL database name |
| `DB_USERNAME` | `jobtracker` | PostgreSQL username |
| `DB_PASSWORD` | `change_me_in_production` | PostgreSQL password |
| `SERVER_PORT` | `8080` | Backend HTTP port |
| `FRONTEND_PORT` | `3000` | Frontend dev server port |

---

## AI Analysis

`POST /api/analyses` with `{"applicationId": 1, "resumeId": 2}` compares a resume's extracted
text against the application's job description and produces a match score, matching and
missing skills, strengths, recommendations, and a summary. `GET /api/analyses[?applicationId=]`
and `GET /api/analyses/{id}` return stored results.

Analyses run in the background. The request is validated, the analysis is stored as `PENDING`
and the response returns immediately; a bounded thread pool then moves it to `PROCESSING` and
finally `COMPLETED` or `FAILED`. When it finishes, the owner gets a WebSocket notification and
the UI fetches the result.

The provider is chosen with `AI_PROVIDER`: `mock` (default), `ollama` or `claude`. Every provider
answers in the same JSON shape, which is validated by one parser before anything is stored.
Ollama and Claude are optional: the application starts and works normally when Ollama is not
running or no Anthropic key is set; only analyses with the selected provider fail, with a
message saying what to fix.

| Response | Meaning |
|---|---|
| `202` | Analysis accepted (`PENDING`); `Location` points at `GET /api/analyses/{id}` |
| `404` | Application or resume not found (or owned by another user) |
| `422` | Missing job description, no extracted resume text, or input over `AI_MAX_INPUT_CHARS` |

Provider failures no longer surface as HTTP errors: the analysis ends as `FAILED` with a safe
`errorMessage`. If the analysis queue is full, the analysis is returned already `FAILED` with a
"try again" message. Analyses interrupted by a backend restart are marked `FAILED` on startup.

### Real-time notifications (WebSocket / STOMP)

| | |
|---|---|
| Endpoint | `ws://<host>/api/ws` (plain WebSocket, STOMP 1.2) |
| Authentication | `Authorization: Bearer <jwt>` header on the STOMP `CONNECT` frame (never in the URL) |
| Subscribe to | `/user/queue/analyses`, the only destination clients may subscribe to |
| Client sends | Nothing; `SEND` frames are refused |

Payload, sent when an analysis completes or fails:

```json
{ "analysisId": 42, "applicationId": 7, "status": "COMPLETED", "matchScore": 78,
  "message": "Analysis complete: 78% match" }
```

Spring routes `/user/queue/analyses` to the sessions of the authenticated user only, so no client
can name another user's queue. The frontend keeps one connection per signed-in user, reconnects
every 5 seconds when it drops, shows a toast for each notification, and polls unfinished analyses
while live updates are unavailable.

| Variable | Default | Description |
|---|---|---|
| `WS_ALLOWED_ORIGINS` | `http://localhost:3000` | Browser origins allowed to open the WebSocket (comma-separated) |
| `ANALYSIS_EXECUTOR_CORE_SIZE` | `4` | Background analysis threads kept ready |
| `ANALYSIS_EXECUTOR_MAX_SIZE` | `8` | Maximum concurrent analyses, and so concurrent AI calls |
| `ANALYSIS_EXECUTOR_QUEUE_CAPACITY` | `100` | Analyses that can wait before new ones are rejected |

Background work and the message broker live in the backend process, so this design targets a
single backend instance; scaling out would need a shared job store and an external STOMP broker.

### Mock (default)

Runs offline and is deterministic: it matches a fixed vocabulary of technical skills, so the same
inputs always produce the same result. No setup. The automated tests always use it.

```bash
AI_PROVIDER=mock
```

### Ollama (local models, free)

Runs a model on your own machine through [Ollama](https://ollama.com), via Spring AI.

**Option A: Ollama installed on the host (recommended for running the backend outside Docker)**

```bash
ollama serve                 # if it is not already running as a service
ollama pull qwen2.5:3b       # ~2 GB, once
```

```bash
# .env, or exported before `mvn spring-boot:run`
AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5:3b
```

If the backend runs in Docker but Ollama runs on the host, use
`OLLAMA_BASE_URL=http://host.docker.internal:11434`. On Linux, Ollama listens only on
`127.0.0.1` by default; start it with `OLLAMA_HOST=0.0.0.0 ollama serve` so the container can
reach it.

**Option B: Ollama in Docker Compose**

```bash
# .env
AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=qwen2.5:3b
```

```bash
docker compose --profile ollama up --build
```

The `ollama` profile starts an Ollama container and a one-shot `ollama-pull` job that downloads
`OLLAMA_MODEL` into a named volume (only on the first run). Without the profile, neither starts.
Do not run this alongside a host Ollama on the same port; change `OLLAMA_PORT` if you need both.

| Variable | Default | Description |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` (`http://ollama:11434` in Compose) | Ollama server |
| `OLLAMA_MODEL` | `qwen2.5:3b` | Model tag; must already be pulled |
| `OLLAMA_CONNECT_TIMEOUT` | `5s` | Fails fast when Ollama is not running |
| `OLLAMA_READ_TIMEOUT` | `180s` | Time allowed for the whole answer |
| `OLLAMA_TEMPERATURE` | `0.2` | Low values keep scores stable between runs |
| `OLLAMA_NUM_CTX` | `8192` | Context window in tokens (Ollama's own default of 2048 is too small) |
| `OLLAMA_NUM_PREDICT` | `1024` | Maximum answer length in tokens |

### Claude (Anthropic API, paid)

Uses Claude through Anthropic's official Java SDK. **Calls are billed to the Anthropic account
that owns the key.** A Claude.ai subscription does not include API access. Without
`ANTHROPIC_API_KEY`, the Claude client is never created and no request leaves the application.

```bash
# .env (never commit this file)
AI_PROVIDER=claude
ANTHROPIC_API_KEY=sk-ant-...
```

| Variable | Default | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | (empty) | API key from the Anthropic Console |
| `CLAUDE_MODEL` | `claude-opus-5-5` | Model ID; `claude-haiku-5-5` costs far less per analysis |
| `CLAUDE_EFFORT` | `medium` | Reasoning effort: `low`, `medium`, `high`, `xhigh`, `max` |
| `CLAUDE_MAX_TOKENS` | `16000` | Upper bound on the response, including reasoning |
| `CLAUDE_TIMEOUT` | `120s` | Per-attempt request timeout |
| `CLAUDE_MAX_RETRIES` | `2` | Retries for rate limits, overload, server and network errors |

The answer is constrained to the analysis JSON schema with structured outputs, and server-side
refusal fallbacks are enabled.

### Recommended local development setup

- **Day to day:** `AI_PROVIDER=mock`. It is instant, free and deterministic.
- **Trying real AI output:** `AI_PROVIDER=ollama` with Ollama on the host and `qwen2.5:3b`.
  Small models that are good at JSON work best; 7B+ models need roughly 8 GB of free RAM on their
  own, and without a GPU an analysis takes minutes.
- **Claude:** only if you have an API key and accept the per-request cost.

### Known limitations of local models

- **Slow without a GPU.** On a CPU, a 3B model typically needs 30-90 seconds per analysis, and
  the first request also loads the model into memory. Raise `OLLAMA_READ_TIMEOUT` for larger models.
- **Memory.** The model runs alongside PostgreSQL, MinIO and the backend. Machines with 8 GB of
  RAM should stay at 3B-class models.
- **Lower quality.** Scores are less consistent than Claude's, skills can be missed or invented,
  and recommendations tend to be generic. Results can differ between runs even at low temperature.
- **Context window.** Long resumes plus long job descriptions can exceed `OLLAMA_NUM_CTX`; the
  analysis is rejected up front (Ollama would otherwise silently drop the instructions).
  Raising it costs memory.
- **Output format.** The JSON schema is enforced through Ollama's structured outputs, but a small
  model can still return something unusable; that analysis is stored as `FAILED`.

---

## API Documentation

Once the backend is running, Swagger UI is available at:

```
http://localhost:8080/api/swagger-ui.html
```

---

## Running Tests

```bash
cd backend

# All tests (requires Docker for Testcontainers)
mvn test

# Unit tests only (no Docker required)
mvn test -Dgroups="unit"
```

Testcontainers automatically starts a PostgreSQL container for integration tests.
No manual database setup is required for tests.

The default suite never contacts a real AI service: the Ollama and Claude providers are tested
against local HTTP stubs of their APIs. One optional test runs a real analysis on your Ollama:

```bash
OLLAMA_LIVE_TEST=true OLLAMA_MODEL=qwen2.5:3b mvn test -Dtest=OllamaLiveTest
```

---

## Stage Progress

| Stage | Description | Status |
|---|---|---|
| 0 | Architecture Contract | Done |
| 1 | Project Skeleton and Infrastructure | Done |
| 2 | Database, Flyway, Domain, CRUD | Done |
| 3 | Authentication and Authorization | Done |
| 4 | React Job Tracker Interface | Done |
| 5 | MinIO, Resume Upload, PDF Extraction | Done |
| 6 | AI Abstraction and Mock Provider | Done |
| 7 | Spring AI, Claude, and Ollama | Done |
| 8 | Async Analysis and WebSocket Notifications | Done |
| 9 | Redis Dashboard Caching | Pending |
| 10 | Testing, Hardening, Final Documentation | Pending |

---

## Known Limitations (Stage 3)

- No frontend login/register UI yet — added in Stage 4.
- JWT refresh tokens not implemented — re-login required after expiry (24h default).
- Token stored in localStorage on the client (XSS risk, acceptable for portfolio).
