import requests
import logging
import time
from config import config

logger = logging.getLogger(__name__)

class OllamaClient:
    def __init__(self, base_url: str = None, model: str = None):
        self.base_url = base_url or config.ollama_url
        self.model = model or config.embedding_model
        self._verified = False

    def health_check(self) -> bool:
        """Verify Ollama is running and model is available."""
        try:
            resp = requests.get(f"{self.base_url}/api/tags", timeout=5)
            if resp.status_code != 200:
                return False

            models = resp.json().get("models", [])
            model_names = [m["name"] for m in models]
            if self.model not in model_names and f"{self.model}:latest" not in model_names:
                logger.error(
                    f"Model '{self.model}' not found. "
                    f"Available: {model_names}. "
                    f"Run: ollama pull {self.model}"
                )
                return False

            self._verified = True
            return True
        except requests.ConnectionError:
            logger.error(f"Cannot connect to Ollama at {self.base_url}")
            return False

    def embed(self, text: str, retries: int = 3) -> list[float]:
        """Generate embedding for text using Ollama."""
        for attempt in range(retries):
            try:
                resp = requests.post(
                    f"{self.base_url}/api/embeddings",
                    json={"model": self.model, "prompt": text},
                    timeout=30
                )
                resp.raise_for_status()

                embedding = resp.json().get("embedding", [])
                if not embedding:
                    raise ValueError("Empty embedding returned from Ollama")

                return embedding

            except requests.RequestException as e:
                wait = 2 ** attempt
                logger.warning(
                    f"Ollama embed failed (attempt {attempt+1}/{retries}): {e}. "
                    f"Retrying in {wait}s..."
                )
                time.sleep(wait)

        raise RuntimeError(f"Ollama embedding failed after {retries} retries")

    def embed_batch(self, texts: list[str], batch_size: int = 10) -> list[list[float]]:
        """Embed multiple texts with progress logging."""
        embeddings = []
        total = len(texts)

        for i, text in enumerate(texts):
            embedding = self.embed(text)
            embeddings.append(embedding)

            if (i + 1) % batch_size == 0 or i == total - 1:
                logger.info(f"Embedded {i+1}/{total} chunks")

        return embeddings

    def generate(self, prompt: str, system: str = None, retries: int = 3) -> str:
        """Generate text completion (used for RAG answers)."""
        for attempt in range(retries):
            try:
                payload = {"model": self.model, "prompt": prompt, "stream": False}
                if system:
                    payload["system"] = system

                resp = requests.post(
                    f"{self.base_url}/api/generate",
                    json=payload,
                    timeout=60
                )
                resp.raise_for_status()
                return resp.json().get("response", "")

            except requests.RequestException as e:
                wait = 2 ** attempt
                logger.warning(
                    f"Ollama generate failed (attempt {attempt+1}/{retries}): {e}. "
                    f"Retrying in {wait}s..."
                )
                time.sleep(wait)

        raise RuntimeError(f"Ollama generation failed after {retries} retries")
