-- V003: Chunk vectors table (pgvector)
-- Stores embedding vectors for semantic search
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE chunk_vectors (
    chunk_id UUID PRIMARY KEY REFERENCES chunks(id) ON DELETE CASCADE,
    embedding vector(384),
    embedding_model VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- HNSW index for fast approximate nearest neighbor search
CREATE INDEX idx_chunk_vectors_embedding ON chunk_vectors
    USING hnsw (embedding vector_cosine_ops);
