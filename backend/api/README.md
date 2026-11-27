# Sehaty Backend API

Separate Cloudflare Worker API; it does not import Firebase SDKs and does not modify the Android app or legacy `cloudflare-push` Worker.

## Endpoints

- `GET /health`: liveness.
- `GET /ready`: PostgreSQL readiness probe; returns 503 until `DATABASE_URL` works.
- `GET /v1`: API metadata.
- `POST /v1/auth/register`: creates a pending account and emails a six-digit verification code.
- `POST /v1/auth/email/verify`: verifies the six-digit code and activates the account.
- `POST /v1/auth/email/resend`: sends a replacement code, with a 60-second cooldown.

Registration JSON:
- Patient: `{ "email", "password", "displayName", "role": "PATIENT" }`
- Doctor: same plus `"role": "DOCTOR", "specialtyName", "city"`
- Clinic: same plus `"role": "CLINIC", "city"`

Verification JSON: `{ "email": "user@example.com", "code": "123456" }`.

## Security behavior

- Passwords are PBKDF2-SHA-256 hashes (310,000 iterations); plaintext passwords are never stored.
- Verification codes are six digits, expire after 10 minutes, are stored only as peppered SHA-256 hashes, and allow at most five attempts.
- Verification consumption and account activation happen in one atomic PostgreSQL statement.
- Resend cooldown is claimed with a conditional database update, so concurrent requests cannot both replace the code.
- Authentication endpoints enforce fixed-window request limits by Cloudflare client IP and normalized email; bucket keys are peppered hashes, not raw identifiers.
- Resend responses are intentionally identical for unknown accounts and active cooldowns to reduce email-address enumeration.
- Request bodies are capped at 16 KiB and email-provider calls time out after eight seconds.
- A new code can be requested once per 60 seconds.
- Accounts remain `PENDING_VERIFICATION` until the database records `email_verified_at`.
- Responses include request IDs and use `cache-control: no-store`.
- Browser CORS is restricted to exact origins in `ALLOWED_ORIGINS`.
- The API never returns the verification code in its response.

## Required Worker secrets

Set these in the Cloudflare Worker secret/variable settings; never commit them:

- `DATABASE_URL`: Neon pooled PostgreSQL connection string.
- `RESEND_API_KEY`: Resend sending API key.
- `VERIFICATION_CODE_PEPPER`: long random secret used when hashing codes.
- `EMAIL_FROM`: sender identity, for example `Sehaty <no-reply@your-verified-domain.example>` after a sending domain is verified.
- `ALLOWED_ORIGINS`: exact app/web origins, as a normal variable.

Resend's unverified test sender can only deliver to the address that owns the Resend account. Sending to arbitrary app users requires a verified sending domain. Do not treat a test send as production readiness.

## Database

- Driver: Neon serverless PostgreSQL.
- Migrations: `migrations/0001_identity_and_profiles.sql` and `migrations/0002_auth_rate_limits.sql`.
- They create users, patient/doctor/clinic profiles, hashed email verification state, and hashed rate-limit buckets.
- The migration has not been applied to the live Neon database yet.

## Current limitations / deployment status

- Registration and email verification API routes are implemented in this branch but are not deployed.
- No live email delivery has been tested; provider secrets and a verified sender domain are not configured.
- Login, refresh-token sessions, password reset, and Android integration are not implemented yet.
- Expired rate-limit buckets are removed daily by a Cloudflare Cron Trigger.
- Firebase and the production Android app remain untouched. Do not cut over until migration, API, security, delivery, and rollback tests pass.

## Local checks

Use Node.js 22 and pnpm 10.23.0:

```sh
corepack enable
corepack prepare pnpm@10.23.0 --activate
pnpm install
pnpm run typecheck
pnpm test
```
