-- Expand aliases without rewriting historical migrations or removing existing rows.
ALTER TABLE urls ALTER COLUMN short_code TYPE VARCHAR(32);
ALTER TABLE analytics_events ALTER COLUMN short_code TYPE VARCHAR(32);
ALTER TABLE analytics_events_legacy ALTER COLUMN short_code TYPE VARCHAR(32);

CREATE TABLE demo_creation_budgets (
    budget_date DATE NOT NULL,
    identity VARCHAR(64) NOT NULL,
    used INTEGER NOT NULL CHECK (used > 0),
    PRIMARY KEY (budget_date, identity)
);
