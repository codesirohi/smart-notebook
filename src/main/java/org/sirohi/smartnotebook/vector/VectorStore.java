package org.sirohi.smartnotebook.vector;

import org.sirohi.smartnotebook.domain.ChunkVector;
import org.sirohi.smartnotebook.domain.ScoredChunk;

import java.util.List;
import java.util.Map;

/**
 * Provider-agnostic interface for vector storage and similarity search.
 * V1: PgVectorStore. Future: Qdrant, Pinecone.
 */
public interface VectorStore {

    void insert(String docId, List<ChunkVector> chunkVectors);

    List<ScoredChunk> search(float[] queryVector, int topK, Map<String, Object> filters);

    void deleteByDocId(String docId);
}
