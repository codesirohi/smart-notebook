package org.sirohi.smartnotebook.dto;

public record Citation(
        String documentTitle,
        int chunkIndex,
        String chunkContent,
        double similarity) {
}
