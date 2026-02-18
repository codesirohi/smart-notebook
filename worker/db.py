import psycopg2
from psycopg2.extras import RealDictCursor, Json
from psycopg2.pool import ThreadedConnectionPool
from contextlib import contextmanager
from config import config
import logging
import atexit

logger = logging.getLogger(__name__)

# ─── Connection Pool ───
# Interview Note: ThreadedConnectionPool prevents connection exhaustion under load.
# Each worker thread gets a connection from the pool instead of creating new ones.
# Pool size matches max_workers to avoid blocking on connection acquisition.

_connection_pool = None

def init_connection_pool(min_conn=1, max_conn=10):
    """
    Initialize the connection pool. Call this once at worker startup.

    Args:
        min_conn: Minimum number of connections to maintain
        max_conn: Maximum number of connections (should match worker max_workers)

    Interview Note: Connection pooling is a production best practice because:
    1. Reduces connection overhead (TCP handshake, auth, etc.)
    2. Prevents connection exhaustion under concurrent load
    3. Provides connection reuse and efficient resource management
    """
    global _connection_pool
    if _connection_pool is not None:
        logger.warning("Connection pool already initialized")
        return

    logger.info(f"Initializing connection pool (min={min_conn}, max={max_conn})")
    _connection_pool = ThreadedConnectionPool(
        minconn=min_conn,
        maxconn=max_conn,
        host=config.db_host,
        port=config.db_port,
        dbname=config.db_name,
        user=config.db_user,
        password=config.db_password
    )

    # Ensure pool is closed on exit
    atexit.register(close_connection_pool)
    logger.info("Connection pool initialized successfully")

def close_connection_pool():
    """Close all connections in the pool. Called automatically on exit."""
    global _connection_pool
    if _connection_pool is not None:
        logger.info("Closing connection pool")
        _connection_pool.closeall()
        _connection_pool = None

def get_connection():
    """
    Get a connection from the pool.

    Returns:
        psycopg2 connection object

    Raises:
        RuntimeError: If pool is not initialized

    Interview Note: This uses ThreadedConnectionPool.getconn() which is thread-safe.
    If all connections are in use, this will block until one becomes available.
    """
    if _connection_pool is None:
        raise RuntimeError("Connection pool not initialized. Call init_connection_pool() first.")
    return _connection_pool.getconn()

def return_connection(conn):
    """
    Return a connection to the pool for reuse.

    Args:
        conn: Connection to return to pool

    Interview Note: Always return connections to avoid pool exhaustion.
    Use try/finally blocks or context managers to ensure cleanup.
    """
    if _connection_pool is not None and conn is not None:
        _connection_pool.putconn(conn)

@contextmanager
def get_cursor(conn, commit=True):
    """
    Context manager for database cursors with automatic commit/rollback.

    Interview Note: This pattern ensures:
    1. Automatic transaction management (commit on success, rollback on error)
    2. Cursor cleanup (close cursor in finally block)
    3. Connection remains valid for reuse in pool
    """
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

@contextmanager
def get_pooled_connection():
    """
    Context manager for pooled connections with automatic return.

    Usage:
        with get_pooled_connection() as conn:
            # Use connection
            pass
        # Connection automatically returned to pool

    Interview Note: This ensures connections are always returned to the pool,
    even if exceptions occur. Prevents connection leaks.
    """
    conn = get_connection()
    try:
        yield conn
    finally:
        return_connection(conn)

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

def update_document_status(conn, document_id, status, raw_content=None, metadata=None):
    """Update document status and optionally store extracted text and metadata."""
    with get_cursor(conn) as cur:
        if raw_content and metadata:
            cur.execute("""
                UPDATE documents
                SET status = %s, raw_content = %s, metadata = %s, updated_at = NOW()
                WHERE id = %s
            """, (status, raw_content, Json(metadata), document_id))
        elif raw_content:
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


def heartbeat_worker(conn, worker_id: str, metadata: dict | None = None):
    """
    Upsert worker heartbeat. Called periodically by each worker process.
    """
    with get_cursor(conn) as cur:
        cur.execute("""
            INSERT INTO worker_heartbeats (worker_id, started_at, last_seen_at, metadata)
            VALUES (%s, NOW(), NOW(), %s::jsonb)
            ON CONFLICT (worker_id)
            DO UPDATE SET
                last_seen_at = NOW(),
                metadata = EXCLUDED.metadata
        """, (worker_id, Json(metadata or {})))
