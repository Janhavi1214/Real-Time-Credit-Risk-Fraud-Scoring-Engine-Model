package com.riskguard.decision.service;

import com.riskguard.decision.config.DecisionProperties;
import com.riskguard.decision.model.FinalDecision;
import com.riskguard.decision.model.Transaction;
import com.riskguard.decision.model.VelocityFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Hard rules engine providing explainable, deterministic risk evaluation.
 *
 * <p><b>Rule Precedence & Regulatory Compliance:</b></p>
 * Hard rules provide deterministic guardrails for scenarios requiring absolute auditability
 * (e.g. anti-money laundering thresholds, high-velocity bursts, or suspicious new devices).
 * A rule-triggered {@code BLOCK} or {@code FLAG} cannot be overridden by an ML model probability.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RulesEngine {

    private final DecisionProperties properties;

    public record RuleResult(
            FinalDecision suggestedDecision,
            List<String> triggeredRules
    ) {}

    /**
     * Evaluates hard business rules against the transaction and its velocity features.
     */
    public RuleResult evaluate(Transaction transaction, VelocityFeatures velocity) {
        List<String> triggeredRules = new ArrayList<>();
        FinalDecision maxSeverityDecision = FinalDecision.APPROVE;

        BigDecimal amount = transaction.getAmount() != null ? transaction.getAmount() : BigDecimal.ZERO;

        // Rule 1: Hard Ceiling Amount Exceeded -> Auto-BLOCK
        if (amount.compareTo(BigDecimal.valueOf(properties.getHardCeilingAmount())) > 0) {
            triggeredRules.add("RULE_HARD_CEILING_EXCEEDED");
            maxSeverityDecision = FinalDecision.BLOCK;
            log.warn("Rule triggered [RULE_HARD_CEILING_EXCEEDED]: amount {} > {}",
                    amount, properties.getHardCeilingAmount());
        }

        // Rule 2: High Velocity (5m Count > N AND Amount > Threshold) -> Auto-FLAG
        if (velocity != null && velocity.getTxnCount5m() > properties.getVelocityCountThreshold()
                && amount.compareTo(BigDecimal.valueOf(properties.getVelocityAmountThreshold())) > 0) {
            triggeredRules.add("RULE_HIGH_VELOCITY_BURST");
            if (maxSeverityDecision != FinalDecision.BLOCK) {
                maxSeverityDecision = FinalDecision.FLAG;
            }
            log.warn("Rule triggered [RULE_HIGH_VELOCITY_BURST]: 5m_count={} > {}, amount={}",
                    velocity.getTxnCount5m(), properties.getVelocityCountThreshold(), amount);
        }

        // Rule 3: Suspicious New Device / Location + High Amount -> Auto-FLAG
        if (isNewOrUnrecognizedDeviceOrLocation(transaction)
                && amount.compareTo(BigDecimal.valueOf(properties.getNewDeviceAmountThreshold())) > 0) {
            triggeredRules.add("RULE_NEW_DEVICE_OR_LOCATION");
            if (maxSeverityDecision != FinalDecision.BLOCK) {
                maxSeverityDecision = FinalDecision.FLAG;
            }
            log.warn("Rule triggered [RULE_NEW_DEVICE_OR_LOCATION]: device={}, location={}, amount={}",
                    transaction.getDeviceId(), transaction.getLocation(), amount);
        }

        return new RuleResult(maxSeverityDecision, triggeredRules);
    }

    private boolean isNewOrUnrecognizedDeviceOrLocation(Transaction transaction) {
        boolean newDevice = transaction.getDeviceId() != null
                && transaction.getDeviceId().toUpperCase().contains("NEW");
        boolean unknownLocation = transaction.getLocation() != null
                && (transaction.getLocation().toUpperCase().contains("UNKNOWN")
                || transaction.getLocation().toUpperCase().contains("NEW"));
        return newDevice || unknownLocation;
    }
}
