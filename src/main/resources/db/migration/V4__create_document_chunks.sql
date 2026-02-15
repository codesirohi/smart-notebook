-- V4: Document Chunks — Vector Store
CREATE TABLE document_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index     INT NOT NULL,                    -- ordering within document
    content         TEXT NOT NULL,                    -- the chunk text
    token_count     INT,                             -- actual token count
    embedding       vector(384),                     -- Phi-3 Mini embedding dimension
    metadata        JSONB DEFAULT '{}',              -- page_number, section_title, etc.
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    UNIQUE(document_id, chunk_index)                 -- no duplicate chunks per document
);

CREATE INDEX idx_chunks_document ON document_chunks(document_id);

-- IVFFlat index for approximate nearest neighbor search
-- lists=100 is appropriate for < 50K chunks. Recalibrate at scale.
CREATE INDEX idx_chunks_embedding ON document_chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

COMMENT ON TABLE document_chunks IS 'Chunked document text with vector embeddings for similarity search';
COMMENT ON COLUMN document_chunks.embedding IS '384-dim vector from Phi-3 Mini. Cosine similarity for retrieval.';
COMMENT ON COLUMN document_chunks.metadata IS 'Chunk-level metadata: page_number, section_title, heading_hierarchy, etc.';
