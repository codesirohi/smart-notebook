"""
Smart Notebook Ingestion Worker

Polls PostgreSQL for pending ingestion tasks and processes them.
Uses SELECT FOR UPDATE SKIP LOCKED for exactly-once task claiming.

Usage:
    python worker.py                    # Run worker
    python worker.py --reap-only        # Only run stale task reaper, then exit
    python worker.py --requeue-failed   # Requeue failed tasks, then exit
"""

import sys
import signal
import argparse
import time
import threading
import os
from config import config
from db import (
    init_connection_pool, close_connection_pool,
    get_connection, return_connection, get_pooled_connection,
    claim_task, complete_task, fail_task,
    reap_stale_tasks, requeue_failed_tasks, store_chunks,
    update_document_status, heartbeat_worker
)
from processor import DocumentProcessor
from ollama_client import OllamaClient

# ─── Structured Logging Setup ───
try:
    from logging_config import (
        configure_structured_logging,
        get_logger,
        log_task_start,
        log_task_complete,
        log_task_failed
    )

    # Configure based on environment
    json_output = os.getenv("LOG_FORMAT", "json").lower() == "json"
    log_level = os.getenv("LOG_LEVEL", "INFO").upper()

    configure_structured_logging(
        log_level=log_level,
        json_output=json_output
    )
    logger = get_logger("worker")
    STRUCTURED_LOGGING = True
except ImportError:
    # Fallback to standard logging
    import logging
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S'
    )
    logger = logging.getLogger("worker")
    STRUCTURED_LOGGING = False

# ─── Graceful Shutdown ───
shutdown_event = threading.Event()

def signal_handler(signum, frame):
    logger.info("shutdown_signal_received", signal=signum, worker_id=config.worker_id)
    shutdown_event.set()

signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)

# ─── Stale Task Reaper (Background Thread) ───
def stale_reaper_loop(interval_sec: int = 60):
    """
    Periodically reclaim stuck tasks. Runs in background thread.

    Interview Note: Uses connection pool with proper cleanup to avoid leaks.
    The context manager ensures connection is returned even if exception occurs.
    """
    while not shutdown_event.is_set():
        try:
            with get_pooled_connection() as conn:
                reclaimed, dead_lettered = reap_stale_tasks(conn, config.stale_timeout_min)
                if reclaimed > 0 or dead_lettered > 0:
                    logger.info(
                        "stale_tasks_reclaimed",
                        reclaimed=reclaimed,
                        dead_lettered=dead_lettered,
                        stale_timeout_min=config.stale_timeout_min,
                        worker_id=config.worker_id
                    )
        except Exception as e:
            logger.error(
                "stale_reaper_error",
                error_type=type(e).__name__,
                error_message=str(e),
                worker_id=config.worker_id,
                exc_info=True
            )

        shutdown_event.wait(interval_sec)

