package org.sirohi.smartnotebook.model.router;

import org.sirohi.smartnotebook.domain.QueryContext;

/**
 * Interface for routing queries to the appropriate model tier.
 * Routing based on retrieval confidence scores.
 */
public interface ModelRouter {

    RoutingDecision route(QueryContext context);
}
