-- Create chat_messages table for storing conversation history
CREATE TABLE chat_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id     UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL,       -- 'user' or 'assistant'
    content     TEXT NOT NULL,
    citations   JSONB DEFAULT '[]',
    metadata    JSONB DEFAULT '{}',
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_messages_chat ON chat_messages(chat_id, created_at ASC);
