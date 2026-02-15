-- V10: Align Legacy Schema with Current Codebase
-- Detects if legacy tables exist (chunks, chunk_vectors) and migrates data to new tables (document_chunks, ingestion_tasks)
-- This fixes the state where V3/V4 were marked as executed but tables are missing.

-- 1. Ensure ingestion_tasks exists (V3 definition)
CREATE TABLE IF NOT EXISTS ingestion_tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    task_type       VARCHAR(50) DEFAULT 'FULL_INGESTION',
    status          VARCHAR(20) DEFAULT 'PENDING',
    priority        INT DEFAULT 0,
    payload         JSONB NOT NULL DEFAULT '{}',
    result          JSONB,
    error_message   TEXT,
    error_details   JSONB,
    retry_count     INT DEFAULT 0,
    max_retries     INT DEFAULT 3,
    locked_at       TIMESTAMP WITH TIME ZONE,
    locked_by       VARCHAR(100),
    completed_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for ingestion_tasks
CREATE INDEX IF NOT EXISTS idx_tasks_status_priority ON ingestion_tasks(status, priority DESC, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_tasks_document ON ingestion_tasks(document_id);

-- 2. Migrate pipeline_runs -> ingestion_tasks (if legacy exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'pipeline_runs') THEN
        INSERT INTO ingestion_tasks (id, document_id, status, payload, created_at, completed_at)
        SELECT id, doc_id, status, config, started_at, completed_at
        FROM pipeline_runs
        ON CONFLICT (id) DO NOTHING;
    END IF;
END $$;


-- 3. Ensure document_chunks exists (V4 definition)
CREATE TABLE IF NOT EXISTS document_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL,
    token_count     INT,
    embedding       vector(384),
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(document_id, chunk_index)
);

-- Index for valid chunks
CREATE INDEX IF NOT EXISTS idx_chunks_document ON document_chunks(document_id);
-- CREATE INDEX IF NOT EXISTS idx_chunks_embedding ON document_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 4. Migrate chunks + chunk_vectors -> document_chunks (if legacy exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'chunks') AND
       EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'chunk_vectors') THEN

        INSERT INTO document_chunks (id, document_id, chunk_index, content, token_count, metadata, created_at, embedding)
        SELECT c.id, c.doc_id, c.position, c.content, c.token_count, c.metadata, c.created_at, cv.embedding
        FROM chunks c
        LEFT JOIN chunk_vectors cv ON c.id = cv.chunk_id
        ON CONFLICT (document_id, chunk_index) DO NOTHING;
    END IF;
END $$;

-- 5. Drop legacy tables (optional, but cleaner)
-- DROP TABLE IF EXISTS chunk_vectors CASCADE;
-- DROP TABLE IF EXISTS chunks CASCADE;
-- DROP TABLE IF EXISTS pipeline_runs CASCADE;
