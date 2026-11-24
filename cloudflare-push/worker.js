const json = (value, status = 200) =>
  new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
  });

const field = (doc, name) => doc?.fields?.[name];
const str = (doc, name) => field(doc, name)?.stringValue ?? "";
const numeric = (doc, name) => {
  const value = field(doc, name);
  return Number(value?.integerValue ?? value?.doubleValue ?? value?.stringValue ?? 0);
};
const b64url = (bytes) => {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
};
const enc = new TextEncoder();

function pemBytes(pem) {
  const body = pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  const binary = atob(body);
  return Uint8Array.from(binary, c => c.charCodeAt(0));
}

async function googleAccessToken(env) {
  const sa = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(enc.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claims = b64url(enc.encode(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging",
    aud: sa.token_uri || "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3500
  })));
  const unsigned = header + "." + claims;
  const key = await crypto.subtle.importKey(
    "pkcs8", pemBytes(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, enc.encode(unsigned));
  const assertion = unsigned + "." + b64url(new Uint8Array(signature));
  const response = await fetch(sa.token_uri || "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion
    })
  });
  if (!response.ok) throw new Error("Google OAuth token exchange failed");
  return (await response.json()).access_token;
}

async function verifyFirebaseUser(idToken, env) {
  const response = await fetch(
    "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=" +
      encodeURIComponent(env.FIREBASE_WEB_API_KEY),
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ idToken })
    }
  );
  if (!response.ok) return null;
  const data = await response.json();
  return data.users?.[0]?.localId || null;
}

async function firestoreGet(projectId, accessToken, path, transaction) {
  const query = transaction ? "?transaction=" + encodeURIComponent(transaction) : "";
  const response = await fetch(
    "https://firestore.googleapis.com/v1/projects/" + encodeURIComponent(projectId) +
      "/databases/(default)/documents/" + path + query,
    { headers: { authorization: "Bearer " + accessToken } }
  );
  if (response.status === 404) return null;
  if (!response.ok) throw new Error("Firestore read failed");
  return response.json();
}

async function firestoreBeginTransaction(projectId, accessToken) {
  const response = await fetch(
    "https://firestore.googleapis.com/v1/projects/" + encodeURIComponent(projectId) +
      "/databases/(default)/documents:beginTransaction",
    {
      method: "POST",
      headers: { authorization: "Bearer " + accessToken, "content-type": "application/json" },
      body: JSON.stringify({ options: { readWrite: {} } })
    }
  );
  if (!response.ok) throw new Error("Firestore transaction could not start");
  const data = await response.json();
  if (!data.transaction) throw new Error("Firestore transaction token missing");
  return data.transaction;
}

async function firestoreRollback(projectId, accessToken, transaction) {
  await fetch(
    "https://firestore.googleapis.com/v1/projects/" + encodeURIComponent(projectId) +
      "/databases/(default)/documents:rollback",
    {
      method: "POST",
      headers: { authorization: "Bearer " + accessToken, "content-type": "application/json" },
      body: JSON.stringify({ transaction })
    }
  );
}

async function firestoreCommit(projectId, accessToken, transaction, writes) {
  const response = await fetch(
    "https://firestore.googleapis.com/v1/projects/" + encodeURIComponent(projectId) +
      "/databases/(default)/documents:commit",
    {
      method: "POST",
      headers: { authorization: "Bearer " + accessToken, "content-type": "application/json" },
      body: JSON.stringify({ transaction, writes })
    }
  );
  if (!response.ok) {
    const error = new Error("Firestore transaction commit failed");
    error.status = response.status;
    error.detail = await response.text().catch(() => "");
    throw error;
  }
  return response.json();
}

const firestoreValue = value => {
  if (value === null || value === undefined) return { nullValue: null };
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number") {
    return Number.isInteger(value)
      ? { integerValue: String(value) }
      : { doubleValue: value };
  }
  if (value instanceof Date) return { timestampValue: value.toISOString() };
  return { stringValue: String(value) };
};

const firestoreFields = values => Object.fromEntries(
  Object.entries(values).map(([key, value]) => [key, firestoreValue(value)])
);

