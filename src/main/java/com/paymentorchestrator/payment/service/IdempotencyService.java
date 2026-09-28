package com.paymentorchestrator.payment.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * The fast-path half of idempotency.
 *
 * This class does NOT make idempotency correct by itself - the Postgres
 * unique constraint on idempotency_key (see PaymentRepository /
 * V1__create_payments_table.sql) is what actually guarantees no duplicate
 * payment can ever be persisted. What this class buys you is:
 *
 *  1. Speed: rejecting an obvious concurrent duplicate in Redis avoids a
 *     wasted trip through validation, entity creation, and a DB round-trip
 *     that would just fail on the unique constraint anyway.
 *  2. A way to distinguish "this key is currently being processed by
 *     someone else, no result yet" (genuine 409) from "this key already
 *     has a result" (idempotent replay) - the DB alone can't tell you that
 *     first case, since there's nothing to find yet.
 *
 * If Redis is unavailable or the lock expires early, correctness still
 * holds: the request proceeds, and either it's the only writer (fine) or
 * it collides with another writer at the DB constraint (caught in
 * PaymentService and handled as a replay). Redis is an optimization, not
 * the source of truth.
 */
@Service
public class IdempotencyService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final String LOCK_KEY_PREFIX = "idem:lock:";

    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @return an owner token to pass to {@link #releaseLock} if the lock was
     *         acquired, or {@code null} if another request already holds it.
     */
    public String tryAcquireLock(String idempotencyKey) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey(idempotencyKey), token, LOCK_TTL);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    /**
     * Releases the lock only if it's still held by the caller that acquired
     * it (checked via the owner token). This prevents a slow request from
     * releasing a lock that has since expired and been re-acquired by a
     * different request.
     */
    public void releaseLock(String idempotencyKey, String token) {
        redisTemplate.execute(
                COMPARE_AND_DELETE_SCRIPT,
                Collections.singletonList(lockKey(idempotencyKey)),
                token
        );
    }

    private String lockKey(String idempotencyKey) {
        return LOCK_KEY_PREFIX + idempotencyKey;
    }
}
