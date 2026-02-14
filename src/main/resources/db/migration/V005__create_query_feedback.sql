-- V005: Query feedback table
-- Stores quality metrics for each answer (groundedness, coverage, routing)
CREATE TABLE query_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query_id UUID NOT NULL REFERENCES queries(id),
    groundedness_score FLOAT,
    coverage_score FLOAT,
    retrieval_scores JSONB,
    routing_decision JSONB,
    patch_plan JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_query_feedback_query_id ON query_feedback(query_id);
