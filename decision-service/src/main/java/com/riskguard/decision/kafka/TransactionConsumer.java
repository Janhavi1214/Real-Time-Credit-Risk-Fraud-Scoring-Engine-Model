package com.riskguard.decision.kafka;

import com.riskguard.decision.model.Transaction;
import com.riskguard.decision.model.VelocityFeatures;
import com.riskguard.decision.service.FeatureService;
import com.riskguard.decision.client.ScoringClient;
import com.riskguard.decision.entity.Decision;
import com.riskguard.decision.model.FinalDecision;
import com.riskguard.decision.repository.DecisionRepository;
import com.riskguard.decision.service.RulesEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer that reads from {@code transactions.raw} and updates
 * real-time Redis velocity features.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionConsumer {

    private final FeatureService featureService;
    private final ScoringClient scoringClient;
    private final RulesEngine rulesEngine;
    private final DecisionRepository decisionRepository;

    @KafkaListener(
            topics = "transactions.raw",
            groupId = "decision-service"
    )
    public void consume(Transaction transaction) {
        log.info("Consumed txn [{}]: userId={}, amount={} {}, merchant={}/{}, method={}",
                transaction.getTransactionId(),
                transaction.getUserId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getMerchantId(),
                transaction.getMerchantCategory(),
                transaction.getPaymentMethod());

        // Update rolling Redis velocity features
        featureService.recordTransaction(transaction);

        VelocityFeatures velocity = featureService.getVelocityFeatures(transaction.getUserId());
        log.info("Velocity features for user [{}]: 5m_count={}, 24h_count={}, 5m_sum={}, 24h_sum={}",
                transaction.getUserId(),
                velocity.getTxnCount5m(),
                velocity.getTxnCount24h(),
                velocity.getAmountSum5m(),
                velocity.getAmountSum24h());

        // 1. Call ML Scoring Service
        Double riskScore = scoringClient.scoreTransaction(transaction, velocity);

        // 2. Evaluate Hard Rules
        RulesEngine.RuleResult ruleResult = rulesEngine.evaluate(transaction, velocity);

        // 3. Combine Results
        FinalDecision finalDecision = ruleResult.suggestedDecision();
        
        if (finalDecision == FinalDecision.APPROVE) {
            // Apply ML threshold if no hard rules blocked/flagged
            if (riskScore > 0.85) {
                finalDecision = FinalDecision.BLOCK;
                ruleResult.triggeredRules().add("ML_HIGH_RISK");
            } else if (riskScore > 0.70) {
                finalDecision = FinalDecision.FLAG;
                ruleResult.triggeredRules().add("ML_MEDIUM_RISK");
            }
        }

        // 4. Persist Decision
        Decision decision = Decision.builder()
                .transactionId(transaction.getTransactionId())
                .userId(transaction.getUserId())
                .mlRiskScore(riskScore)
                .rulesTriggered(String.join(",", ruleResult.triggeredRules()))
                .finalDecision(finalDecision)
                .decidedAt(Instant.now())
                .build();

        decisionRepository.save(decision);

        log.info("Persisted Decision for txn [{}]: {} (Score: {}, Rules: {})",
                transaction.getTransactionId(),
                finalDecision,
                riskScore,
                ruleResult.triggeredRules());
    }
}

