package com.riskguard.decision.service;

import com.riskguard.decision.model.Transaction;
import com.riskguard.decision.model.VelocityFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private FeatureService featureService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        featureService = new FeatureService(redisTemplate);
    }

    @Test
    @DisplayName("recordTransaction → adds entry to ZSET, evicts >24h entries, and sets key TTL")
    void recordTransaction_success() {
        Transaction txn = new Transaction();
        txn.setTransactionId("txn-101");
        txn.setUserId("user-1");
        txn.setAmount(new BigDecimal("100.50"));
        txn.setTimestamp(Instant.parse("2026-08-12T12:00:00Z"));

        featureService.recordTransaction(txn);

        verify(zSetOperations).add(
                eq("user:velocity:user-1"),
                contains("txn-101:100.50"),
                eq((double) Instant.parse("2026-08-12T12:00:00Z").toEpochMilli())
        );

        verify(zSetOperations).removeRangeByScore(
                eq("user:velocity:user-1"),
                eq(0.0),
                anyDouble()
        );

        verify(redisTemplate).expire(eq("user:velocity:user-1"), eq(Duration.ofHours(25)));
    }

    @Test
    @DisplayName("getVelocityFeatures → correctly aggregates 5m and 24h transaction counts and sums")
    void getVelocityFeatures_aggregatesCorrectly() {
        Instant now = Instant.parse("2026-08-12T12:00:00Z");
        String userId = "user-1";

        // Mock 3 transactions in Redis:
        // 1. 2 minutes ago ($50) -> inside 5m & 24h
        // 2. 4 minutes ago ($30) -> inside 5m & 24h
        // 3. 2 hours ago ($200)   -> inside 24h only
        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(new DefaultTypedTuple<>("txn-1:50.00", (double) now.minus(Duration.ofMinutes(2)).toEpochMilli()));
        tuples.add(new DefaultTypedTuple<>("txn-2:30.00", (double) now.minus(Duration.ofMinutes(4)).toEpochMilli()));
        tuples.add(new DefaultTypedTuple<>("txn-3:200.00", (double) now.minus(Duration.ofHours(2)).toEpochMilli()));

        when(zSetOperations.rangeByScoreWithScores(eq("user:velocity:user-1"), anyDouble(), anyDouble()))
                .thenReturn(tuples);

        VelocityFeatures velocity = featureService.getVelocityFeatures(userId, now);

        assertThat(velocity.getTxnCount5m()).isEqualTo(2);
        assertThat(velocity.getTxnCount24h()).isEqualTo(3);
        assertThat(velocity.getAmountSum5m()).isEqualByComparingTo("80.00");
        assertThat(velocity.getAmountSum24h()).isEqualByComparingTo("280.00");
    }

    @Test
    @DisplayName("getVelocityFeatures → old entries (>24h) are evicted and excluded")
    void getVelocityFeatures_expiresOldEntries() {
        Instant now = Instant.parse("2026-08-12T12:00:00Z");
        String userId = "user-2";

        when(zSetOperations.rangeByScoreWithScores(eq("user:velocity:user-2"), anyDouble(), anyDouble()))
                .thenReturn(Set.of());

        VelocityFeatures velocity = featureService.getVelocityFeatures(userId, now);

        // Verify eviction call was executed for cutoff older than 24h
        verify(zSetOperations).removeRangeByScore(
                eq("user:velocity:user-2"),
                eq(0.0),
                eq((double) now.minus(Duration.ofHours(24)).toEpochMilli())
        );

        assertThat(velocity.getTxnCount5m()).isZero();
        assertThat(velocity.getTxnCount24h()).isZero();
        assertThat(velocity.getAmountSum5m()).isEqualByComparingTo("0");
        assertThat(velocity.getAmountSum24h()).isEqualByComparingTo("0");
    }
}
