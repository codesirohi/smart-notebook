# Smart Notebook 🧠

> **AI-Powered Knowledge Base with RAG**
> Organize knowledge into notebooks → Upload documents → Chat with your data → Get cited, grounded answers

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.12+-blue.svg)](https://www.python.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+pgvector-blue.svg)](https://www.postgresql.org/)

---

## Overview

Smart Notebook is a knowledge base that organizes documents into **notebooks**, ingests them (PDFs, Markdown, text), and provides **conversational RAG-powered chat** with cited, grounded answers scoped to each notebook's knowledge.

**Key architectural choices:**

- **Multi-notebook isolation** — Each notebook is an independent knowledge workspace with its own documents and chat threads
- **Conversational chat** — Persistent chat history injected into the RAG prompt for follow-up questions and contextual dialogue
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
│                 │     │  - notebooks    │     │                 │
│  /api/notebooks │     │  - documents    │     │  poll → extract │
│  /api/chats     │     │  - chats        │     │  → chunk → embed│
│  /api/health    │     │  - embeddings   │     │  → store        │
│                 │     │  (pgvector)     │     │                 │
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
1. User creates a notebook and uploads documents to it
2. API stores document metadata + creates a `PENDING` task in the ingestion queue
3. Python worker claims the task atomically, extracts text, chunks it, generates embeddings via Ollama
4. Embeddings stored in pgvector; task marked `COMPLETED`
5. User starts a chat in the notebook — each message triggers a RAG query **scoped to that notebook's documents**, with conversation history for follow-up context

---

## Tech Stack

| Component | Technology | Purpose |
|---|---|---|
| API Server | Spring Boot 3.4.3 + Java 21 | REST API, query orchestration, health checks |
| Streaming | Spring WebFlux + Project Reactor | SSE token streaming for chat responses |
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

# Create a notebook
curl -X POST http://localhost:8080/api/notebooks \
  -H "Content-Type: application/json" \
  -d '{"name": "Research Papers"}'

# Upload a document to a notebook
curl -X POST http://localhost:8080/api/notebooks/{notebookId}/documents/upload \
  -F "file=@sample.pdf"

# Check ingestion status
curl http://localhost:8080/api/tasks/{taskId}/status

# Start a chat and ask questions
curl -X POST http://localhost:8080/api/notebooks/{notebookId}/chats \
  -H "Content-Type: application/json" \
  -d '{"title": "Paper Q&A"}'

curl -X POST http://localhost:8080/api/chats/{chatId}/messages \
  -H "Content-Type: application/json" \
  -d '{"content": "What are the key findings?"}'

# Stream chat response (SSE — token-by-token)
curl -N -X POST http://localhost:8080/api/chats/{chatId}/messages/stream \
  -H "Content-Type: application/json" \
  -d '{"content": "Tell me more about that"}'
```

---

## Project Structure

```
smart-notebook/
├── src/main/java/org/sirohi/smartnotebook/
│   ├── controller/          # REST endpoints (Notebook, Document, Chat, Health)
│   ├── service/             # Business logic (Notebook, Document, Chat, Ingestion, Query)
│   ├── gateway/             # ModelGateway interface + OllamaModelGateway
│   ├── repository/          # NotebookRepo, DocumentRepo, ChatRepo, VectorSearchRepo
│   ├── model/               # JPA entities (Notebook, Document, Chat, ChatMessage)
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

### Notebooks

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/notebooks` | Create a notebook |
| `GET` | `/api/notebooks` | List all notebooks |
| `GET` | `/api/notebooks/{id}` | Get notebook details |
| `PUT` | `/api/notebooks/{id}` | Update notebook |
| `DELETE` | `/api/notebooks/{id}` | Delete notebook + all data |

### Documents

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/notebooks/{id}/documents/upload` | Upload document to a notebook |
| `GET` | `/api/notebooks/{id}/documents` | List documents in a notebook |
| `GET` | `/api/documents/{id}` | Get document details |
| `GET` | `/api/tasks/{taskId}/status` | Check ingestion task status |

### Chat

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/notebooks/{id}/chats` | Start a new chat |
| `GET` | `/api/notebooks/{id}/chats` | List chats in a notebook |
| `GET` | `/api/chats/{chatId}` | Get chat with message history |
| `POST` | `/api/chats/{chatId}/messages` | Send a message (blocking) |
| `POST` | `/api/chats/{chatId}/messages/stream` | Send a message (SSE streaming) |
| `DELETE` | `/api/chats/{chatId}` | Delete a chat |

### System

| Method | Path | Description |
|---|---|---|
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

See [SMART_NOTEBOOK_BLUEPRINT.md](SMART_NOTEBOOK_BLUEPRINT.md) for the full evolution path and detailed design.

| Phase | Focus | Status |
|---|---|---|
| **Phase 1** | Upload → Ingest → Query with citations | ✅ Complete |
| **Phase 1.5** | Multi-notebook organization, conversational chat, SSE streaming | 🚧 In Progress |
| **Phase 2** | Hybrid search (BM25 + vector), reranking, multi-model routing | Planned |
| **Phase 3** | Groundedness validation, evaluation framework, feedback loop | Planned |
| **Phase 4** | Agentic RAG, multi-agent coordination, table/image understanding | Planned |

---

## License

This project is licensed under the MIT License — see [LICENSE](LICENSE) for details.

---

<div align="center">
  <strong>Built with Spring Boot, Python, PostgreSQL, and Ollama</strong>
</div>
