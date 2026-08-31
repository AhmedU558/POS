#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
# POS Database Backup Script
# ─────────────────────────────────────────────────────────────────────
# Usage:  ./backup.sh [output_dir]
#
# Environment variables (required):
#   PGHOST      – PostgreSQL host (default: localhost)
#   PGPORT      – PostgreSQL port (default: 5432)
#   PGDATABASE  – database name   (default: pos)
#   PGUSER      – database user   (default: pos)
#   PGPASSWORD  – set via .pgpass or this variable
#
# The script produces a compressed custom-format dump suitable for
# pg_restore. File naming: pos_backup_YYYYMMDD_HHMMSS.dump
# ─────────────────────────────────────────────────────────────────────
set -euo pipefail

BACKUP_DIR="${1:-./backups}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
FILENAME="pos_backup_${TIMESTAMP}.dump"

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-pos}"
PGUSER="${PGUSER:-pos}"

mkdir -p "${BACKUP_DIR}"

echo "[$(date -Iseconds)] Starting backup of ${PGDATABASE}@${PGHOST}:${PGPORT} ..."

pg_dump \
  --host="${PGHOST}" \
  --port="${PGPORT}" \
  --username="${PGUSER}" \
  --dbname="${PGDATABASE}" \
  --format=custom \
  --compress=9 \
  --verbose \
  --file="${BACKUP_DIR}/${FILENAME}"

FILESIZE="$(stat -c%s "${BACKUP_DIR}/${FILENAME}" 2>/dev/null || stat -f%z "${BACKUP_DIR}/${FILENAME}")"

echo "[$(date -Iseconds)] Backup complete: ${BACKUP_DIR}/${FILENAME} (${FILESIZE} bytes)"
echo "[$(date -Iseconds)] Verify with:  pg_restore --list ${BACKUP_DIR}/${FILENAME}"
