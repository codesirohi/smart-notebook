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
from typing import Protocol
from abc import abstractmethod

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
                document_id=doc_id,
                source_path=source_path,
                content_type=content_type
            )

            extract_start = time.time()
            raw_text, basic_meta = extract_text(source_path, content_type)
            extract_duration = int((time.time() - extract_start) * 1000)

            if not raw_text or len(raw_text.strip()) < 10:
                raise ValueError("Extracted text is empty or too short")

            logger.info(
                "text_extracted",
                document_id=doc_id,
                text_length=len(raw_text),
                duration_ms=extract_duration,
                metadata=basic_meta
            )

            # 2. Structured metadata extraction (LLM-based)
            extraction_model = state['config'].get('extraction_model', config.extraction_model)
            logger.info(
                "extracting_metadata",
                document_id=doc_id,
                model=extraction_model,
                text_sample_length=min(2000, len(raw_text))
            )

            meta_start = time.time()
            llm_meta = extract_metadata(raw_text, extraction_model)
            meta_duration = int((time.time() - meta_start) * 1000)

            # Merge metadata
            final_meta = {**basic_meta, **llm_meta}

            total_duration = int((time.time() - start_time) * 1000)
            logger.info(
                "extract_step_completed",
                document_id=doc_id,
                total_duration_ms=total_duration,
                text_extraction_ms=extract_duration,
                metadata_extraction_ms=meta_duration,
                final_metadata=final_meta
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
                document_id=doc_id,
                error_type=type(e).__name__,
                error_message=str(e),
                duration_ms=duration,
                exc_info=True
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
                document_id=doc_id,
                text_length=len(state["raw_text"]),
                chunk_size=chunk_size,
                chunk_overlap=chunk_overlap
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
                document_id=doc_id,
                chunks_created=len(chunks_list),
                total_tokens=total_tokens,
                avg_tokens_per_chunk=avg_tokens,
                duration_ms=duration
            )

            return {**state, "chunks": chunks_list, "status": "CHUNKED"}

        except Exception as e:
            duration = int((time.time() - start_time) * 1000)
            logger.error(
                "chunk_step_failed",
                document_id=doc_id,
                error_type=type(e).__name__,
                error_message=str(e),
                duration_ms=duration,
                exc_info=True
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
                document_id=doc_id,
                model=model,
                provider=provider,
                chunk_count=len(state["chunks"])
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
                    embedding_dimensions=len(embeddings[0]) if embeddings else 0
                )

            logger.info(
                "embed_step_completed",
                document_id=doc_id,
                embeddings_generated=len(embeddings),
                embedding_dimensions=len(embeddings[0]) if embeddings else 0,
                api_duration_ms=embed_duration,
                total_duration_ms=total_duration,
                model=model,
                provider=provider
            )

            return {**state, "embeddings": embeddings, "status": "EMBEDDED"}

        except Exception as e:
            duration = int((time.time() - start_time) * 1000)
            logger.error(
                "embed_step_failed",
                document_id=doc_id,
                model=state['config'].get('embedding_model', config.embedding_model),
                error_type=type(e).__name__,
                error_message=str(e),
                duration_ms=duration,
                exc_info=True
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
            from db import get_connection, store_chunks, update_document_status

            logger.info(
                "storing_to_database",
                document_id=doc_id,
                chunks_to_store=len(state["chunks"])
            )

            conn = get_connection()

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

            conn.close()

            total_duration = int((time.time() - start_time) * 1000)

            logger.info(
                "store_step_completed",
                document_id=doc_id,
                chunks_stored=len(chunk_records),
                store_chunks_ms=store_duration,
                update_document_ms=update_duration,
                total_duration_ms=total_duration
            )

            return {**state, "status": "COMPLETED"}

        except Exception as e:
            duration = int((time.time() - start_time) * 1000)
            logger.error(
                "store_step_failed",
                document_id=doc_id,
                error_type=type(e).__name__,
                error_message=str(e),
                duration_ms=duration,
                exc_info=True
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
        logger.info(f"Pipeline initialized with {len(self.steps)} steps")

    def execute(self, initial_state: IngestionState) -> IngestionState:
        """
        Execute all pipeline steps sequentially.

        Args:
            initial_state: Initial ingestion state

        Returns:
            Final state with status "COMPLETED" or "FAILED"
        """
        state = initial_state
        doc_id = state.get("document_id", "unknown")
        start_time = time.time()

        logger.info(
            "pipeline_started",
            document_id=doc_id,
            source_path=state.get("source_path"),
            content_type=state.get("content_type"),
            steps=self.get_step_names()
        )

        step_timings = {}

        for step in self.steps:
            # Skip remaining steps if already failed
            if state["status"] == "FAILED":
                logger.warning(
                    "skipping_step",
                    document_id=doc_id,
                    step=step.name,
                    reason="pipeline_already_failed"
                )
                break

            step_start = time.time()
            logger.debug("executing_step", document_id=doc_id, step=step.name)

            try:
                state = step.process(state)
                step_duration = int((time.time() - step_start) * 1000)
                step_timings[step.name] = step_duration

                if state["status"] == "FAILED":
                    logger.error(
                        "step_failed",
                        document_id=doc_id,
                        step=step.name,
                        errors=state['errors'],
                        duration_ms=step_duration
                    )
                    break

                logger.debug(
                    "step_completed",
                    document_id=doc_id,
                    step=step.name,
                    duration_ms=step_duration
                )

            except Exception as e:
                # Catch any unexpected errors not handled by the step itself
                step_duration = int((time.time() - step_start) * 1000)
                step_timings[step.name] = step_duration

                logger.error(
                    "step_unexpected_error",
                    document_id=doc_id,
                    step=step.name,
                    error_type=type(e).__name__,
                    error_message=str(e),
                    duration_ms=step_duration,
                    exc_info=True
                )
                state["errors"].append(f"Unexpected: {step.name}: {str(e)}")
                state["status"] = "FAILED"
                break

        # Final status log
        total_duration = int((time.time() - start_time) * 1000)

        if state["status"] == "COMPLETED":
            logger.info(
                "pipeline_completed",
                document_id=doc_id,
                total_duration_ms=total_duration,
                step_timings=step_timings,
                chunks_created=len(state.get("chunks", [])),
                metadata_extracted=bool(state.get("metadata"))
            )
        else:
            logger.error(
                "pipeline_failed",
                document_id=doc_id,
                total_duration_ms=total_duration,
                step_timings=step_timings,
                errors=state['errors'],
                failed_at_status=state.get("status")
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
