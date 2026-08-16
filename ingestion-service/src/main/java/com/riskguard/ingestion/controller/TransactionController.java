package com.riskguard.ingestion.controller;

import com.riskguard.ingestion.kafka.TransactionProducer;
import com.riskguard.ingestion.model.Transaction;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for ingesting financial transactions.
 * Validates incoming transactions, assigns a transactionId, publishes to Kafka,
 * and returns 202 Accepted.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionProducer transactionProducer;

    @PostMapping
    public ResponseEntity<Map<String, String>> ingestTransaction(
            @Valid @RequestBody Transaction transaction) {

        // Assign a server-side transaction ID
        String txnId = UUID.randomUUID().toString();
        transaction.setTransactionId(txnId);

        log.info("Received transaction [{}]: userId={}, amount={} {}, merchant={}/{}",
                txnId,
                transaction.getUserId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getMerchantId(),
                transaction.getMerchantCategory());

        // Publish asynchronously to Kafka
        transactionProducer.send(transaction);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(Map.of("transactionId", txnId, "status", "ACCEPTED"));
    }
}

