-- V15: Worker heartbeats for readiness checks
CREATE TABLE IF NOT EXISTS worker_heartbeats (
    worker_id       VARCHAR(200) PRIMARY KEY,
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_seen_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_worker_heartbeats_last_seen
    ON worker_heartbeats (last_seen_at DESC);

COMMENT ON TABLE worker_heartbeats IS 'Heartbeat records from Python ingestion workers.';
COMMENT ON COLUMN worker_heartbeats.last_seen_at IS 'Updated every few seconds by active workers.';