const documentName = (projectId, path) =>
  "projects/" + projectId + "/databases/(default)/documents/" + path;

const timeLabel = minutes =>
  String(Math.floor(minutes / 60)).padStart(2, "0") + ":" +
  String(minutes % 60).padStart(2, "0");

async function bookAppointment(projectId, accessToken, patientUid, input) {
  const doctorId = typeof input.doctorId === "string" ? input.doctorId : "";
  const date = typeof input.date === "string" ? input.date : "";
  const visitType = typeof input.visitType === "string" ? input.visitType : "";
  const note = typeof input.note === "string" ? input.note.trim() : "";
  const beneficiaryName = typeof input.beneficiaryName === "string" ? input.beneficiaryName.trim() : "";
  const beneficiaryRelation = typeof input.beneficiaryRelation === "string" ? input.beneficiaryRelation.trim() : "";
  const isToday = input.isToday === true;
  const nowMinutes = Number.isInteger(input.nowMinutes) && input.nowMinutes >= 0 && input.nowMinutes < 1440
    ? input.nowMinutes : 0;

  if (!/^[A-Za-z0-9_-]{1,128}$/.test(doctorId) ||
      !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
      !["CONSULTATION", "FOLLOW_UP"].includes(visitType) ||
      note.length > 300 || beneficiaryName.length > 100 || beneficiaryRelation.length > 40) {
    return json({ status: "INVALID", error: "invalid_booking" }, 400);
  }

  const appointmentId = doctorId + "_" + date + "_" + patientUid + "_" + visitType;
  const legacyId = doctorId + "_" + date + "_" + patientUid;
  const queueId = doctorId + "_" + date;
  const appointmentPath = "appointments/" + appointmentId;
  const legacyPath = "appointments/" + legacyId;
  const queuePath = "queues/" + queueId;

  for (let attempt = 0; attempt < 4; attempt++) {
    const transaction = await firestoreBeginTransaction(projectId, accessToken);
    let committed = false;
    try {
      const [user, doctor, subscription, appointment, legacy, queue] = await Promise.all([
        firestoreGet(projectId, accessToken, "users/" + patientUid, transaction),
        firestoreGet(projectId, accessToken, "doctors/" + doctorId, transaction),
        firestoreGet(projectId, accessToken, "subscriptions/" + doctorId, transaction),
        firestoreGet(projectId, accessToken, appointmentPath, transaction),
        firestoreGet(projectId, accessToken, legacyPath, transaction),
        firestoreGet(projectId, accessToken, queuePath, transaction)
      ]);

      if (!user || str(user, "accountType") !== "patient") {
        return json({ status: "FORBIDDEN", error: "patient_account_required" }, 403);
      }
      if (!doctor || field(doctor, "approved")?.booleanValue !== true) {
        return json({ status: "DOCTOR_UNAVAILABLE", error: "doctor_not_approved" }, 409);
      }
      const expiresAt = field(subscription, "expiresAt")?.timestampValue;
      if (!subscription || str(subscription, "status") !== "ACTIVE" ||
          !expiresAt || Date.parse(expiresAt) <= Date.now()) {
        return json({ status: "DOCTOR_UNAVAILABLE", error: "doctor_subscription_inactive" }, 409);
      }

      const legacyStatus = str(legacy, "status");
      if (legacy && ["PENDING", "CONFIRMED"].includes(legacyStatus)) {
        return json({
          status: "LEGACY_EXIST",
          number: numeric(legacy, "number"),
          time: str(legacy, "time")
        });
      }

      if (appointment) {
        const status = str(appointment, "status");
        if (status === "CONFIRMED") {
          return json({ status: "EXIST", number: numeric(appointment, "number"), time: str(appointment, "time") });
        }
        if (status === "PENDING") {
          return json({ status: "PENDING_EXIST", number: numeric(appointment, "number"), time: str(appointment, "time") });
        }
        if (status !== "CANCELLED") return json({ status: "BLOCKED" }, 409);
      }

      const last = queue ? numeric(queue, "last") : 0;
      const current = queue ? numeric(queue, "current") : 0;
      const capacityValue = numeric(doctor, "dailyCapacity");
      const dailyCapacity = capacityValue > 0 ? Math.min(500, capacityValue) : 30;
      if (last >= dailyCapacity) return json({ status: "CAPACITY" });

      const startHourValue = field(doctor, "startHour");
      const endHourValue = field(doctor, "endHour");
      const stepValue = field(doctor, "slotMinutes");
      const startHour = startHourValue ? Math.max(0, Math.min(23, numeric(doctor, "startHour"))) : 9;
      const endHour = endHourValue ? Math.max(startHour + 1, Math.min(24, numeric(doctor, "endHour"))) : 17;
      const step = stepValue ? Math.max(10, Math.min(180, numeric(doctor, "slotMinutes"))) : 30;
      const number = last + 1;
      const scheduled = startHour * 60 + ((number - 1) * step);
      const ahead = Math.max(0, number - 1 - current);
      const slot = isToday ? Math.max(scheduled, nowMinutes + ahead * step) : scheduled;
      if (slot + step > endHour * 60) return json({ status: "FULL" });

      const createdAt = new Date();
      const appointmentFields = {
        patientId: patientUid,
        patientName: beneficiaryName || str(user, "name") || "المريض",
        patientPhone: str(user, "phone"),
        doctorId,
        doctorName: str(doctor, "name"),
        specialty: str(doctor, "specialty"),
        clinicName: str(doctor, "clinicName"),
        address: str(doctor, "address"),
        city: str(doctor, "city"),
        date,
        slot,
        time: timeLabel(slot),
        number,
        status: "PENDING",
        note,
        visitType,
        createdAt
      };
      if (beneficiaryRelation) appointmentFields.beneficiaryRelation = beneficiaryRelation;

      const queueFields = {
        doctorId,
        date,
        last: number,
        current,
        lastAppointmentId: appointmentId
      };

      const queueWrite = {
        update: { name: documentName(projectId, queuePath), fields: firestoreFields(queueFields) },
        updateMask: { fieldPaths: Object.keys(queueFields) },
        currentDocument: queue ? { updateTime: queue.updateTime } : { exists: false }
      };
      const appointmentWrite = {
        update: { name: documentName(projectId, appointmentPath), fields: firestoreFields(appointmentFields) },
        updateMask: { fieldPaths: Object.keys(appointmentFields) },
        currentDocument: appointment ? { updateTime: appointment.updateTime } : { exists: false }
      };

      await firestoreCommit(projectId, accessToken, transaction, [queueWrite, appointmentWrite]);
      committed = true;
      try {
        await notifyUser(
          projectId, accessToken, doctorId, "طلب حجز جديد",
          appointmentFields.patientName + " طلب موعداً بتاريخ " + date + " الساعة " +
            appointmentFields.time + " — رقم " + number,
          { type: "appointment", appointmentId, event: "booking_requested" }
        );
      } catch (error) {
        console.error("Booking push notification failed:", error?.message || "unknown");
      }
      return json({ status: "OK", number, time: appointmentFields.time, date });
    } catch (error) {
      if (error?.status === 409 || error?.status === 400) {
        if (attempt < 3) continue;
      }
      console.error("Booking transaction failed:", error?.message || "unknown");
      return json({ status: "FAILED", error: "booking_transaction_failed" }, 503);
    } finally {
      if (!committed) {
        await firestoreRollback(projectId, accessToken, transaction).catch(() => {});
      }
    }
  }
  return json({ status: "RETRY", error: "booking_conflict_retry" }, 409);
}

