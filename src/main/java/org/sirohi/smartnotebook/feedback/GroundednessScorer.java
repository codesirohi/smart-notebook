package org.sirohi.smartnotebook.feedback;

import java.util.List;

/**
 * Scores how well an LLM-generated answer is grounded in the retrieved chunks.
 *
 * <p>
 * Used asynchronously after every query response to detect hallucinations
 * and feed the quality dashboard.
 * </p>
 */
public interface GroundednessScorer {

    /**
     * Scores groundedness of an answer against source chunks.
     *
     * @param answer       the LLM-generated answer
     * @param sourceChunks the chunks used as context
     * @return a score between 0.0 (hallucinated) and 1.0 (fully grounded)
     */
    double score(String answer, List<String> sourceChunks);
}
