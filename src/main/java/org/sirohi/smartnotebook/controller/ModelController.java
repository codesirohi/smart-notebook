package org.sirohi.smartnotebook.controller;

import org.sirohi.smartnotebook.gateway.GatewayFactory;
import org.sirohi.smartnotebook.gateway.ModelGateway;
import org.sirohi.smartnotebook.gateway.ModelHealth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Endpoint to check status of configured LLM providers.
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final GatewayFactory gatewayFactory;

    public ModelController(GatewayFactory gatewayFactory) {
        this.gatewayFactory = gatewayFactory;
    }

    @GetMapping
    public List<ModelHealth> getModelStatus() {
        return gatewayFactory.getAllGateways().stream()
                .map(ModelGateway::health)
                .sorted(Comparator.comparing(ModelHealth::provider))
                .toList();
    }
}
