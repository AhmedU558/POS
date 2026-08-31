# Backup & Restore Validation

## Overview

The POS system provides `pg_dump`/`pg_restore` based backup and restore scripts
located in `scripts/`.

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/backup.sh` | Creates a compressed custom-format PostgreSQL dump |
| `scripts/restore.sh` | Restores a database from a backup dump file |

## Validation Procedure

### Backup

```bash
export PGHOST=localhost PGPORT=5432 PGDATABASE=pos PGUSER=pos PGPASSWORD=<password>
chmod +x scripts/backup.sh
./scripts/backup.sh ./backups
```

Expected: `pos_backup_YYYYMMDD_HHMMSS.dump` created in `./backups/`.

Verify contents:
```bash
pg_restore --list ./backups/pos_backup_*.dump | head -20
```

### Restore

```bash
chmod +x scripts/restore.sh
./scripts/restore.sh ./backups/pos_backup_YYYYMMDD_HHMMSS.dump
```

Expected: Database dropped, recreated, and restored. Table count printed.

### Post-Restore Verification

1. Start the application: `mvn spring-boot:run`
2. Verify Flyway detects no pending migrations
3. Login with existing credentials
4. Verify data integrity (stores, products, users exist)

## Backup Strategy Recommendations

| Concern | Recommendation |
|---------|----------------|
| Frequency | Daily full backup, hourly WAL archiving for PITR |
| Retention | 30 days of daily backups, 7 days of hourly WAL |
| Storage | Off-site encrypted storage (S3, Azure Blob, etc.) |
| Testing | Monthly restore-to-staging validation |
| Monitoring | Alert on backup failure or size anomaly |

## Limitations

- Scripts require `pg_dump`, `pg_restore`, and `psql` on `PATH`
- Restore drops and recreates the target database (destructive)
- Application-level file storage (if any) is not covered by these scripts
- Redis cache is ephemeral and not backed up
