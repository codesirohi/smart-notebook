-- V13: Revert embedding column to 384 dimensions (for all-minilm)

-- 1. Drop the existing index
DROP INDEX IF EXISTS idx_chunks_embedding;

-- 2. Truncate the table (incompatible vectors)
TRUNCATE TABLE document_chunks;

-- 3. Alter the column type back to 384
ALTER TABLE document_chunks 
ALTER COLUMN embedding TYPE vector(384);

-- 4. Recreate the index
CREATE INDEX idx_chunks_embedding ON document_chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

COMMENT ON COLUMN document_chunks.embedding IS '384-dim vector from all-minilm';
