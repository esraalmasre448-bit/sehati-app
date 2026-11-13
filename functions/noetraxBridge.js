const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const { createRemoteJWKSet, jwtVerify } = require("jose");

const NOETRAX_PROJECT_ID = "noetrax";
const NOETRAX_ALLOWED_UIDS = defineSecret("NOETRAX_ALLOWED_UIDS");
const firebaseKeys = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
);

async function authenticateNoetrax(req) {
  const header = req.get("authorization") || "";
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    const error = new Error("Missing bearer token");
    error.status = 401;
    throw error;
  }

  let payload;
  try {
    ({ payload } = await jwtVerify(match[1], firebaseKeys, {
      issuer: `https://securetoken.google.com/${NOETRAX_PROJECT_ID}`,
      audience: NOETRAX_PROJECT_ID,
      algorithms: ["RS256"]
    }));
  } catch (_) {
    const error = new Error("Invalid NOETRAX Firebase ID token");
    error.status = 401;
    throw error;
  }

  if (typeof payload.sub !== "string" || payload.sub.length < 1 || payload.sub.length > 128) {
    const error = new Error("Invalid account identity");
    error.status = 401;
    throw error;
  }

  const allowed = NOETRAX_ALLOWED_UIDS.value()
    .split(",")
    .map((uid) => uid.trim())
    .filter(Boolean);

  if (!allowed.includes(payload.sub)) {
    const error = new Error("NOETRAX account is not authorized for the Sehati bridge");
    error.status = 403;
    throw error;
  }

  return payload.sub;
}

async function enforceRateLimit(uid) {
  const db = getFirestore();
  const ref = db.collection("_noetraxBridgeRateLimits").doc(uid);
  const now = Date.now();

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const data = snap.exists ? snap.data() : {};
    const windowStart = data.windowStart?.toMillis?.() || 0;
    const count = windowStart > now - 60_000 ? (data.count || 0) : 0;

    if (count >= 30) {
      const error = new Error("Rate limit exceeded");
      error.status = 429;
      throw error;
    }

    tx.set(ref, {
      windowStart: Timestamp.fromMillis(count ? windowStart : now),
      count: count + 1,
      updatedAt: Timestamp.fromMillis(now)
    });
  });
}

exports.noetraxSehatiBridge = onRequest({
  region: "europe-west1",
  secrets: [NOETRAX_ALLOWED_UIDS],
  maxInstances: 3,
  cors: false
}, async (req, res) => {
  res.set("Cache-Control", "no-store");

  if (req.method !== "GET" || req.path !== "/v1/status") {
    res.status(404).json({ error: "not_found" });
    return;
  }

  try {
    const uid = await authenticateNoetrax(req);
    await enforceRateLimit(uid);

    // This first endpoint deliberately exposes no patient, doctor, booking,
    // prescription, or other health data. Data routes require a separate,
    // explicit ownership model and audit review before being added.
    res.status(200).json({
      connected: true,
      service: "sehati",
      bridgeVersion: 1,
      capabilities: ["status"]
    });
  } catch (error) {
    const status = Number.isInteger(error.status) ? error.status : 500;
    if (status >= 500) console.error("NOETRAX bridge error", error.message);
    res.status(status).json({
      error: status === 401 ? "unauthorized"
        : status === 403 ? "not_authorized"
        : status === 429 ? "rate_limited"
        : "internal_error"
    });
  }
});
