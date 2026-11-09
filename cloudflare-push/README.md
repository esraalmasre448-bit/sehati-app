# Free-tier chat push relay for Sehati

This Worker sends Firebase Cloud Messaging (FCM) notifications after an authenticated app user sends a chat message. It is a lightweight alternative to Firebase Cloud Functions; it does not use Firestore triggers and does not require enabling the Firebase Blaze plan.

## One-time setup

1. Create a free Cloudflare account and create a Worker named `sehati-chat-push`.
2. Deploy `worker.js` as the Worker source. The endpoint used by the app is `https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev/send`.
3. In Firebase Console, open Project settings → Service accounts → Generate new private key. Keep the downloaded JSON private; never commit it or send it in chat.
4. In Google Cloud Console for the same Firebase project, enable the **Firebase Cloud Messaging API**. Give the service account the permissions needed to read Firestore documents and send FCM messages (Cloud Datastore access plus Firebase Cloud Messaging API send permission). Use a dedicated service account with the minimum required roles where possible.
5. In the Cloudflare Worker settings, add these variables/secrets:
   - Secret `FIREBASE_SERVICE_ACCOUNT_JSON`: paste the complete service-account JSON.
   - Variable `FIREBASE_PROJECT_ID`: the Firebase project ID (from Project settings).
   - Variable `FIREBASE_WEB_API_KEY`: the Web API key from Project settings → General → Your apps (this is the app's public Firebase API key).
6. In GitHub → Settings → Secrets and variables → Actions, add repository secret `PUSH_WORKER_URL` with the Worker base URL, for example `https://sehati-chat-push.example.workers.dev` (no `/send` suffix).
7. Run the Build APK workflow on the feature branch after the Worker and secret are configured. The Android app embeds only the public Worker URL; the service-account private key stays in Cloudflare.

## What it does

- Validates the Firebase ID token with Firebase Authentication.
- Reads the referenced message and conversation using the service account.
- Verifies that the authenticated sender is a participant in that conversation and owns the message.
- Finds all registered FCM devices for the other participant and sends a high-priority notification.
- Uses the existing `sehati_general` Android notification channel, which already has sound and vibration.
- Does not expose service-account credentials or let a client choose an arbitrary recipient.

## Important

- FCM delivery is free. Cloudflare Workers has a free tier, subject to Cloudflare's current limits.
- The Firestore service account is privileged. Store it only as a Cloudflare Worker secret and restrict/delete the key if it is ever exposed.
- The app registers one token per installation in `fcmTokens`. The Firestore rules in this branch restrict each user to their own token documents.
- Android 13+ requires notification permission. The app already requests it; users must allow it in Android settings.
- The push relay is not active until the Worker is deployed and the GitHub Actions secret `PUSH_WORKER_URL` is set.
