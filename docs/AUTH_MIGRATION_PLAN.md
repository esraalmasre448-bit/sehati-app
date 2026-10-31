# Authentication migration plan

Status: design/review only. No production authentication code or Firebase configuration has been changed.

## Confirmed product requirements
- Primary sign-in identifier: verified phone number + password.
- SMS OTP is required to verify phone ownership and for password recovery.
- Keep email as an optional additional sign-in/recovery method.
- Existing Firebase Auth users, UIDs, Firestore records, roles, appointments, conversations and prescriptions must remain intact.
- Changing a phone number requires recent authentication and verification of the new number.
- Losing access to the old number must use a separately verified recovery process; name or old phone number alone is insufficient.
- Do not store passwords, OTPs, or reusable authentication secrets in Firestore.
- Return generic responses for account lookup/recovery to reduce account enumeration.
- Apply server-side throttling/abuse controls and App Check where supported.

## Findings from current branch
- `AuthScreen.kt` currently uses `createUserWithEmailAndPassword`, `signInWithEmailAndPassword`, and `sendPasswordResetEmail`.
- The signup form stores the phone as an ordinary `users/{uid}.phone` field; this does not prove phone ownership.
- Login currently deletes an Auth user if the corresponding Firestore profile is missing. This is unsafe for transient/profile-migration states and must not be retained in a migration.
- `Account.kt` reauthenticates with `EmailAuthProvider`; it must support the actual linked providers and recent-login requirements.
- `app/build.gradle.kts` currently includes Firebase Auth and Firestore, but no Firebase App Check dependency.
- The repository branch inspected does not contain `firebase.json` or a root `package.json` at the paths checked; no Cloud Functions deployment structure was found in this inspection.

## Critical platform constraint
Firebase Phone Auth verifies a phone number and can sign in with an SMS credential, but it does not natively provide a phone-number-plus-password provider. A client-side query from phone number to email is not an acceptable workaround: it risks exposing account identifiers and enabling enumeration. Replacing existing email addresses with synthetic aliases would also conflict with the requirement to preserve email as an optional sign-in method.

## Safe implementation direction
1. Keep existing Email/Password accounts and UIDs unchanged during the first phase.
2. Add verified phone linking to existing signed-in accounts using Firebase Auth's supported phone credential/linking flow. Store only the verified normalized phone in the user profile, with server rules preventing arbitrary edits.
3. Design phone + password sign-in as a server-mediated identity lookup/authentication flow only if a supported secure backend can be provisioned and tested. The backend must never disclose the account email, must use generic failure responses, enforce rate limits, and return only a valid Firebase session/token after successful password verification. Do not ship a client-side phone-to-email lookup.
4. Keep email/password login available throughout migration. Do not force-migrate users until account linking and recovery have been proven with test accounts.
5. Password reset by phone must verify an SMS OTP and then update/reset the credential through a supported trusted authentication flow; do not implement password storage or verification in Firestore.
6. Update account deletion and password-change reauthentication to use recent-login/provider-aware flows.
7. Add emulator/integration tests for existing email users, verified phone linking, duplicate phone collisions, wrong OTP, expired OTP, rate limiting, recovery, changed/lost phone, deletion, and unchanged UIDs/data.
8. Roll out gradually with a rollback path; no production deployment until tests and Firebase project configuration are confirmed.

## Blocking decisions / prerequisites
- Confirm whether the Firebase project has billing/SMS regions and provider settings configured, and whether a trusted backend (Cloud Functions or another approved server) is available. Do not expose project secrets in chat.
- Confirm operational cost and availability of SMS for Syria and the European countries where users are expected.
- Confirm the account recovery evidence/support process for users who permanently lose their phone number.

## Explicitly not done
- No Auth provider settings changed.
- No users migrated, deleted, or re-keyed.
- No Firestore rules deployed or edited.
- No Firebase secrets, API keys, signing keys, or passwords accessed.
- No build or APK claimed.
