# Smart Notebook 🧠

> **AI-Powered Knowledge Base with RAG**
> Upload documents → Ingest & embed asynchronously → Ask questions → Get cited, grounded answers

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.12+-blue.svg)](https://www.python.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+pgvector-blue.svg)](https://www.postgresql.org/)

---

## Overview

Smart Notebook is a knowledge base that ingests documents (PDFs, Markdown, text), chunks and embeds them using AI, and answers natural-language questions with **cited, grounded responses** via Retrieval Augmented Generation (RAG).

**Key architectural choices:**

- **Polyglot design** — Java API (Spring Boot) + Python ingestion worker, each using the language best suited to its role
- **Postgres-as-queue** — `SELECT FOR UPDATE SKIP LOCKED` for async task processing with exactly-once semantics — no external broker needed
- **Vendor-agnostic model abstraction** — `ModelGateway` interface makes LLM providers swappable via configuration
- **Single-database architecture** — pgvector + relational data in one PostgreSQL instance for semantic search and filtering in the same query

> For the full design rationale, architecture decisions, and implementation details, see [SMART_NOTEBOOK_BLUEPRINT.md](SMART_NOTEBOOK_BLUEPRINT.md).

---

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Spring Boot    │     │   PostgreSQL    │     │  Python Worker  │
│  REST API       │────▶│                 │◀────│  (CLI Poller)   │
│                 │     │  - documents    │     │                 │
│  /api/upload    │     │  - task_queue   │     │  poll → extract │
│  /api/query     │     │  - embeddings   │     │  → chunk → embed│
│  /api/health    │     │  (pgvector)     │     │  → store        │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
                                                         │
                                                         ▼
                                                ┌─────────────────┐
                                                │   Ollama         │
                                                │   (Local LLM)    │
                                                │   Port 11434     │
                                                └─────────────────┘
```

**Data flow:**
1. User uploads a document via REST API
2. API stores document metadata + creates a `PENDING` task in the ingestion queue
3. Python worker claims the task atomically, extracts text, chunks it, generates embeddings via Ollama
4. Embeddings stored in pgvector; task marked `COMPLETED`
5. User queries via `/api/query` — API embeds the question, runs vector similarity search, assembles context, and gets a RAG-powered answer with citations

---

## Tech Stack

| Component | Technology | Purpose |
|---|---|---|
| API Server | Spring Boot 3.4.3 + Java 21 | REST API, query orchestration, health checks |
| Ingestion Worker | Python 3.12 | Text extraction, chunking, embedding generation |
| Database | PostgreSQL 16 + pgvector | Documents, task queue, vector embeddings |
| LLM Provider | Ollama (local) | Embeddings + completions via `ModelGateway` |
| Migrations | Flyway | Schema versioning |

---

## Getting Started

### Prerequisites

- Java 21+
- Python 3.12+
- Docker & Docker Compose
- [Ollama](https://ollama.ai/) installed locally

### Setup

```bash
# 1. Clone
git clone https://github.com/codesirohi/smart-notebook.git
cd smart-notebook

# 2. Start Postgres (pgvector)
make db-up

# 3. Install Ollama model
ollama pull phi3:mini

# 4. Start the API (Terminal 1)
make api

# 5. Set up & start the worker (Terminal 2)
make worker-setup
make worker
```

### Verify

```bash
# Health check
curl http://localhost:8080/api/health

# Upload a document
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@sample.pdf"

# Check ingestion status
curl http://localhost:8080/api/tasks/{taskId}/status

# Ask a question
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "What are the key findings?", "topK": 5}'
```

---

## Project Structure

```
smart-notebook/
├── src/main/java/org/sirohi/smartnotebook/
│   ├── controller/          # REST endpoints (Document, Query, Health)
│   ├── service/             # Business logic (Document, Ingestion, Query, FileStorage)
│   ├── gateway/             # ModelGateway interface + OllamaModelGateway
│   ├── repository/          # DocumentRepository, VectorSearchRepository, QueryLogRepository
│   ├── model/               # JPA entities
│   ├── dto/                 # Request/response records
│   └── exception/           # Global error handling
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/        # Flyway migrations (5 files)
├── worker/                  # Python ingestion worker
│   ├── worker.py            # Main poll loop with graceful shutdown
│   ├── processor.py         # Extract → chunk → embed pipeline
│   ├── chunker.py           # Semantic-aware sliding window chunking
│   ├── extractors.py        # PDF (PyPDF2 + pdfplumber), Markdown, Text
│   ├── ollama_client.py     # Ollama HTTP client with retry logic
│   ├── db.py                # Task claiming, chunk storage, stale reaper
│   ├── config.py            # Environment-based configuration
│   └── requirements.txt
├── docker-compose.yml       # PostgreSQL + pgvector
├── Makefile                 # Dev commands
└── SMART_NOTEBOOK_BLUEPRINT.md  # Full design document
```

---

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/documents/upload` | Upload a document (multipart) |
| `GET` | `/api/documents` | List all documents (paginated) |
| `GET` | `/api/documents/{id}` | Get document details |
| `GET` | `/api/tasks/{taskId}/status` | Check ingestion task status |
| `POST` | `/api/query` | Ask a question (RAG) |
| `GET` | `/api/health` | System health (DB, Ollama, queue stats) |

---

## Configuration

Configuration is managed via `application.yml` with Spring profiles:

| Variable | Description | Default |
|---|---|---|
| `OLLAMA_URL` | Ollama server URL | `http://localhost:11434` |
| `OLLAMA_MODEL` | Model for embeddings + completions | `phi3:mini` |
| `SPRING_DATASOURCE_URL` | PostgreSQL connection | `jdbc:postgresql://localhost:5432/smartnotebook` |
| `APP_UPLOAD_DIR` | File storage directory | `./uploads` |

Worker configuration is in `worker/.env.example`.

---

## Make Commands

```bash
make db-up          # Start PostgreSQL
make db-down        # Stop PostgreSQL
make db-reset       # Reset database (delete all data)
make api            # Start Spring Boot API
make worker         # Start Python ingestion worker
make worker-setup   # Set up Python virtualenv
make test           # Run Java tests
make status         # Show status of all services
make clean          # Clean build artifacts
```

---

## Roadmap

See [SMART_NOTEBOOK_BLUEPRINT.md §18](SMART_NOTEBOOK_BLUEPRINT.md) for the full evolution path.

| Phase | Focus | Status |
|---|---|---|
| **Phase 1** | Upload → Ingest → Query with citations | ✅ Complete |
| **Phase 2** | Hybrid search, reranking, multi-model routing | Planned |
| **Phase 3** | Groundedness validation, evaluation framework, feedback loop | Planned |
| **Phase 4** | Agentic RAG, multi-agent coordination, table/image understanding | Planned |

---

## License

This project is licensed under the MIT License — see [LICENSE](LICENSE) for details.

---

<div align="center">
  <strong>Built with Spring Boot, Python, PostgreSQL, and Ollama</strong>
</div>
