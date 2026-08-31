# Production Release & Rollback Plan

## Release Candidate

- **Version**: 1.0.0-RC1
- **Date**: 2026-08-31
- **Branch**: `main`

## Pre-deployment Checklist

| # | Item | Status |
|---|------|--------|
| 1 | All 392 backend tests pass | ✅ |
| 2 | Frontend build succeeds (0 errors) | ✅ |
| 3 | Flyway migrations (V1–V53) apply cleanly | ✅ |
| 4 | Hibernate schema validation passes | ✅ |
| 5 | UAT checklist completed | ✅ |
| 6 | Backup/restore scripts validated | ✅ |
| 7 | No hardcoded secrets in codebase | ✅ |
| 8 | OWASP dependency check configured | ✅ |
| 9 | Rate limiting active | ✅ |
| 10 | CORS configured via environment variable | ✅ |

## Deployment Steps

### 1. Take Production Backup
```bash
./scripts/backup.sh /secure/backups/pre-release
```

### 2. Deploy Backend
```bash
# Build production JAR
cd backend
mvn clean package -DskipTests -Pprod

# Deploy (example — adapt to your infrastructure)
java -jar target/pos-backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

### 3. Run Migrations
Flyway runs automatically on startup (`spring.flyway.enabled=true`).
Migrations are idempotent and versioned (V1–V53).

### 4. Deploy Frontend
```bash
cd frontend
npm run build
# Deploy .next/standalone or static export to your hosting
```

### 5. Smoke Tests
After deployment, verify these endpoints:

| Test | Endpoint | Expected |
|------|----------|----------|
| Health | `GET /api/v1/health` | 200 OK |
| Actuator | `GET /actuator/health` | 200 UP |
| Login | `POST /api/v1/auth/login` | 200 + JWT |
| Products | `GET /api/v1/products` (with JWT) | 200 + data |
| Stores | `GET /api/v1/stores` (with JWT) | 200 + data |

## Rollback Plan

### Database Rollback
```bash
# Restore from pre-release backup
./scripts/restore.sh /secure/backups/pre-release/pos_backup_YYYYMMDD_HHMMSS.dump
```

### Application Rollback
1. Stop current application instance
2. Redeploy previous JAR version
3. Restart application
4. Verify health endpoint

### Rollback Decision Criteria
Trigger rollback if any of these occur within the first 24 hours:
- Authentication failures for valid credentials
- Data corruption or missing records
- HTTP 500 error rate > 1%
- Payment/sale processing failures
- Database connection pool exhaustion

## Environment Variables Required

| Variable | Description | Example |
|----------|-------------|---------|
| `JWT_SECRET` | JWT signing key (min 256-bit) | *generate securely* |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://host:5432/pos` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `pos` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | *vault-managed* |
| `SPRING_DATA_REDIS_HOST` | Redis host | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Redis port | `6379` |
| `APP_SECURITY_CORS_ALLOWED_ORIGINS` | CORS origins | `https://pos.example.com` |
| `APP_SECURITY_RATE_LIMIT_MAX_TOKENS` | Rate limit per IP/min | `100` |

## Monitoring Recommendations

| Concern | Tool/Approach |
|---------|---------------|
| Application health | `/actuator/health` + uptime monitor |
| Error tracking | Structured logging + log aggregator |
| Performance | Hibernate statistics (enabled), response time monitoring |
| Database | Connection pool metrics, slow query log |
| Security | Failed auth attempt monitoring, rate limit hit rate |
