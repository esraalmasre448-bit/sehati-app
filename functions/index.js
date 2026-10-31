const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const crypto = require("crypto");
const nodemailer = require("nodemailer");

initializeApp();

const SMTP_HOST = defineSecret("SMTP_HOST");
const SMTP_PORT = defineSecret("SMTP_PORT");
const SMTP_USER = defineSecret("SMTP_USER");
const SMTP_PASS = defineSecret("SMTP_PASS");
const SMTP_FROM = defineSecret("SMTP_FROM");
const OTP_HMAC_SECRET = defineSecret("OTP_HMAC_SECRET");

const REGION = "europe-west1";
const CODE_TTL_MS = 10 * 60 * 1000;
const RESEND_WAIT_MS = 60 * 1000;
const DAILY_LIMIT = 5;
const MAX_ATTEMPTS = 5;

function digest(uid, code, secret) {
  return crypto.createHmac("sha256", secret).update(uid + ":" + code).digest("hex");
}

function safeEqual(a, b) {
  const aa = Buffer.from(String(a), "hex");
  const bb = Buffer.from(String(b), "hex");
  return aa.length === bb.length && crypto.timingSafeEqual(aa, bb);
}

function mailer() {
  return nodemailer.createTransport({
    host: SMTP_HOST.value(),
    port: Number(SMTP_PORT.value() || 587),
    secure: Number(SMTP_PORT.value() || 587) === 465,
    auth: { user: SMTP_USER.value(), pass: SMTP_PASS.value() }
  });
}

exports.requestEmailVerificationCode = onCall({
  region: REGION,
  secrets: [SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, SMTP_FROM, OTP_HMAC_SECRET]
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "يجب تسجيل الدخول أولاً.");
  const uid = request.auth.uid;
  const email = request.auth.token.email;
  if (!email || request.auth.token.email_verified === true) {
    throw new HttpsError("failed-precondition", "لا يوجد بريد غير موثق لهذا الحساب.");
  }

  const now = Date.now();
  const db = getFirestore();
  const rateRef = db.collection("_emailOtpRateLimits").doc(uid);
  const codeRef = db.collection("_emailVerificationCodes").doc(uid);
  const code = String(crypto.randomInt(0, 1000000)).padStart(6, "0");
  const codeHash = digest(uid, code, OTP_HMAC_SECRET.value());

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(rateRef);
    const data = snap.exists ? snap.data() : {};
    const lastSent = data.lastSentAt ? data.lastSentAt.toMillis() : 0;
    const dayStart = data.dayStart ? data.dayStart.toMillis() : 0;
    const sameDay = dayStart > 0 && now - dayStart < 24 * 60 * 60 * 1000;
    const count = sameDay ? (data.dailyCount || 0) : 0;
    if (now - lastSent < RESEND_WAIT_MS) {
      throw new HttpsError("resource-exhausted", "انتظر دقيقة قبل إعادة إرسال الرمز.");
    }
    if (count >= DAILY_LIMIT) {
      throw new HttpsError("resource-exhausted", "وصلت إلى الحد اليومي لإرسال الرموز. حاول غداً.");
    }
    tx.set(rateRef, {
      lastSentAt: Timestamp.fromMillis(now),
      dayStart: Timestamp.fromMillis(sameDay ? dayStart : now),
      dailyCount: count + 1
    });
    tx.set(codeRef, {
      codeHash,
      expiresAt: Timestamp.fromMillis(now + CODE_TTL_MS),
      attempts: 0,
      createdAt: Timestamp.fromMillis(now)
    });
  });

  try {
    await mailer().sendMail({
      from: SMTP_FROM.value(),
      to: email,
      subject: "رمز التحقق - الطريق إلى صحتي",
      text: "رمز التحقق الخاص بك هو: " + code + "\nصلاحية الرمز 10 دقائق. لا تشارك الرمز مع أي شخص.",
      html: "<div dir=\"rtl\" style=\"font-family:Arial,sans-serif\"><h2>الطريق إلى صحتي</h2><p>رمز التحقق الخاص بك:</p><div style=\"font-size:32px;font-weight:bold;letter-spacing:8px\">" + code + "</div><p>تنتهي صلاحية الرمز بعد 10 دقائق. لا تشاركه مع أي شخص.</p></div>"
    });
  } catch (err) {
    await codeRef.delete().catch(() => {});
    console.error("OTP email delivery failed", err && err.message);
    throw new HttpsError("unavailable", "تعذر إرسال البريد الآن. تحقق من إعدادات البريد وحاول لاحقاً.");
  }

  return { success: true, expiresInSeconds: 600, resendAfterSeconds: 60 };
});

exports.verifyEmailVerificationCode = onCall({
  region: REGION,
  secrets: [OTP_HMAC_SECRET]
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "يجب تسجيل الدخول أولاً.");
  const code = String(request.data && request.data.code || "").trim();
  if (!/^\d{6}$/.test(code)) {
    throw new HttpsError("invalid-argument", "أدخل رمزاً مكوناً من 6 أرقام.");
  }
  const uid = request.auth.uid;
  const db = getFirestore();
  const ref = db.collection("_emailVerificationCodes").doc(uid);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "لا يوجد رمز فعال. اطلب إرسال رمز جديد.");

  const data = snap.data();
  if (data.expiresAt.toMillis() <= Date.now()) {
    await ref.delete();
    throw new HttpsError("deadline-exceeded", "انتهت صلاحية الرمز. اطلب رمزاً جديداً.");
  }
  if ((data.attempts || 0) >= MAX_ATTEMPTS) {
    await ref.delete();
    throw new HttpsError("resource-exhausted", "تجاوزت عدد المحاولات. اطلب رمزاً جديداً.");
  }

  const expected = digest(uid, code, OTP_HMAC_SECRET.value());
  if (!safeEqual(data.codeHash, expected)) {
    await ref.update({ attempts: (data.attempts || 0) + 1 });
    throw new HttpsError("permission-denied", "الرمز غير صحيح. تحقق منه وحاول مجدداً.");
  }

  await getAuth().updateUser(uid, { emailVerified: true });
  await ref.delete();
  return { success: true, verified: true };
});
