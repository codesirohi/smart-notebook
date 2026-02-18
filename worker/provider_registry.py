from abc import ABC, abstractmethod
from typing import Optional, Dict, Any, Type

class LLMProvider(ABC):
    """
    Abstract base class for LLM providers.
    """
    
    @abstractmethod
    def create_chat_model(self, model_name: str, temperature: float = 0, **kwargs):
        pass

    @abstractmethod
    def create_embeddings(self, model_name: str, **kwargs):
        pass
        
    @property
    @abstractmethod
    def provider_name(self) -> str:
        pass

class ProviderRegistry:
    """
    Registry for LLM providers.
    """
    _providers: Dict[str, Type[LLMProvider]] = {}

    @classmethod
    def register(cls, name: str, provider_cls: Type[LLMProvider]):
        cls._providers[name.lower()] = provider_cls

    @classmethod
    def get_provider_class(cls, name: str) -> Optional[Type[LLMProvider]]:
        return cls._providers.get(name.lower())

    @classmethod
    def list_providers(cls):
        return list(cls._providers.keys())

def register_provider(name: str):
    """Decorator to register a provider."""
    def decorator(cls):
        ProviderRegistry.register(name, cls)
        return cls
    return decorator
