import os
from dataclasses import dataclass

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
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "phi3:mini")

    # Worker
    poll_interval_sec: int = int(os.getenv("POLL_INTERVAL", "2"))
    stale_timeout_min: int = int(os.getenv("STALE_TIMEOUT_MIN", "5"))
    worker_id: str = os.getenv("WORKER_ID", f"{os.uname().nodename}-{os.getpid()}")

    # Chunking
    chunk_size: int = int(os.getenv("CHUNK_SIZE", "512"))
    chunk_overlap: int = int(os.getenv("CHUNK_OVERLAP", "50"))

    @property
    def db_url(self) -> str:
        return f"postgresql://{self.db_user}:{self.db_password}@{self.db_host}:{self.db_port}/{self.db_name}"

config = Config()
