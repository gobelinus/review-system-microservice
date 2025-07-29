-- Create additional schemas if needed
CREATE SCHEMA IF NOT EXISTS reviews;
CREATE SCHEMA IF NOT EXISTS audit;

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Set timezone
SET timezone = 'UTC';

-- Create initial tables (will be managed by Flyway migrations)
-- This is just for initial setup, actual schema will be in migrations

GRANT ALL PRIVILEGES ON DATABASE reviewsystem TO reviewuser;
GRANT ALL PRIVILEGES ON SCHEMA reviews TO reviewuser;
GRANT ALL PRIVILEGES ON SCHEMA audit TO reviewuser;
