# Sehaty Backend API

This is a separate Cloudflare Worker API. It does not import Firebase SDKs and does not modify the Android app or legacy `cloudflare-push` Worker.

## Current endpoints

- `GET /health`: liveness only.
- `GET /ready`: deliberately returns `503 not_ready` until the PostgreSQL adapter and a real bounded connectivity probe exist.
- `GET /v1`: versioned API metadata.

All responses include a request ID. Browser CORS responses are enabled only for exact origins listed in the `ALLOWED_ORIGINS` Worker variable. No credentials belong in source control.

## Local checks

From this directory:

```sh
npm install
npm run typecheck
npm test
```

## Deployment status

The Worker configuration is prepared, but this API is not deployed yet. Deployment waits for CI validation and explicit environment configuration. PostgreSQL, account authentication, and production routes are not implemented in this scaffold.

## Next milestone

Add PostgreSQL schema migrations and a server-side database adapter with a bounded readiness probe. Only then should `/ready` become successful. Keep Firebase and the production Android app untouched until migration parity and rollback tests pass.
