"""
Simple Ingestion Pipeline - Replaces LangGraph

A straightforward, linear pipeline for document ingestion:
1. Extract text and metadata
2. Chunk the text
3. Generate embeddings
4. Store to database

No branching, no conditionals, no cycles - just a simple sequence of steps.
"""

import time
from typing import Any, Dict, Protocol
from abc import abstractmethod
from contextlib import nullcontext

from state import IngestionState
from config import config

# Use structured logging
try:
    from logging_config import get_logger, log_performance
    logger = get_logger(__name__)
    STRUCTURED_LOGGING = True
except ImportError:
    # Fallback to standard logging if structlog not installed
    import logging
    logger = logging.getLogger(__name__)
    STRUCTURED_LOGGING = False


def _log_fields(state: IngestionState, **extra: Any) -> Dict[str, Any]:
    """Build consistent correlation fields for every pipeline log event."""
    fields: Dict[str, Any] = {
        "task_id": state.get("task_id"),
        "document_id": state.get("document_id"),
        "worker_id": state.get("worker_id"),
        "retry_count": state.get("retry_count"),
        "current_step": state.get("current_step"),
        "pipeline_status": state.get("status"),
    }
    fields.update(extra)
    return {k: v for k, v in fields.items() if v is not None}


def _persist_task_progress(state: IngestionState, phase: str, **extra: Any) -> None:
    """
    Persist stage-level progress into ingestion_tasks.result while task is PROCESSING.
    """
    conn = state.get("db_conn")
    task_id = state.get("task_id")
    if conn is None or not task_id:
        return

    payload: Dict[str, Any] = {
        "task_id": str(task_id),
        "document_id": state.get("document_id"),
        "worker_id": state.get("worker_id"),
        "pipeline_status": phase,
        "current_step": state.get("current_step"),
        "step_timings": state.get("step_timings", {}),
        "errors": state.get("errors", []),
        "updated_at_epoch_ms": int(time.time() * 1000),
    }
    payload.update({k: v for k, v in extra.items() if v is not None})

    try:
        from db import update_task_progress
        update_task_progress(conn, task_id, payload)
    except Exception as e:
        logger.warning(
            "task_progress_update_failed",
            **_log_fields(
                state,
                phase=phase,
                error_type=type(e).__name__,
                error_message=str(e),
            ),
        )


# ─── Pipeline Step Protocol ───

class PipelineStep(Protocol):
    """Protocol defining what a pipeline step must implement."""

    @abstractmethod
    def process(self, state: IngestionState) -> IngestionState:
        """
        Process the state and return updated state.
        Should handle its own errors and update state["status"] accordingly.
        """
        ...

    @property
    @abstractmethod
    def name(self) -> str:
        """Human-readable step name for logging."""
        ...


# ─── Step Implementations ───

class ExtractStep:
    """Extract text and structured metadata from document."""

    @property
    def name(self) -> str:
        return "Extract"

    def process(self, state: IngestionState) -> IngestionState:
        start_time = time.time()
        doc_id = state.get("document_id", "unknown")

        try:
            from extractors import extract_text
            from extractors_v2 import extract_metadata

            source_path = state['source_path']
            content_type = state['content_type']

            # 1. Basic text extraction (PyPDF2/Text/Markdown)
            logger.info(
                "extracting_text",
                **_log_fields(
                    state,
                source_path=source_path,
                content_type=content_type
                )
            )

            extract_start = time.time()
            raw_text, basic_meta = extract_text(source_path, content_type)
            extract_duration = int((time.time() - extract_start) * 1000)

            if not raw_text or len(raw_text.strip()) < 10:
                raise ValueError("Extracted text is empty or too short")

            logger.info(
                "text_extracted",
                **_log_fields(
                    state,
                text_length=len(raw_text),
                duration_ms=extract_duration,
                metadata=basic_meta
                )
            )

            # 2. Structured metadata extraction (LLM-based)
            extraction_model = state['config'].get('extraction_model', config.extraction_model)
            logger.info(
                "extracting_metadata",
                **_log_fields(
                    state,
                model=extraction_model,
                text_sample_length=min(2000, len(raw_text))
                )
            )

            meta_start = time.time()
            llm_meta = extract_metadata(raw_text, extraction_model)
            meta_duration = int((time.time() - meta_start) * 1000)

            # Merge metadata
            final_meta = {**basic_meta, **llm_meta}

            total_duration = int((time.time() - start_time) * 1000)
            logger.info(
                "extract_step_completed",
                **_log_fields(
                    state,
                total_duration_ms=total_duration,
                text_extraction_ms=extract_duration,
                metadata_extraction_ms=meta_duration,
                final_metadata=final_meta
                )
            )

            return {
                **state,
                "raw_text": raw_text,
                "metadata": final_meta,
                "status": "EXTRACTED"
            }

        except Exception as e:
            duration = int((time.time() - start_time) * 1000)
            logger.error(
                "extract_step_failed",
                **_log_fields(
                    state,
                error_type=type(e).__name__,
                error_message=str(e),
                duration_ms=duration,
                exc_info=True
                )
            )
            return {
                **state,
                "errors": state["errors"] + [f"Extract: {str(e)}"],
                "status": "FAILED"
            }