async function firestoreQuery(projectId, accessToken, collectionId, filters) {
  const response = await fetch(
    "https://firestore.googleapis.com/v1/projects/" + encodeURIComponent(projectId) +
      "/databases/(default)/documents:runQuery",
    {
      method: "POST",
      headers: {
        authorization: "Bearer " + accessToken,
        "content-type": "application/json"
      },
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId }],
          where: filters.length === 1
            ? filters[0]
            : { compositeFilter: { op: "AND", filters } },
          limit: 500
        }
      })
    }
  );
  if (!response.ok) throw new Error("Firestore query failed");
  return (await response.json()).map(row => row.document).filter(Boolean);
}

async function getDeviceTokens(projectId, accessToken, uid) {
  const docs = await firestoreQuery(projectId, accessToken, "fcmTokens", [
    {
      fieldFilter: {
        field: { fieldPath: "uid" },
        op: "EQUAL",
        value: { stringValue: uid }
      }
    }
  ]);
  return docs.map(doc => ({
    name: doc.name,
    token: str(doc, "token")
  })).filter(item => item.token);
}

async function sendFcm(projectId, accessToken, token, title, body, data = {}) {
  const payload = {};
  for (const [key, value] of Object.entries(data)) {
    if (value !== undefined && value !== null) payload[key] = String(value);
  }
  payload.title = title;
  payload.body = body;
  const response = await fetch(
    "https://fcm.googleapis.com/v1/projects/" + encodeURIComponent(projectId) + "/messages:send",
    {
      method: "POST",
      headers: {
        authorization: "Bearer " + accessToken,
        "content-type": "application/json"
      },
      body: JSON.stringify({
        message: {
          token,
          data: payload,
          android: {
            priority: "HIGH",
            notification: { channel_id: "sehati_general", sound: "default" }
          }
        }
      })
    }
  );
  return response.ok;
}

