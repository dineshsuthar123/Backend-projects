CREATE TABLE urls (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(16) NOT NULL UNIQUE,
    original_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_urls_short_code_active ON urls (short_code, active);
CREATE INDEX idx_urls_expires_at ON urls (expires_at) WHERE expires_at IS NOT NULL;

CREATE TABLE analytics_events (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ip_hash VARCHAR(64),
    user_agent_hash VARCHAR(64),
    referer VARCHAR(1024)
);

CREATE INDEX idx_analytics_events_short_code ON analytics_events (short_code);
CREATE INDEX idx_analytics_events_occurred_at ON analytics_events (occurred_at);
CREATE INDEX idx_analytics_events_short_code_occurred_at ON analytics_events (short_code, occurred_at DESC);
