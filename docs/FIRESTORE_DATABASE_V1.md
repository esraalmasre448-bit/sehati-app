# Sehati Firestore Database — Version 1

> This is the canonical, additive data contract for the existing Firebase project.
> Firestore is schemaless: collections/documents are created by the first successful write.
> Do not create a second Firebase project or delete/recreate the current database for this schema.
> Existing user, doctor, appointment, prescription and subscription documents must be preserved.

## 1. Design rules

- Firebase Authentication UID is the primary identity key. Never use email or phone as a document ID.
- Store timestamps as Firestore Timestamp values; use server timestamps for authoritative events.
- Keep public doctor profile data separate from private contact data.
- Every patient-owned health document is private by default. A doctor reads it only while a patient grant is valid.
- Booking allocation, subscription activation, admin decisions and audit events are trusted-server operations. Never let a client allocate queue numbers or grant itself a paid subscription.
- Medical records and prescriptions are append-only. Corrections are new records or a void operation with reason; do not erase clinical history.
- Do not store large images, scans or PDFs as base64 in Firestore. Store files in Firebase Storage and keep Storage paths/metadata in Firestore.
- Never store passwords, raw email OTPs, FCM service-account credentials or other secrets in Firestore.

## 2. Active collections already used by the app

### Identity and roles

| Path | Document ID | Main fields |
|---|---|---|
| `users/{uid}` | Auth UID | `name, phone, email, accountType(patient\|doctor\|clinic), createdAt, emailVerified` |
| `doctors/{uid}` | Auth UID | Public listing/profile: `uid, name, specialty, clinicName, city, address, bio, fee, photo, approved, available, emergency, workDays, startHour, endHour, slotMinutes, dailyCapacity, workLocations, currentWorkLocationId, currentWorkPlaceName, lat, lng, mapLink, updatedAt` |
| `doctorPrivate/{uid}` | Auth UID | `phone, emergencyPhone, updatedAt`; never expose in public doctor queries |
| `config/admin` | `admin` | Server-managed admin UID; clients cannot write |
| `config/contact` | `contact` | Official support/payment contact and configured plan prices |
| `config/developer` | `developer` | Developer display name/photo |

The `accountType` values are exactly `patient`, `doctor`, and `clinic`. Approval is stored separately in `doctors/{uid}.approved`.

### Clinics and work locations

| Path | Document ID | Main fields |
|---|---|---|
| `doctorBranches/{doctorUid}/items/{branchId}` | Generated branch ID | `name, city, address, mapLink, lat, lng, active, updatedAt` |
| `doctors/{uid}.workLocations` | Embedded map | `clinic` and optional `hospital` work-place objects with schedule/capacity/location |
| `doctors/{uid}.currentWorkLocationId` | Field | Selected active workplace ID |

### Appointments and queue

| Path | Document ID | Main fields |
|---|---|---|
| `appointments/{appointmentId}` | `{doctorUid}_{yyyy-MM-dd}_{patientUid}_{CONSULTATION|FOLLOW_UP}` for new queue bookings; older IDs remain readable | `patientId, patientName, patientPhone, doctorId, doctorName, specialty, clinicName, address, city, date, slot, time, number, status, note, visitType, beneficiaryRelation, createdAt`; optional `followUpDate, followUpTime, paidAmount, paidCurrency, paidRecordedAt` |
| `queues/{doctorUid}_{yyyy-MM-dd}` | Doctor + local date | `doctorId, date, last, current, lastAppointmentId` |
| `slots/{legacyId}` | Legacy slot ID | Legacy compatibility only. Client create/read/update are intentionally disabled by current rules. Do not use this as the new booking path. |

Appointment status lifecycle:
- `PENDING` → `CONFIRMED` or `CANCELLED`
- `CONFIRMED` → `DONE` or `CANCELLED`
- No client may create/delete an appointment directly.
- A patient may cancel their own eligible appointment. A doctor may approve/complete/cancel appointments subject to approval and active subscription.
- The trusted booking service must allocate `number`, increment `queues.last`, and create the appointment in one transaction.
- `CONSULTATION` and `FOLLOW_UP` are distinct booking types. Keep their IDs distinct so a patient can have both types on the same doctor/date.

### Clinical information

| Path | Document ID | Main fields |
|---|---|---|
| `healthProfiles/{patientUid}` | Patient UID | `uid, bloodType, allergies, conditions, emergencyContact, notes, updatedAt` |
| `healthAccessGrants/{patientUid}/doctors/{doctorUid}` | Doctor UID | `patientUid, doctorUid, expiresAt, grantedAt, revoked` |
| `prescriptions/{prescriptionId}` | Generated ID | `doctorId, doctorName, patientId, patientName, appointmentId, kind(rx\|lab), text, createdAt`; void metadata: `status, voidedAt, voidedBy, voidReason` |
| `familyProfiles/{patientUid}/members/{memberId}` | Generated ID | `name, relation, createdAt` |
| `medicationReminders/{patientUid}/items/{itemId}` | Generated ID | `medicine, dose, time, active, createdAt, updatedAt` |