# ─── Main Poll Loop ───
def run_worker():
    logger.info(
        "worker_starting",
        worker_id=config.worker_id,
        poll_interval_sec=config.poll_interval_sec,
        stale_timeout_min=config.stale_timeout_min,
        ollama_url=config.ollama_url,
        embedding_model=config.embedding_model
    )

    # Initialize connection pool
    # Interview Note: Pool size matches max_workers to prevent connection blocking
    max_workers = config.max_workers if hasattr(config, 'max_workers') else 3
    init_connection_pool(min_conn=1, max_conn=max_workers + 2)  # +2 for main thread + reaper
    logger.info(
        "connection_pool_initialized",
        min_conn=1,
        max_conn=max_workers + 2,
        worker_id=config.worker_id
    )

    # Pre-flight checks
    ollama = OllamaClient()
    if not ollama.health_check():
        logger.error(
            "ollama_health_check_failed",
            ollama_url=config.ollama_url,
            embedding_model=config.embedding_model,
            worker_id=config.worker_id
        )
        close_connection_pool()
        sys.exit(1)

    logger.info("ollama_health_check_passed", ollama_url=config.ollama_url, worker_id=config.worker_id)

    # Start stale reaper thread
    reaper_thread = threading.Thread(target=stale_reaper_loop, daemon=True)
    reaper_thread.start()
    logger.info("stale_reaper_started", worker_id=config.worker_id)

    # Initialize Pipeline (replaces LangGraph)
    from pipeline import create_ingestion_pipeline
    ingestion_pipeline = create_ingestion_pipeline()
    logger.info(
        "pipeline_initialized",
        steps=ingestion_pipeline.get_step_names(),
        worker_id=config.worker_id
    )

    # Main loop connection for heartbeat and task claiming
    # Interview Note: Long-lived connection for main thread, separate from task processing
    conn = get_connection()
    consecutive_empty = 0
    heartbeat_interval_sec = int(os.getenv("WORKER_HEARTBEAT_INTERVAL_SEC", "5"))
    last_heartbeat = 0.0

    logger.info(
        "worker_ready",
        worker_id=config.worker_id,
        max_concurrent_tasks=max_workers
    )

    from concurrent.futures import ThreadPoolExecutor
    
    def process_task(task):
        """
        Process a single task in a separate thread.
        Each thread gets its own DB connection from the pool.

        Interview Note: Using connection pool prevents creating new DB connections
        for each task, which would exhaust database connections under load.
        The try/finally ensures connection is returned to pool even on errors.
        """
        task_id = task['id']
        doc_id = task['document_id']
        payload = task['payload']

        if STRUCTURED_LOGGING:
            log_task_start(logger, task_id, str(doc_id), worker_id=config.worker_id)
        else:
            logger.info(f"Thread starting task {task_id} (doc: {doc_id})")

        thread_conn = None
        start_time = time.time()

        try:
            # Get connection from pool
            thread_conn = get_connection()

            # Prepare initial state
            base_config = {"chunk_size": 512, "chunk_overlap": 50}
            if 'config' in payload and isinstance(payload['config'], dict):
                base_config.update(payload['config'])

            initial_state = {
                "document_id": str(doc_id),
                "source_path": payload.get('source_path'),
                "content_type": payload.get('content_type', 'text/plain'),
                "config": base_config,
                "status": "PENDING",
                "metadata": {},
                "chunks": [],
                "embeddings": [],
                "errors": []
            }

            # Run Pipeline (pipeline logs internally with structured logging)
            final_state = ingestion_pipeline.execute(initial_state)
            duration = int((time.time() - start_time) * 1000)

            if final_state["status"] == "COMPLETED":
                result = {
                    "status": "COMPLETED",
                    "chunks_created": len(final_state["chunks"]),
                    "processing_time_ms": duration,
                    "metadata_extracted": True
                }
                complete_task(thread_conn, task_id, result)

                if STRUCTURED_LOGGING:
                    log_task_complete(
                        logger,
                        task_id,
                        str(doc_id),
                        duration,
                        chunks_created=len(final_state["chunks"]),
                        worker_id=config.worker_id
                    )
                else:
                    logger.info(f"Task {task_id} completed in {duration}ms")
            else:
                raise RuntimeError(f"Pipeline failed: {final_state.get('errors')}")

        except Exception as e:
            duration = int((time.time() - start_time) * 1000)

            if STRUCTURED_LOGGING:
                log_task_failed(
                    logger,
                    task_id,
                    str(doc_id),
                    e,
                    duration_ms=duration,
                    worker_id=config.worker_id
                )
            else:
                logger.error(f"Task {task_id} failed: {e}", exc_info=True)

            error_details = {
                "exception_type": type(e).__name__,
                "message": str(e),
                "worker_id": config.worker_id
            }
            if thread_conn:
                fail_task(thread_conn, task_id, str(e), error_details)
                update_document_status(thread_conn, str(doc_id), 'FAILED')
        finally:
            # Return connection to pool (critical for preventing leaks)
            if thread_conn:
                return_connection(thread_conn)

    # Main Executor Loop
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = set()
        
        while not shutdown_event.is_set():
            try:
                now = time.time()
                if now - last_heartbeat >= heartbeat_interval_sec:
                    heartbeat_worker(conn, config.worker_id, {
                        "poll_interval_sec": config.poll_interval_sec,
                        "embedding_model": config.embedding_model
                    })
                    last_heartbeat = now

                # Clean up completed futures
                done = {f for f in futures if f.done()}
                futures -= done
                
                # If pool is full, wait a bit
                if len(futures) >= max_workers:
                    time.sleep(0.5)
                    continue

                # Claim next task
                task = claim_task(conn, config.worker_id)

                if not task:
                    consecutive_empty += 1
                    if consecutive_empty == 1:
                        logger.debug("no_pending_tasks", worker_id=config.worker_id, active_threads=len(futures))
                    elif consecutive_empty % 30 == 0:
                        logger.info(
                            "worker_idle",
                            worker_id=config.worker_id,
                            active_threads=len(futures),
                            idle_polls=consecutive_empty
                        )

                    # If no tasks, sleep efficiently but check for shutdown
                    # Only sleep long if we really have nothing to do
                    wait_time = config.poll_interval_sec if len(futures) == 0 else 1.0
                    shutdown_event.wait(wait_time)
                    continue

                consecutive_empty = 0
                logger.info(
                    "task_claimed",
                    task_id=task['id'],
                    document_id=task['document_id'],
                    active_threads=len(futures),
                    worker_id=config.worker_id
                )

                # Submit task to pool
                future = executor.submit(process_task, task)
                futures.add(future)

            except Exception as e:
                logger.error(
                    "worker_loop_error",
                    error_type=type(e).__name__,
                    error_message=str(e),
                    worker_id=config.worker_id,
                    exc_info=True
                )
                try:
                    return_connection(conn)
                except:
                    pass
                time.sleep(5)
                conn = get_connection()
                logger.info("database_reconnected", worker_id=config.worker_id)

    logger.info("worker_shutdown", worker_id=config.worker_id)

    # Clean shutdown: return connection and close pool
    return_connection(conn)
    close_connection_pool()

# ─── CLI Entry Point ───
def main():
    parser = argparse.ArgumentParser(description="Smart Notebook Ingestion Worker")
    parser.add_argument("--reap-only", action="store_true",
                        help="Run stale task reaper once and exit")
    parser.add_argument("--requeue-failed", action="store_true",
                        help="Requeue all failed tasks and exit")
    args = parser.parse_args()

    if args.reap_only:
        # Initialize minimal pool for one-off operation
        init_connection_pool(min_conn=1, max_conn=1)
        with get_pooled_connection() as conn:
            reclaimed, dead = reap_stale_tasks(conn, config.stale_timeout_min)
            logger.info(f"Reaper: reclaimed={reclaimed}, dead_lettered={dead}")
        close_connection_pool()
        return

    if args.requeue_failed:
        # Initialize minimal pool for one-off operation
        init_connection_pool(min_conn=1, max_conn=1)
        with get_pooled_connection() as conn:
            count = requeue_failed_tasks(conn)
            logger.info(f"Requeued {count} failed tasks")
        close_connection_pool()
        return

    run_worker()

if __name__ == "__main__":
    main()
