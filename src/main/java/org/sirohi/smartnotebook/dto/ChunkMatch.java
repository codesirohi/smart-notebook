package org.sirohi.smartnotebook.dto;

import java.util.UUID;

public record ChunkMatch(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        int chunkIndex,
        String content,
        double similarity,
        int tokenCount,
        String metadata) {
}
