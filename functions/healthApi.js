const { onRequest } = require("firebase-functions/v2/https");
const { initializeApp, getApps } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

if (!getApps().length) initializeApp();

const REGION = "europe-west1";
const db = getFirestore();

function reply(res, status, body) {
  res.status(status).set("Cache-Control", "no-store").json(body);
}
function text(value, max) {
  return typeof value === "string" ? value.trim().slice(0, max) : "";
}
function number(data, key, fallback = 0) {
  const value = data && data[key];
  return Number.isFinite(value) ? value : fallback;
}
function timeLabel(minutes) {
  return String(Math.floor(minutes / 60)).padStart(2, "0") + ":" +
    String(minutes % 60).padStart(2, "0");
}
async function authenticate(req) {
  const match = String(req.get("authorization") || "").match(/^Bearer\s+(.+)$/i);
  if (!match) return null;
  try {
    const decoded = await getAuth().verifyIdToken(match[1], true);
    return decoded.uid;
  } catch (_) {
    return null;
  }
}
async function notify(uid, title, body, data = {}) {
  if (!uid) return { sent: 0, failed: 0 };
  const snapshot = await db.collection("fcmTokens").where("uid", "==", uid).limit(500).get();
  const tokenDocs = snapshot.docs
    .map(doc => ({ ref: doc.ref, token: text(doc.get("token"), 4096) }))
    .filter(item => item.token);
  const tokens = tokenDocs.map(item => item.token);
  if (!tokens.length) return { sent: 0, failed: 0 };
  const response = await getMessaging().sendEachForMulticast({
    tokens,
    data: Object.fromEntries(Object.entries({ ...data, title, body })
      .map(([key, value]) => [key, String(value)])),
    android: { priority: "high", notification: { channelId: "sehati_general", sound: "default" } }
  });
  const invalid = [];
  response.responses.forEach((item, index) => {
    if (!item.success && [
      "messaging/invalid-registration-token",
      "messaging/registration-token-not-registered"
    ].includes(item.error && item.error.code)) invalid.push(tokenDocs[index].ref);
  });
  if (invalid.length) {
    const batch = db.batch();
    invalid.forEach(ref => batch.delete(ref));
    await batch.commit();
  }
  return { sent: response.successCount, failed: response.failureCount };
}

