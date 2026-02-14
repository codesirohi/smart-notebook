package org.sirohi.smartnotebook.queue;

/**
 * Abstraction for publishing ingestion requests to a message queue.
 *
 * <p>
 * V1: {@link RedisListPublisher} (dev). Future: SQS, Kafka.
 * </p>
 */
public interface MessagePublisher {

    /**
     * Publishes a document ingestion request to the queue.
     *
     * @param documentId the ID of the document to be processed
     * @param payload    the serialized ingestion request (JSON)
     */
    void publish(String documentId, String payload);
}