class ChunkStep:
    """Chunk the extracted text into manageable pieces."""

    @property
    def name(self) -> str:
        return "Chunk"

    def process(self, state: IngestionState) -> IngestionState:
        if state["status"] == "FAILED":
            return state

        start_time = time.time()
        doc_id = state.get("document_id", "unknown")

        try:
            from chunker import chunk_text

            cfg = state["config"]
            metadata = state["metadata"]
            chunk_size = cfg.get('chunk_size', 512)
            chunk_overlap = cfg.get('chunk_overlap', 50)

            logger.info(
                "chunking_text",
                **_log_fields(
                    state,
                text_length=len(state["raw_text"]),
                chunk_size=chunk_size,
                chunk_overlap=chunk_overlap
                )
            )

            chunks_obj = chunk_text(
                state["raw_text"],
                chunk_size=chunk_size,
                chunk_overlap=chunk_overlap,
                document_title=metadata.get('title', 'Untitled')
            )

            if not chunks_obj:
                raise ValueError("No chunks created")

            # Convert to dictionaries for state
            chunks_list = []
            total_tokens = 0
            for c in chunks_obj:
                chunks_list.append({
                    "index": c.index,
                    "content": c.content,
                    "token_count": c.token_count,
                    "metadata": c.metadata
                })
                total_tokens += c.token_count

            duration = int((time.time() - start_time) * 1000)
            avg_tokens = total_tokens // len(chunks_list) if chunks_list else 0

            logger.info(
                "chunk_step_completed",
                **_log_fields(
                    state,
                chunks_created=len(chunks_list),
                total_tokens=total_tokens,
                avg_tokens_per_chunk=avg_tokens,
                duration_ms=duration
                )
            )

            return {**state, "chunks": chunks_list, "status": "CHUNKED"}

        except Exception as e:
            duration = int((time.time() - start_time) * 1000)
            logger.error(
                "chunk_step_failed",
                **_log_fields(
                    state,
                error_type=type(e).__name__,
                error_message=str(e),
                duration_ms=duration,
                exc_info=True
                )
            )
            return {
                **state,
                "errors": state["errors"] + [f"Chunk: {str(e)}"],
                "status": "FAILED"
            }


class EmbedStep:
    """Generate embeddings for all chunks."""

    @property
    def name(self) -> str:
        return "Embed"

    def process(self, state: IngestionState) -> IngestionState:
        if state["status"] == "FAILED":
            return state

        start_time = time.time()
        doc_id = state.get("document_id", "unknown")

        try:
            from llm_factory import LLMFactory

            # Determine embedding model
            model = state['config'].get('embedding_model', config.embedding_model)
            provider = LLMFactory.get_provider_for_model(model)

            logger.info(
                "embedding_chunks",
                **_log_fields(
                    state,
                model=model,
                provider=provider,
                chunk_count=len(state["chunks"])
                )
            )

            embeddings_model = LLMFactory.create_embeddings(provider, model)
            texts = [c["content"] for c in state["chunks"]]

            embed_start = time.time()
            embeddings = embeddings_model.embed_documents(texts)
            embed_duration = int((time.time() - embed_start) * 1000)

            total_duration = int((time.time() - start_time) * 1000)

            # Log API call for monitoring and cost tracking
            if STRUCTURED_LOGGING:
                from logging_config import log_api_call
                log_api_call(
                    logger,
                    provider=provider,
                    model=model,
                    operation="embedding",
                    duration_ms=embed_duration,
                    success=True,
                    chunks=len(embeddings),
                    embedding_dimensions=len(embeddings[0]) if embeddings else 0,
                    task_id=state.get("task_id"),
                    document_id=state.get("document_id"),
                    current_step=state.get("current_step")
                )

            logger.info(
                "embed_step_completed",
                **_log_fields(
                    state,
                embeddings_generated=len(embeddings),
                embedding_dimensions=len(embeddings[0]) if embeddings else 0,
                api_duration_ms=embed_duration,
                total_duration_ms=total_duration,
                model=model,
                provider=provider
                )
            )

            return {**state, "embeddings": embeddings, "status": "EMBEDDED"}

        except Exception as e:
            duration = int((time.time() - start_time) * 1000)
            logger.error(
                "embed_step_failed",
                **_log_fields(
                    state,
                model=state['config'].get('embedding_model', config.embedding_model),
                error_type=type(e).__name__,
                error_message=str(e),
                duration_ms=duration,
                exc_info=True
                )
            )
            return {
                **state,
                "errors": state["errors"] + [f"Embed: {str(e)}"],
                "status": "FAILED"
            }


