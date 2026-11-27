# Sehaty Backend (Firebase-independent)

This directory is the home for the replacement backend. The current Android app and the existing `cloudflare-push` Worker remain untouched during the migration.

## Target architecture

- **API/runtime:** Cloudflare Workers (TypeScript), deployed independently from the legacy push/booking Worker.
- **Primary database:** PostgreSQL (managed PostgreSQL provider; Neon is the initial candidate).
- **Cache and rate limits:** Redis-compatible managed cache when needed; start without making it a hard dependency.
- **Files:** Cloudflare R2 object storage.
- **Background jobs:** Cloudflare Queues.
- **Authentication:** backend-owned accounts, password hashing, short-lived access tokens, rotating refresh sessions, email verification and role-based authorization.
- **Push delivery:** isolate push delivery behind an adapter. Android background push may still require a push transport such as FCM; that transport must not be used as the identity, database, or application backend.

## Migration rules

1. Do not delete or modify Firebase data during initial development.
2. Do not switch the production Android app to this API until parity tests pass.
3. Migrate one domain at a time: identity/profiles, doctors, appointments/queues, conversations, prescriptions, subscriptions, notifications.
4. Use stable application-owned IDs and an auditable migration mapping for existing Firebase UIDs.
5. Never expose database credentials, signing keys, or service credentials in the Android app or repository.
6. Add schema migrations, automated tests, health checks, rate limits, and observability before production cutover.
7. Keep rollback possible until data reconciliation and production verification are complete.

## First milestone

Create a minimal API with:
- `GET /health` (liveness only; no secrets or database details),
- `GET /ready` (checks required dependencies without exposing credentials),
- versioned `/v1` routes,
- environment-based configuration and local test coverage.

No production data migration or Firebase shutdown is part of this milestone.
