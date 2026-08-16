package com.riskguard.ingestion.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents an incoming financial transaction to be scored for fraud risk.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    /**
     * Server-assigned unique identifier (UUID). Ignored if sent by the client.
     */
    private String transactionId;

    @NotBlank(message = "userId is required")
    private String userId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    private String currency;

    @NotBlank(message = "merchantId is required")
    private String merchantId;

    @NotBlank(message = "merchantCategory is required")
    private String merchantCategory;

    @NotNull(message = "timestamp is required")
    private Instant timestamp;

    private String deviceId;

    private String location;

    @NotBlank(message = "paymentMethod is required")
    private String paymentMethod;
}
