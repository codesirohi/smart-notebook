-- V3: Ingestion Tasks — Durable Postgres-based Task Queue
CREATE TABLE ingestion_tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    task_type       VARCHAR(50) DEFAULT 'FULL_INGESTION',
    status          VARCHAR(20) DEFAULT 'PENDING',
                    -- PENDING → PROCESSING → COMPLETED
                    --                      → FAILED
                    --                      → DEAD_LETTER (max retries exceeded)
    priority        INT DEFAULT 0,                           -- higher = processed first
    payload         JSONB NOT NULL,                          -- input config for the worker
    result          JSONB,                                   -- output summary from worker
    error_message   TEXT,
    error_details   JSONB,                                   -- structured error info
    retry_count     INT DEFAULT 0,
    max_retries     INT DEFAULT 3,
    locked_at       TIMESTAMP WITH TIME ZONE,                -- when a worker claimed this task
    locked_by       VARCHAR(100),                            -- worker instance identifier
    completed_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_tasks_status_priority ON ingestion_tasks(status, priority DESC, created_at ASC);
CREATE INDEX idx_tasks_document ON ingestion_tasks(document_id);
CREATE INDEX idx_tasks_locked_at ON ingestion_tasks(locked_at)
    WHERE status = 'PROCESSING';  -- partial index for stale task detection

COMMENT ON TABLE ingestion_tasks IS 'Postgres-based task queue with exactly-once claim semantics';
COMMENT ON COLUMN ingestion_tasks.locked_at IS 'Set when worker claims task. Used by stale reaper to detect crashed workers.';
COMMENT ON COLUMN ingestion_tasks.locked_by IS 'Hostname/PID of claiming worker. For debugging multi-worker scenarios.';
COMMENT ON COLUMN ingestion_tasks.priority IS 'Priority queue support. 0=normal, 1+=higher priority.';
