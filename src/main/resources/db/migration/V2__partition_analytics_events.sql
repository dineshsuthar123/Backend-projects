ALTER TABLE IF EXISTS analytics_events RENAME TO analytics_events_legacy;
ALTER SEQUENCE IF EXISTS analytics_events_id_seq RENAME TO analytics_events_legacy_id_seq;

CREATE SEQUENCE analytics_events_id_seq;

CREATE TABLE analytics_events (
    id BIGINT NOT NULL DEFAULT nextval('analytics_events_id_seq'),
    short_code VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ip_hash VARCHAR(64),
    user_agent_hash VARCHAR(64),
    referer VARCHAR(1024),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

CREATE TABLE analytics_events_2026_06 PARTITION OF analytics_events
    FOR VALUES FROM ('2026-06-01 00:00:00+00') TO ('2026-07-01 00:00:00+00');

CREATE TABLE analytics_events_2026_07 PARTITION OF analytics_events
    FOR VALUES FROM ('2026-07-01 00:00:00+00') TO ('2026-08-01 00:00:00+00');

CREATE TABLE analytics_events_2026_08 PARTITION OF analytics_events
    FOR VALUES FROM ('2026-08-01 00:00:00+00') TO ('2026-09-01 00:00:00+00');

CREATE TABLE analytics_events_default PARTITION OF analytics_events DEFAULT;

INSERT INTO analytics_events (id, short_code, occurred_at, ip_hash, user_agent_hash, referer)
SELECT id, short_code, occurred_at, ip_hash, user_agent_hash, referer
FROM analytics_events_legacy;

SELECT setval(
    'analytics_events_id_seq',
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM analytics_events), 1),
    TRUE
);

CREATE INDEX idx_analytics_events_partitioned_short_code ON analytics_events (short_code);
CREATE INDEX idx_analytics_events_partitioned_occurred_at ON analytics_events (occurred_at);
CREATE INDEX idx_analytics_events_partitioned_short_code_occurred_at ON analytics_events (short_code, occurred_at DESC);
