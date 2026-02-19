package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.dto.ProviderStatusResponse;
import org.sirohi.smartnotebook.dto.ProviderTestResponse;
import org.sirohi.smartnotebook.exception.BadRequestException;
import org.sirohi.smartnotebook.exception.ResourceNotFoundException;
import org.sirohi.smartnotebook.model.ProviderCredential;
import org.sirohi.smartnotebook.repository.PipelineModelConfigRepository;
import org.sirohi.smartnotebook.repository.ProviderCredentialRepository;
import org.sirohi.smartnotebook.security.CredentialEncryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service for managing provider credentials (API keys).
 *
 * Security features:
 * - API keys encrypted at rest using AES-256-GCM
 * - Keys masked in all responses
 * - Audit logging for all credential changes
 * - Validates credentials before saving
 */
@Service
public class ProviderCredentialService {

    private static final Logger log = LoggerFactory.getLogger(ProviderCredentialService.class);

    private final ProviderCredentialRepository credentialRepository;
    private final CredentialEncryption encryption;
    private final WebClient webClient;
    private final CredentialProvider credentialProvider;
    private final PipelineModelConfigRepository pipelineConfigRepository;

    public ProviderCredentialService(ProviderCredentialRepository credentialRepository,
                                     CredentialEncryption encryption,
                                     @org.springframework.context.annotation.Lazy CredentialProvider credentialProvider,
                                     PipelineModelConfigRepository pipelineConfigRepository) {
        this.credentialRepository = credentialRepository;
        this.encryption = encryption;
        this.webClient = WebClient.builder().build();
        this.credentialProvider = credentialProvider;
        this.pipelineConfigRepository = pipelineConfigRepository;
    }

    /**
     * List all providers with their status.
     */
    @Transactional(readOnly = true)
    public List<ProviderStatusResponse> listProviders() {
        return credentialRepository.findAll().stream()
                .map(this::toStatusResponse)
                .toList();
    }

    /**
     * Get a specific provider's status.
     */
    @Transactional(readOnly = true)
    public ProviderStatusResponse getProvider(String providerName) {
        ProviderCredential cred = credentialRepository.findByProviderName(providerName)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found: " + providerName));
        return toStatusResponse(cred);
    }

    /**
     * Update credentials for a provider.
     * Validates the credentials before saving. If validation fails,
     * credentials are NOT saved and an exception is thrown.
     */
    @Transactional
    public ProviderStatusResponse updateCredentials(String providerName, String apiKey, String baseUrl) {
        log.info("Updating credentials for provider: {}", providerName);

        ProviderCredential cred = credentialRepository.findByProviderName(providerName)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found: " + providerName));

        // Set base URL if provided (needed for validation)
        String effectiveBaseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : cred.getBaseUrl();

        // Validate credentials BEFORE saving
        ProviderTestResponse validationResult = validateCredentials(providerName, apiKey, effectiveBaseUrl);

        if (!validationResult.success()) {
            log.warn("Credential validation failed for provider {}: {}", providerName, validationResult.message());
            throw new IllegalArgumentException("Invalid API key: " + validationResult.message());
        }

        // Validation passed - now save
        String encryptedKey = encryption.encrypt(apiKey);
        cred.setEncryptedApiKey(encryptedKey);

        if (baseUrl != null && !baseUrl.isBlank()) {
            cred.setBaseUrl(baseUrl);
        }

        cred.setValidationStatus("valid");
        cred.setLastValidatedAt(OffsetDateTime.now());
        cred.setEnabled(true);

        cred = credentialRepository.save(cred);

        // Invalidate cache so gateways pick up new credentials
        credentialProvider.invalidateCache(providerName);

        log.info("Credentials validated and saved for provider: {}", providerName);

        return toStatusResponse(cred);
    }

    /**
     * Validate credentials without saving.
     */
    private ProviderTestResponse validateCredentials(String providerName, String apiKey, String baseUrl) {
        long startTime = System.currentTimeMillis();

        try {
            boolean success = switch (providerName) {
                case "ollama" -> testOllamaWithUrl(baseUrl);
                case "openai" -> testOpenAIWithKey(apiKey);
                case "anthropic" -> testAnthropicWithKey(apiKey);
                case "google" -> testGoogleWithKey(apiKey);
                case "groq" -> testGroqWithKey(apiKey);
                default -> false;
            };

            int latencyMs = (int) (System.currentTimeMillis() - startTime);

            if (success) {
                return ProviderTestResponse.success(providerName, latencyMs, List.of());
            } else {
                return ProviderTestResponse.failure(providerName, "Connection test failed");
            }
        } catch (Exception e) {
            log.warn("Credential validation error for {}: {}", providerName, e.getMessage());
            return ProviderTestResponse.failure(providerName, e.getMessage());
        }
    }

