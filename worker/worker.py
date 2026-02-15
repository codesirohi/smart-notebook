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
import logging
import argparse
import time
import threading
from config import config
from db import (
    get_connection, claim_task, complete_task, fail_task,
    reap_stale_tasks, requeue_failed_tasks, store_chunks,
    update_document_status
)
from processor import DocumentProcessor
from ollama_client import OllamaClient

# ─── Logging Setup ───
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger("worker")

# ─── Graceful Shutdown ───
shutdown_event = threading.Event()

def signal_handler(signum, frame):
    logger.info(f"Received signal {signum}. Shutting down gracefully...")
    shutdown_event.set()

signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)

# ─── Stale Task Reaper (Background Thread) ───
def stale_reaper_loop(interval_sec: int = 60):
    """Periodically reclaim stuck tasks. Runs in background thread."""
    while not shutdown_event.is_set():
        try:
            conn = get_connection()
            reclaimed, dead_lettered = reap_stale_tasks(conn, config.stale_timeout_min)
            if reclaimed > 0 or dead_lettered > 0:
                logger.info(
                    f"Stale reaper: reclaimed={reclaimed}, dead_lettered={dead_lettered}"
                )
            conn.close()
        except Exception as e:
            logger.error(f"Stale reaper error: {e}")

        shutdown_event.wait(interval_sec)

# ─── Main Poll Loop ───
def run_worker():
    logger.info(f"Worker starting: id={config.worker_id}")
    logger.info(f"Polling interval: {config.poll_interval_sec}s")
    logger.info(f"Stale timeout: {config.stale_timeout_min}min")
    logger.info(f"Ollama: {config.ollama_url} (model: {config.embedding_model})")

    # Pre-flight checks
    ollama = OllamaClient()
    if not ollama.health_check():
        logger.error("Ollama health check failed. Is Ollama running?")
        logger.error(f"  Try: ollama serve && ollama pull {config.embedding_model}")
        sys.exit(1)

    logger.info("Ollama health check passed")

    # Start stale reaper thread
    reaper_thread = threading.Thread(target=stale_reaper_loop, daemon=True)
    reaper_thread.start()
    logger.info("Stale task reaper started (background thread)")

    # Initialize Graph
    from graph import create_ingestion_graph
    ingestion_graph = create_ingestion_graph()
    logger.info("Ingestion Graph compiled")

    conn = get_connection()
    consecutive_empty = 0
    max_workers = config.max_workers if hasattr(config, 'max_workers') else 3
    
    logger.info(f"Worker ready. Polling for tasks (Concurrency: {max_workers})...")

    from concurrent.futures import ThreadPoolExecutor
    
    def process_task(task):
        """
        Process a single task in a separate thread.
        Each thread needs its own DB connection for safety.
        """
        task_id = task['id']
        doc_id = task['document_id']
        payload = task['payload']
        
        # Determine worker ID for logging context
        logger.info(f"Thread starting task {task_id} (doc: {doc_id})")
        
        thread_conn = None
        try:
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

            # Run Graph
            logger.info(f"Invoking Ingestion Graph for {doc_id}...")
            start_time = time.time()
            final_state = ingestion_graph.invoke(initial_state)
            duration = int((time.time() - start_time) * 1000)

            if final_state["status"] == "COMPLETED":
                result = {
                    "status": "COMPLETED",
                    "chunks_created": len(final_state["chunks"]),
                    "processing_time_ms": duration,
                    "metadata_extracted": True
                }
                complete_task(thread_conn, task_id, result)
                logger.info(f"Task {task_id} completed in {duration}ms")
            else:
                raise RuntimeError(f"Graph failed: {final_state.get('errors')}")

        except Exception as e:
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
            if thread_conn:
                try: 
                    thread_conn.close() 
                except: 
                    pass

    # Main Executor Loop
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = set()
        
        while not shutdown_event.is_set():
            try:
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
                        logger.debug("No pending tasks. Waiting...")
                    elif consecutive_empty % 30 == 0:
                        logger.info(f"Still waiting... (Active threads: {len(futures)})")

                    # If no tasks, sleep efficiently but check for shutdown
                    # Only sleep long if we really have nothing to do
                    wait_time = config.poll_interval_sec if len(futures) == 0 else 1.0
                    shutdown_event.wait(wait_time)
                    continue

                consecutive_empty = 0
                logger.info(f"Claimed task {task['id']}, submitting to pool (Active: {len(futures)})")
                
                # Submit task to pool
                future = executor.submit(process_task, task)
                futures.add(future)

            except Exception as e:
                logger.error(f"Worker loop error: {e}", exc_info=True)
                try:
                    conn.close()
                except:
                    pass
                time.sleep(5)
                conn = get_connection()
                logger.info("Database reconnected")

    logger.info("Worker shut down gracefully")
    conn.close()

# ─── CLI Entry Point ───
def main():
    parser = argparse.ArgumentParser(description="Smart Notebook Ingestion Worker")
    parser.add_argument("--reap-only", action="store_true",
                        help="Run stale task reaper once and exit")
    parser.add_argument("--requeue-failed", action="store_true",
                        help="Requeue all failed tasks and exit")
    args = parser.parse_args()

    if args.reap_only:
        conn = get_connection()
        reclaimed, dead = reap_stale_tasks(conn, config.stale_timeout_min)
        logger.info(f"Reaper: reclaimed={reclaimed}, dead_lettered={dead}")
        conn.close()
        return

    if args.requeue_failed:
        conn = get_connection()
        count = requeue_failed_tasks(conn)
        logger.info(f"Requeued {count} failed tasks")
        conn.close()
        return

    run_worker()

if __name__ == "__main__":
    main()
