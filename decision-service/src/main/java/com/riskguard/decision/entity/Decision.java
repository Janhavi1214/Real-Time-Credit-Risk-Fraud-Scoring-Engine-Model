package com.riskguard.decision.entity;

import com.riskguard.decision.model.FinalDecision;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA Entity representing a persisted fraud decision in PostgreSQL.
 */
@Entity
@Table(name = "decisions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Decision {

    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false, length = 64)
    private String transactionId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "ml_risk_score", nullable = false)
    private Double mlRiskScore;

    @Column(name = "rules_triggered", length = 1000)
    private String rulesTriggered;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_decision", nullable = false, length = 20)
    private FinalDecision finalDecision;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}
