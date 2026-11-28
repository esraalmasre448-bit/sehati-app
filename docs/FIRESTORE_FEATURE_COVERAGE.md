# Firestore Feature Coverage Audit — الطريق الصحي

Audit target: Android app on `firebase/firestore-foundation`.
Purpose: distinguish data that is actually persisted in Firestore from device-local state, server-only state, and future features. This is an additive contract; it does not create collections until documents are written.

## 1. Active Firestore data used by the app

| App area | Firestore path | Data / responsibility | Access boundary |
|---|---|---|---|
| Accounts and roles | `users/{uid}` | Name, phone, email, accountType, createdAt, emailVerified | Owner and admin; account type is constrained |
| Public provider directory | `doctors/{uid}` | Provider profile, specialty, city/address, working hours, capacity, availability, emergency flag, locations | Signed-in users read approved profiles; provider edits own profile; admin approves |
| Private provider contact | `doctorPrivate/{uid}` | Phone and emergency phone | Provider owner/admin; approved patient lookup as encoded in current rules |
| Provider subscription | `subscriptions/{providerUid}` | Trial/paid status and expiry | Provider owner and admin; activation/renewal controlled by admin |
| Subscription audit | `subscriptionHistory/{eventId}` | Admin history: action, payment method, period, months, admin UID, timestamps | Admin only; immutable to clients |
| Renewal requests | `renewalRequests/{requestId}` | Provider request, plan, amount, status, review metadata | Provider owner and admin |
| Admin/payment configuration | `config/admin`, `config/contact`, `config/developer` | Admin identity, payment contact/QR/prices, developer card | Admin-controlled writes |
| Appointments | `appointments/{appointmentId}` | Patient/provider, date, visit type, queue number, time, note, status, follow-up, recorded payment | Patient and treating provider; creation is trusted-server-only |
| Daily queue | `queues/{doctorUid}_{yyyy-MM-dd}` | Last/current number and last appointment | Signed-in reads; booking creation is trusted-server-only; provider advances current |
| Prescriptions and lab requests | `prescriptions/{id}` | Linked appointment, provider/patient, rx/lab text, creation and void metadata | Patient and treating provider; retained, not deleted |
| Conversations | `conversations/{doctorUid}_{patientUid}` | Participants, linked appointment, latest-message preview | Only the two participants |
| Messages | `messages/{messageId}` | Conversation ID, sender, text, timestamp | Only conversation participants; immutable to clients |
| Push device registrations | `fcmTokens/{uid}_{installationId}` | Per-installation FCM token and update timestamp | Owner only; trusted sender reads for delivery |
| Provider branches | `doctorBranches/{doctorUid}/items/{branchId}` | Branch name, city, address, map coordinates/link, active flag | Provider owner; patients see active branches of approved providers |
| Family members | `familyProfiles/{patientUid}/members/{memberId}` | Patient's family member name and relation | Patient owner only |
| Health profile | `healthProfiles/{patientUid}` | Blood type, allergies, conditions, emergency contact, notes | Patient owner; approved/active provider only with a valid temporary grant |
| Health access grants | `healthAccessGrants/{patientUid}/doctors/{doctorUid}` | Grant expiry, granted timestamp, revoked state | Patient controls grants; provider access is checked against grant |
| Favorites | `favorites/{patientUid}/doctors/{doctorUid}` | Saved approved provider and timestamp | Patient owner only |
| Medication plans | `medicationReminders/{patientUid}/items/{itemId}` | Medicine, dose, time, active state and timestamps | Patient owner only; soft-disable rather than delete |
| Legacy booking compatibility | `slots/{legacyId}` | Legacy path only | Client reads/writes disabled by current rules |

## 2. Features derived from existing records

These features do not require a separate collection today:
- Emergency/on-call directory is derived from provider profile fields such as `emergency`, `available`, `approved`, and subscription state.
- Doctor dashboard and statistics are calculated from appointments, queue and recorded payment fields.
- Patient appointment lists and provider patient lists are queries over `appointments`.
- Follow-up reminders are currently represented by follow-up fields on the appointment and delivered through the push relay.
- Prescription history is queried from `prescriptions`.
- Chat unread/notification behavior uses message/conversation activity and the push relay; there is no durable per-user notification inbox yet.

## 3. Device-local features (not Firestore data)

- App lock configuration is device-local.
- The visit-preparation checklist explicitly saves its checkboxes in Android SharedPreferences on that phone.
- Medication reminder scheduling is performed locally by Android after reading the Firestore medication plan.
- UI preferences and other settings must be audited separately before describing them as cloud-synced.

## 4. Server-only data and trusted operations

The existing Cloud Functions retain email OTP request/verification and the separate NOETRAX bridge. PR #91 adds `sehatiApi`, an authenticated Admin SDK HTTPS API for appointment booking/queue allocation and chat, appointment, prescription and queue push delivery. The API export is wired into `functions/index.js`; CI now installs dependencies and verifies that `sehatiApi` is actually discoverable for deployment.

The Android booking screen still calls `PushSender.bookAppointment` at the configured Cloudflare Worker. That Worker remains the active trusted booking/push service. The new Firebase API has not yet been deployed or integration-tested against the existing Firebase project. Do not remove or disable the Worker or change the Android endpoint until the new API passes Emulator Suite and smoke tests, is deployed, and the app is deliberately migrated.

Private server collections:
- `_emailVerificationCodes/{uid}`: hashed OTP, expiry and attempts.
- `_emailOtpRateLimits/{uid}`: resend and daily limits.
- `_noetraxBridgeRateLimits/{uid}`: separate NOETRAX integration state; unrelated to the medical data model.

## 5. Planned collections — not active app functionality

These are intentionally not enabled by client rules and must not be presented as working features until the UI, backend, rules and tests exist:
- `notificationInbox/{uid}/items/{notificationId}`: durable in-app notification history and read state.
- `auditLogs/{eventId}`: trusted append-only security/admin audit trail.
- `medicalRecordEvents/{patientUid}/items/{eventId}`: longitudinal clinical events with consent and clinician provenance.
- `clinicStaff/{clinicUid}/members/{uid}`: delegated clinic staff and branch-level roles.
- `appointmentEvents/{appointmentId}/items/{eventId}`: immutable appointment transition history.
- `paymentReceipts/{receiptId}`: verified payment metadata only; never card or bank credentials.

## 6. Validation and rollout status

- Firestore rules emulator workflow passed for commit `00c08d93810072e8a6956e6e5d03b396220c4340` (GitHub Actions run 46129769013).
- Android APK build workflow passed for the same commit (run 46129769286).
- Existing rules include owner/participant boundaries, explicit health-data grants, provider approval/subscription checks, immutable prescription/message constraints, and server-only appointment/queue creation.
- Existing composite indexes are maintained in `firestore.indexes.json`, including provider emergency lookup.
- This audit and schema contract do not deploy anything to the live Firebase project. Production deployment still requires a reviewed release and authenticated Firebase deployment access. Do not publish unreviewed rules or switch booking endpoints based only on a successful schema/test workflow.
- Before a Firebase-only backend is declared complete: port booking and push endpoints to Cloud Functions; add transaction/race and notification tests; audit every remaining Android Firestore query against indexes; back up production data; deploy indexes/rules/functions in a controlled sequence; verify live smoke tests.
