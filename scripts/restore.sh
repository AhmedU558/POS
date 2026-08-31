#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
# POS Database Restore Script
# ─────────────────────────────────────────────────────────────────────
# Usage:  ./restore.sh <backup_file>
#
# Environment variables (required):
#   PGHOST      – PostgreSQL host (default: localhost)
#   PGPORT      – PostgreSQL port (default: 5432)
#   PGDATABASE  – target database (default: pos)
#   PGUSER      – database user   (default: pos)
#   PGPASSWORD  – set via .pgpass or this variable
#
# WARNING: This drops and recreates the target database.
#          Ensure you have a valid backup before running.
# ─────────────────────────────────────────────────────────────────────
set -euo pipefail

BACKUP_FILE="${1:?Usage: $0 <backup_file>}"

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "ERROR: Backup file not found: ${BACKUP_FILE}" >&2
  exit 1
fi

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-pos}"
PGUSER="${PGUSER:-pos}"

echo "[$(date -Iseconds)] WARNING: This will DROP and recreate database '${PGDATABASE}'."
echo "[$(date -Iseconds)] Restoring from: ${BACKUP_FILE}"
read -p "Continue? [y/N] " -n 1 -r
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo "Aborted."
  exit 0
fi

echo "[$(date -Iseconds)] Dropping existing connections and database ..."

psql \
  --host="${PGHOST}" \
  --port="${PGPORT}" \
  --username="${PGUSER}" \
  --dbname="postgres" \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${PGDATABASE}' AND pid <> pg_backend_pid();" \
  -c "DROP DATABASE IF EXISTS ${PGDATABASE};" \
  -c "CREATE DATABASE ${PGDATABASE} OWNER ${PGUSER};"

echo "[$(date -Iseconds)] Restoring database ..."

pg_restore \
  --host="${PGHOST}" \
  --port="${PGPORT}" \
  --username="${PGUSER}" \
  --dbname="${PGDATABASE}" \
  --verbose \
  --clean \
  --if-exists \
  --no-owner \
  --no-acl \
  "${BACKUP_FILE}"

echo "[$(date -Iseconds)] Restore complete. Verifying table count ..."

TABLE_COUNT=$(psql \
  --host="${PGHOST}" \
  --port="${PGPORT}" \
  --username="${PGUSER}" \
  --dbname="${PGDATABASE}" \
  -t -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE';")

echo "[$(date -Iseconds)] Restored ${TABLE_COUNT} tables in '${PGDATABASE}'."
echo "[$(date -Iseconds)] Done. Verify the application starts correctly."
