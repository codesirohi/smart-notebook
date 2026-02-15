# Smart Notebook 🧠

> **AI-Powered Knowledge Base with RAG**
> Organize knowledge into notebooks → Upload documents → Chat with your data → Get cited, grounded answers

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.12+-blue.svg)](https://www.python.org/)
[![LangGraph](https://img.shields.io/badge/LangGraph-State_Machine-blueviolet.svg)](https://langchain-ai.github.io/langgraph/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+pgvector-blue.svg)](https://www.postgresql.org/)

---

## Overview

Smart Notebook is a knowledge base that organizes documents into **notebooks**, ingests them (PDFs, Markdown, text), and provides **conversational RAG-powered chat** with cited, grounded answers scoped to each notebook's knowledge.

**Key architectural choices:**

- **High-Concurrency Design** — Java 21 Virtual Threads for high-throughput I/O + Python ThreadPool worker for parallel ingestion

- **Multi-notebook isolation** — Each notebook is an independent knowledge workspace with its own documents and chat threads
- **LangGraph Ingestion Pipeline** — Robust, state-machine driven document processing (Extract → Chunk → Embed → Store) with error handling
- **Polyglot design** — Java API (Spring Boot) + Python ingestion worker, each using the language best suited to its role
- **Multi-LLM Support** — Vendor-agnostic design supporting **OpenAI**, **Anthropic**, **Gemini**, and **Ollama** (local) via `LLMFactory`
- **Postgres-as-queue** — `SELECT FOR UPDATE SKIP LOCKED` for async task processing with exactly-once semantics
- **Single-database architecture** — pgvector + relational data in one PostgreSQL instance for semantic search and filtering in the same query
- **Cost-Optimized Architecture** — A tri-layer strategy (Ingestion, Retrieval, Inference) to minimize token usage and maximize performance on 8GB hardware.

> For the full design rationale, architecture decisions, and implementation details, see [SMART_NOTEBOOK_BLUEPRINT.md](SMART_NOTEBOOK_BLUEPRINT.md).

---

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Spring Boot    │     │   PostgreSQL    │     │  Python Worker  │
│  REST API       │────▶│                 │◀────│  (LangGraph)    │
│                 │     │  - notebooks    │     │                 │
│  /api/notebooks │     │  - documents    │     │  poll → extract │
│  /api/chats     │     │  - chats        │     │  → chunk → embed│
│  /api/health    │     │  - embeddings   │     │  → store        │
│                 │     │  (pgvector)     │     │                 │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
                                                         │
                                                         ▼
                                                ┌──────────────────┐
                                                │   LLM Providers  │
                                                │ ---------------- │
                                                │   Ollama (Local) │
                                                │   OpenAI         │
                                                │   Anthropic      │
                                                │   Google Gemini  │
                                                └──────────────────┘
```

**Data flow:**
1. User creates a notebook and uploads documents to it
2. API stores document metadata + creates a `PENDING` task in the ingestion queue
3. Python worker claims the task and executes a **LangGraph** workflow:
    - **Extract**: Text and metadata extraction (using LLMs for structured metadata)
    - **Chunk**: Semantic splitting (512 tokens + overlap)
    - **Embed**: Generate vector embeddings (Phi-3, OpenAI, Gemini, etc.)
    - **Store**: Save vectors to Postgres (pgvector)
4. User starts a chat in the notebook — each message triggers a RAG query **scoped to that notebook**, with conversation history
5. Responses are streamed back via Server-Sent Events (SSE)

---

## Tech Stack

| Component | Technology | Purpose |
|---|---|---|
| API Server | Spring Boot 3.4.3 + Java 21 (Virtual Threads) | REST API, query orchestration, health checks |
| Streaming | Spring WebFlux + Project Reactor | SSE token streaming for chat responses |
| Ingestion Worker | Python 3.12 + ThreadPoolExecutor | Reliable, parallel document processing pipeline |
| AI / ML | LangChain + LLMFactory | Abstraction for OpenAI, Anthropic, Gemini, Ollama |
| Database | PostgreSQL 16 + pgvector | Documents, task queue, vector embeddings, chat history |
| Migrations | Flyway | Schema versioning (V1-V11) |

---

## Getting Started

### Prerequisites

- Java 21+
- Python 3.12+
- Docker & Docker Compose
- [Ollama](https://ollama.ai/) (optional, for local inference)

### Setup

```bash
# 1. Clone
git clone https://github.com/codesirohi/smart-notebook.git
cd smart-notebook

# 2. Start Postgres (pgvector)
make db-up

# 3. Configure Environment (if using cloud LLMs)
# Edit .env or export variables:
# export APP_MODELS_OPENAI_API_KEY=sk-...
# export APP_MODELS_GEMINI_API_KEY=...

# 4. Start the App & Worker (Parallel)
./dev.sh
```

### Verify

```bash
# Health check
curl http://localhost:8080/api/health

# Create a notebook
curl -X POST http://localhost:8080/api/notebooks \
  -H "Content-Type: application/json" \
  -d '{"name": "Research Papers"}'

# Upload a document
curl -X POST http://localhost:8080/api/notebooks/{notebookId}/documents/upload \
  -F "file=@sample.pdf"

# Check ingestion status
curl http://localhost:8080/api/tasks/{taskId}/status

# Start a chat
curl -X POST http://localhost:8080/api/notebooks/{notebookId}/chats \
  -H "Content-Type: application/json" \
  -d '{"title": "Paper Q&A"}'
```

---

## Project Structure

```
smart-notebook/
├── src/main/java/org/sirohi/smartnotebook/
│   ├── controller/          # REST endpoints (Notebook, Document, Chat, Health)
│   ├── service/             # Business logic
│   ├── gateway/             # LLM Factory & Provider Implementations
│   ├── repository/          # JPA Repositories
│   ├── model/               # Entities (Notebook, Document, Chat, Message)
│   └── exception/           # Global error handling
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/        # Flyway migrations (V1-V11)
├── worker/                  # Python ingestion worker
│   ├── worker.py            # Main poll loop
│   ├── graph.py             # LangGraph workflow definition
│   ├── processor.py         # Task execution logic
│   ├── chunker.py           # Text chunking strategies
│   ├── extractors_v2.py     # Metadata extraction (LLM-based)
│   ├── llm_factory.py       # Multi-provider LLM factory
│   ├── db.py                # Database operations
│   └── config.py            # Configuration
├── docker-compose.yml       # PostgreSQL + pgvector
├── Makefile                 # Dev commands

```

---

## Roadmap




| Phase | Focus | Status |
|---|---|---|
| **Phase 1** | Basic Ingestion & RAG | ✅ Complete |
| **Phase 1.5** | Notebooks, Chats, Streaming | ✅ Complete |
| **Phase 2** | Multi-Provider Support | ✅ Complete (OpenAI, Anthropic, Gemini, Ollama) |
| **Phase 2.5** | Robust Ingestion (LangGraph) | ✅ Complete |
| **Phase 3** | Evaluating Groundedness & Cost Optimization | Planned |
| **Phase 3.5** | User Feedback Loop & Recursive Indexing | Planned |
| **Phase 4** | Agentic Workflows & Map-Reduce Ingestion | Planned |
| **Phase 5** | Asymmetric RAG (Model Distillation) | Planned |

### Cost Optimization Strategy (Planned)

We are implementing a strict "Tri-Layer" optimization strategy to minimize cloud costs and run efficiently on local hardware:

1.  **Storage Layer**: Map-Reduce summarization during ingestion to store dense knowledge, not raw fluff.
2.  **Retrieval Layer**: Semantic Caching (stop repeated queries), HyDE (optimize precision), and Local Cross-Encoders (re-ranking).
3.  **Inference Layer**: Dynamic Model Routing (simple queries -> small models), Prompt Compression, and Token-Efficient Output Syntax.



### Key Learnings: Why This Architecture?

> We evaluated simpler stacks (e.g., Python Flask wrappers) but chose a polyglot, multi-stage pipeline for specific reasons:

1.  **Why not a simple Flask RAG service?**
    *   **Memory**: An always-on Flask server eats 150MB+ RAM idle. Our CLI worker uses **0MB** idle.
    *   **Async vs Sync**: Flask encourages blocking HTTP calls. Our Postgres-queue design handles heavy re-ranking (3s+) without blocking the API.

2.  **Why "Advanced" RAG?**
    *   **Naive RAG fails** on vague queries ("pricing?"). We use **HyDE** (Hypothetical Document Embeddings) to bridge the semantic gap.
    *   **Bi-Encoders aren't enough**. We use a second-stage **Cross-Encoder** to re-rank results, tripling precision for technical queries.

3.  **Does this scale to heavy traffic?**
    *   **Yes.** The polyglot architecture separates the **API Tier** (Java IO-bound) from the **Inference Tier** (Python GPU-bound).
    *   **Production Path**: Swap internal components without rewriting code. Postgres Queue -> Kafka/SQS. Single Worker -> GPU Auto-Scaling Group.

---

## License

This project is licensed under the MIT License.
