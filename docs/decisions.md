# Decision Engine Logic

The `decision-service` evaluates transactions using a hybrid approach that combines deterministic hard rules and probabilistic machine learning scores.

## Architecture

When a transaction is consumed from the `transactions.raw` Kafka topic:

1. **Velocity Features:** Real-time rolling counters (5m and 24h frequency/amount) are fetched from Redis.
2. **ML Score:** A synchronous REST call is made to the `scoring-service` to retrieve the Random Forest probability score (0.0 to 1.0).
3. **Hard Rules:** The `RulesEngine` evaluates regulatory and basic risk thresholds (e.g. hard amount ceiling, sudden high-velocity bursts).

## Combination Strategy

Hard rules take precedence over ML models to ensure compliance and explainability:

- If a hard rule evaluates to `BLOCK`, the transaction is immediately blocked.
- If a hard rule evaluates to `FLAG`, the transaction is flagged (unless another rule blocked it).
- If NO hard rules trigger (`APPROVE`), the ML score dictates the final outcome:
  - `> 0.85`: **BLOCK** (Added rule `ML_HIGH_RISK`)
  - `> 0.70`: **FLAG** (Added rule `ML_MEDIUM_RISK`)
  - Else: **APPROVE**

All decisions are asynchronously persisted to the PostgreSQL `decisions` table for auditability and can be retrieved via the `GET /api/v1/decisions/{transactionId}` endpoint.
