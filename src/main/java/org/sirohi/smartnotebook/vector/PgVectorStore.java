package org.sirohi.smartnotebook.vector;

import org.sirohi.smartnotebook.domain.ChunkVector;
import org.sirohi.smartnotebook.domain.ScoredChunk;

import java.util.List;
import java.util.Map;

/**
 * V1 implementation of {@link VectorStore} backed by PostgreSQL + pgvector.
 *
 * <p>
 * Stores embeddings in the {@code chunk_vectors} table and uses HNSW index
 * for approximate nearest neighbor search.
 * </p>
 */
public class PgVectorStore implements VectorStore {

    @Override
    public void insert(String docId, List<ChunkVector> chunkVectors) {
        // TODO: implement pgvector insert (batch INSERT INTO chunk_vectors)
        throw new UnsupportedOperationException("PgVectorStore not yet implemented");
    }

    @Override
    public List<ScoredChunk> search(float[] queryVector, int topK, Map<String, Object> filters) {
        // TODO: implement pgvector cosine similarity search
        throw new UnsupportedOperationException("PgVectorStore not yet implemented");
    }

    @Override
    public void deleteByDocId(String docId) {
        // TODO: implement cascade delete of vectors for a document
        throw new UnsupportedOperationException("PgVectorStore not yet implemented");
    }
}
