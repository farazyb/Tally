#!/bin/bash
# =====================================================================
# Tally - PostgreSQL init script (LOCAL DEVELOPMENT ONLY)
#
# Runs only once: when the container starts with an EMPTY data volume.
# After changing this file, remove the volume to apply it again.
#
# Each service gets:
#   * its own user (role with LOGIN), password from .env (*_DB_PASSWORD)
#   * its own database, OWNED by that user
#     (the owner can create tables in schema public, so Flyway works
#      on PostgreSQL 15+ without extra grants)
#   * no access for anyone else (CONNECT revoked from PUBLIC)
#   * REPLICATION, so Debezium can read its WAL
#
# Tables are NOT created here. Flyway in each service does that.
# =====================================================================
set -euo pipefail

# Passwords are passed as psql variables and inserted with :'name', which quotes them safely.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v user_pw="$USER_DB_PASSWORD" \
    -v orchestrator_pw="$ORCHESTRATOR_DB_PASSWORD" \
    -v wallet_pw="$WALLET_DB_PASSWORD" \
    -v risk_pw="$RISK_DB_PASSWORD" \
    -v notification_pw="$NOTIFICATION_DB_PASSWORD" <<'EOSQL'

-- User Service
CREATE USER user_svc WITH PASSWORD :'user_pw';
CREATE DATABASE user_db OWNER user_svc;
REVOKE ALL ON DATABASE user_db FROM PUBLIC;
ALTER ROLE user_svc WITH REPLICATION;

-- Transaction Orchestrator (saga state)
CREATE USER orchestrator_svc WITH PASSWORD :'orchestrator_pw';
CREATE DATABASE orchestrator_db OWNER orchestrator_svc;
REVOKE ALL ON DATABASE orchestrator_db FROM PUBLIC;
ALTER ROLE orchestrator_svc WITH REPLICATION;

-- Wallet Service (event store)
CREATE USER wallet_svc WITH PASSWORD :'wallet_pw';
CREATE DATABASE wallet_db OWNER wallet_svc;
REVOKE ALL ON DATABASE wallet_db FROM PUBLIC;
ALTER ROLE wallet_svc WITH REPLICATION;

-- Risk / Limit Service
CREATE USER risk_svc WITH PASSWORD :'risk_pw';
CREATE DATABASE risk_db OWNER risk_svc;
REVOKE ALL ON DATABASE risk_db FROM PUBLIC;
ALTER ROLE risk_svc WITH REPLICATION;

-- Notification Service
CREATE USER notification_svc WITH PASSWORD :'notification_pw';
CREATE DATABASE notification_db OWNER notification_svc;
REVOKE ALL ON DATABASE notification_db FROM PUBLIC;
ALTER ROLE notification_svc WITH REPLICATION;
EOSQL
