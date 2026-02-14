-- V004: Query log table
-- Logs every question asked and the answer generated
CREATE TABLE queries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question TEXT NOT NULL,
    answer TEXT,
    model_used VARCHAR(100),
    model_tier VARCHAR(20),
    latency_ms INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
