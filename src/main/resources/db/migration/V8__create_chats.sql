-- Create chats table for conversational threads within notebooks
CREATE TABLE chats (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notebook_id UUID NOT NULL REFERENCES notebooks(id) ON DELETE CASCADE,
    title       VARCHAR(500),
    metadata    JSONB DEFAULT '{}',
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_chats_notebook ON chats(notebook_id);
CREATE INDEX idx_chats_updated ON chats(updated_at DESC);
