-- V006: Pipeline runs table
-- Tracks ingestion pipeline executions for observability and A/B testing
CREATE TABLE pipeline_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_id UUID NOT NULL REFERENCES documents(id),
    pipeline_version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    chunk_count INTEGER,
    quality_score FLOAT,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    config JSONB DEFAULT '{}'::jsonb
);

CREATE INDEX idx_pipeline_runs_doc_id ON pipeline_runs(doc_id);
