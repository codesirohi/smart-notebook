package org.sirohi.smartnotebook.gateway;

import org.sirohi.smartnotebook.model.ModelProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory to retrieve the correct ModelGateway based on provider.
 */
@Component
public class GatewayFactory {

    private final Map<String, ModelGateway> gateways;

    public GatewayFactory(List<ModelGateway> GatewayList) {
        this.gateways = GatewayList.stream()
                .collect(Collectors.toMap(ModelGateway::providerId, Function.identity()));
    }

    /**
     * Get gateway by explicit provider name.
     */
    public ModelGateway getGateway(String providerId) {
        return gateways.getOrDefault(providerId, gateways.get("ollama"));
    }

    /**
     * Get gateway by resolving model name using ModelProvider heuristic.
     */
    public ModelGateway getGatewayForModel(String modelName) {
        ModelProvider provider = ModelProvider.fromModelName(modelName);
        return getGateway(provider.getProviderId());
    }

    public java.util.Collection<ModelGateway> getAllGateways() {
        return gateways.values();
    }
}
