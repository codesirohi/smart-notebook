"""
Core utilities for the Smart Notebook worker.

This module contains database operations, logging configuration,
text chunking, and document extraction utilities.
"""

from .db import (
    init_connection_pool,
    close_connection_pool,
    get_connection,
    return_connection,
    get_pooled_connection,
    claim_task,
    complete_task,
    fail_task,
    reap_stale_tasks,
    requeue_failed_tasks,
    store_chunks,
    update_document_status,
    heartbeat_worker,
    update_task_progress
)
from .logging_config import configure_structured_logging, get_logger
from .chunker import chunk_text, Chunk
from .extractors import extract_text
from .processor import DocumentProcessor

__all__ = [
    # Database
    'init_connection_pool',
    'close_connection_pool',
    'get_connection',
    'return_connection',
    'get_pooled_connection',
    'claim_task',
    'complete_task',
    'fail_task',
    'reap_stale_tasks',
    'requeue_failed_tasks',
    'store_chunks',
    'update_document_status',
    'heartbeat_worker',
    'update_task_progress',
    # Logging
    'configure_structured_logging',
    'get_logger',
    # Chunking
    'chunk_text',
    'Chunk',
    # Extraction
    'extract_text',
    # Processing
    'DocumentProcessor',
]
