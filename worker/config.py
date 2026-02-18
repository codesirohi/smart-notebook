import os
from dataclasses import dataclass
from dotenv import load_dotenv

load_dotenv()

@dataclass
class Config:
    # Database
    db_host: str = os.getenv("DB_HOST", "localhost")
    db_port: int = int(os.getenv("DB_PORT", "5432"))
    db_name: str = os.getenv("DB_NAME", "smartnotebook")
    db_user: str = os.getenv("DB_USER", "notebook")
    db_password: str = os.getenv("DB_PASSWORD", "notebook_dev")

    # Ollama
    ollama_url: str = os.getenv("OLLAMA_URL", "http://localhost:11434")
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "all-minilm")
    extraction_model: str = os.getenv("EXTRACTION_MODEL", "tinyllama")

    # Providers (Dynamic)
    # Map of provider_name -> api_key
    providers: dict = None

    def __post_init__(self):
        self.providers = {}
        # Load standard keys
        if os.getenv("OPENAI_API_KEY"):
            self.providers["openai"] = os.getenv("OPENAI_API_KEY")
        if os.getenv("ANTHROPIC_API_KEY"):
            self.providers["anthropic"] = os.getenv("ANTHROPIC_API_KEY")
        if os.getenv("GEMINI_API_KEY"):
            self.providers["google"] = os.getenv("GEMINI_API_KEY")
            
        # Load dynamic keys: PROVIDER_{NAME}_API_KEY
        for key, value in os.environ.items():
            if key.startswith("PROVIDER_") and key.endswith("_API_KEY"):
                # PROVIDER_COHERE_API_KEY -> cohere
                provider_name = key[9:-8].lower()
                self.providers[provider_name] = value

    # Worker
    poll_interval_sec: int = int(os.getenv("POLL_INTERVAL", "2"))
    stale_timeout_min: int = int(os.getenv("STALE_TIMEOUT_MIN", "5"))
    worker_id: str = os.getenv("WORKER_ID", f"{os.uname().nodename}-{os.getpid()}")

    # Chunking
    chunk_size: int = int(os.getenv("CHUNK_SIZE", "128"))
    chunk_overlap: int = int(os.getenv("CHUNK_OVERLAP", "15"))

    @property
    def db_url(self) -> str:
        return f"postgresql://{self.db_user}:{self.db_password}@{self.db_host}:{self.db_port}/{self.db_name}"

config = Config()
