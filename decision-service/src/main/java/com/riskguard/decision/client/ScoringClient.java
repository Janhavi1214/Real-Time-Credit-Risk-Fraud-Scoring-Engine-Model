package com.riskguard.decision.client;

import com.riskguard.decision.config.DecisionProperties;
import com.riskguard.decision.model.Transaction;
import com.riskguard.decision.model.VelocityFeatures;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Client calling Python FastAPI scoring-service /score endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoringClient {

    private final DecisionProperties properties;
    private RestClient restClient;

    private synchronized RestClient getRestClient() {
        if (restClient == null) {
            restClient = RestClient.builder()
                    .baseUrl(properties.getScoringServiceUrl())
                    .build();
        }
        return restClient;
    }

    @Data
    public static class ScoreResponseDto {
        private Double risk_score;
        private String model_version;
    }

    /**
     * Calls scoring-service to compute the ML fraud risk score.
     */
    public Double scoreTransaction(Transaction transaction, VelocityFeatures velocity) {
        try {
            Map<String, Object> payload = new HashMap<>();
            BigDecimal amount = transaction.getAmount() != null ? transaction.getAmount() : BigDecimal.ZERO;
            payload.put("amount", amount.doubleValue());

            // Convert velocity feature to relative time or feature indicators if available
            double timeInSeconds = transaction.getTimestamp() != null
                    ? transaction.getTimestamp().getEpochSecond() % 172800
                    : 0.0;
            payload.put("time", timeInSeconds);

            // Populate V1..V28 (if paymentMethod/merchant indicators map to PCA features or default 0.0)
            for (int i = 1; i <= 28; i++) {
                payload.put("v" + i, 0.0);
            }

            // Simple heuristic feature mapping for demo
            if (velocity != null && velocity.getTxnCount5m() > 2) {
                payload.put("v1", -2.5);
                payload.put("v3", -3.0);
                payload.put("v4", 2.0);
            }

            ScoreResponseDto response = getRestClient().post()
                    .uri("/score")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(ScoreResponseDto.class);

            if (response != null && response.getRisk_score() != null) {
                log.info("ML scoring returned score={} (version={}) for txn [{}]",
                        response.getRisk_score(), response.getModel_version(), transaction.getTransactionId());
                return response.getRisk_score();
            }

        } catch (Exception e) {
            log.error("Scoring service call failed for txn [{}]: {}. Fallback score = 0.0",
                    transaction.getTransactionId(), e.getMessage());
        }

        return 0.0;
    }
}