async function bookAppointment(uid, input) {
  const doctorId = text(input.doctorId, 128);
  const date = text(input.date, 10);
  const visitType = text(input.visitType, 20);
  const note = text(input.note, 300);
  const beneficiaryName = text(input.beneficiaryName, 100);
  const beneficiaryRelation = text(input.beneficiaryRelation, 40);
  const isToday = input.isToday === true;
  const nowMinutes = Number.isInteger(input.nowMinutes) && input.nowMinutes >= 0 && input.nowMinutes < 1440
    ? input.nowMinutes : 0;
  const parsedDate = /^\d{4}-\d{2}-\d{2}$/.test(date) ? new Date(date + "T00:00:00Z") : null;
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(doctorId) || !parsedDate ||
      parsedDate.toISOString().slice(0, 10) !== date ||
      !["CONSULTATION", "FOLLOW_UP"].includes(visitType)) {
    return { http: 400, body: { status: "INVALID", error: "invalid_booking" } };
  }

  const patientRef = db.collection("users").doc(uid);
  const doctorRef = db.collection("doctors").doc(doctorId);
  const subscriptionRef = db.collection("subscriptions").doc(doctorId);
  const appointmentId = doctorId + "_" + date + "_" + uid + "_" + visitType;
  const legacyId = doctorId + "_" + date + "_" + uid;
  const appointmentRef = db.collection("appointments").doc(appointmentId);
  const legacyRef = db.collection("appointments").doc(legacyId);
  const queueRef = db.collection("queues").doc(doctorId + "_" + date);

  const result = await db.runTransaction(async tx => {
    const [patientSnap, doctorSnap, subSnap, appointmentSnap, legacySnap, queueSnap] =
      await Promise.all([
        tx.get(patientRef), tx.get(doctorRef), tx.get(subscriptionRef),
        tx.get(appointmentRef), tx.get(legacyRef), tx.get(queueRef)
      ]);
    if (!patientSnap.exists || patientSnap.get("accountType") !== "patient") {
      return { status: "FORBIDDEN", http: 403 };
    }
    if (!doctorSnap.exists || doctorSnap.get("approved") !== true) {
      return { status: "DOCTOR_UNAVAILABLE", http: 409 };
    }
    const sub = subSnap.data() || {};
    if (sub.status !== "ACTIVE" || !(sub.expiresAt instanceof Timestamp) ||
        sub.expiresAt.toMillis() <= Date.now()) {
      return { status: "DOCTOR_UNAVAILABLE", http: 409 };
    }
    if (legacySnap.exists && ["PENDING", "CONFIRMED"].includes(legacySnap.get("status"))) {
      return { status: "LEGACY_EXIST", number: number(legacySnap.data(), "number"), time: text(legacySnap.get("time"), 8) };
    }
    if (appointmentSnap.exists) {
      const status = appointmentSnap.get("status");
      if (status === "CONFIRMED") return { status: "EXIST", number: number(appointmentSnap.data(), "number"), time: text(appointmentSnap.get("time"), 8) };
      if (status === "PENDING") return { status: "PENDING_EXIST", number: number(appointmentSnap.data(), "number"), time: text(appointmentSnap.get("time"), 8) };
      if (status !== "CANCELLED") return { status: "BLOCKED", http: 409 };
    }

    const doctor = doctorSnap.data();
    const workDays = Array.isArray(doctor.workDays) ? doctor.workDays : [];
    if (workDays.length) {
      const weekday = new Date(date + "T12:00:00Z").getUTCDay() + 1;
      const allowed = workDays.map(Number).filter(day => day >= 1 && day <= 7);
      if (allowed.length && !allowed.includes(weekday)) return { status: "DAY_OFF" };
    }
    const queue = queueSnap.data() || {};
    const last = number(queue, "last");
    const current = number(queue, "current");
    const capacity = Math.max(1, Math.min(500, number(doctor, "dailyCapacity", 30)));
    if (last >= capacity) return { status: "CAPACITY" };
    const startHour = Math.max(0, Math.min(23, number(doctor, "startHour", 9)));
    const endHour = Math.max(startHour + 1, Math.min(24, number(doctor, "endHour", 17)));
    const step = Math.max(10, Math.min(180, number(doctor, "slotMinutes", 30)));
    const next = last + 1;
    const scheduled = startHour * 60 + (next - 1) * step;
    const slot = isToday
      ? Math.max(scheduled, nowMinutes + Math.max(0, next - 1 - current) * step)
      : scheduled;
    if (slot + step > endHour * 60) return { status: "FULL" };

    const patient = patientSnap.data();
    const appointment = {
      patientId: uid,
      patientName: beneficiaryName || text(patient.name, 100) || "المريض",
      patientPhone: text(patient.phone, 30),
      doctorId,
      doctorName: text(doctor.name, 120),
      specialty: text(doctor.specialty, 120),
      clinicName: text(doctor.clinicName, 120),
      address: text(doctor.address, 300),
      city: text(doctor.city, 100),
      date,
      slot,
      time: timeLabel(slot),
      number: next,
      status: "PENDING",
      note,
      visitType,
      createdAt: Timestamp.now()
    };
    if (beneficiaryRelation) appointment.beneficiaryRelation = beneficiaryRelation;
    tx.set(appointmentRef, appointment);
    tx.set(queueRef, {
      doctorId, date, last: next, current,
      lastAppointmentId: appointmentId
    }, { merge: true });
    return { status: "OK", number: next, time: appointment.time, date, appointmentId,
      patientName: appointment.patientName };
  });

  if (result.status === "OK") {
    try {
      await notify(doctorId, "طلب حجز جديد",
        result.patientName + " طلب موعداً بتاريخ " + date + " الساعة " + result.time +
        " — رقم " + result.number,
        { type: "appointment", appointmentId: result.appointmentId, event: "booking_requested" });
    } catch (error) {
      console.error("Booking notification failed", error && error.message);
    }
    delete result.appointmentId;
    delete result.patientName;
  }
  const http = result.http || (result.status === "FORBIDDEN" ? 403 : result.status === "INVALID" ? 400 : 200);
  delete result.http;
  return { http, body: result };
}

