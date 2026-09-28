-- gen_random_uuid() comes from pgcrypto (built into Postgres 13+, just needs enabling)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL,
    merchant_id     VARCHAR(255) NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency        CHAR(3) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_payments_merchant_id ON payments (merchant_id);
CREATE INDEX idx_payments_status ON payments (status);
