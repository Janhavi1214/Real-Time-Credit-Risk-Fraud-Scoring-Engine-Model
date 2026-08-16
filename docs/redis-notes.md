# RiskGuard — Redis Feature Cache Design Notes

## Why Redis for Real-Time Velocity Features?

1. **Sub-Millisecond Read/Write Latency:** Real-time fraud scoring requires sub-50ms total response time. Redis operates entirely in memory, delivering sub-millisecond reads and writes when recording transactions and querying velocity features during scoring.
2. **Transient Feature Lifetime:** Velocity counters (e.g. 5-minute and 24-hour transaction frequency and spend sums) are rolling state metrics that do not need permanent durability; expired window data naturally discards without cluttering primary storage.
3. **Preventing Database Bottlenecks:** Querying and aggregating rolling window metrics directly on a relational database like PostgreSQL for every high-throughput incoming transaction would cause heavy disk I/O, table locking, and unacceptable latency degradation.
4. **Sliding Window Precision with Sorted Sets (ZSET):** Redis Sorted Sets allow storing transaction events indexed by epoch timestamps. Using `ZREMRANGEBYSCORE` and `ZRANGEBYSCORE`, we maintain precise sliding windows rather than fixed block counters, preventing boundary fraud exploit spikes.
