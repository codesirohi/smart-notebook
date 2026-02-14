package org.sirohi.smartnotebook.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * V1 implementation of {@link MessagePublisher} using Redis Lists.
 *
 * <p>
 * Uses Redis LPUSH/BRPOP for a simple, zero-config dev queue.
 * The Python worker uses BRPOP on the same key to consume messages.
 * Production alternative: {@link SqsPublisher}.
 * </p>
 */
@Service
public class RedisListPublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisListPublisher.class);
    private static final String QUEUE_KEY = "smart-notebook:ingestion-queue";

    private final StringRedisTemplate redisTemplate;

    public RedisListPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void publish(String documentId, String payload) {
        log.info("Publishing ingestion request for document {} to Redis queue", documentId);
        redisTemplate.opsForList().leftPush(QUEUE_KEY, payload);
        log.debug("Message enqueued successfully for document {}", documentId);
    }
}
