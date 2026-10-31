# Email verification on Firebase Spark

The Android app uses Firebase Authentication's built-in email verification link:
- Account creation calls `FirebaseUser.sendEmailVerification()`.
- The user opens the link received by email, returns to the app, and taps **"تحققت من بريدي، متابعة"**.
- The app reloads the Firebase user, confirms `isEmailVerified`, refreshes the ID token, and updates the existing Firestore user profile.
- The existing account-type handling and doctor/clinic trial subscription setup are preserved.

This flow does not require Cloud Functions, SMTP credentials, Secret Manager, or a Blaze billing account. Firebase Authentication sends the action email using the project's configured email templates and authorized domains.

## Firebase Console checklist

1. Open Authentication → Sign-in method and ensure Email/Password is enabled.
2. Open Authentication → Templates → Email address verification and review the sender/name and message.
3. Open Authentication → Settings → Authorized domains and ensure the app's Firebase project's default hosted action domain is present.
4. Build and install the APK, then test with a real inbox:
   - register a new patient account;
   - confirm the verification link opens and verifies the same Firebase project;
   - return to the app and tap **"تحققت من بريدي، متابعة"**;
   - confirm the user profile is updated and the app opens;
   - repeat with doctor and clinic accounts and verify the existing trial-subscription behavior;
   - sign out and sign in again;
   - test password reset.

## Legacy code

The `functions/` directory contains the previous six-digit OTP prototype. It is not called by the Android app and must not be deployed for this Spark-plan configuration. It can be removed in a later cleanup after the link-based flow has been tested end-to-end.
