# RiskGuard — Real-Time Credit Risk & Fraud Scoring Engine

A microservices-based system that ingests financial transactions in real time,
scores them for fraud risk using a trained ML model, and renders
approve / flag / block decisions — all orchestrated through Kafka with
sub-second latency.

## Architecture

```
┌────────────┐      ┌───────┐      ┌─────────────────┐      ┌──────────────────┐
│  Client /  │─REST─▶ Inges-│─Kafka─▶  ML Scoring    │─REST─▶  Decision       │
│  API       │      │ tion  │      │  Service (Py)   │      │  Service (Java)  │
└────────────┘      └───────┘      └────────┬────────┘      └───────┬──────────┘
                                            │                       │
                                       ┌────▼────┐            ┌────▼────┐
                                       │  Redis  │            │ Postgres│
                                       └─────────┘            └─────────┘
```

> **Note:** Full architecture diagram will be added in `docs/` as the project
> evolves.

## Project Structure

| Directory            | Description                                    |
|----------------------|------------------------------------------------|
| `ingestion-service/` | Spring Boot — REST ingestion + Kafka producer   |
| `scoring-service/`   | Python FastAPI — ML fraud-risk scoring          |
| `decision-service/`  | Spring Boot — rules engine + persistence        |
| `infra/`             | Docker Compose & infrastructure config          |
| `docs/`              | Documentation & design notes                    |

## Tech Stack

Java 21 · Spring Boot 3.3 · Apache Kafka · Redis · PostgreSQL · Python 3.11 ·
FastAPI · scikit-learn · Docker Compose · Maven

## Getting Started

> **Prerequisites:** Ensure you have Docker and Docker Compose installed.

To run the entire suite (Kafka, Redis, Postgres, ML Scoring, Ingestion, Decision) locally:

```bash
cd infra
docker compose --profile app up --build
```

### Testing the System

Send a transaction to the ingestion service:

```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "amount": 2500.0,
    "currency": "USD",
    "merchantId": "M999",
    "merchantCategory": "ELECTRONICS",
    "paymentMethod": "CREDIT_CARD"
  }'
```

Check the evaluated decision:
```bash
# Replace with the transactionId returned by the POST request above
curl http://localhost:8082/api/v1/decisions/<transactionId>
```

## License

This project is for educational / portfolio purposes.
