import json
import logging
import time
import os
import sys

# Add parent directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from config import config
from core.extractors import extract_text
from core.chunker import chunk_text
from llm.ollama_client import OllamaClient

logger = logging.getLogger(__name__)

class DocumentProcessor:
    def __init__(self):
        self.ollama = OllamaClient()

    def process(self, task: dict) -> tuple[dict, list[dict], str]:
        """
        Full ingestion pipeline:
        1. Extract text from document
        2. Chunk into semantically coherent pieces
        3. Generate embeddings via Ollama
        4. Prepare chunk records for storage

        Returns (result_summary, chunk_records, raw_text).
        """
        start_time = time.time()
        payload = task['payload']
        if isinstance(payload, str):
            payload = json.loads(payload)

        document_id = payload['document_id']
        file_path = payload['source_path']
        content_type = payload['content_type']
        chunk_config = payload.get('config', {})

        logger.info(f"Processing document {document_id} ({content_type}): {file_path}")

        # --- Step 1: Extract ---
        logger.info("Step 1/4: Extracting text...")
        raw_text, extraction_metadata = extract_text(file_path, content_type)

        if not raw_text or len(raw_text.strip()) < 10:
            raise ValueError(
                f"Extracted text too short ({len(raw_text)} chars). "
                "Document may be empty or image-only."
            )

        logger.info(f"Extracted {len(raw_text)} chars, {extraction_metadata}")

        # --- Step 2: Chunk ---
        logger.info("Step 2/4: Chunking text...")
        chunks = chunk_text(
            raw_text,
            chunk_size=chunk_config.get('chunk_size', config.chunk_size),
            chunk_overlap=chunk_config.get('chunk_overlap', config.chunk_overlap),
            document_title=payload.get('title', '')
        )

        if not chunks:
            raise ValueError("Chunking produced zero chunks")

        logger.info(f"Created {len(chunks)} chunks")

        # --- Step 3: Embed ---
        logger.info("Step 3/4: Generating embeddings via Ollama...")
        texts = [c.content for c in chunks]
        embeddings = self.ollama.embed_batch(texts)

        # --- Step 4: Prepare records ---
        logger.info("Step 4/4: Preparing chunk records...")
        chunk_records = []
        for chunk, embedding in zip(chunks, embeddings):
            chunk_records.append({
                'chunk_index': chunk.index,
                'content': chunk.content,
                'token_count': chunk.token_count,
                'embedding_str': f"[{','.join(str(x) for x in embedding)}]",
                'metadata': chunk.metadata
            })

        processing_time = time.time() - start_time

        result = {
            "chunks_created": len(chunks),
            "embedding_dimensions": len(embeddings[0]) if embeddings else 0,
            "total_tokens": sum(c.token_count for c in chunks),
            "processing_time_ms": int(processing_time * 1000),
            "extraction_method": extraction_metadata.get("extraction_method", "unknown"),
            "warnings": extraction_metadata.get("warnings", []),
            "raw_text_length": len(raw_text),
        }

        logger.info(
            f"Document {document_id} processed: "
            f"{result['chunks_created']} chunks, "
            f"{result['total_tokens']} tokens, "
            f"{result['processing_time_ms']}ms"
        )

        return result, chunk_records, raw_text
