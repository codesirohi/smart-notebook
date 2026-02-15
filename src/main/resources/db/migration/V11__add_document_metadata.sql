-- V11: Add Metadata Column to Documents
-- Stores structured extraction results (Title, Summary, Topics) from LangExtract

ALTER TABLE documents
ADD COLUMN IF NOT EXISTS metadata JSONB DEFAULT '{}';

COMMENT ON COLUMN documents.metadata IS 'Structured extraction results: title, summary, topics, entities';
