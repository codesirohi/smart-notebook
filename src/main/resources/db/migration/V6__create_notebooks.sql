-- Create notebooks table for organizing documents into workspaces
CREATE TABLE notebooks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    metadata    JSONB DEFAULT '{}',
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_notebooks_created ON notebooks(created_at DESC);

-- Create a default notebook
INSERT INTO notebooks (id, name, description)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default', 'Default notebook for unscoped documents');