async function notifyUser(projectId, accessToken, uid, title, body, data) {
  if (!uid) return { sent: 0, failed: 0 };
  const tokens = await getDeviceTokens(projectId, accessToken, uid);
  if (!tokens.length) return { sent: 0, failed: 0 };
  const results = await Promise.all(tokens.map(item =>
    sendFcm(projectId, accessToken, item.token, title, body, data)
  ));
  return {
    sent: results.filter(Boolean).length,
    failed: results.filter(ok => !ok).length
  };
}

async function sendChatNotification(projectId, accessToken, senderUid, input) {
  const convId = typeof input.convId === "string" ? input.convId : "";
  const messageId = typeof input.messageId === "string" ? input.messageId : "";
  if (!/^[A-Za-z0-9_-]{3,180}$/.test(convId) ||
      !/^[A-Za-z0-9_-]{3,180}$/.test(messageId)) {
    return json({ error: "invalid_reference" }, 400);
  }

  const [conversation, message] = await Promise.all([
    firestoreGet(projectId, accessToken, "conversations/" + encodeURIComponent(convId)),
    firestoreGet(projectId, accessToken, "messages/" + encodeURIComponent(messageId))
  ]);
  if (!conversation || !message) return json({ error: "not_found" }, 404);
  if (str(message, "convId") !== convId || str(message, "senderId") !== senderUid) {
    return json({ error: "forbidden" }, 403);
  }

  const patientUid = str(conversation, "patientId");
  const doctorUid = str(conversation, "doctorId");
  if (!patientUid || !doctorUid ||
      (senderUid !== patientUid && senderUid !== doctorUid)) {
    return json({ error: "forbidden" }, 403);
  }
  const receiverUid = senderUid === patientUid ? doctorUid : patientUid;
  const senderName = senderUid === patientUid
    ? (str(conversation, "patientName") || "المريض")
    : (str(conversation, "doctorName") || "الطبيب");
  const messageText = str(message, "text").slice(0, 1000);
  if (!messageText) return json({ error: "empty_message" }, 400);

  const result = await notifyUser(
    projectId, accessToken, receiverUid,
    "رسالة جديدة من " + senderName, messageText,
    { type: "chat", convId, messageId }
  );
  return json(result);
}

