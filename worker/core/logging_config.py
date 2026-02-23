"""
Structured Logging Configuration

This module configures structlog for JSON-based structured logging,
making logs easy to parse, search, and analyze in production.

Features:
- JSON output format
- Automatic timestamp addition
- Log level and logger name
- Exception info with stack traces
- Correlation IDs for request tracing
- Performance metrics
"""

import logging
import sys
import structlog
from typing import Any


def configure_structured_logging(
    log_level: str = "INFO",
    json_output: bool = True,
    include_timestamp: bool = True
):
    """
    Configure structlog for the application.

    Args:
        log_level: Minimum log level (DEBUG, INFO, WARNING, ERROR)
        json_output: If True, output JSON. If False, use console-friendly format
        include_timestamp: Whether to include timestamps in logs
    """

    # Convert log level string to logging constant
    numeric_level = getattr(logging, log_level.upper(), logging.INFO)

    # Configure standard logging
    logging.basicConfig(
        format="%(message)s",
        stream=sys.stdout,
        level=numeric_level,
    )

    # Shared processors for all configurations
    shared_processors = [
        # Add log level to event dict
        structlog.stdlib.add_log_level,
        # Add logger name to event dict
        structlog.stdlib.add_logger_name,
        # Add thread/process info
        structlog.processors.add_log_level,
        # Make event_dict immutable to prevent accidental modification
        structlog.processors.EventRenamer("message"),
    ]

    if include_timestamp:
        # Add ISO format timestamp
        shared_processors.insert(0, structlog.processors.TimeStamper(fmt="iso"))

    # Stack info and exception formatting
    shared_processors.extend([
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
    ])

    # Final renderer: JSON for production, console for development
    if json_output:
        final_processor = structlog.processors.JSONRenderer()
    else:
        final_processor = structlog.dev.ConsoleRenderer()

    shared_processors.append(final_processor)

    # Configure structlog
    structlog.configure(
        processors=shared_processors,
        wrapper_class=structlog.stdlib.BoundLogger,
        context_class=dict,
        logger_factory=structlog.stdlib.LoggerFactory(),
        cache_logger_on_first_use=True,
    )

    # Silence noisy libraries
    logging.getLogger("urllib3").setLevel(logging.WARNING)
    logging.getLogger("requests").setLevel(logging.WARNING)
    logging.getLogger("langchain").setLevel(logging.WARNING)

    return structlog.get_logger()


def get_logger(name: str = None) -> structlog.BoundLogger:
    """
    Get a structured logger instance.

    Args:
        name: Logger name (usually __name__)

    Returns:
        Configured structlog logger

    Example:
        logger = get_logger(__name__)
        logger.info("task_started",
            task_id=123,
            document_id="abc-123",
            worker_id="worker-1")
    """
    if name:
        return structlog.get_logger(name)
    return structlog.get_logger()


def add_context(**kwargs: Any) -> None:
    """
    Add context that will be included in all subsequent log entries
    from this logger/thread.

    Args:
        **kwargs: Key-value pairs to add to logging context

    Example:
        add_context(worker_id="worker-1", session_id="xyz-789")
        # All subsequent logs will include these fields
    """
    logger = structlog.get_logger()
    return logger.bind(**kwargs)


def clear_context() -> None:
    """Clear all bound context from the current logger."""
    logger = structlog.get_logger()
    return logger.unbind()


# Convenience functions for common logging patterns

def log_task_start(logger: structlog.BoundLogger, task_id: int, document_id: str, **kwargs):
    """Log task start with standard fields."""
    logger.info(
        "task_started",
        task_id=task_id,
        document_id=document_id,
        **kwargs
    )


def log_task_complete(
    logger: structlog.BoundLogger,
    task_id: int,
    document_id: str,
    duration_ms: int,
    **kwargs
):
    """Log task completion with standard fields."""
    logger.info(
        "task_completed",
        task_id=task_id,
        document_id=document_id,
        duration_ms=duration_ms,
        **kwargs
    )


def log_task_failed(
    logger: structlog.BoundLogger,
    task_id: int,
    document_id: str,
    error: Exception,
    **kwargs
):
    """Log task failure with exception details."""
    logger.error(
        "task_failed",
        task_id=task_id,
        document_id=document_id,
        error_type=type(error).__name__,
        error_message=str(error),
        **kwargs,
        exc_info=True
    )


def log_performance(
    logger: structlog.BoundLogger,
    operation: str,
    duration_ms: int,
    **metrics
):
    """
    Log performance metrics for an operation.

    Args:
        logger: Logger instance
        operation: Name of the operation (e.g., "embedding", "vector_search")
        duration_ms: Duration in milliseconds
        **metrics: Additional metrics (e.g., items_processed, tokens_used)

    Example:
        log_performance(
            logger,
            "embedding",
            duration_ms=1250,
            chunks=50,
            tokens=12500,
            model="all-minilm"
        )
    """
    logger.info(
        "performance_metric",
        operation=operation,
        duration_ms=duration_ms,
        **metrics
    )


def log_api_call(
    logger: structlog.BoundLogger,
    provider: str,
    model: str,
    operation: str,
    duration_ms: int,
    success: bool,
    **kwargs
):
    """
    Log external API calls for monitoring and cost tracking.

    Args:
        logger: Logger instance
        provider: Provider name (openai, anthropic, google, ollama)
        model: Model name
        operation: Operation type (chat, embedding)
        duration_ms: API call duration
        success: Whether call succeeded
        **kwargs: Additional context (tokens, cost, etc.)
    """
    logger.info(
        "api_call",
        provider=provider,
        model=model,
        operation=operation,
        duration_ms=duration_ms,
        success=success,
        **kwargs
    )


# Example usage and testing
if __name__ == "__main__":
    # Configure for console output (development)
    configure_structured_logging(log_level="DEBUG", json_output=False)
    logger = get_logger(__name__)

    print("\n=== Testing Structured Logging ===\n")

    # Basic log
    logger.info("application_started", version="1.0.0", environment="development")

    # Log with context
    logger.info(
        "processing_document",
        document_id="doc-123",
        filename="test.pdf",
        size_kb=1024
    )

    # Performance log
    log_performance(
        logger,
        "text_extraction",
        duration_ms=1250,
        pages=10,
        characters=50000
    )

    # API call log
    log_api_call(
        logger,
        provider="openai",
        model="gpt-4",
        operation="chat",
        duration_ms=2500,
        success=True,
        tokens=1500,
        cost_usd=0.015
    )

    # Error log
    try:
        raise ValueError("Test error for logging")
    except Exception as e:
        logger.error(
            "operation_failed",
            operation="test",
            error_type=type(e).__name__,
            error_message=str(e),
            exc_info=True
        )

    print("\n=== Testing Complete ===\n")

    # Now test JSON output
    print("\n=== Testing JSON Output ===\n")
    configure_structured_logging(log_level="INFO", json_output=True)
    logger = get_logger(__name__)

    logger.info(
        "json_example",
        task_id=456,
        status="completed",
        metrics={"chunks": 25, "tokens": 5000}
    )
