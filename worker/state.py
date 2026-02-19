from typing import TypedDict, List, Dict, Any, Optional

class Chunk(TypedDict):
    content: str
    token_count: int
    metadata: Dict[str, Any]
    index: int

class IngestionState(TypedDict):
    # Input
    task_id: str
    document_id: str
    source_path: str
    content_type: str
    config: Dict[str, Any]
    db_conn: Optional[Any]
    worker_id: Optional[str]
    retry_count: Optional[int]

    # Intermediate
    raw_text: Optional[str]
    metadata: Dict[str, Any]  # Extracted structured data (Title, Summary)
    chunks: List[Chunk]
    embeddings: List[List[float]]
    current_step: Optional[str]
    step_timings: Dict[str, int]

    # Output/Status
    status: str
    errors: List[str]
