package org.sirohi.smartnotebook.dto;

import java.util.UUID;

/**
 * ChunkMatch extended with embedding vector for MMR re-ranking.
 * Used internally for diversity computation.
 */
public record ChunkWithEmbedding(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        int chunkIndex,
        String content,
        double similarity,
        int tokenCount,
        String metadata,
        float[] embedding) {

    /**
     * Convert to ChunkMatch (without embedding) for final results.
     */
    public ChunkMatch toChunkMatch() {
        return new ChunkMatch(
                chunkId, documentId, documentTitle,
                chunkIndex, content, similarity,
                tokenCount, metadata);
    }
}
