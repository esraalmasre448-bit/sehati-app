# NOETRAX ↔ الطريق إلى صحتي bridge

## Current scope

The first bridge endpoint is intentionally **status-only**. It verifies a Firebase ID token issued by the separate NOETRAX Firebase project (`noetrax`), checks the NOETRAX UID against a server-side allowlist, applies a per-UID rate limit, and returns connection metadata only.

It does not read or return patient records, doctor records, appointments, prescriptions, messages, payments, or any other health data. Do not add data endpoints until the owner has defined the exact use case, account mapping, data fields, and access permissions.

## Configure

1. In the Sehati Firebase project, set the secret to the exact NOETRAX Firebase Auth UID(s) that are approved to use the bridge (comma-separated if more than one):

   `firebase functions:secrets:set NOETRAX_ALLOWED_UIDS`

2. Deploy the function from the Sehati project root:

   `firebase deploy --only functions:noetraxSehatiBridge`

3. The endpoint is:

   `https://europe-west1-<SEHATI_PROJECT_ID>.cloudfunctions.net/noetraxSehatiBridge/v1/status`

4. NOETRAX must send its current Firebase Authentication ID token in the HTTP header:

   `Authorization: Bearer <NOETRAX_FIREBASE_ID_TOKEN>`

5. The expected successful response contains `connected: true`, `service: "sehati"`, `bridgeVersion: 1`, and `capabilities: ["status"]`.

## Security notes

- Never place Sehati service-account credentials, Meta access tokens, or bridge secrets in the Android APK or source repository.
- Only the exact allowlisted NOETRAX Firebase Auth UID is accepted.
- The token is verified against Google's Firebase Secure Token signing keys, issuer, audience, and RS256 signature.
- Requests are rate-limited to 30 per UID per minute.
- Responses are marked `Cache-Control: no-store`.
- This endpoint is not a general-purpose admin API and grants no access to patient data.
