package com.riskguard.decision.kafka;

import com.riskguard.decision.model.Transaction;
import com.riskguard.decision.model.VelocityFeatures;
import com.riskguard.decision.service.FeatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        // TODO (Next stage): call scoring-service REST endpoint + rules engine + persist decision
    }
}

