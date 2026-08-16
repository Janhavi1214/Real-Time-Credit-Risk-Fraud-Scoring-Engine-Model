package com.riskguard.ingestion.kafka;

import com.riskguard.ingestion.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes validated transactions to the {@code transactions.raw} Kafka topic.
 *
 * <p><b>Partitioning strategy — key = userId</b></p>
 * All transactions for a given user land on the same Kafka partition because
 * we use {@code userId} as the message key.  This is essential for the
 * downstream velocity-feature computation (e.g. "3+ transactions within
 * 5 minutes") — the consumer assigned to that partition can maintain an
 * in-memory sliding window per user without needing cross-partition
 * coordination or a distributed cache lookup for every event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProducer {

    private static final String TOPIC = "transactions.raw";

    private final KafkaTemplate<String, Transaction> kafkaTemplate;

    /**
     * Sends the transaction asynchronously.  The controller returns 202
     * immediately; success/failure is logged via the completion callback.
     */
    public void send(Transaction transaction) {
        kafkaTemplate.send(TOPIC, transaction.getUserId(), transaction)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish txn [{}]: {}",
                                transaction.getTransactionId(), ex.getMessage(), ex);
                    } else {
                        log.info("Published txn [{}] → {} [partition={}, offset={}]",
                                transaction.getTransactionId(),
                                TOPIC,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
