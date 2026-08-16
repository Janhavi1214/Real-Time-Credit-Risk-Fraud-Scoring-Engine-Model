package com.riskguard.decision.service;

import com.riskguard.decision.model.Transaction;
import com.riskguard.decision.model.VelocityFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Manages real-time rolling velocity features per user backed by Redis Sorted Sets (ZSET).
 *
 * <p><b>Why Redis Sorted Sets (ZSET) vs Simple TTL Counter Keys?</b></p>
 * Standard TTL counter keys (e.g. INCR with EXPIRE) only support fixed-window blocks
 * (tumbling windows). Fixed windows suffer from boundary spikes (e.g., a burst of 10
 * transactions at 11:59 and 10 at 12:01 both pass, despite 20 transactions occurring
 * in 2 minutes!).
 *
 * <p>Sorted Sets (ZSET) store each transaction event with its epoch millisecond timestamp
 * as the score. This enables true <b>sliding-window aggregations</b> via {@code ZREMRANGEBYSCORE}
 * and {@code ZRANGEBYSCORE}, evicting stale transactions dynamically while allowing sub-millisecond
 * read/write queries at scoring time.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureService {

    private static final String KEY_PREFIX = "user:velocity:";
    private static final Duration WINDOW_5M = Duration.ofMinutes(5);
    private static final Duration WINDOW_24H = Duration.ofHours(24);
    private static final Duration KEY_TTL = Duration.ofHours(25); // TTL buffer for auto-cleanup

    private final StringRedisTemplate redisTemplate;

    /**
     * Records a transaction in the user's velocity sliding window.
     */
    public void recordTransaction(Transaction transaction) {
        if (transaction == null || transaction.getUserId() == null) {
            return;
        }

        String key = getKey(transaction.getUserId());
        Instant txnTime = transaction.getTimestamp() != null ? transaction.getTimestamp() : Instant.now();
        double score = txnTime.toEpochMilli();

        // Format member as "txnId:amount" or "uuid:amount"
        String member = String.format("%s:%s",
                transaction.getTransactionId() != null ? transaction.getTransactionId() : java.util.UUID.randomUUID().toString(),
                transaction.getAmount() != null ? transaction.getAmount().toString() : "0.00");

        // 1. Add element to ZSET
        redisTemplate.opsForZSet().add(key, member, score);

        // 2. Remove entries older than 24 hours
        double cutoff24h = txnTime.minus(WINDOW_24H).toEpochMilli();
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff24h);

        // 3. Set key TTL so inactive user keys automatically expire
        redisTemplate.expire(key, KEY_TTL);

        log.debug("Recorded txn in velocity cache [user={}]: member={}, score={}",
                transaction.getUserId(), member, score);
    }

    /**
     * Computes velocity features for a given user relative to the current time.
     */
    public VelocityFeatures getVelocityFeatures(String userId) {
        return getVelocityFeatures(userId, Instant.now());
    }

    /**
     * Computes velocity features for a given user relative to a specific reference time.
     */
    public VelocityFeatures getVelocityFeatures(String userId, Instant referenceTime) {
        String key = getKey(userId);
        double nowScore = referenceTime.toEpochMilli();
        double cutoff5m = referenceTime.minus(WINDOW_5M).toEpochMilli();
        double cutoff24h = referenceTime.minus(WINDOW_24H).toEpochMilli();

        // Evict expired entries older than 24h
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff24h);

        // Retrieve entries in 24h window
        Set<ZSetOperations.TypedTuple<String>> tuples24h =
                redisTemplate.opsForZSet().rangeByScoreWithScores(key, cutoff24h, nowScore);

        long count5m = 0;
        long count24h = 0;
        BigDecimal sum5m = BigDecimal.ZERO;
        BigDecimal sum24h = BigDecimal.ZERO;

        if (tuples24h != null) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples24h) {
                String value = tuple.getValue();
                Double score = tuple.getScore();

                if (value == null || score == null) {
                    continue;
                }

                BigDecimal amount = extractAmount(value);
                count24h++;
                sum24h = sum24h.add(amount);

                if (score >= cutoff5m) {
                    count5m++;
                    sum5m = sum5m.add(amount);
                }
            }
        }

        return VelocityFeatures.builder()
                .txnCount5m(count5m)
                .txnCount24h(count24h)
                .amountSum5m(sum5m)
                .amountSum24h(sum24h)
                .build();
    }

    private String getKey(String userId) {
        return KEY_PREFIX + userId;
    }

    private BigDecimal extractAmount(String member) {
        try {
            int lastColonPos = member.lastIndexOf(':');
            if (lastColonPos != -1 && lastColonPos < member.length() - 1) {
                return new BigDecimal(member.substring(lastColonPos + 1));
            }
        } catch (Exception e) {
            log.warn("Failed to parse amount from ZSET member [{}]: {}", member, e.getMessage());
        }
        return BigDecimal.ZERO;
    }
}
