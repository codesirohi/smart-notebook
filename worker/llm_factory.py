from enum import Enum
import os
from typing import Optional
from config import config
from provider_registry import ProviderRegistry, LLMProvider, register_provider

# Conditional imports
try:
    from langchain_openai import ChatOpenAI, OpenAIEmbeddings
except ImportError:
    ChatOpenAI = None
    OpenAIEmbeddings = None

try:
    from langchain_anthropic import ChatAnthropic
except ImportError:
    ChatAnthropic = None

try:
    from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings
except ImportError:
    ChatGoogleGenerativeAI = None
    GoogleGenerativeAIEmbeddings = None

from langchain_community.chat_models import ChatOllama
from langchain_community.embeddings import OllamaEmbeddings

class ModelProvider(str, Enum):
    GOOGLE = "google"
    ANTHROPIC = "anthropic"
    OPENAI = "openai"
    OLLAMA = "ollama"

# --- Provider Implementations ---

@register_provider(ModelProvider.OPENAI)
class OpenAIProvider(LLMProvider):
    @property
    def provider_name(self) -> str:
        return "openai"

    def create_chat_model(self, model_name: str, temperature: float = 0, **kwargs):
        if not ChatOpenAI: raise ImportError("langchain-openai missing")
        api_key = config.providers.get("openai")
        if not api_key: raise ValueError("OpenAI API key not found in config")
        return ChatOpenAI(model=model_name, temperature=temperature, api_key=api_key)

    def create_embeddings(self, model_name: str, **kwargs):
        if not OpenAIEmbeddings: raise ImportError("langchain-openai missing")
        api_key = config.providers.get("openai")
        if not api_key: raise ValueError("OpenAI API key not found in config")
        return OpenAIEmbeddings(model=model_name, api_key=api_key)

@register_provider("nvidia")
class NvidiaProvider(LLMProvider):
    """
    NVIDIA AI Endpoints provider (via OpenAI compatibility).
    Requires PROVIDER_NVIDIA_API_KEY environment variable.
    """
    @property
    def provider_name(self) -> str:
        return "nvidia"

    def create_chat_model(self, model_name: str, temperature: float = 0, **kwargs):
        if not ChatOpenAI: raise ImportError("langchain-openai missing")
        api_key = config.providers.get("nvidia")
        if not api_key: raise ValueError("NVIDIA API key not found. Set PROVIDER_NVIDIA_API_KEY.")
        
        return ChatOpenAI(
            model=model_name, 
            temperature=temperature, 
            api_key=api_key,
            base_url="https://integrate.api.nvidia.com/v1"
        )

    def create_embeddings(self, model_name: str, **kwargs):
        if not OpenAIEmbeddings: raise ImportError("langchain-openai missing")
        api_key = config.providers.get("nvidia")
        if not api_key: raise ValueError("NVIDIA API key not found. Set PROVIDER_NVIDIA_API_KEY.")
        
        return OpenAIEmbeddings(
            model=model_name, 
            api_key=api_key,
            base_url="https://integrate.api.nvidia.com/v1"
        )

@register_provider(ModelProvider.ANTHROPIC)
class AnthropicProvider(LLMProvider):
    @property
    def provider_name(self) -> str:
        return "anthropic"

    def create_chat_model(self, model_name: str, temperature: float = 0, **kwargs):
        if not ChatAnthropic: raise ImportError("langchain-anthropic missing")
        api_key = config.providers.get("anthropic")
        if not api_key: raise ValueError("Anthropic API key not found in config")
        return ChatAnthropic(model=model_name, temperature=temperature, api_key=api_key)

    def create_embeddings(self, model_name: str, **kwargs):
        raise ValueError("Anthropic does not support embeddings.")

@register_provider(ModelProvider.GOOGLE)
class GoogleProvider(LLMProvider):
    @property
    def provider_name(self) -> str:
        return "google"

    def create_chat_model(self, model_name: str, temperature: float = 0, **kwargs):
        if not ChatGoogleGenerativeAI: raise ImportError("langchain-google-genai missing")
        api_key = config.providers.get("google")
        if not api_key: raise ValueError("Google API key not found in config")
        # Note: Google uses 'google_api_key' param
        return ChatGoogleGenerativeAI(model=model_name, temperature=temperature, google_api_key=api_key)

    def create_embeddings(self, model_name: str, **kwargs):
        if not GoogleGenerativeAIEmbeddings: raise ImportError("langchain-google-genai missing")
        api_key = config.providers.get("google")
        if not api_key: raise ValueError("Google API key not found in config")
        return GoogleGenerativeAIEmbeddings(model=model_name, google_api_key=api_key)

@register_provider(ModelProvider.OLLAMA)
class OllamaProvider(LLMProvider):
    @property
    def provider_name(self) -> str:
        return "ollama"

    def create_chat_model(self, model_name: str, temperature: float = 0, **kwargs):
        return ChatOllama(model=model_name, temperature=temperature, base_url=config.ollama_url)

    def create_embeddings(self, model_name: str, **kwargs):
        return OllamaEmbeddings(model=model_name, base_url=config.ollama_url)

# --- Factory ---

class LLMFactory:
    """
    Factory to create LangChain LLM and Embedding instances using Registry Pattern.
    """
    
    @staticmethod
    def get_provider_for_model(model_name: str) -> str:
        if not model_name:
            return ModelProvider.OLLAMA
        lower = model_name.lower()
        if lower.startswith("gpt") or lower.startswith("o1"):
            return ModelProvider.OPENAI
        if lower.startswith("claude"):
            return ModelProvider.ANTHROPIC
        if lower.startswith("gemini") or "google" in lower or lower.startswith("models/") or "text-embedding" in lower:
            return ModelProvider.GOOGLE
        if lower.startswith("text-embedding-ada") or "openai" in lower: # OpenAI embedding models
             return ModelProvider.OPENAI
        if lower.startswith("meta/") or lower.startswith("nvidia/"):
             return "nvidia"
        return ModelProvider.OLLAMA

    @staticmethod
    def create_chat_model(provider: str, model_name: str, temperature: float = 0):
        provider_name = provider.lower()
        provider_cls = ProviderRegistry.get_provider_class(provider_name)
        
        if not provider_cls:
            raise ValueError(f"Unsupported provider: {provider_name}")
            
        instance = provider_cls()
        return instance.create_chat_model(model_name, temperature)

    @staticmethod
    def create_embeddings(provider: str, model_name: str):
        provider_name = provider.lower()
        provider_cls = ProviderRegistry.get_provider_class(provider_name)
        
        if not provider_cls:
            raise ValueError(f"Unsupported provider: {provider_name}")
            
        instance = provider_cls()
        return instance.create_embeddings(model_name)
