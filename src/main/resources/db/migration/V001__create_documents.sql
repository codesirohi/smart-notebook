-- V001: Documents table
-- Stores metadata about uploaded files (PDFs, markdown, text)
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    file_size_bytes BIGINT,
    uploaded_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    error_message TEXT
);

CREATE UNIQUE INDEX idx_documents_content_hash ON documents(content_hash);
