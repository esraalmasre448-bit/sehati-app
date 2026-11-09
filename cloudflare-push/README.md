# Free-tier chat push relay for Sehati

This Worker sends Firebase Cloud Messaging (FCM) notifications after an authenticated app user sends a chat message. It is a lightweight alternative to Firebase Cloud Functions; it does not use Firestore triggers and does not require enabling the Firebase Blaze plan.

## One-time setup

The repository includes a GitHub Actions workflow that deploys the Worker for you. You do not need to paste or edit the Worker source in the Cloudflare dashboard.

1. Create a free Cloudflare account. Open **Workers & Pages → Create → Worker**, name it exactly `sehati-chat-push`, and deploy the starter Worker once. The deployment workflow updates this existing Worker; it does not need permission to create or delete Workers.
2. Create a Cloudflare API token. Give it **Workers Editor** scoped only to the `sehati-chat-push` Worker (or the legacy **Workers Scripts: Edit** permission scoped to that Worker), plus **Account Settings: Read** if Wrangler requests account metadata. Do not grant account-wide Admin.
3. Copy the Cloudflare **Account ID** from the account's Workers & Pages overview.
4. In Firebase Console, open **Project settings → Service accounts → Generate new private key**. Keep the downloaded JSON private; never commit it or send it in chat.
5. In Google Cloud Console for the same Firebase project, enable the **Firebase Cloud Messaging API**. Give the service account the minimum roles needed to read Firestore and send FCM messages (Cloud Datastore User and Firebase Cloud Messaging API Admin). Do not grant Owner or Editor.
6. In GitHub → **Settings → Secrets and variables → Actions**, add these repository secrets:
   - `CLOUDFLARE_API_TOKEN`: the token from step 1.
   - `CLOUDFLARE_ACCOUNT_ID`: the Account ID from step 2.
   - `FIREBASE_SERVICE_ACCOUNT_JSON`: the complete downloaded service-account JSON.
   - `FIREBASE_PROJECT_ID`: the Firebase project ID. It must match the `project_id` inside the service-account JSON.
   - `FIREBASE_WEB_API_KEY`: the Web API key from Firebase Project settings → General → Your apps.
7. In GitHub → **Actions**, run **Deploy Chat Push Worker** on `main`. The workflow validates the inputs, deploys `cloudflare-push/worker.js`, and stores the Firebase values as encrypted Worker secrets. It never prints the service-account JSON.
8. Open the successful deployment run and copy the Worker base URL shown in the Wrangler deployment output. It looks like `https://sehati-chat-push.YOUR-SUBDOMAIN.workers.dev`.
9. Back in GitHub → **Settings → Secrets and variables → Actions**, add repository secret `PUSH_WORKER_URL` with that base URL **without** the `/send` suffix.
10. Run **Build APK** on `main`. This embeds only the public Worker URL in the APK; the service-account private key remains in Cloudflare.

## What it does

- Validates the Firebase ID token with Firebase Authentication.
- Reads the referenced message and conversation using the service account.
- Verifies that the authenticated sender is a participant in that conversation and owns the message.
- Finds all registered FCM devices for the other participant and sends a high-priority notification.
- Uses the existing `sehati_general` Android notification channel, which has sound and vibration.
- Does not expose service-account credentials or let a client choose an arbitrary recipient.

## Important

- FCM delivery is free. Cloudflare Workers has a free tier, subject to Cloudflare's current limits.
- The Firestore service account is privileged. Store its JSON only as a GitHub Actions secret used by the deployment workflow, which installs it as a Cloudflare Worker secret. Never commit it or paste it into chat.
- If the key is ever exposed, revoke it in Google Cloud immediately and generate a replacement.
- The app registers one token per installation in `fcmTokens`; the Firestore rules restrict each user to their own token documents.
- Android 13+ requires notification permission. Users must allow it in Android settings.
- Chat push delivery is not active until the Worker is deployed, `PUSH_WORKER_URL` is saved, and a release APK is built with that URL.