async function notifyAppointment(uid, input) {
  const id = text(input.appointmentId, 180);
  const event = text(input.event, 40);
  if (!/^[A-Za-z0-9_-]{3,180}$/.test(id)) return { http: 400, body: { error: "invalid_appointment" } };
  const snap = await db.collection("appointments").doc(id).get();
  if (!snap.exists) return { http: 404, body: { error: "not_found" } };
  const a = snap.data();
  const patientId = text(a.patientId, 128), doctorId = text(a.doctorId, 128);
  const patientName = text(a.patientName, 100) || "المريض";
  const doctorName = text(a.doctorName, 100) || "الطبيب";
  let receiver, title, body;
  if (event === "booking_requested" && uid === patientId && a.status === "PENDING") {
    receiver = doctorId; title = "طلب حجز جديد";
    body = patientName + " طلب موعداً بتاريخ " + a.date + " الساعة " + a.time + " — رقم " + a.number;
  } else if (event === "booking_approved" && uid === doctorId && a.status === "CONFIRMED") {
    receiver = patientId; title = "تم تأكيد حجزك";
    body = doctorName + " وافق على موعدك بتاريخ " + a.date + " الساعة " + a.time;
  } else if (event === "appointment_cancelled" && a.status === "CANCELLED" &&
      (uid === doctorId || uid === patientId)) {
    receiver = uid === doctorId ? patientId : doctorId;
    title = "تم إلغاء الموعد"; body = "تم إلغاء الموعد بتاريخ " + a.date + " الساعة " + a.time;
  } else if (event === "appointment_done" && uid === doctorId && a.status === "DONE") {
    receiver = patientId; title = "اكتملت زيارتك"; body = "أنهى " + doctorName + " زيارتك.";
  } else if (event === "followup_scheduled" && uid === doctorId &&
      /^\d{4}-\d{2}-\d{2}$/.test(text(a.followUpDate, 10)) &&
      /^([01]\d|2[0-3]):[0-5]\d$/.test(text(a.followUpTime, 5))) {
    receiver = patientId; title = "تم تحديد موعد متابعة";
    body = doctorName + " حدّد لك موعد متابعة بتاريخ " + a.followUpDate + " الساعة " + a.followUpTime;
  } else if (event === "booking_created" && uid === patientId && a.status === "CONFIRMED") {
    receiver = doctorId; title = "حجز جديد"; body = patientName + " حجز موعداً بتاريخ " + a.date;
  } else {
    return { http: 403, body: { error: "forbidden" } };
  }
  return { http: 200, body: await notify(receiver, title, body,
    { type: "appointment", appointmentId: id, event }) };
}

