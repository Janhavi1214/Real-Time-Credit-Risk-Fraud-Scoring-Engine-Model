package com.riskguard.decision.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configurable thresholds for ML decision boundaries and rules engine parameters.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "riskguard.decision")
public class DecisionProperties {

    /** ML Risk Score lower bound for FLAG decision (e.g., 0.3). */
    private double mlThresholdFlag = 0.3;

    /** ML Risk Score upper bound for BLOCK decision (e.g., 0.7). */
    private double mlThresholdBlock = 0.7;

    /** Absolute monetary transaction ceiling for hard auto-BLOCK rule ($). */
    private double hardCeilingAmount = 10000.0;

    /** Transaction amount threshold for high velocity rule ($). */
    private double velocityAmountThreshold = 500.0;

    /** 5-minute transaction count threshold for high velocity rule. */
    private long velocityCountThreshold = 3;

    /** Transaction amount threshold for new device / unknown location rule ($). */
    private double newDeviceAmountThreshold = 1000.0;

    /** Base URL of the Python FastAPI scoring service. */
    private String scoringServiceUrl = "http://localhost:8000";
}
