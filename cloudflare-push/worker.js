const json = (value, status = 200) =>
  new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
  });

const field = (doc, name) => doc?.fields?.[name];
const str = (doc, name) => field(doc, name)?.stringValue ?? "";
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

async function getDeviceTokens(projectId, accessToken, uid) {
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
          from: [{ collectionId: "fcmTokens" }],
          where: {
            fieldFilter: {
              field: { fieldPath: "uid" },
              op: "EQUAL",
              value: { stringValue: uid }
            }
          }
        }
      })
    }
  );
  if (!response.ok) throw new Error("FCM token lookup failed");
  const rows = await response.json();
  return rows.map(row => row.document).filter(Boolean).map(doc => ({
    name: doc.name,
    token: str(doc, "token")
  })).filter(item => item.token);
}

async function sendFcm(projectId, accessToken, token, title, body, convId, messageId) {
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
          notification: { title, body },
          data: { type: "chat", convId, messageId },
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

export default {
  async fetch(request, env) {
    if (request.method !== "POST" || new URL(request.url).pathname !== "/send") {
      return json({ error: "not_found" }, 404);
    }
    const authorization = request.headers.get("authorization") || "";
    const match = authorization.match(/^Bearer\s+(.+)$/i);
    if (!match) return json({ error: "unauthorized" }, 401);

    let input;
    try { input = await request.json(); }
    catch { return json({ error: "invalid_json" }, 400); }

    const convId = typeof input.convId === "string" ? input.convId : "";
    const messageId = typeof input.messageId === "string" ? input.messageId : "";
    if (!/^[A-Za-z0-9_-]{3,180}$/.test(convId) ||
        !/^[A-Za-z0-9_-]{3,180}$/.test(messageId)) {
      return json({ error: "invalid_reference" }, 400);
    }

    try {
      const senderUid = await verifyFirebaseUser(match[1], env);
      if (!senderUid) return json({ error: "unauthorized" }, 401);

      const projectId = env.FIREBASE_PROJECT_ID;
      const accessToken = await googleAccessToken(env);
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

      const tokens = await getDeviceTokens(projectId, accessToken, receiverUid);
      if (!tokens.length) return json({ sent: 0, reason: "no_registered_devices" });

      const results = await Promise.all(tokens.map(async item => ({
        name: item.name,
        ok: await sendFcm(
          projectId, accessToken, item.token,
          "رسالة جديدة من " + senderName, messageText, convId, messageId
        )
      })));
      return json({
        sent: results.filter(item => item.ok).length,
        failed: results.filter(item => !item.ok).length
      });
    } catch (error) {
      console.error("Push relay error:", error?.message || "unknown");
      return json({ error: "push_delivery_failed" }, 500);
    }
  }
};
