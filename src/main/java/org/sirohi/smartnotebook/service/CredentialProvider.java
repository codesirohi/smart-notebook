package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.model.ProviderCredential;
import org.sirohi.smartnotebook.repository.ProviderCredentialRepository;
import org.sirohi.smartnotebook.security.CredentialEncryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides credentials to gateways dynamically from the database.
 *
 * This service bridges the gap between:
 * - API endpoints that save credentials to the database
 * - Gateways that need credentials to make API calls
 *
 * Features:
 * - Caches credentials in memory for performance
 * - Cache is invalidated when credentials are updated
 * - Thread-safe concurrent access
 */
@Service
public class CredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(CredentialProvider.class);

    private final ProviderCredentialRepository credentialRepository;
    private final CredentialEncryption encryption;

    // Simple in-memory cache for credentials
    private final ConcurrentHashMap<String, CachedCredential> cache = new ConcurrentHashMap<>();

    public CredentialProvider(ProviderCredentialRepository credentialRepository,
                              CredentialEncryption encryption) {
        this.credentialRepository = credentialRepository;
        this.encryption = encryption;
    }

    /**
     * Get decrypted API key for a provider.
     * Returns null if provider doesn't exist or has no credentials.
     */
    public String getApiKey(String providerName) {
        CachedCredential cached = cache.get(providerName);
        if (cached != null && !cached.isExpired()) {
            return cached.apiKey;
        }

        // Fetch from database
        Optional<ProviderCredential> credOpt = credentialRepository.findByProviderName(providerName);
        if (credOpt.isEmpty()) {
            cache.put(providerName, new CachedCredential(null, null, false));
            return null;
        }

        ProviderCredential cred = credOpt.get();

        // Ollama doesn't require API key - just check if enabled
        if ("ollama".equals(providerName)) {
            cache.put(providerName, new CachedCredential(
                    null,
                    cred.getBaseUrl(),
                    cred.isEnabled()
            ));
            return null;
        }

        // Other providers require API key
        if (!cred.hasCredentials()) {
            cache.put(providerName, new CachedCredential(null, null, false));
            return null;
        }

        String decryptedKey = encryption.decrypt(cred.getEncryptedApiKey());

        cache.put(providerName, new CachedCredential(
                decryptedKey,
                cred.getBaseUrl(),
                cred.isEnabled()
        ));

        return decryptedKey;
    }

    /**
     * Get base URL for a provider (if custom URL is configured).
     */
    public String getBaseUrl(String providerName) {
        CachedCredential cached = cache.get(providerName);
        if (cached != null && !cached.isExpired()) {
            return cached.baseUrl;
        }

        // Trigger cache population
        getApiKey(providerName);

        cached = cache.get(providerName);
        return cached != null ? cached.baseUrl : null;
    }

    /**
     * Check if a provider is enabled and has valid credentials.
     */
    public boolean isProviderAvailable(String providerName) {
        // Ollama doesn't require API key
        if ("ollama".equals(providerName)) {
            return isProviderEnabled(providerName);
        }

        String apiKey = getApiKey(providerName);
        return apiKey != null && !apiKey.isBlank() && isProviderEnabled(providerName);
    }

    /**
     * Check if provider is enabled.
     */
    public boolean isProviderEnabled(String providerName) {
        CachedCredential cached = cache.get(providerName);
        if (cached != null && !cached.isExpired()) {
            return cached.enabled;
        }

        // Trigger cache population
        getApiKey(providerName);

        cached = cache.get(providerName);
        return cached != null && cached.enabled;
    }

    /**
     * Invalidate cache for a provider.
     * Call this when credentials are updated.
     */
    public void invalidateCache(String providerName) {
        log.debug("Invalidating credential cache for provider: {}", providerName);
        cache.remove(providerName);
    }

    /**
     * Invalidate entire cache.
     */
    public void invalidateAllCache() {
        log.debug("Invalidating all credential cache");
        cache.clear();
    }

    /**
     * Simple cached credential holder.
     */
    private static class CachedCredential {
        final String apiKey;
        final String baseUrl;
        final boolean enabled;
        final long cachedAt;

        // Cache TTL: 5 minutes
        private static final long TTL_MS = 5 * 60 * 1000;

        CachedCredential(String apiKey, String baseUrl, boolean enabled) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.enabled = enabled;
            this.cachedAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > TTL_MS;
        }
    }
}
