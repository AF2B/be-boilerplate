-- ═══════════════════════════════════════════════════════════════════════════
-- Where each relay of the event store is: the position of the last event it
-- published to Kafka. The row of a relay is also its lock, so of the instances
-- of a service only one publishes at a time. Idempotent.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS event_relay_positions (
    name       VARCHAR(255) PRIMARY KEY,
    position   BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