async function handle(req, res) {
  if (req.method !== "POST") return reply(res, 405, { error: "method_not_allowed" });
  const uid = await authenticate(req);
  if (!uid) return reply(res, 401, { error: "unauthorized" });
  const input = req.body && typeof req.body === "object" ? req.body : {};
  const path = req.path.replace(/\/+$/, "");
  try {
    if (path.endsWith("/book-appointment")) {
      const result = await bookAppointment(uid, input);
      return reply(res, result.http, result.body);
    }
    if (path.endsWith("/notify-appointment")) {
      const result = await notifyAppointment(uid, input);
      return reply(res, result.http, result.body);
    }
    if (path.endsWith("/send")) {
      const convId = text(input.convId, 180), messageId = text(input.messageId, 180);
      if (!/^[A-Za-z0-9_-]{3,180}$/.test(convId) || !/^[A-Za-z0-9_-]{3,180}$/.test(messageId))
        return reply(res, 400, { error: "invalid_reference" });
      const [conv, msg] = await Promise.all([
        db.collection("conversations").doc(convId).get(),
        db.collection("messages").doc(messageId).get()
      ]);
      if (!conv.exists || !msg.exists || msg.get("convId") !== convId || msg.get("senderId") !== uid)
        return reply(res, 403, { error: "forbidden" });
      if (!text(msg.get("text"), 1000)) return reply(res, 400, { error: "empty_message" });
      const c = conv.data();
      const receiver = uid === c.patientId ? c.doctorId : uid === c.doctorId ? c.patientId : "";
      if (!receiver) return reply(res, 403, { error: "forbidden" });
      return reply(res, 200, await notify(receiver, "رسالة جديدة",
        text(msg.get("text"), 1000), { type: "chat", convId, messageId }));
    }
    if (path.endsWith("/notify-prescription")) {
      const id = text(input.prescriptionId, 180);
      const snap = await db.collection("prescriptions").doc(id).get();
      if (!snap.exists || snap.get("doctorId") !== uid)
        return reply(res, 403, { error: "forbidden" });
      const p = snap.data();
      return reply(res, 200, await notify(text(p.patientId, 128),
        p.kind === "lab" ? "طلب تحاليل جديد" : "وصفة طبية جديدة",
        "أرسل " + (text(p.doctorName, 100) || "طبيبك") + " مستنداً طبياً جديداً. افتح وصفاتك الطبية للاطلاع عليه.",
        { type: "prescription", prescriptionId: id }));
    }
    if (path.endsWith("/notify-queue")) {
      const date = text(input.date, 10);
      if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return reply(res, 400, { error: "invalid_date" });
      const queue = await db.collection("queues").doc(uid + "_" + date).get();
      if (!queue.exists || queue.get("doctorId") !== uid || queue.get("date") !== date)
        return reply(res, 403, { error: "forbidden" });
      const current = number(queue.data(), "current");
      const appointments = await db.collection("appointments")
        .where("doctorId", "==", uid).where("date", "==", date).limit(500).get();
      const upcoming = appointments.docs.map(doc => ({ id: doc.id, ...doc.data() }))
        .filter(a => a.status === "CONFIRMED" && a.patientId &&
          a.number >= current && a.number <= current + 3);
      const sends = await Promise.all(upcoming.map(a => {
        const offset = a.number - current;
        const title = offset === 0 ? "حان دورك" : "دورك قريب";
        const body = offset === 0 ? "تفضّل بالدخول إلى " + (a.doctorName || "الطبيب") :
          offset === 1 ? "أنت التالي عند " + (a.doctorName || "الطبيب") :
          "بقي " + (offset - 1) + " قبلك عند " + (a.doctorName || "الطبيب");
        return notify(a.patientId, title, body, {
          type: "queue", appointmentId: a.id, date, doctorId: uid, queueNumber: a.number
        });
      }));
      return reply(res, 200, {
        current, patientsNotified: sends.length,
        sent: sends.reduce((sum, item) => sum + item.sent, 0),
        failed: sends.reduce((sum, item) => sum + item.failed, 0)
      });
    }
    return reply(res, 404, { error: "not_found" });
  } catch (error) {
    console.error("Sehati API error", error && error.message);
    return reply(res, 500, { error: "internal_error" });
  }
}

exports.sehatiApi = onRequest({
  region: REGION,
  timeoutSeconds: 60,
  memory: "256MiB",
  maxInstances: 30,
  cors: false
}, handle);
