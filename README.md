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

> Instructions will be added as each service is implemented.

## License

This project is for educational / portfolio purposes.
