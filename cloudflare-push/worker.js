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

async function firestoreGet(projectId, accessToken, path) {
  const response = await fetch(
    "https://firestore.googleapis.com/v1/projects/" + encodeURIComponent(projectId) +
      "/databases/(default)/documents/" + path,
    { headers: { authorization: "Bearer " + accessToken } }
  );
  if (response.status === 404) return null;
  if (!response.ok) throw new Error("Firestore read failed");
  return response.json();
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
        !["/send", "/notify-appointment", "/notify-prescription", "/notify-queue"].includes(path)) {
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