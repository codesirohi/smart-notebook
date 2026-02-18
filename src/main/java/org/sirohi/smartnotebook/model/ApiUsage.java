package org.sirohi.smartnotebook.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for API usage tracking.
 * Tracks all API calls for quota enforcement and cost analysis.
 */
@Entity
@Table(name = "api_usage")
public class ApiUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "operation", nullable = false, length = 30)
    private String operation;  // 'completion', 'embedding', 'extraction'

    @Column(name = "tokens_input")
    private int tokensInput = 0;

    @Column(name = "tokens_output")
    private int tokensOutput = 0;

    // Note: total_tokens is a generated column in the DB
    // We don't map it directly since it's computed

    @Column(name = "estimated_cost_usd", precision = 10, scale = 6)
    private BigDecimal estimatedCostUsd = BigDecimal.ZERO;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "success")
    private boolean success = true;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "request_timestamp")
    private OffsetDateTime requestTimestamp;

    @Column(name = "notebook_id")
    private UUID notebookId;

    @Column(name = "chat_id")
    private UUID chatId;

    @Column(name = "document_id")
    private UUID documentId;

    @PrePersist
    protected void onCreate() {
        if (requestTimestamp == null) {
            requestTimestamp = OffsetDateTime.now();
        }
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public int getTokensInput() {
        return tokensInput;
    }

    public void setTokensInput(int tokensInput) {
        this.tokensInput = tokensInput;
    }

    public int getTokensOutput() {
        return tokensOutput;
    }

    public void setTokensOutput(int tokensOutput) {
        this.tokensOutput = tokensOutput;
    }

    public int getTotalTokens() {
        return tokensInput + tokensOutput;
    }

    public BigDecimal getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public void setEstimatedCostUsd(BigDecimal estimatedCostUsd) {
        this.estimatedCostUsd = estimatedCostUsd;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getRequestTimestamp() {
        return requestTimestamp;
    }

    public void setRequestTimestamp(OffsetDateTime requestTimestamp) {
        this.requestTimestamp = requestTimestamp;
    }

    public UUID getNotebookId() {
        return notebookId;
    }

    public void setNotebookId(UUID notebookId) {
        this.notebookId = notebookId;
    }

    public UUID getChatId() {
        return chatId;
    }

    public void setChatId(UUID chatId) {
        this.chatId = chatId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    /**
     * Builder pattern for creating usage records.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ApiUsage usage = new ApiUsage();

        public Builder provider(String providerName) {
            usage.setProviderName(providerName);
            return this;
        }

        public Builder model(String modelName) {
            usage.setModelName(modelName);
            return this;
        }

        public Builder operation(String operation) {
            usage.setOperation(operation);
            return this;
        }

        public Builder tokens(int input, int output) {
            usage.setTokensInput(input);
            usage.setTokensOutput(output);
            return this;
        }

        public Builder cost(BigDecimal costUsd) {
            usage.setEstimatedCostUsd(costUsd);
            return this;
        }

        public Builder latency(int ms) {
            usage.setLatencyMs(ms);
            return this;
        }

        public Builder success(boolean success) {
            usage.setSuccess(success);
            return this;
        }

        public Builder error(String message) {
            usage.setSuccess(false);
            usage.setErrorMessage(message);
            return this;
        }

        public Builder notebook(UUID notebookId) {
            usage.setNotebookId(notebookId);
            return this;
        }

        public Builder chat(UUID chatId) {
            usage.setChatId(chatId);
            return this;
        }

        public Builder document(UUID documentId) {
            usage.setDocumentId(documentId);
            return this;
        }

        public ApiUsage build() {
            return usage;
        }
    }
}
