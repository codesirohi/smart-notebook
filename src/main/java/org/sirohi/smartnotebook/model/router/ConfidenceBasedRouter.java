package org.sirohi.smartnotebook.model.router;

import org.sirohi.smartnotebook.domain.QueryContext;

/**
 * V1 implementation of {@link ModelRouter} using confidence-based routing.
 *
 * <p>
 * Routing logic:
 * </p>
 * <ul>
 * <li>Top retrieval score &gt; 0.82 → Budget model (~70% of queries)</li>
 * <li>After reranking &gt; 0.75 → Budget model (~15%)</li>
 * <li>Otherwise → Frontier model (~15%)</li>
 * </ul>
 */
public class ConfidenceBasedRouter implements ModelRouter {

    @Override
    public RoutingDecision route(QueryContext context) {
        // TODO: implement confidence-based routing logic
        throw new UnsupportedOperationException("ConfidenceBasedRouter not yet implemented");
    }
}
