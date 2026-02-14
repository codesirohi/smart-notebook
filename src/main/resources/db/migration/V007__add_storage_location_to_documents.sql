-- V007: Add storage_location to documents
-- Tracks where the physical file is stored (local path or object storage key)
ALTER TABLE documents
ADD COLUMN storage_location VARCHAR(1024);
