-- Add notebook_id FK to documents table
ALTER TABLE documents ADD COLUMN notebook_id UUID REFERENCES notebooks(id);

-- Assign all existing documents to the default notebook
UPDATE documents SET notebook_id = '00000000-0000-0000-0000-000000000001' WHERE notebook_id IS NULL;

-- Make notebook_id NOT NULL after backfill
ALTER TABLE documents ALTER COLUMN notebook_id SET NOT NULL;
ALTER TABLE documents ALTER COLUMN notebook_id SET DEFAULT '00000000-0000-0000-0000-000000000001';

CREATE INDEX idx_documents_notebook ON documents(notebook_id);
