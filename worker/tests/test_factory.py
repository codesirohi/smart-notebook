import pytest
import sys
import os
from unittest.mock import patch, MagicMock

# Add absolute path to worker so imports work properly for testing
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from llm.factory import OllamaProvider, OpenAIProvider
from config import config

class TestLLMFactory:

    @patch('llm.factory.ChatOllama')
    def test_ollama_provider_disabled_in_cloud_mode(self, mock_chat_ollama):
        provider = OllamaProvider()
        
        # Override config flag
        config.local_models_enabled = False
        
        # Assert exception is explicitly raised
        with pytest.raises(RuntimeError) as excinfo:
            provider.create_chat_model("llama3.2")
        
        assert "Local models are disabled in this environment (Cloud Mode)" in str(excinfo.value)
        
        with pytest.raises(RuntimeError) as excinfo:
            provider.create_embeddings("nomic-embed-text")
            
        assert "Local models are disabled in this environment (Cloud Mode)" in str(excinfo.value)

    @patch('llm.factory.ChatOllama')
    @patch('llm.factory.OllamaEmbeddings')
    def test_ollama_provider_enabled_locally(self, mock_embeddings, mock_chat_ollama):
        provider = OllamaProvider()
        
        # Override config flag
        config.local_models_enabled = True
        
        # Should not raise
        chat = provider.create_chat_model("llama3.2")
        assert chat is not None
        
        emb = provider.create_embeddings("nomic-embed-text")
        assert emb is not None

    @patch('llm.factory.ChatOpenAI')
    def test_openai_provider_timeout_and_retries_configured(self, mock_chat_openai):
        provider = OpenAIProvider()
        
        # Provide a mock key for validation
        config.providers = {"openai": "test-key-123"}
        
        provider.create_chat_model("gpt-4o")
        
        # Verify it was instantiated with our exact timeout and retries values
        mock_chat_openai.assert_called_once_with(
            model="gpt-4o",
            temperature=0,
            api_key="test-key-123",
            max_retries=3,
            request_timeout=30.0
        )
