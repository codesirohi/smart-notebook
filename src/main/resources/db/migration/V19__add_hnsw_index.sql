-- Create an HNSW index on the embedding column for the document_chunks table.
-- Using vector_cosine_ops which is optimized for cosine similarity searches (<=> operator).
-- HNSW (Hierarchical Navigable Small World) provides much faster approximate nearest neighbor (ANN) search.

-- It's important to build the index concurrently if this were a massive production table, 
-- but in Flyway scripts running on startup, standard CREATE INDEX is generally used.
CREATE INDEX ON document_chunks USING hnsw (embedding vector_cosine_ops);