class StoreStep:
    """Store chunks and metadata to database."""

    @property
    def name(self) -> str:
        return "Store"

    def process(self, state: IngestionState) -> IngestionState:
        if state["status"] == "FAILED":
            return state

        start_time = time.time()
        doc_id = state["document_id"]

        try:
            from db import get_pooled_connection, store_chunks, update_document_status

            logger.info(
                "storing_to_database",
                **_log_fields(
                    state,
                chunks_to_store=len(state["chunks"])
                )
            )

            # Prepare chunk records
            chunk_records = []
            for i, chunk in enumerate(state["chunks"]):
                embedding = state["embeddings"][i]
                chunk_records.append({
                    'chunk_index': chunk["index"],
                    'content': chunk["content"],
                    'token_count': chunk["token_count"],
                    'embedding_str': f"[{','.join(str(x) for x in embedding)}]",
                    'metadata': chunk["metadata"]
                })

            existing_conn = state.get("db_conn")
            conn_context = nullcontext(existing_conn) if existing_conn is not None else get_pooled_connection()

            with conn_context as conn:
                # Store chunks
                store_start = time.time()
                store_chunks(conn, doc_id, chunk_records)
                store_duration = int((time.time() - store_start) * 1000)

                # Update document with metadata
                update_start = time.time()
                update_document_status(
                    conn,
                    doc_id,
                    "INDEXED",
                    raw_content=state["raw_text"],
                    metadata=state["metadata"]
                )
                update_duration = int((time.time() - update_start) * 1000)

            total_duration = int((time.time() - start_time) * 1000)

            logger.info(
                "store_step_completed",
                **_log_fields(
                    state,
                chunks_stored=len(chunk_records),
                store_chunks_ms=store_duration,
                update_document_ms=update_duration,
                total_duration_ms=total_duration
                )
            )

            return {**state, "status": "COMPLETED"}

        except Exception as e:
            duration = int((time.time() - start_time) * 1000)
            logger.error(
                "store_step_failed",
                **_log_fields(
                    state,
                error_type=type(e).__name__,
                error_message=str(e),
                duration_ms=duration,
                exc_info=True
                )
            )
            return {
                **state,
                "errors": state["errors"] + [f"Store: {str(e)}"],
                "status": "FAILED"
            }


# ─── Main Pipeline ───

