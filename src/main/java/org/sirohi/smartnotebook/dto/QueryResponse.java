package org.sirohi.smartnotebook.dto;

import java.util.List;

public record QueryResponse(
        String answer,
        List<Citation> citations,
        double confidence,
        long latencyMs) {
}
