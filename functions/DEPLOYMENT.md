# Email OTP deployment

The Android client now calls two Firebase callable functions:
- `requestEmailVerificationCode`
- `verifyEmailVerificationCode`

The functions generate a six-digit code on the server, store only an HMAC digest in a private Firestore collection, expire it after 10 minutes, limit resend to once per minute and five sends per rolling 24-hour window, and allow at most five verification attempts. The code is never written to app logs or returned to the client.

## Required setup before production use

1. The Firebase project must use the Blaze plan to deploy Cloud Functions. Blaze requires a linked Cloud Billing account and can incur charges. Configure a budget alert and a Cloud Run spend cap before deployment. The Functions no-cost quota does not remove the requirement for Blaze.
2. Configure an SMTP account that is permitted to send transactional email. Do not put SMTP credentials in Android, GitHub source files, Firestore, or this chat.
3. Install Node.js 20 and the Firebase CLI, authenticate, and select the same Firebase project used by the Android app.
4. From the repository root, set these Firebase Secret Manager values using the CLI (the CLI prompts for each value):
   - `firebase functions:secrets:set SMTP_HOST`
   - `firebase functions:secrets:set SMTP_PORT` (normally 587 for STARTTLS or 465 for implicit TLS)
   - `firebase functions:secrets:set SMTP_USER`
   - `firebase functions:secrets:set SMTP_PASS`
   - `firebase functions:secrets:set SMTP_FROM` (verified sender address, e.g. Sehati <no-reply@example.com>)
   - `firebase functions:secrets:set OTP_HMAC_SECRET` (a randomly generated secret of at least 32 bytes; keep it private)
5. Deploy with `firebase deploy --only functions`.
6. Build and install the Android APK, then test with a real inbox: signup, wrong code, expired code, resend cooldown, correct code, logout/login, and password reset.

## Important

The code and Android integration are committed, but the functions are not deployed from this repository change. They cannot send email until SMTP secrets are configured and the functions are deployed. Never share SMTP passwords or the HMAC secret in chat. If the Firebase project must remain on the Spark plan with no billing account, Firebase Cloud Functions cannot be deployed; choose a different backend host before enabling this flow.