class IngestionPipeline:
    """
    Simple linear pipeline for document ingestion.

    Replaces LangGraph with a straightforward sequential processor.
    Much simpler, easier to debug, and no unnecessary dependencies.

    Usage:
        pipeline = IngestionPipeline()
        result = pipeline.execute(initial_state)
    """

    def __init__(self):
        self.steps = [
            ExtractStep(),
            ChunkStep(),
            EmbedStep(),
            StoreStep()
        ]
        logger.info("pipeline_initialized", steps=self.get_step_names(), total_steps=len(self.steps))

    def execute(self, initial_state: IngestionState) -> IngestionState:
        """
        Execute all pipeline steps sequentially.

        Args:
            initial_state: Initial ingestion state

        Returns:
            Final state with status "COMPLETED" or "FAILED"
        """
        state = {**initial_state}
        state["step_timings"] = dict(state.get("step_timings", {}))
        start_time = time.time()

        logger.info(
            "pipeline_started",
            **_log_fields(
                state,
                source_path=state.get("source_path"),
                content_type=state.get("content_type"),
                steps=self.get_step_names(),
                total_steps=len(self.steps),
            ),
        )
        _persist_task_progress(
            state,
            "RUNNING",
            total_steps=len(self.steps),
            event="pipeline_started",
        )

        step_timings = state["step_timings"]

        for step_index, step in enumerate(self.steps, start=1):
            # Skip remaining steps if already failed
            if state["status"] == "FAILED":
                logger.warning(
                    "skipping_step",
                    **_log_fields(
                        state,
                        step=step.name,
                        step_index=step_index,
                        reason="pipeline_already_failed",
                    ),
                )
                break

            state["current_step"] = step.name
            step_start = time.time()
            logger.info(
                "pipeline_step_started",
                **_log_fields(
                    state,
                    step=step.name,
                    step_index=step_index,
                    total_steps=len(self.steps),
                ),
            )
            _persist_task_progress(
                state,
                "RUNNING",
                step=step.name,
                step_index=step_index,
                total_steps=len(self.steps),
                event="step_started",
            )

            try:
                state = step.process(state)
                step_duration = int((time.time() - step_start) * 1000)
                step_timings[step.name] = step_duration
                state["step_timings"] = step_timings

                if state["status"] == "FAILED":
                    logger.error(
                        "step_failed",
                        **_log_fields(
                            state,
                            step=step.name,
                            step_index=step_index,
                            duration_ms=step_duration,
                            errors=state["errors"],
                        ),
                    )
                    _persist_task_progress(
                        state,
                        "FAILED",
                        step=step.name,
                        step_index=step_index,
                        duration_ms=step_duration,
                        event="step_failed",
                        errors=state["errors"],
                    )
                    break

                logger.info(
                    "pipeline_step_completed",
                    **_log_fields(
                        state,
                        step=step.name,
                        step_index=step_index,
                        duration_ms=step_duration,
                    ),
                )
                _persist_task_progress(
                    state,
                    "RUNNING",
                    step=step.name,
                    step_index=step_index,
                    duration_ms=step_duration,
                    event="step_completed",
                )

            except Exception as e:
                # Catch any unexpected errors not handled by the step itself
                step_duration = int((time.time() - step_start) * 1000)
                step_timings[step.name] = step_duration
                state["step_timings"] = step_timings

                logger.error(
                    "step_unexpected_error",
                    **_log_fields(
                        state,
                        step=step.name,
                        step_index=step_index,
                        error_type=type(e).__name__,
                        error_message=str(e),
                        duration_ms=step_duration,
                        exc_info=True,
                    ),
                )
                state["errors"].append(f"Unexpected: {step.name}: {str(e)}")
                state["status"] = "FAILED"
                _persist_task_progress(
                    state,
                    "FAILED",
                    step=step.name,
                    step_index=step_index,
                    duration_ms=step_duration,
                    event="step_unexpected_error",
                    error_type=type(e).__name__,
                    error_message=str(e),
                    errors=state["errors"],
                )
                break

        # Final status log
        total_duration = int((time.time() - start_time) * 1000)

        if state["status"] == "COMPLETED":
            logger.info(
                "pipeline_completed",
                **_log_fields(
                    state,
                    total_duration_ms=total_duration,
                    step_timings=step_timings,
                    chunks_created=len(state.get("chunks", [])),
                    metadata_extracted=bool(state.get("metadata")),
                ),
            )
            _persist_task_progress(
                state,
                "COMPLETED",
                event="pipeline_completed",
                total_duration_ms=total_duration,
                step_timings=step_timings,
                chunks_created=len(state.get("chunks", [])),
                metadata_extracted=bool(state.get("metadata")),
            )
        else:
            logger.error(
                "pipeline_failed",
                **_log_fields(
                    state,
                    total_duration_ms=total_duration,
                    step_timings=step_timings,
                    errors=state["errors"],
                    failed_at_status=state.get("status"),
                ),
            )
            _persist_task_progress(
                state,
                "FAILED",
                event="pipeline_failed",
                total_duration_ms=total_duration,
                step_timings=step_timings,
                errors=state["errors"],
                failed_at_status=state.get("status"),
            )

        return state

    def get_step_names(self) -> list[str]:
        """Get list of step names for debugging/monitoring."""
        return [step.name for step in self.steps]


# ─── Factory Function (matches old API) ───

def create_ingestion_pipeline() -> IngestionPipeline:
    """
    Factory function to create pipeline instance.
    Provides same API as old create_ingestion_graph() for easy migration.
    """
    return IngestionPipeline()
