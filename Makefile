# Smart Notebook — Development Commands

.PHONY: help db-up db-down db-reset api worker test clean status

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

# ─── Database ───
db-up: ## Start PostgreSQL via Docker Compose
	docker compose up -d postgres
	@echo "Waiting for Postgres..."
	@sleep 3
	@docker compose exec postgres pg_isready -U notebook -d smartnotebook || echo "Warning: Postgres not ready yet"

db-down: ## Stop PostgreSQL
	docker compose down

db-reset: ## Reset database (delete all data)
	docker compose down -v
	$(MAKE) db-up

# ─── API (Spring Boot) ───
api: ## Start Spring Boot API
	SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx384m"

# ─── Worker (Python) ───
worker: ## Start ingestion worker
	cd worker && python3 worker.py

worker-reap: ## Run stale task reaper
	cd worker && python3 worker.py --reap-only

worker-requeue: ## Requeue failed tasks
	cd worker && python3 worker.py --requeue-failed

worker-setup: ## Set up Python worker virtualenv
	cd worker && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt

# ─── Testing ───
test: ## Run all tests
	./mvnw test

test-unit: ## Run unit tests only
	./mvnw test -Dtest='*Test' -DfailIfNoTests=false

# ─── Status ───
status: ## Show status of all services
	@echo "=== Docker ==="
	@docker compose ps 2>/dev/null || echo "Docker Compose not running"
	@echo ""
	@echo "=== API ==="
	@curl -s http://localhost:8080/api/health 2>/dev/null | python3 -m json.tool || echo "API not running"
	@echo ""
	@echo "=== Ollama ==="
	@curl -s http://localhost:11434/api/tags 2>/dev/null | python3 -m json.tool || echo "Ollama not running"

# ─── Clean ───
clean: ## Clean build artifacts
	./mvnw clean
	rm -rf uploads/

# ─── Utils ───
kill-port: ## Kill process on port 8080
	./kill_port.sh

stop: ## Stop all services (App, Worker, Docker)
	./stop.sh
