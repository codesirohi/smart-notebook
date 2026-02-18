package org.sirohi.smartnotebook.dto;

import java.util.List;

/**
 * SSE event payload for streaming chat responses.
 * Each event carries either a text token or a final completion summary.
 */
public record ChatTokenEvent(
        String token,
        boolean done,
        List<Citation> citations,
        double confidence,
        long latencyMs,
        String error) {

    /** Factory for intermediate token events. */
    public static ChatTokenEvent token(String token) {
        return new ChatTokenEvent(token, false, List.of(), 0.0, 0, null);
    }

    /** Factory for the final completion event with metadata. */
    public static ChatTokenEvent done(List<Citation> citations, double confidence, long latencyMs) {
        return new ChatTokenEvent("", true, citations, confidence, latencyMs, null);
    }

    /** Factory for error events. */
    public static ChatTokenEvent error(String message) {
        return new ChatTokenEvent(null, true, List.of(), 0.0, 0, message);
    }
}
