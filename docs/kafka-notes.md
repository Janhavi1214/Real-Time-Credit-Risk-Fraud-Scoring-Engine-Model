# Kafka Design Notes — RiskGuard

## Topic: `transactions.raw`

| Property          | Value |
|-------------------|-------|
| Partitions        | 3     |
| Replication Factor| 1     |
| Key               | `userId` (String) |
| Value             | Transaction JSON   |

## Partitioning Strategy

Messages are keyed by `userId` so that all transactions for a given user
deterministically land on the same partition.  This guarantees that the
downstream consumer assigned to that partition can maintain an in-memory
sliding window of recent events per user — critical for computing velocity
features (e.g. "number of transactions in the last 5 minutes") without
cross-partition coordination or extra database lookups.

## Why Kafka Instead of a Direct REST Call?

We could have the ingestion service call the decision service directly via
REST, but Kafka gives us three important properties for a fraud-scoring
pipeline:

1. **Durability** — if the decision service crashes or is redeployed,
   transactions are retained on the broker and replayed automatically once
   the consumer comes back up; nothing is lost.
2. **Decoupling** — the ingestion service doesn't need to know the address,
   availability, or scaling strategy of downstream consumers; adding a new
   consumer (e.g. an analytics pipeline) is just another consumer group.
3. **Back-pressure & replay** — during traffic spikes the topic acts as a
   buffer; consumers process at their own pace.  If a scoring bug is found,
   we can reset offsets and reprocess historical transactions without
   re-ingesting them.
