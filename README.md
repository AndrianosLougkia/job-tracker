# Job Tracker

An AI-powered job application tracker built as a portfolio project.
Track applications, upload resumes, and receive AI-driven resume-versus-job-description analysis.

> **Build status:** Stage 1 complete — project skeleton, infrastructure, health endpoint.

---

## Architecture

See [ARCHITECTURE.md](./ARCHITECTURE.md) for the full architecture contract including
module structure, entity model, flow diagrams, and configuration strategy.

**Summary:**
- Modular monolith backend (Spring Boot 3 / Java 21)
- React 18 + Vite frontend
- PostgreSQL primary store with Flyway migrations
- Redis dashboard cache (Stage 9)
- MinIO file storage for resume PDFs (Stage 5)
- Three AI providers: Mock (default), Claude, Ollama (Stage 6 onwards)
- Asynchronous analysis with WebSocket notifications (Stage 8)

---

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Security, Spring Data JPA |
| Database | PostgreSQL 16, Flyway |
| Cache | Redis 7 |
| Object storage | MinIO |
| AI | Spring AI (Claude / Ollama / Mock) |
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

---

## Stage Progress

| Stage | Description | Status |
|---|---|---|
| 0 | Architecture Contract | Done |
| 1 | Project Skeleton and Infrastructure | Done |
| 2 | Database, Flyway, Domain, CRUD | Pending |
| 3 | Authentication and Authorization | Pending |
| 4 | React Job Tracker Interface | Pending |
| 5 | MinIO, Resume Upload, PDF Extraction | Pending |
| 6 | AI Abstraction and Mock Provider | Pending |
| 7 | Spring AI, Claude, and Ollama | Pending |
| 8 | Async Analysis and WebSocket Notifications | Pending |
| 9 | Redis Dashboard Caching | Pending |
| 10 | Testing, Hardening, Final Documentation | Pending |

---

## Known Limitations (Stage 1)

- No authentication — all endpoints are open. Added in Stage 3.
- No business logic — only the health endpoint exists. Added in Stage 2.
- Security config is permissive by design for development. Replaced in Stage 3.
