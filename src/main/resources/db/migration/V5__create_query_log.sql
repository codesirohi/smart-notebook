-- V5: Query Log — Lightweight query audit trail
CREATE TABLE query_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question        TEXT NOT NULL,
    answer          TEXT,
    chunks_used     UUID[],                          -- references to document_chunks.id
    model_used      VARCHAR(100),
    latency_ms      INT,
    token_count_in  INT,
    token_count_out INT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_query_log_created ON query_log(created_at DESC);

COMMENT ON TABLE query_log IS 'Lightweight query audit log. Foundation for Phase 3 feedback loop.';
