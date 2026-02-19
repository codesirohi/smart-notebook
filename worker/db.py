import psycopg2
from psycopg2.extras import RealDictCursor, Json
from psycopg2.pool import ThreadedConnectionPool
from contextlib import contextmanager
from config import config
import logging
import atexit
import hashlib

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


def update_task_progress(conn, task_id, progress: dict):
    """
    Persist in-flight task progress in ingestion_tasks.result as JSONB.

    This keeps status='PROCESSING' while exposing stage-level progress to
    /api/tasks/{taskId}/status for UI/debug visibility.
    """
    with get_cursor(conn) as cur:
        cur.execute("""
            UPDATE ingestion_tasks
            SET result = COALESCE(result, '{}'::jsonb) || %s::jsonb,
                updated_at = NOW()
            WHERE id = %s
        """, (Json(progress), task_id))

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

def compute_content_hash(content: str) -> str:
    """
    Compute SHA-256 hash of content for deduplication.

    Interview Note: SHA-256 provides:
    1. Collision resistance (practically impossible to find two texts with same hash)
    2. Fast computation (microseconds for typical chunks)
    3. Fixed 64-char hex output regardless of input size
    """
    return hashlib.sha256(content.encode('utf-8')).hexdigest()


def store_chunks(conn, document_id, chunks: list[dict]):
    """
    Bulk insert chunks with embeddings and deduplication.

    Replacement + deduplication strategy:
    1. Remove existing chunks for this document (full re-index semantics)
    2. Compute SHA-256 hash of each new chunk's content
    3. Check if identical content exists in ANY document
    4. Reuse embedding for duplicate content, otherwise store fresh embedding

    Why full replacement:
    - Upsert alone leaves stale tail chunks when the new chunk count is smaller.
    - Deleting first guarantees retrieval only sees the latest extraction/chunking result.

    Deduplication details:
    1. Compute SHA-256 hash of each chunk's content
    2. Check if identical content exists in ANY document
    3. Skip storing duplicate content, link to existing chunk instead
    4. Saves ~20% storage on documents with repetitive content

    Interview Note: Content-based deduplication is more effective than
    document-level deduplication because similar documents often share
    common sections (headers, footers, boilerplate).
    """
    stored_count = 0
    skipped_count = 0

    with get_cursor(conn) as cur:
        # Full replacement for this document to avoid stale chunks after reprocessing.
        cur.execute("""
            DELETE FROM document_chunks
            WHERE document_id = %s
        """, (document_id,))

        for chunk in chunks:
            content_hash = compute_content_hash(chunk['content'])

            # Check for existing chunk with same content hash
            cur.execute("""
                SELECT id FROM document_chunks
                WHERE content_hash = %s
                LIMIT 1
            """, (content_hash,))
            existing = cur.fetchone()

            if existing:
                # Duplicate content found - skip storing but log the reference
                skipped_count += 1
                logger.debug(
                    f"Skipping duplicate chunk {chunk['chunk_index']} "
                    f"(matches existing chunk {existing['id']})"
                )
                # Still insert a reference row for this document's chunk ordering
                # but reuse the embedding from the existing chunk
                cur.execute("""
                    INSERT INTO document_chunks
                        (document_id, chunk_index, content, content_hash,
                         token_count, embedding, metadata)
                    SELECT %s, %s, %s, %s, %s,
                           (SELECT embedding FROM document_chunks WHERE id = %s),
                           %s::jsonb
                    ON CONFLICT (document_id, chunk_index)
                    DO UPDATE SET
                        content = EXCLUDED.content,
                        content_hash = EXCLUDED.content_hash,
                        token_count = EXCLUDED.token_count,
                        embedding = EXCLUDED.embedding,
                        metadata = EXCLUDED.metadata
                """, (
                    document_id,
                    chunk['chunk_index'],
                    chunk['content'],
                    content_hash,
                    chunk['token_count'],
                    existing['id'],
                    Json(chunk.get('metadata', {}))
                ))
            else:
                # New unique content - store with embedding
                cur.execute("""
                    INSERT INTO document_chunks
                        (document_id, chunk_index, content, content_hash,
                         token_count, embedding, metadata)
                    VALUES (%s, %s, %s, %s, %s, %s::vector, %s::jsonb)
                    ON CONFLICT (document_id, chunk_index)
                    DO UPDATE SET
                        content = EXCLUDED.content,
                        content_hash = EXCLUDED.content_hash,
                        token_count = EXCLUDED.token_count,
                        embedding = EXCLUDED.embedding,
                        metadata = EXCLUDED.metadata
                """, (
                    document_id,
                    chunk['chunk_index'],
                    chunk['content'],
                    content_hash,
                    chunk['token_count'],
                    chunk['embedding_str'],
                    Json(chunk.get('metadata', {}))
                ))
                stored_count += 1

    if skipped_count > 0:
        logger.info(
            f"Chunk deduplication: stored={stored_count}, "
            f"reused_embeddings={skipped_count}, "
            f"savings={skipped_count/(stored_count+skipped_count)*100:.1f}%"
        )

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
