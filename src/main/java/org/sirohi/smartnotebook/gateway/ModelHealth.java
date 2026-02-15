package org.sirohi.smartnotebook.gateway;

public record ModelHealth(
        boolean available,
        String provider,
        String model,
        String message) {
}
