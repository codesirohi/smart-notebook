-- V2: Documents table — Source Document Registry
CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(500) NOT NULL,
    content_type    VARCHAR(50) NOT NULL,           -- 'pdf', 'markdown', 'text'
    raw_content     TEXT,                            -- full extracted text (post-processing)
    file_path       VARCHAR(1000),                   -- path to original uploaded file
    file_size_bytes BIGINT,
    checksum        VARCHAR(64),                     -- SHA-256 for deduplication
    status          VARCHAR(20) DEFAULT 'UPLOADED',  -- UPLOADED | PROCESSING | INDEXED | FAILED
    metadata        JSONB DEFAULT '{}',              -- extensible: page_count, author, etc.
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_checksum ON documents(checksum);
CREATE INDEX idx_documents_created ON documents(created_at DESC);

COMMENT ON TABLE documents IS 'Source documents uploaded by the user';
COMMENT ON COLUMN documents.checksum IS 'SHA-256 hash for dedup — reject re-uploads of identical files';
COMMENT ON COLUMN documents.raw_content IS 'Populated by worker after text extraction. NULL until processed.';
