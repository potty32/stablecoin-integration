-- PostgreSQL Initialisierungs-Script (docker-entrypoint-initdb.d)
-- Erstellt den App-User, der von Flyway V8 (GRANT ... TO stablecoin_app) vorausgesetzt wird.
-- WICHTIG: Muss vor dem Flyway-Start ausgeführt werden!

CREATE USER stablecoin_app WITH PASSWORD 'stablecoin_app_pass';
GRANT CONNECT ON DATABASE stablecoin_dev TO stablecoin_app;
