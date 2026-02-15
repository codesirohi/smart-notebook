import psycopg2
from psycopg2.extras import RealDictCursor, Json
from contextlib import contextmanager
from config import config
import logging

logger = logging.getLogger(__name__)

def get_connection():
    return psycopg2.connect(
        host=config.db_host,
        port=config.db_port,
        dbname=config.db_name,
        user=config.db_user,
        password=config.db_password
    )

@contextmanager
def get_cursor(conn, commit=True):
    cur = conn.cursor(cursor_factory=RealDictCursor)
    try:
        yield cur
        if commit:
            conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.close()

def claim_task(conn, worker_id: str):
    """
    Atomically claim the next pending task.
    Uses FOR UPDATE SKIP LOCKED for exactly-once semantics.
    """
    with get_cursor(conn) as cur:
        cur.execute("""
            UPDATE ingestion_tasks
            SET status = 'PROCESSING',
                locked_at = NOW(),
                locked_by = %s,
                updated_at = NOW()
            WHERE id = (
                SELECT id FROM ingestion_tasks
                WHERE status = 'PENDING'
                ORDER BY priority DESC, created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            RETURNING *
        """, (worker_id,))
        return cur.fetchone()

def complete_task(conn, task_id, result: dict):
    """Mark task as completed with result summary."""
    with get_cursor(conn) as cur:
        cur.execute("""
            UPDATE ingestion_tasks
            SET status = 'COMPLETED',
                result = %s::jsonb,
                completed_at = NOW(),
                updated_at = NOW()
            WHERE id = %s
        """, (Json(result), task_id))

def fail_task(conn, task_id, error_message: str, error_details: dict = None):
    """Mark task as failed. Increment retry count."""
    with get_cursor(conn) as cur:
        cur.execute("""
            UPDATE ingestion_tasks
            SET status = CASE
                    WHEN retry_count + 1 >= max_retries THEN 'DEAD_LETTER'
                    ELSE 'FAILED'
                END,
                error_message = %s,
                error_details = %s::jsonb,
                retry_count = retry_count + 1,
                updated_at = NOW()
            WHERE id = %s
        """, (error_message, Json(error_details or {}), task_id))

def requeue_failed_tasks(conn):
    """Reset FAILED tasks back to PENDING for retry."""
    with get_cursor(conn) as cur:
        cur.execute("""
            UPDATE ingestion_tasks
            SET status = 'PENDING',
                locked_at = NULL,
                locked_by = NULL,
                updated_at = NOW()
            WHERE status = 'FAILED'
              AND retry_count < max_retries
        """)
        return cur.rowcount

def reap_stale_tasks(conn, timeout_minutes: int):
    """Reclaim tasks stuck in PROCESSING beyond the timeout."""
    with get_cursor(conn) as cur:
        cur.execute("""
            UPDATE ingestion_tasks
            SET status = 'PENDING',
                locked_at = NULL,
                locked_by = NULL,
                retry_count = retry_count + 1,
                error_message = 'Reclaimed by stale task reaper (worker likely crashed)',
                updated_at = NOW()
            WHERE status = 'PROCESSING'
              AND locked_at < NOW() - INTERVAL '%s minutes'
              AND retry_count < max_retries
        """, (timeout_minutes,))
        reclaimed = cur.rowcount

        cur.execute("""
            UPDATE ingestion_tasks
            SET status = 'DEAD_LETTER',
                error_message = 'Max retries exceeded after stale reap',
                updated_at = NOW()
            WHERE status = 'PROCESSING'
              AND locked_at < NOW() - INTERVAL '%s minutes'
              AND retry_count >= max_retries
        """, (timeout_minutes,))
        dead_lettered = cur.rowcount

        return reclaimed, dead_lettered

def store_chunks(conn, document_id, chunks: list[dict]):
    """
    Bulk insert chunks with embeddings.
    Uses ON CONFLICT for idempotent re-processing.
    """
    with get_cursor(conn) as cur:
        for chunk in chunks:
            cur.execute("""
                INSERT INTO document_chunks
                    (document_id, chunk_index, content, token_count, embedding, metadata)
                VALUES (%s, %s, %s, %s, %s::vector, %s::jsonb)
                ON CONFLICT (document_id, chunk_index)
                DO UPDATE SET
                    content = EXCLUDED.content,
                    token_count = EXCLUDED.token_count,
                    embedding = EXCLUDED.embedding,
                    metadata = EXCLUDED.metadata
            """, (
                document_id,
                chunk['chunk_index'],
                chunk['content'],
                chunk['token_count'],
                chunk['embedding_str'],
                Json(chunk.get('metadata', {}))
            ))

def update_document_status(conn, document_id, status, raw_content=None):
    """Update document status and optionally store extracted text."""
    with get_cursor(conn) as cur:
        if raw_content:
            cur.execute("""
                UPDATE documents
                SET status = %s, raw_content = %s, updated_at = NOW()
                WHERE id = %s
            """, (status, raw_content, document_id))
        else:
            cur.execute("""
                UPDATE documents SET status = %s, updated_at = NOW()
                WHERE id = %s
            """, (status, document_id))