async function sendAppointmentNotification(projectId, accessToken, senderUid, input) {
  const appointmentId = typeof input.appointmentId === "string" ? input.appointmentId : "";
  const event = typeof input.event === "string" ? input.event : "";
  if (!/^[A-Za-z0-9_-]{3,180}$/.test(appointmentId)) {
    return json({ error: "invalid_appointment" }, 400);
  }

  const appointment = await firestoreGet(
    projectId, accessToken, "appointments/" + encodeURIComponent(appointmentId)
  );
  if (!appointment) return json({ error: "not_found" }, 404);

  const patientUid = str(appointment, "patientId");
  const doctorUid = str(appointment, "doctorId");
  const patientName = str(appointment, "patientName") || "المريض";
  const doctorName = str(appointment, "doctorName") || "الطبيب";
  const status = str(appointment, "status");
  const date = str(appointment, "date");
  const time = str(appointment, "time");
  const number = numeric(appointment, "number");
  if (!patientUid || !doctorUid) return json({ error: "invalid_appointment" }, 400);

  let receiverUid = "";
  let title = "";
  let body = "";
  if (event === "booking_requested") {
    if (senderUid !== patientUid || status !== "PENDING") return json({ error: "forbidden" }, 403);
    receiverUid = doctorUid;
    title = "طلب حجز جديد";
    body = patientName + " طلب موعداً بتاريخ " + date + " الساعة " + time + " — رقم " + number;
  } else if (event === "booking_created") {
    if (senderUid !== patientUid || status !== "CONFIRMED") return json({ error: "forbidden" }, 403);
    receiverUid = doctorUid;
    title = "حجز جديد";
    body = patientName + " حجز موعداً بتاريخ " + date + " الساعة " + time + " — رقم " + number;
  } else if (event === "booking_approved") {
    if (senderUid !== doctorUid || status !== "CONFIRMED") return json({ error: "forbidden" }, 403);
    receiverUid = patientUid;
    title = "تم تأكيد حجزك";
    body = doctorName + " وافق على موعدك بتاريخ " + date + " الساعة " + time + " — رقمك " + number;
  } else if (event === "appointment_cancelled") {
    if (status !== "CANCELLED" || (senderUid !== doctorUid && senderUid !== patientUid)) {
      return json({ error: "forbidden" }, 403);
    }
    receiverUid = senderUid === doctorUid ? patientUid : doctorUid;
    title = "تم إلغاء الموعد";
    body = senderUid === doctorUid
      ? "ألغى " + doctorName + " موعدك بتاريخ " + date + " الساعة " + time
      : "ألغى " + patientName + " الموعد بتاريخ " + date + " الساعة " + time;
  } else if (event === "appointment_done") {
    if (senderUid !== doctorUid || status !== "DONE") return json({ error: "forbidden" }, 403);
    receiverUid = patientUid;
    title = "اكتملت زيارتك";
    body = "أنهى " + doctorName + " زيارتك. نتمنى لك دوام الصحة.";
  } else if (event === "followup_scheduled") {
    const followUpDate = str(appointment, "followUpDate");
    const followUpTime = str(appointment, "followUpTime");
    if (senderUid !== doctorUid || !/^\d{4}-\d{2}-\d{2}$/.test(followUpDate) ||
        !/^([01]\d|2[0-3]):[0-5]\d$/.test(followUpTime)) {
      return json({ error: "forbidden" }, 403);
    }
    receiverUid = patientUid;
    title = "تم تحديد موعد متابعة";
    body = doctorName + " حدّد لك موعد متابعة بتاريخ " + followUpDate + " الساعة " + followUpTime;
  } else {
    return json({ error: "invalid_event" }, 400);
  }

  const result = await notifyUser(
    projectId, accessToken, receiverUid, title, body,
    { type: "appointment", appointmentId, event }
  );
  return json(result);
}

async function sendPrescriptionNotification(projectId, accessToken, senderUid, input) {
  const prescriptionId = typeof input.prescriptionId === "string" ? input.prescriptionId : "";
  if (!/^[A-Za-z0-9_-]{3,180}$/.test(prescriptionId)) {
    return json({ error: "invalid_prescription" }, 400);
  }
  const prescription = await firestoreGet(
    projectId, accessToken, "prescriptions/" + encodeURIComponent(prescriptionId)
  );
  if (!prescription) return json({ error: "not_found" }, 404);

  const doctorUid = str(prescription, "doctorId");
  const patientUid = str(prescription, "patientId");
  if (!doctorUid || !patientUid || senderUid !== doctorUid) {
    return json({ error: "forbidden" }, 403);
  }
  const kind = str(prescription, "kind");
  const doctorName = str(prescription, "doctorName") || "طبيبك";
  const title = kind === "lab" ? "طلب تحاليل جديد" : "وصفة طبية جديدة";
  const body = "أرسل " + doctorName + " مستنداً طبياً جديداً. افتح وصفاتك الطبية للاطلاع عليه.";
  const result = await notifyUser(
    projectId, accessToken, patientUid, title, body,
    { type: "prescription", prescriptionId }
  );
  return json(result);
}

