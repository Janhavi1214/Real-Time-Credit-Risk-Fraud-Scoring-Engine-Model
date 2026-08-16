package com.riskguard.ingestion.controller;

import com.riskguard.ingestion.kafka.TransactionProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionProducer transactionProducer;

    private static final String ENDPOINT = "/api/v1/transactions";


    // ── helpers ───────────────────────────────────────────────────────

    private static String validTransactionJson() {
        return """
                {
                  "userId": "user-42",
                  "amount": 150.75,
                  "currency": "USD",
                  "merchantId": "merch-001",
                  "merchantCategory": "electronics",
                  "timestamp": "2026-08-12T10:30:00Z",
                  "deviceId": "device-xyz",
                  "location": "New York",
                  "paymentMethod": "CREDIT_CARD"
                }
                """;
    }

    // ── tests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid transaction → 202 Accepted with transactionId")
    void validTransaction_returns202() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTransactionJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("Missing required field (userId) → 400 with field error")
    void missingUserId_returns400() throws Exception {
        String json = """
                {
                  "amount": 100.00,
                  "currency": "USD",
                  "merchantId": "merch-001",
                  "merchantCategory": "electronics",
                  "timestamp": "2026-08-12T10:30:00Z",
                  "paymentMethod": "CREDIT_CARD"
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'userId')]").exists());
    }

    @Test
    @DisplayName("Negative amount → 400 with validation error")
    void negativeAmount_returns400() throws Exception {
        String json = """
                {
                  "userId": "user-42",
                  "amount": -50.00,
                  "currency": "USD",
                  "merchantId": "merch-001",
                  "merchantCategory": "electronics",
                  "timestamp": "2026-08-12T10:30:00Z",
                  "paymentMethod": "CREDIT_CARD"
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'amount')]").exists());
    }

    @Test
    @DisplayName("Malformed JSON → 400 with 'Malformed JSON' error")
    void malformedJson_returns400() throws Exception {
        String broken = "{ this is not valid json }}}";

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(broken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed JSON"));
    }
}