A patient owns their health profile and access grants. A doctor may read the profile only with a non-revoked, unexpired grant and a valid approved/active provider account. A prescription must be linked to an appointment for that same patient and doctor. A voided prescription remains stored with its reason.

### Communication and notifications

| Path | Document ID | Main fields |
|---|---|---|
| `conversations/{doctorUid}_{patientUid}` | Stable pair ID | `doctorId, patientId, doctorName, patientName, appointmentId, lastMessage, lastSenderId, lastAt, createdAt` |
| `messages/{messageId}` | Generated ID | `convId, senderId, text, createdAt` |
| `fcmTokens/{uid}_{installationId}` | User + app installation | `uid, token, updatedAt` |

Only the two conversation participants may read messages. A sender can create a message only as themselves. FCM tokens are private to their owner; the trusted notification sender may use them to deliver push notifications. Never expose tokens in app listings or analytics.

### Billing and administration

| Path | Document ID | Main fields |
|---|---|---|
| `subscriptions/{providerUid}` | Doctor/clinic UID | `uid, status, trial, paymentMethod, startedAt, expiresAt` |
| `renewalRequests/{requestId}` | Generated ID | `uid, doctorName, months(1\|3\|6\|12), planLabel, amount, status, createdAt`; review: `reviewedAt, reviewedBy` |
| `subscriptionHistory/{eventId}` | Generated ID | Admin-only immutable activation/renewal event |
| `config/contact` | Fixed ID | Current official payment instructions and plan prices |

A provider cannot self-activate or extend a paid subscription. Only admin/trusted server operations can approve renewals and write subscription history.

## 3. Server-only collections

Cloud Functions currently use:
- `_emailVerificationCodes/{uid}`: hashed OTP, expiry, attempt count; raw OTP is never stored.
- `_emailOtpRateLimits/{uid}`: resend and daily rate-limit counters.
- `_noetraxBridgeRateLimits/{uid}`: separate integration rate limit; unrelated to the patient medical record.

These collections must remain inaccessible to Android clients. Add TTL policies only after confirming the product's retention requirements and the supported Firestore TTL configuration.

## 4. Planned additions — not yet active

These are future capabilities and must not be presented as working until their app screens, trusted functions, rules and tests are implemented:
- `notificationInbox/{uid}/items/{notificationId}`: durable in-app notification history/read state.
- `auditLogs/{eventId}`: append-only admin/security audit events written only by trusted server code.
- `medicalRecordEvents/{patientUid}/items/{eventId}`: normalized longitudinal record events (visit, diagnosis, measurement, attachment reference), with patient consent and clinician provenance.
- `clinicStaff/{clinicUid}/members/{uid}`: delegated staff roles and per-branch permissions.
- `appointmentEvents/{appointmentId}/items/{eventId}`: immutable appointment state-transition history.
- `paymentReceipts/{receiptId}`: verified payment metadata; never store card/bank credentials.

Do not create these paths from the client until the matching authorization model and feature implementation exist.

## 5. Query/index contract

The app currently queries:
- doctors by `approved`, and emergency doctors by `approved + emergency`;
- appointments by `doctorId`, `patientId`, `date`, and the pair `patientId + doctorId`;
- appointments by `doctorId + date` in the trusted queue notification service;
- prescriptions by `doctorId` or `patientId`;
- messages by `convId`;
- renewal requests ordered by `createdAt DESC`;
- device tokens by `uid`;
- active doctor branches by `active`.

Single-field queries use Firestore's automatic single-field indexes. Composite indexes required by the app are kept in `firestore.indexes.json`. Deploy indexes before shipping code that depends on a new compound query.

## 6. Important current integration gap

The Android queue booking screen calls `PushSender.bookAppointment`, which currently targets the Cloudflare push Worker. That Worker verifies Firebase ID tokens and uses trusted Firestore/FCM access to create appointments and allocate queue numbers. The old `Booking.kt` direct-write path is not a valid replacement: current Firestore rules deny client appointment/queue creation and disable legacy slot creation.

Therefore, Firebase remains the database/auth provider, but the project is not yet a Firebase-only backend. Before removing Cloudflare, port the booking transaction and authenticated notification endpoints to Firebase Cloud Functions (Admin SDK), test them in the Emulator Suite, then switch the Android endpoint. Keep the existing Worker available until the replacement passes those tests.

## 7. Safe rollout

1. Keep the current Firebase project and all existing documents.
2. Deploy only the additive composite index in this change.
3. Build and test Cloud Functions against the Firestore Emulator; do not weaken rules to make a failing client write pass.
4. Run emulator tests for patient/doctor/admin access, expired grants, cross-user reads, appointment transitions, duplicate booking races, and prescription immutability.
5. Verify current live records and take a backup/export before any data migration.
6. Only then deploy reviewed rules/functions and update Android. No live rules or production data are changed by this schema document.
