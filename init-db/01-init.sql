-- ComplyAI PostgreSQL Initialization Script
-- Creates the keycloak database and user required by Keycloak container

-- Create keycloak user
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'keycloak') THEN
    CREATE USER keycloak WITH PASSWORD 'keycloak';
  END IF;
END
$$;

-- Create keycloak database
SELECT 'CREATE DATABASE keycloak OWNER keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
