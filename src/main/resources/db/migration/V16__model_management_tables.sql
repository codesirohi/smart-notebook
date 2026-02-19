-- ═══════════════════════════════════════════════════════════════════════════
-- V16: Model Management Tables
-- ═══════════════════════════════════════════════════════════════════════════
-- Purpose: Enable dynamic model management via UI
--   - Local Ollama model install/uninstall with hardware recommendations
--   - Secure API key storage for paid providers (encrypted)
--   - Per-step pipeline model configuration
--   - Usage tracking and quota management
-- ═══════════════════════════════════════════════════════════════════════════

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. PROVIDER CREDENTIALS (Encrypted API Keys)
-- ═══════════════════════════════════════════════════════════════════════════
-- Stores API keys for paid providers (OpenAI, Anthropic, Google, Groq)
-- Keys are encrypted using AES-256-GCM before storage
CREATE TABLE provider_credentials (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_name       VARCHAR(50) NOT NULL UNIQUE,  -- 'openai', 'anthropic', 'google', 'groq'
    display_name        VARCHAR(100),                 -- 'OpenAI', 'Anthropic', 'Google AI'
    encrypted_api_key   TEXT,                         -- AES-256-GCM encrypted (NULL if not set)
    base_url            VARCHAR(500),                 -- Custom endpoint URL (optional)
    is_enabled          BOOLEAN DEFAULT true,
    last_validated_at   TIMESTAMP WITH TIME ZONE,     -- Last successful API test
    validation_status   VARCHAR(20) DEFAULT 'unknown', -- 'valid', 'invalid', 'unknown'
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

COMMENT ON TABLE provider_credentials IS 'Stores encrypted API credentials for LLM providers';
COMMENT ON COLUMN provider_credentials.encrypted_api_key IS 'AES-256-GCM encrypted API key (Base64 encoded)';
COMMENT ON COLUMN provider_credentials.validation_status IS 'Result of last API key validation test';

-- Seed default providers (without keys - ollama doesn't need one)
INSERT INTO provider_credentials (provider_name, display_name, is_enabled, validation_status) VALUES
    ('ollama', 'Ollama (Local)', true, 'valid'),
    ('openai', 'OpenAI', false, 'unknown'),
    ('anthropic', 'Anthropic', false, 'unknown'),
    ('google', 'Google AI (Gemini)', false, 'unknown'),
    ('groq', 'Groq Cloud', false, 'unknown');

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. LOCAL MODELS (Ollama Model Registry)
-- ═══════════════════════════════════════════════════════════════════════════
-- Tracks available and installed Ollama models with hardware requirements
CREATE TABLE local_models (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name          VARCHAR(100) NOT NULL UNIQUE, -- 'llama3:8b', 'nomic-embed-text'
    model_type          VARCHAR(20) NOT NULL,         -- 'llm', 'embedding'
    display_name        VARCHAR(200),                 -- 'Llama 3 (8B)'
    description         TEXT,
    size_bytes          BIGINT,
    parameter_count     VARCHAR(20),                  -- '7B', '13B', '70B'
    quantization        VARCHAR(20),                  -- 'Q4_0', 'Q8_0', 'F16'
    context_length      INT,                          -- 4096, 8192, etc.
    min_ram_gb          INT NOT NULL DEFAULT 4,       -- Minimum RAM required
    recommended_ram_gb  INT,                          -- Recommended RAM for best perf
    is_installed        BOOLEAN DEFAULT false,
    is_recommended      BOOLEAN DEFAULT false,        -- Based on hardware check
    install_status      VARCHAR(20) DEFAULT 'not_installed', -- 'installing', 'installed', 'failed'
    install_progress    INT DEFAULT 0,                -- 0-100 percentage
    installed_at        TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

COMMENT ON TABLE local_models IS 'Registry of available Ollama models with hardware requirements';
COMMENT ON COLUMN local_models.min_ram_gb IS 'Minimum RAM in GB required to run this model';
COMMENT ON COLUMN local_models.is_recommended IS 'True if model is recommended for current hardware';

-- Create index for efficient filtering
CREATE INDEX idx_local_models_type ON local_models(model_type);
CREATE INDEX idx_local_models_installed ON local_models(is_installed);

-- Seed curated model list (popular models with hardware requirements)
INSERT INTO local_models (model_name, model_type, display_name, description, parameter_count, min_ram_gb, recommended_ram_gb, context_length) VALUES
    -- LLM Models (sorted by RAM requirement)
    ('tinyllama', 'llm', 'TinyLlama (1.1B)', 'Ultra-lightweight, fast inference. Good for testing and low-resource environments.', '1.1B', 2, 4, 2048),
    ('phi3', 'llm', 'Phi-3 Mini (3.8B)', 'Microsoft small model with excellent quality for its size. Great for general use.', '3.8B', 4, 8, 4096),
    ('llama3.2', 'llm', 'Llama 3.2 (3B)', 'Meta latest model, excellent quality and fast. Recommended for most users.', '3B', 4, 8, 8192),
    ('llama3:8b', 'llm', 'Llama 3 (8B)', 'Meta flagship model with best balance of speed and quality.', '8B', 8, 16, 8192),
    ('mistral', 'llm', 'Mistral (7B)', 'Strong reasoning capabilities. Excellent for complex tasks.', '7B', 8, 16, 8192),
    ('codellama:7b', 'llm', 'Code Llama (7B)', 'Optimized for code generation and understanding.', '7B', 8, 16, 4096),
    ('qwen2.5', 'llm', 'Qwen 2.5 (7B)', 'Alibaba model, strong at code and reasoning.', '7B', 8, 16, 8192),
    ('mixtral', 'llm', 'Mixtral 8x7B', 'Mixture of Experts model, high quality but resource intensive.', '47B', 32, 48, 32768),
    ('llama3:70b-q4', 'llm', 'Llama 3 (70B Q4)', 'Largest Llama model, best quality but requires significant resources.', '70B', 40, 64, 8192),

    -- Embedding Models (sorted by quality)
    ('all-minilm', 'embedding', 'All-MiniLM (384-dim)', 'Tiny but decent embeddings. Use when RAM is very limited.', NULL, 1, 2, 512),
    ('nomic-embed-text', 'embedding', 'Nomic Embed Text (768-dim)', 'Best local embeddings. Recommended for most RAG applications.', NULL, 2, 4, 8192),
    ('mxbai-embed-large', 'embedding', 'MxBai Large (1024-dim)', 'High quality embeddings with larger dimensions.', NULL, 4, 8, 512),
    ('snowflake-arctic-embed', 'embedding', 'Snowflake Arctic Embed', 'Excellent for RAG with long context support.', NULL, 4, 8, 512);

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. PIPELINE MODEL CONFIGURATION
-- ═══════════════════════════════════════════════════════════════════════════
-- Configures which model to use for each pipeline step
-- NULL notebook_id means global default, otherwise notebook-specific override
CREATE TABLE pipeline_model_config (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notebook_id         UUID REFERENCES notebooks(id) ON DELETE CASCADE,  -- NULL = global default
    step_name           VARCHAR(30) NOT NULL,         -- 'extraction', 'embedding', 'chat'
    provider_name       VARCHAR(50) NOT NULL,         -- 'ollama', 'openai', 'anthropic', 'google', 'groq'
    model_name          VARCHAR(100) NOT NULL,        -- 'gpt-4', 'tinyllama', 'nomic-embed-text'
    parameters          JSONB DEFAULT '{}',           -- temperature, max_tokens, etc.
    is_active           BOOLEAN DEFAULT true,
    priority            INT DEFAULT 0,                -- For fallback ordering (higher = preferred)
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(notebook_id, step_name)                    -- One model per step per notebook
);

COMMENT ON TABLE pipeline_model_config IS 'Per-step model configuration for ingestion and query pipelines';
COMMENT ON COLUMN pipeline_model_config.step_name IS 'Pipeline step: extraction, embedding, or chat';
COMMENT ON COLUMN pipeline_model_config.parameters IS 'Model-specific parameters like temperature, max_tokens';

-- Index for efficient config lookup
CREATE INDEX idx_pipeline_config_notebook ON pipeline_model_config(notebook_id);
CREATE INDEX idx_pipeline_config_step ON pipeline_model_config(step_name);

-- Seed default global config (using current baseline models)
INSERT INTO pipeline_model_config (notebook_id, step_name, provider_name, model_name, parameters) VALUES
    (NULL, 'extraction', 'ollama', 'tinyllama', '{"temperature": 0.2, "max_tokens": 500}'),
    (NULL, 'embedding', 'ollama', 'nomic-embed-text', '{}'),
    (NULL, 'chat', 'ollama', 'tinyllama', '{"temperature": 0.7, "max_tokens": 1024}');

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. API USAGE TRACKING
-- ═══════════════════════════════════════════════════════════════════════════
-- Tracks all API calls for quota enforcement and cost analysis
CREATE TABLE api_usage (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_name       VARCHAR(50) NOT NULL,
    model_name          VARCHAR(100),
    operation           VARCHAR(30) NOT NULL,         -- 'completion', 'embedding', 'extraction'
    tokens_input        INT DEFAULT 0,
    tokens_output       INT DEFAULT 0,
    total_tokens        INT GENERATED ALWAYS AS (tokens_input + tokens_output) STORED,
    estimated_cost_usd  DECIMAL(10, 6) DEFAULT 0,
    latency_ms          INT,
    success             BOOLEAN DEFAULT true,
    error_message       TEXT,
    request_timestamp   TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    notebook_id         UUID REFERENCES notebooks(id) ON DELETE SET NULL,
    chat_id             UUID REFERENCES chats(id) ON DELETE SET NULL,
    document_id         UUID REFERENCES documents(id) ON DELETE SET NULL
);

COMMENT ON TABLE api_usage IS 'Tracks API usage for quota enforcement and cost analysis';
COMMENT ON COLUMN api_usage.estimated_cost_usd IS 'Estimated cost in USD based on token pricing';

-- Indexes for efficient quota checking and analytics
CREATE INDEX idx_api_usage_provider_time ON api_usage(provider_name, request_timestamp DESC);
-- NOTE: DATE(timestamptz) / date_trunc(timestamptz) are STABLE (timezone-dependent),
-- so we normalize to UTC first to keep index expressions IMMUTABLE.
CREATE INDEX idx_api_usage_daily ON api_usage(provider_name, ((request_timestamp AT TIME ZONE 'UTC')::date));
CREATE INDEX idx_api_usage_monthly ON api_usage(provider_name, DATE_TRUNC('month', request_timestamp AT TIME ZONE 'UTC'));
CREATE INDEX idx_api_usage_notebook ON api_usage(notebook_id, request_timestamp DESC);

-- ═══════════════════════════════════════════════════════════════════════════
-- 5. QUOTA LIMITS CONFIGURATION
-- ═══════════════════════════════════════════════════════════════════════════
-- Configurable limits per provider (no defaults per user request)
CREATE TABLE quota_limits (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_name       VARCHAR(50) NOT NULL,
    limit_type          VARCHAR(30) NOT NULL,         -- 'daily_tokens', 'monthly_cost_usd', 'requests_per_minute'
    limit_value         BIGINT NOT NULL,              -- The limit amount
    window_seconds      INT NOT NULL,                 -- 86400 for daily, 2592000 for monthly, 60 for rpm
    current_usage       BIGINT DEFAULT 0,             -- Cached current usage (updated periodically)
    reset_at            TIMESTAMP WITH TIME ZONE,     -- When quota resets
    is_enabled          BOOLEAN DEFAULT true,
    alert_threshold_pct INT DEFAULT 80,               -- Alert when usage reaches this %
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(provider_name, limit_type)
);

COMMENT ON TABLE quota_limits IS 'Configurable quota limits per provider';
COMMENT ON COLUMN quota_limits.window_seconds IS 'Time window: 86400 (daily), 2592000 (monthly), 60 (per minute)';
COMMENT ON COLUMN quota_limits.alert_threshold_pct IS 'Send alert when usage exceeds this percentage';

-- No default limits inserted per user decision - users set limits manually

-- ═══════════════════════════════════════════════════════════════════════════
-- 6. HARDWARE INFO (Cached for recommendations)
-- ═══════════════════════════════════════════════════════════════════════════
-- Caches hardware detection results for model recommendations
CREATE TABLE hardware_info (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    total_ram_gb        INT,
    available_ram_gb    INT,
    cpu_cores           INT,
    cpu_model           VARCHAR(200),
    gpu_name            VARCHAR(200),
    gpu_vram_gb         INT,
    os_name             VARCHAR(100),
    os_version          VARCHAR(50),
    recommended_tier    VARCHAR(20),                  -- 'minimal', 'standard', 'performance', 'high_end'
    last_checked_at     TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT single_row CHECK (id = '00000000-0000-0000-0000-000000000001'::uuid)
);

COMMENT ON TABLE hardware_info IS 'Cached hardware detection results (singleton table)';
COMMENT ON COLUMN hardware_info.recommended_tier IS 'Hardware tier for model recommendations';

-- ═══════════════════════════════════════════════════════════════════════════
-- 7. CREDENTIAL AUDIT LOG (Security)
-- ═══════════════════════════════════════════════════════════════════════════
-- Tracks all credential changes for security audit
CREATE TABLE credential_audit_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_name       VARCHAR(50) NOT NULL,
    action              VARCHAR(30) NOT NULL,         -- 'created', 'updated', 'deleted', 'validated', 'failed'
    action_details      JSONB DEFAULT '{}',           -- Additional context (no sensitive data)
    ip_address          VARCHAR(45),                  -- For security audit
    user_agent          VARCHAR(500),
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

COMMENT ON TABLE credential_audit_log IS 'Audit trail for credential changes (security compliance)';

CREATE INDEX idx_audit_log_provider ON credential_audit_log(provider_name, created_at DESC);
CREATE INDEX idx_audit_log_action ON credential_audit_log(action, created_at DESC);

-- ═══════════════════════════════════════════════════════════════════════════
-- 8. HELPER FUNCTIONS
-- ═══════════════════════════════════════════════════════════════════════════

-- Function to get effective config for a notebook (with global fallback)
CREATE OR REPLACE FUNCTION get_effective_pipeline_config(p_notebook_id UUID, p_step_name VARCHAR)
RETURNS TABLE (
    provider_name VARCHAR,
    model_name VARCHAR,
    parameters JSONB
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        pmc.provider_name,
        pmc.model_name,
        pmc.parameters
    FROM pipeline_model_config pmc
    WHERE (pmc.notebook_id = p_notebook_id OR pmc.notebook_id IS NULL)
      AND pmc.step_name = p_step_name
      AND pmc.is_active = true
    ORDER BY pmc.notebook_id NULLS LAST  -- Notebook-specific takes precedence
    LIMIT 1;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_effective_pipeline_config IS 'Returns effective model config for a step (notebook-specific or global fallback)';

-- Function to calculate daily token usage for a provider
CREATE OR REPLACE FUNCTION get_daily_token_usage(p_provider_name VARCHAR)
RETURNS BIGINT AS $$
BEGIN
    RETURN COALESCE(
        (SELECT SUM(total_tokens)
         FROM api_usage
         WHERE provider_name = p_provider_name
           AND DATE(request_timestamp) = CURRENT_DATE),
        0
    );
END;
$$ LANGUAGE plpgsql;

-- Function to calculate monthly cost for a provider
CREATE OR REPLACE FUNCTION get_monthly_cost(p_provider_name VARCHAR)
RETURNS DECIMAL(10, 4) AS $$
BEGIN
    RETURN COALESCE(
        (SELECT SUM(estimated_cost_usd)
         FROM api_usage
         WHERE provider_name = p_provider_name
           AND DATE_TRUNC('month', request_timestamp) = DATE_TRUNC('month', CURRENT_DATE)),
        0
    );
END;
$$ LANGUAGE plpgsql;

-- ═══════════════════════════════════════════════════════════════════════════
-- END OF MIGRATION
-- ═══════════════════════════════════════════════════════════════════════════