async function sendQueueNotifications(projectId, accessToken, senderUid, input) {
  const date = typeof input.date === "string" ? input.date : "";
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return json({ error: "invalid_date" }, 400);

  const queueId = senderUid + "_" + date;
  const queue = await firestoreGet(projectId, accessToken, "queues/" + encodeURIComponent(queueId));
  if (!queue || str(queue, "doctorId") !== senderUid || str(queue, "date") !== date) {
    return json({ error: "forbidden" }, 403);
  }
  const current = numeric(queue, "current");
  const docs = await firestoreQuery(projectId, accessToken, "appointments", [
    { fieldFilter: { field: { fieldPath: "doctorId" }, op: "EQUAL", value: { stringValue: senderUid } } },
    { fieldFilter: { field: { fieldPath: "date" }, op: "EQUAL", value: { stringValue: date } } }
  ]);

  const upcoming = docs
    .map(doc => ({
      id: doc.name.split("/").pop(),
      patientUid: str(doc, "patientId"),
      number: numeric(doc, "number"),
      status: str(doc, "status"),
      doctorName: str(doc, "doctorName") || "الطبيب"
    }))
    .filter(item =>
      item.status === "CONFIRMED" && item.patientUid &&
      item.number >= current && item.number <= current + 3
    );

  const sends = await Promise.all(upcoming.map(item => {
    const offset = item.number - current;
    const title = offset === 0 ? "حان دورك" : "دورك قريب";
    const body = offset === 0
      ? "تفضّل بالدخول إلى " + item.doctorName
      : offset === 1
        ? "أنت التالي عند " + item.doctorName
        : "بقي " + (offset - 1) + " قبلك عند " + item.doctorName;
    return notifyUser(
      projectId, accessToken, item.patientUid, title, body,
      { type: "queue", appointmentId: item.id, date, doctorId: senderUid, queueNumber: item.number }
    );
  }));
  return json({
    current,
    patientsNotified: sends.length,
    sent: sends.reduce((sum, item) => sum + item.sent, 0),
    failed: sends.reduce((sum, item) => sum + item.failed, 0)
  });
}

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (request.method !== "POST" ||
        !["/send", "/notify-appointment", "/notify-prescription", "/notify-queue", "/book-appointment"].includes(path)) {
      return json({ error: "not_found" }, 404);
    }
    const authorization = request.headers.get("authorization") || "";
    const match = authorization.match(/^Bearer\s+(.+)$/i);
    if (!match) return json({ error: "unauthorized" }, 401);

    let input;
    try { input = await request.json(); }
    catch { return json({ error: "invalid_json" }, 400); }

    try {
      const senderUid = await verifyFirebaseUser(match[1], env);
      if (!senderUid) return json({ error: "unauthorized" }, 401);
      const projectId = env.FIREBASE_PROJECT_ID;
      const accessToken = await googleAccessToken(env);

      if (path === "/book-appointment") return await bookAppointment(projectId, accessToken, senderUid, input);
      if (path === "/send") return await sendChatNotification(projectId, accessToken, senderUid, input);
      if (path === "/notify-appointment") return await sendAppointmentNotification(projectId, accessToken, senderUid, input);
      if (path === "/notify-prescription") return await sendPrescriptionNotification(projectId, accessToken, senderUid, input);
      return await sendQueueNotifications(projectId, accessToken, senderUid, input);
    } catch (error) {
      console.error("Push relay error:", error?.message || "unknown");
      return json({ error: "push_delivery_failed" }, 500);
    }
  }
};