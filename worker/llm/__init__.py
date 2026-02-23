"""
LLM integrations for the Smart Notebook worker.

This module provides factory methods for creating LangChain
LLM and embedding instances for various providers.
"""

from .factory import LLMFactory, ModelProvider
from .provider_registry import ProviderRegistry, LLMProvider, register_provider
from .ollama_client import OllamaClient

__all__ = [
    'LLMFactory',
    'ModelProvider',
    'ProviderRegistry',
    'LLMProvider',
    'register_provider',
    'OllamaClient',
]
