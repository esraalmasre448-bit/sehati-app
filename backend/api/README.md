# Sehaty Backend API

This is a separate Cloudflare Worker API. It does not import Firebase SDKs and does not modify the Android app or legacy `cloudflare-push` Worker.

## Current endpoints

- `GET /health`: liveness only.
- `GET /ready`: performs a PostgreSQL `SELECT 1` probe when `DATABASE_URL` is configured; otherwise returns `503 not_ready`. Connection failures are reported without exposing secrets.
- `GET /v1`: versioned API metadata.

All responses include a request ID. Browser CORS responses are enabled only for exact origins listed in the `ALLOWED_ORIGINS` Worker variable. No credentials belong in source control.

## PostgreSQL

- Driver: Neon serverless PostgreSQL client.
- Initial migration: `migrations/0001_identity_and_profiles.sql`.
- Initial tables: users, patient profiles, doctor profiles, clinic profiles.
- Password storage is represented only by `password_hash`; authentication endpoints are not implemented yet.
- The migration is a reviewed SQL file and has not been applied to a live database.

Set `DATABASE_URL` as a secret in the Worker environment. Never commit it or put it in Android.

## Local checks

From this directory:

```sh
npm install
npm run typecheck
npm test
```

## Deployment status

The Worker configuration is prepared, but this API is not deployed yet. A live PostgreSQL database has not been provisioned or connected, and the migration has not been applied.

## Next milestone

Provision a managed PostgreSQL database, store its connection string as a Worker secret, apply the migration through a controlled migration process, and verify `/ready` against the real database. Then add registration, email verification, login, refresh-session rotation, and role authorization. Keep Firebase and the production Android app untouched until migration parity and rollback tests pass.
