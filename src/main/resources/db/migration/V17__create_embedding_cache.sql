-- V17: Query Embedding Cache
-- Caches embeddings for repeated queries to save 100-240ms per cache hit.
-- Uses SHA-256 hash of normalized query text for fast lookups.

CREATE TABLE query_embedding_cache (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query_hash      VARCHAR(64) NOT NULL,           -- SHA-256 hash of normalized query
    query_text      TEXT NOT NULL,                  -- Original query (for debugging)
    embedding_model VARCHAR(100) NOT NULL,          -- Model used (cache key part)
    embedding       vector(768),                    -- Cached embedding vector
    hit_count       INT DEFAULT 0,                  -- Analytics: how often this cache entry is used
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_used_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    UNIQUE(query_hash, embedding_model)             -- One embedding per query+model combo
);

-- Fast lookup index on hash + model
CREATE INDEX idx_embedding_cache_lookup ON query_embedding_cache(query_hash, embedding_model);

-- Index for TTL cleanup (delete old unused entries)
CREATE INDEX idx_embedding_cache_last_used ON query_embedding_cache(last_used_at);

COMMENT ON TABLE query_embedding_cache IS 'Caches query embeddings to avoid redundant API calls. Saves 100-240ms per cache hit.';
COMMENT ON COLUMN query_embedding_cache.query_hash IS 'SHA-256 hash of lowercased, trimmed query text';
COMMENT ON COLUMN query_embedding_cache.hit_count IS 'Number of times this cached embedding was reused';
