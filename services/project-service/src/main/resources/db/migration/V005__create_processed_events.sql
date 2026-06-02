-- Processed events table (idempotency for Kafka consumers)
CREATE TABLE processed_events (
    event_id     VARCHAR(255) NOT NULL,
    topic        VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);
