-- V14: Resize embedding column for nomic-embed-text (768 dimensions)
-- Note: Changing vector dimension requires recreating the index and truncating data
-- Old all-minilm (384-dim) embeddings are incompatible with nomic-embed-text (768-dim)

-- 1. Drop the existing index
DROP INDEX IF EXISTS idx_chunks_embedding;

-- 2. Truncate the table because old 384-dim embeddings are incompatible
TRUNCATE TABLE document_chunks;

-- 3. Alter the column type to 768 dimensions
ALTER TABLE document_chunks
ALTER COLUMN embedding TYPE vector(768);

-- 4. Recreate the index
CREATE INDEX idx_chunks_embedding ON document_chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

COMMENT ON COLUMN document_chunks.embedding IS '768-dim vector from nomic-embed-text';