    private boolean testOllamaWithUrl(String baseUrl) {
        String url = (baseUrl != null) ? baseUrl : "http://localhost:11434";
        try {
            String response = webClient.get()
                    .uri(url + "/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return response != null && response.contains("models");
        } catch (Exception e) {
            log.debug("Ollama test failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean testOpenAIWithKey(String apiKey) {
        try {
            String response = webClient.get()
                    .uri("https://api.openai.com/v1/models")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return response != null && response.contains("data");
        } catch (Exception e) {
            log.debug("OpenAI test failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean testAnthropicWithKey(String apiKey) {
        try {
            // Anthropic: test by calling models endpoint
            String response = webClient.get()
                    .uri("https://api.anthropic.com/v1/models")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return response != null && response.contains("data");
        } catch (Exception e) {
            log.debug("Anthropic test failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean testGoogleWithKey(String apiKey) {
        try {
            String response = webClient.get()
                    .uri("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return response != null && response.contains("models");
        } catch (Exception e) {
            log.debug("Google test failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean testGroqWithKey(String apiKey) {
        try {
            String response = webClient.get()
                    .uri("https://api.groq.com/openai/v1/models")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return response != null && response.contains("data");
        } catch (Exception e) {
            log.debug("Groq test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Delete credentials for a provider.
     */
    @Transactional
    public void deleteCredentials(String providerName) {
        log.info("Deleting credentials for provider: {}", providerName);

        if (!credentialRepository.existsByProviderName(providerName)) {
            throw new ResourceNotFoundException("Provider not found: " + providerName);
        }

        credentialRepository.clearCredentials(providerName);

        // Invalidate cache
        credentialProvider.invalidateCache(providerName);

        log.info("Credentials deleted for provider: {}", providerName);
    }

    /**
     * Test credentials for a provider.
     */
    @Transactional
    public ProviderTestResponse testCredentials(String providerName) {
        log.info("Testing credentials for provider: {}", providerName);

        ProviderCredential cred = credentialRepository.findByProviderName(providerName)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found: " + providerName));

        if (!cred.hasCredentials() && !providerName.equals("ollama")) {
            return ProviderTestResponse.failure(providerName, "No credentials configured");
        }

        long startTime = System.currentTimeMillis();

        try {
            boolean success = testProviderConnection(cred);
            int latencyMs = (int) (System.currentTimeMillis() - startTime);

            if (success) {
                // Update validation status
                credentialRepository.updateValidationStatus(providerName, "valid", OffsetDateTime.now());
                log.info("Credentials valid for provider: {} ({}ms)", providerName, latencyMs);
                return ProviderTestResponse.success(providerName, latencyMs, List.of());
            } else {
                credentialRepository.updateValidationStatus(providerName, "invalid", OffsetDateTime.now());
                return ProviderTestResponse.failure(providerName, "Connection test failed");
            }

        } catch (Exception e) {
            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            log.warn("Credentials test failed for provider: {} ({}ms): {}", providerName, latencyMs, e.getMessage());
            credentialRepository.updateValidationStatus(providerName, "invalid", OffsetDateTime.now());
            return ProviderTestResponse.failure(providerName, e.getMessage());
        }
    }

    /**
     * Enable or disable a provider.
     */
    @Transactional
    public ProviderStatusResponse setEnabled(String providerName, boolean enabled) {
        log.info("{} provider: {}", enabled ? "Enabling" : "Disabling", providerName);
        String normalizedProvider = providerName.trim().toLowerCase();

        if (!credentialRepository.existsByProviderName(normalizedProvider)) {
            throw new ResourceNotFoundException("Provider not found: " + providerName);
        }
        if (!enabled) {
            int inUseCount = pipelineConfigRepository.findByProviderNameAndActiveTrue(normalizedProvider).size();
            if (inUseCount > 0) {
                throw new BadRequestException(
                        "Cannot disable provider '" + normalizedProvider + "' because it is used in "
                                + inUseCount
                                + " active pipeline configuration(s). Update pipeline model config first.");
            }
        }

        credentialRepository.setEnabled(normalizedProvider, enabled);

        // Invalidate cache
        credentialProvider.invalidateCache(normalizedProvider);

        return getProvider(normalizedProvider);
    }

    /**
     * Get decrypted API key for a provider (for internal use only).
     */
    public String getDecryptedApiKey(String providerName) {
        ProviderCredential cred = credentialRepository.findByProviderName(providerName)
                .orElse(null);

        if (cred == null || !cred.hasCredentials()) {
            return null;
        }

        return encryption.decrypt(cred.getEncryptedApiKey());
    }

    /**
     * Check if provider is available (enabled and has credentials or is Ollama).
     */
    @Transactional(readOnly = true)
    public boolean isProviderAvailable(String providerName) {
        return credentialRepository.findByProviderName(providerName)
                .map(cred -> cred.isEnabled() && (cred.hasCredentials() || providerName.equals("ollama")))
                .orElse(false);
    }

    // ─── Private Helpers ───

    private ProviderStatusResponse toStatusResponse(ProviderCredential cred) {
        String maskedKey = null;
        if (cred.hasCredentials()) {
            String decrypted = encryption.decrypt(cred.getEncryptedApiKey());
            maskedKey = CredentialEncryption.maskApiKey(decrypted);
        }

        List<String> supportedOps = ProviderStatusResponse.getSupportedOperations(cred.getProviderName());

        return ProviderStatusResponse.from(cred, maskedKey, supportedOps);
    }

    private boolean testProviderConnection(ProviderCredential cred) {
        String providerName = cred.getProviderName();
        String apiKey = cred.hasCredentials() ? encryption.decrypt(cred.getEncryptedApiKey()) : null;
        String baseUrl = cred.getBaseUrl();

        return switch (providerName) {
            case "ollama" -> testOllamaWithUrl(baseUrl);
            case "openai" -> testOpenAIWithKey(apiKey);
            case "anthropic" -> testAnthropicWithKey(apiKey);
            case "google" -> testGoogleWithKey(apiKey);
            case "groq" -> testGroqWithKey(apiKey);
            default -> false;
        };
    }
}
