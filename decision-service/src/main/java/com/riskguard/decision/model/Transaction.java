package com.riskguard.decision.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lightweight DTO mirroring the ingestion-service Transaction model.
 * Used for Kafka JSON deserialization on the consumer side.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String merchantId;
    private String merchantCategory;
    private Instant timestamp;
    private String deviceId;
    private String location;
    private String paymentMethod;
}
