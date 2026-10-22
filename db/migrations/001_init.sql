-- ═══════════════════════════════════════════════════════════════════════════
-- Initial schema for be-boilerplate
--
-- Run by docker-entrypoint-initdb.d on the first start of the local
-- container, and by the pipeline before the integration tests. Idempotent.
--
-- The `events` table is not here: borba-event-store-component creates it,
-- with its append-only triggers, when the service starts.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Users ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Orders ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS orders (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL,
    items      JSONB NOT NULL,
    total      NUMERIC(12, 2) NOT NULL,
    status     VARCHAR(50) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_orders_user ON orders (user_id);
