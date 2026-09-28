# payment-service — Week 1

Week 1 of the Distributed Payment Orchestration Platform: core domain,
enum-based state machine, Postgres schema, a basic (synchronous) REST API,
and idempotency via Redis lock + Postgres unique-constraint fallback.

No Kafka, no PSP calls yet - those land in weeks 2-3. Right now, creating a
payment immediately moves it `CREATED -> PENDING`, and a manual
`/transitions` endpoint stands in for what will later be PSP-callback-driven
transitions, so you can exercise and demo the state machine today.

## Run it

```bash
# 1. Start Postgres + Redis
docker compose up -d

# 2. Run the service (needs JDK 17+, Maven)
mvn spring-boot:run
```

Service comes up on `http://localhost:8081`. Flyway runs the schema
migration automatically on startup.

## Try idempotency

```bash
# First request: creates a payment (201 Created)
curl -i -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-1" \
  -d '{"merchantId":"merchant-1","amount":499.00,"currency":"INR"}'

# Same key again: returns the SAME payment (200 OK, "replayed": true), no duplicate created
curl -i -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-1" \
  -d '{"merchantId":"merchant-1","amount":499.00,"currency":"INR"}'
```

Fire both requests near-simultaneously from two terminals to see the
Redis-lock / DB-constraint race handling kick in - one wins and persists,
the other detects the race and returns the same payment instead of
erroring or duplicating.

## Try the state machine

```bash
PAYMENT_ID=<id from a create response above>

# Legal transition: PENDING -> AUTHORIZED
curl -i -X POST http://localhost:8081/api/v1/payments/$PAYMENT_ID/transitions \
  -H "Content-Type: application/json" \
  -d '{"targetStatus":"AUTHORIZED"}'

# Illegal transition: AUTHORIZED -> PENDING directly -> 409 Conflict
curl -i -X POST http://localhost:8081/api/v1/payments/$PAYMENT_ID/transitions \
  -H "Content-Type: application/json" \
  -d '{"targetStatus":"PENDING"}'
```

## Run the tests

```bash
mvn test
```

Covers:
- `PaymentStateMachineTest` - every legal and illegal transition in the graph
- `PaymentTest` - the entity refuses illegal transitions and leaves status unchanged
- `PaymentServiceTest` - the four idempotency scenarios (new key, replay via
  DB lookup, replay via DB-constraint race, genuine in-flight conflict)

## What's deliberately not here yet

- No Kafka / outbox / async processing (week 2)
- No real PSP call - the API is the only thing driving state right now (week 3)
- No ledger, webhooks, or reconciliation (weeks 4-6)
- `POST /{id}/transitions` is a temporary test/demo endpoint and will be
  replaced or locked down once PSP-callback-driven transitions exist

## Project layout

```
domain/       Payment entity, PaymentStatus, PaymentStateMachine (the rules)
dto/          Request/response records
repository/   Spring Data JPA repository
service/      PaymentService (orchestration), IdempotencyService (Redis lock)
controller/   REST endpoints
exception/    Domain exceptions + global HTTP mapping
config/       Redis bean config
```
