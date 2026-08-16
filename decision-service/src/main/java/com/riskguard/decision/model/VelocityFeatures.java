package com.riskguard.decision.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Data Transfer Object containing real-time velocity features for a user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityFeatures {

    /** Number of transactions by the user in the last 5 minutes. */
    private long txnCount5m;

    /** Number of transactions by the user in the last 24 hours. */
    private long txnCount24h;

    /** Total sum of transaction amounts by the user in the last 5 minutes. */
    private BigDecimal amountSum5m;

    /** Total sum of transaction amounts by the user in the last 24 hours. */
    private BigDecimal amountSum24h;
}
