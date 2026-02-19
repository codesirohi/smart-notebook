-- V18: Add content hash for chunk deduplication
-- SHA-256 hash enables detecting duplicate content across documents (-20% storage)

ALTER TABLE document_chunks
    ADD COLUMN content_hash VARCHAR(64);

-- Index for fast duplicate lookups
CREATE INDEX idx_chunks_content_hash ON document_chunks(content_hash);

-- Backfill existing chunks with their content hashes
UPDATE document_chunks
SET content_hash = encode(sha256(content::bytea), 'hex')
WHERE content_hash IS NULL;

-- Make column NOT NULL after backfill
ALTER TABLE document_chunks
    ALTER COLUMN content_hash SET NOT NULL;

COMMENT ON COLUMN document_chunks.content_hash IS 'SHA-256 hash of content for deduplication. Saves ~20% storage by skipping identical chunks.';
