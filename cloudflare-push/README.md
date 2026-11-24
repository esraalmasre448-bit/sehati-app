# Free-tier chat push relay for Sehati

This Worker sends Firebase Cloud Messaging (FCM) notifications after an authenticated app user sends a chat message. It is a lightweight alternative to Firebase Cloud Functions; it does not use Firestore triggers and does not require enabling the Firebase Blaze plan.

## One-time setup

The repository includes a GitHub Actions workflow that deploys the Worker for you. You do not need to paste or edit the Worker source in the Cloudflare dashboard.

1. Create a free Cloudflare account. Open **Workers & Pages → Create → Worker**, name it exactly `sehati-chat-push`, and deploy the starter Worker once. The deployment workflow updates this existing Worker; it does not need permission to create or delete Workers.
2. Create a Cloudflare API token. Give it **Workers Editor** scoped only to the `sehati-chat-push` Worker (or the legacy **Workers Scripts: Edit** permission scoped to that Worker), plus **Account Settings: Read** if Wrangler requests account metadata. Do not grant account-wide Admin.
3. Copy the Cloudflare **Account ID** from the account's Workers & Pages overview.
4. In Google Cloud Console for the same Firebase project, open **IAM & Admin → Service Accounts** and use the existing dedicated `sehati-push-worker` account already configured for this Worker. If it does not exist, create it once and use it consistently. Keep its JSON key private; never commit it or send it in chat. Do not use a broad Owner/Editor service account.
5. Enable the **Firebase Cloud Messaging API**. Grant the dedicated service account **Firebase Cloud Messaging API Admin** for notifications. For secure appointment booking, the Worker also needs Firestore write access. Prefer a custom project role named `SehatiBookingWorker` containing only `datastore.databases.get`, `datastore.entities.get`, `datastore.entities.list`, `datastore.entities.create`, and `datastore.entities.update`. Assign that role to this dedicated service account in the same project. If custom roles are unavailable, use **Cloud Datastore User** instead of Viewer; do not grant Owner or Editor. This service account is privileged and must remain dedicated to this Worker.
6. In GitHub → **Settings → Secrets and variables → Actions**, add these repository secrets:
   - `CLOUDFLARE_API_TOKEN`: the token from step 2.
   - `CLOUDFLARE_ACCOUNT_ID`: the Account ID from step 3.
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
- Exposes an authenticated `/book-appointment` endpoint. It verifies the Firebase user, patient account, doctor approval and active subscription, then atomically creates or rebooks a typed appointment and advances the shared queue in one Firestore transaction.
- The Android app sends appointment requests to this endpoint. Firestore rules deny direct patient writes to appointments and queue counters, preventing clients from forging queue numbers or creating appointments without advancing the queue.
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
- Chat notifications and secure booking are not active until the Worker is deployed, `PUSH_WORKER_URL` is saved, the dedicated service account has the required Firestore permissions, and a release APK is built with that URL.
- No Firebase Functions or Blaze plan is required for the Worker endpoint. Cloudflare Workers usage remains subject to Cloudflare's current free-tier limits.
