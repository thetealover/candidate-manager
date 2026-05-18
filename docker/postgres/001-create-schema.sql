-- Create the application schema on first startup.
-- This script runs once when the Docker volume is fresh (i.e. after docker compose down).
-- Liquibase then applies all migrations into this schema on application startup.
create schema if not exists "canmanager-ws";
