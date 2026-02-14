package org.sirohi.smartnotebook.model.router;

/**
 * Routing decision result: which model tier to use, why, and at what
 * confidence.
 */
public record RoutingDecision(
        String modelTier,
        String reason,
        double confidenceScore,
        boolean reranked) {
}
