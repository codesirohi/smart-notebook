from typing import TypedDict, List, Dict, Any, Optional

class Chunk(TypedDict):
    content: str
    token_count: int
    metadata: Dict[str, Any]
    index: int

class IngestionState(TypedDict):
    # Input
    document_id: str
    source_path: str
    content_type: str
    config: Dict[str, Any]

    # Intermediate
    raw_text: Optional[str]
    metadata: Dict[str, Any]  # Extracted structured data (Title, Summary)
    chunks: List[Chunk]
    embeddings: List[List[float]]

    # Output/Status
    status: str
    errors: List[str]
