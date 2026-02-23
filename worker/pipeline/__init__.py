"""
Ingestion pipeline for the Smart Notebook worker.

This module provides a simple, linear pipeline for document ingestion:
1. Extract text and metadata
2. Chunk the text
3. Generate embeddings
4. Store to database
"""

from .pipeline import IngestionPipeline, create_ingestion_pipeline

__all__ = [
    'IngestionPipeline',
    'create_ingestion_pipeline',
]
