package org.sirohi.smartnotebook.queue;

import org.springframework.stereotype.Service;

/**
 * V1 implementation of {@link MessagePublisher} using Redis Lists.
 *
 * <p>
 * Uses Redis LPUSH/BRPOP for a simple, zero-config dev queue.
 * Production alternative: {@link SqsPublisher}.
 * </p>
 */
@Service
public class RedisListPublisher implements MessagePublisher {

    @Override
    public void publish(String documentId, String payload) {
        // TODO: implement Redis LPUSH logic
        throw new UnsupportedOperationException("RedisListPublisher not yet implemented");
    }
}
