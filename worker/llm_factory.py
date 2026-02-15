from enum import Enum
import os
from dataclasses import dataclass
from typing import Optional

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
from worker.config import config

class ModelProvider(str, Enum):
    GOOGLE = "google"
    ANTHROPIC = "anthropic"
    OPENAI = "openai"
    OLLAMA = "ollama"

class LLMFactory:
    """
    Factory to create LangChain LLM and Embedding instances.
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
        if lower.startswith("gemini"):
            return ModelProvider.GOOGLE
        if lower.startswith("text-embedding") and "openai" not in lower: # OpenAI embedding models
             return ModelProvider.OPENAI
        return ModelProvider.OLLAMA

    @staticmethod
    def create_chat_model(provider: str, model_name: str, temperature: float = 0):
        provider = provider.lower()
        
        if provider == ModelProvider.OPENAI:
            if not ChatOpenAI: raise ImportError("langchain-openai missing")
            return ChatOpenAI(model=model_name, temperature=temperature, api_key=config.openai_api_key)
            
        elif provider == ModelProvider.ANTHROPIC:
            if not ChatAnthropic: raise ImportError("langchain-anthropic missing")
            return ChatAnthropic(model=model_name, temperature=temperature, api_key=config.anthropic_api_key)
            
        elif provider == ModelProvider.GOOGLE:
            if not ChatGoogleGenerativeAI: raise ImportError("langchain-google-genai missing")
            return ChatGoogleGenerativeAI(model=model_name, temperature=temperature, google_api_key=config.gemini_api_key)
            
        elif provider == ModelProvider.OLLAMA:
            return ChatOllama(model=model_name, temperature=temperature, base_url=config.ollama_url)
            
        else:
            raise ValueError(f"Unsupported provider: {provider}")

    @staticmethod
    def create_embeddings(provider: str, model_name: str):
        provider = provider.lower()
        
        if provider == ModelProvider.OPENAI:
            if not OpenAIEmbeddings: raise ImportError("langchain-openai missing")
            return OpenAIEmbeddings(model=model_name, api_key=config.openai_api_key)
            
        elif provider == ModelProvider.GOOGLE:
             if not GoogleGenerativeAIEmbeddings: raise ImportError("langchain-google-genai missing")
             return GoogleGenerativeAIEmbeddings(model=model_name, google_api_key=config.gemini_api_key)
             
        elif provider == ModelProvider.OLLAMA:
             return OllamaEmbeddings(model=model_name, base_url=config.ollama_url)
             
        elif provider == ModelProvider.ANTHROPIC:
             raise ValueError("Anthropic does not support embeddings.")
             
        else:
             raise ValueError(f"Unsupported provider: {provider}")
