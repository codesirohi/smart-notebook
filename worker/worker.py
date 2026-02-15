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

    # Initialize processor
    processor = DocumentProcessor()

    conn = get_connection()
    consecutive_empty = 0

    logger.info("Worker ready. Polling for tasks...")

    while not shutdown_event.is_set():
        try:
            # Claim next task
            task = claim_task(conn, config.worker_id)

            if not task:
                consecutive_empty += 1
                if consecutive_empty == 1:
                    logger.debug("No pending tasks. Waiting...")
                elif consecutive_empty % 30 == 0:
                    logger.info("Still waiting for tasks...")

                shutdown_event.wait(config.poll_interval_sec)
                continue

            consecutive_empty = 0
            task_id = task['id']
            doc_id = task['document_id']

            logger.info(f"Claimed task {task_id} (document: {doc_id})")

            try:
                # Process the document
                result, chunk_records, raw_text = processor.process(task)

                # Store chunks in database
                store_chunks(conn, str(doc_id), chunk_records)

                # Update document with extracted text
                update_document_status(conn, str(doc_id), 'INDEXED', raw_text)

                # Mark task complete
                complete_task(conn, task_id, result)

                logger.info(
                    f"Task {task_id} completed: "
                    f"{result['chunks_created']} chunks in {result['processing_time_ms']}ms"
                )

            except Exception as e:
                logger.error(f"Task {task_id} failed: {e}", exc_info=True)

                error_details = {
                    "exception_type": type(e).__name__,
                    "message": str(e),
                    "worker_id": config.worker_id
                }

                fail_task(conn, task_id, str(e), error_details)
                update_document_status(conn, str(doc_id), 'FAILED')

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
