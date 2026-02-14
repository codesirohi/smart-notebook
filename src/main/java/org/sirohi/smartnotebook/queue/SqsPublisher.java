package org.sirohi.smartnotebook.queue;

/**
 * Production implementation of {@link MessagePublisher} using AWS SQS.
 *
 * <p>
 * Activated via profile or configuration. Not used in local development.
 * </p>
 */
public class SqsPublisher implements MessagePublisher {

    @Override
    public void publish(String documentId, String payload) {
        // TODO: implement SQS SendMessage logic
        throw new UnsupportedOperationException("SqsPublisher not yet implemented");
    }
}
