#!/usr/bin/env python3
"""
Verification script for the LLM Provider Registry Pattern.

Tests:
1. Registry can register and retrieve providers
2. Dummy providers can be created and instantiated
3. Existing providers (OpenAI, Anthropic, Google, Ollama) are registered
4. Factory can create instances via registry
"""

import os
import sys
from typing import Optional
from provider_registry import ProviderRegistry, LLMProvider, register_provider

# Mock config for testing
class MockConfig:
    providers = {"test": "test-key-123", "openai": "fake-openai-key"}
    ollama_url = "http://localhost:11434"

# Create a dummy provider for testing
@register_provider("test-provider")
class TestProvider(LLMProvider):
    @property
    def provider_name(self) -> str:
        return "test-provider"

    def create_chat_model(self, model_name: str, temperature: float = 0, **kwargs):
        return f"TestChatModel(model={model_name}, temp={temperature})"

    def create_embeddings(self, model_name: str, **kwargs):
        return f"TestEmbeddings(model={model_name})"


def test_registry_registration():
    """Test that providers can be registered and retrieved."""
    print("✓ Test 1: Provider Registration")

    # Test that our test provider was registered
    test_provider_cls = ProviderRegistry.get_provider_class("test-provider")
    assert test_provider_cls is not None, "Test provider should be registered"
    assert test_provider_cls == TestProvider, "Retrieved provider should match TestProvider"

    print("  - Test provider registered successfully")


def test_provider_instantiation():
    """Test that providers can be instantiated."""
    print("✓ Test 2: Provider Instantiation")

    test_provider_cls = ProviderRegistry.get_provider_class("test-provider")
    instance = test_provider_cls()

    assert isinstance(instance, LLMProvider), "Instance should be an LLMProvider"
    assert instance.provider_name == "test-provider", "Provider name should match"

    # Test create methods
    chat_model = instance.create_chat_model("test-model")
    embeddings = instance.create_embeddings("test-embedding-model")

    assert "TestChatModel" in chat_model, "Chat model should be created"
    assert "TestEmbeddings" in embeddings, "Embeddings should be created"

    print("  - Provider instantiated successfully")
    print(f"  - Chat model: {chat_model}")
    print(f"  - Embeddings: {embeddings}")


def test_existing_providers():
    """Test that existing providers are registered."""
    print("✓ Test 3: Existing Providers Registration")

    # Import llm_factory to trigger provider registration
    from llm_factory import (
        OpenAIProvider, AnthropicProvider, GoogleProvider,
        OllamaProvider, NvidiaProvider
    )

    expected_providers = ["openai", "anthropic", "google", "ollama", "nvidia"]
    registered_providers = ProviderRegistry.list_providers()

    print(f"  - Registered providers: {registered_providers}")

    for provider in expected_providers:
        assert provider in registered_providers, f"{provider} should be registered"
        provider_cls = ProviderRegistry.get_provider_class(provider)
        assert provider_cls is not None, f"{provider} class should be retrievable"

    print("  - All expected providers are registered")


def test_factory_usage():
    """Test that the factory can use the registry."""
    print("✓ Test 4: Factory Usage")

    from llm_factory import LLMFactory

    # Mock config
    original_config = None
    try:
        import config
        original_config = config.config
        config.config = MockConfig()

        # Test provider detection
        provider = LLMFactory.get_provider_for_model("gpt-4")
        assert provider == "openai", "Should detect OpenAI for gpt-4"

        provider = LLMFactory.get_provider_for_model("claude-3")
        assert provider == "anthropic", "Should detect Anthropic for claude"

        provider = LLMFactory.get_provider_for_model("gemini-pro")
        assert provider == "google", "Should detect Google for gemini"

        provider = LLMFactory.get_provider_for_model("llama2")
        assert provider == "ollama", "Should detect Ollama for unknown models"

        provider = LLMFactory.get_provider_for_model("nvidia/llama-3.1")
        assert provider == "nvidia", "Should detect NVIDIA for nvidia/ prefix"

        print("  - Provider detection works correctly")

        # Test registry retrieval
        openai_cls = ProviderRegistry.get_provider_class("openai")
        assert openai_cls is not None, "OpenAI provider should be retrievable"

        print("  - Registry retrieval works correctly")

    finally:
        if original_config:
            config.config = original_config


def test_dynamic_provider():
    """Test that dynamic providers can be added."""
    print("✓ Test 5: Dynamic Provider Registration")

    # Register a new provider at runtime
    @register_provider("cohere")
    class CohereProvider(LLMProvider):
        @property
        def provider_name(self) -> str:
            return "cohere"

        def create_chat_model(self, model_name: str, temperature: float = 0, **kwargs):
            return f"CohereChatModel(model={model_name})"

        def create_embeddings(self, model_name: str, **kwargs):
            return f"CohereEmbeddings(model={model_name})"

    # Verify it's registered
    cohere_cls = ProviderRegistry.get_provider_class("cohere")
    assert cohere_cls is not None, "Cohere provider should be registered"
    assert cohere_cls == CohereProvider, "Retrieved provider should match CohereProvider"

    # Verify it can be instantiated
    instance = cohere_cls()
    chat_model = instance.create_chat_model("command-r")
    assert "CohereChatModel" in chat_model, "Cohere chat model should be created"

    print("  - Dynamic provider 'cohere' registered and instantiated successfully")
    print(f"  - Chat model: {chat_model}")


def main():
    print("\n" + "=" * 60)
    print("LLM Provider Registry Verification")
    print("=" * 60 + "\n")

    try:
        test_registry_registration()
        test_provider_instantiation()
        test_existing_providers()
        test_factory_usage()
        test_dynamic_provider()

        print("\n" + "=" * 60)
        print("✅ All tests passed!")
        print("=" * 60 + "\n")

        print("Summary:")
        print("  - Registry pattern is working correctly")
        print("  - All existing providers are registered")
        print("  - New providers can be added dynamically")
        print("  - Factory integrates with registry successfully")
        print("\nTo add a new provider:")
        print("  1. Set PROVIDER_{NAME}_API_KEY environment variable")
        print("  2. Create a provider class with @register_provider decorator")
        print("  3. No other code changes needed!")

        return 0

    except AssertionError as e:
        print(f"\n❌ Test failed: {e}")
        return 1
    except Exception as e:
        print(f"\n❌ Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        return 1


if __name__ == "__main__":
    sys.exit(main())
