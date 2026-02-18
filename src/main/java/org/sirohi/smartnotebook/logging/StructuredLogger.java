package org.sirohi.smartnotebook.logging;

import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.Map;
import java.util.HashMap;

/**
 * Structured logging utility for production-grade observability.
 *
 * Interview Note: This provides structured logging similar to Python's structlog,
 * making logs machine-parseable for monitoring and alerting systems.
 *
 * Uses SLF4J MDC (Mapped Diagnostic Context) to attach structured fields to log events.
 * When paired with JSON logging appenders (Logback/Log4j2), these fields become
 * top-level JSON keys for easy querying in log aggregation systems.
 *
 * Example:
 * <pre>
 * StructuredLogger.info(log, "query_completed")
 *     .field("latency_ms", 1234)
 *     .field("citations_count", 5)
 *     .field("model", "gpt-4")
 *     .log();
 * </pre>
 *
 * Benefits:
 * 1. Consistent log format across services
 * 2. Easy to parse and alert on specific fields
 * 3. Better than string templates for production monitoring
 * 4. Works with existing SLF4J/Logback infrastructure
 */
public class StructuredLogger {

    /**
     * Builder for structured log events.
     */
    public static class LogEvent {
        private final Logger logger;
        private final LogLevel level;
        private final String event;
        private final Map<String, String> fields = new HashMap<>();
        private Throwable throwable;

        private LogEvent(Logger logger, LogLevel level, String event) {
            this.logger = logger;
            this.level = level;
            this.event = event;
        }

        /**
         * Add a structured field to the log event.
         *
         * @param key Field name
         * @param value Field value (will be converted to string)
         * @return this LogEvent for chaining
         */
        public LogEvent field(String key, Object value) {
            if (value != null) {
                fields.put(key, String.valueOf(value));
            }
            return this;
        }

        /**
         * Add multiple fields at once.
         */
        public LogEvent fields(Map<String, Object> fields) {
            fields.forEach(this::field);
            return this;
        }

        /**
         * Add an exception to the log event.
         */
        public LogEvent exception(Throwable t) {
            this.throwable = t;
            return this;
        }

        /**
         * Emit the log event.
         *
         * Interview Note: MDC (Mapped Diagnostic Context) is a thread-local storage
         * that SLF4J uses to attach contextual information to log events.
         * We populate MDC, emit the log, then clean up to prevent pollution.
         */
        public void log() {
            try {
                // Populate MDC with structured fields
                fields.forEach(MDC::put);
                MDC.put("event", event);

                // Emit log at appropriate level
                switch (level) {
                    case DEBUG -> {
                        if (throwable != null) logger.debug(event, throwable);
                        else logger.debug(event);
                    }
                    case INFO -> {
                        if (throwable != null) logger.info(event, throwable);
                        else logger.info(event);
                    }
                    case WARN -> {
                        if (throwable != null) logger.warn(event, throwable);
                        else logger.warn(event);
                    }
                    case ERROR -> {
                        if (throwable != null) logger.error(event, throwable);
                        else logger.error(event);
                    }
                }
            } finally {
                // Clean up MDC to prevent pollution of other log events
                fields.keySet().forEach(MDC::remove);
                MDC.remove("event");
            }
        }
    }

    private enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * Create a DEBUG level structured log event.
     *
     * @param logger SLF4J logger instance
     * @param event Event name (e.g., "query_started", "embedding_generated")
     * @return LogEvent builder
     */
    public static LogEvent debug(Logger logger, String event) {
        return new LogEvent(logger, LogLevel.DEBUG, event);
    }

    /**
     * Create an INFO level structured log event.
     */
    public static LogEvent info(Logger logger, String event) {
        return new LogEvent(logger, LogLevel.INFO, event);
    }

    /**
     * Create a WARN level structured log event.
     */
    public static LogEvent warn(Logger logger, String event) {
        return new LogEvent(logger, LogLevel.WARN, event);
    }

    /**
     * Create an ERROR level structured log event.
     */
    public static LogEvent error(Logger logger, String event) {
        return new LogEvent(logger, LogLevel.ERROR, event);
    }

    /**
     * Convenience method for performance logging with standardized fields.
     *
     * Interview Note: Standardizing performance logs makes it easy to:
     * 1. Build dashboards (e.g., p50/p95/p99 latencies)
     * 2. Set up alerts (e.g., alert when latency > budget)
     * 3. Track performance regressions over time
     */
    public static class PerformanceLog {
        public static void log(Logger logger, String operation, long latencyMs, Map<String, Object> additionalFields) {
            LogEvent event = info(logger, "performance")
                    .field("operation", operation)
                    .field("latency_ms", latencyMs);

            if (additionalFields != null) {
                event.fields(additionalFields);
            }

            event.log();
        }
    }
}
