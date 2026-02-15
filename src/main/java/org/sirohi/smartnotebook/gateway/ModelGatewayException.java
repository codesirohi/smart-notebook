package org.sirohi.smartnotebook.gateway;

public class ModelGatewayException extends RuntimeException {

    public ModelGatewayException(String message) {
        super(message);
    }

    public ModelGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
