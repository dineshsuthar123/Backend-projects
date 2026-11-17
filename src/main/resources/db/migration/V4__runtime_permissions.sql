-- The dev stack has a single role; production separates runtime from DDL.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'shortener_app') THEN
        REVOKE ALL ON flyway_schema_history FROM shortener_app;
        REVOKE UPDATE, DELETE ON urls FROM shortener_app;
        REVOKE UPDATE ON analytics_events FROM shortener_app;
        REVOKE ALL ON analytics_events_legacy FROM shortener_app;
    END IF;
END $$;
