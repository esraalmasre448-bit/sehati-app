import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import worker from "./worker.js";

const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const privatePem = privateKey.export({ type: "pkcs8", format: "pem" });
const projectId = "demo-sehati";
const serviceAccount = JSON.stringify({
  client_email: "worker@demo-sehati.iam.gserviceaccount.com",
  private_key: privatePem,
  token_uri: "https://oauth2.googleapis.com/token",
  project_id: projectId
});

const value = v => {
  if (typeof v === "string") return { stringValue: v };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v)
    ? { integerValue: String(v) } : { doubleValue: v };
  if (v instanceof Date) return { timestampValue: v.toISOString() };
  return { nullValue: null };
};
const fields = obj => Object.fromEntries(Object.entries(obj).map(([k, v]) => [k, value(v)]));
const decode = obj => Object.fromEntries(Object.entries(obj || {}).map(([k, v]) => {
  if ("stringValue" in v) return [k, v.stringValue];
  if ("integerValue" in v) return [k, Number(v.integerValue)];
  if ("doubleValue" in v) return [k, v.doubleValue];
  if ("booleanValue" in v) return [k, v.booleanValue];
  if ("timestampValue" in v) return [k, v.timestampValue];
  return [k, null];
}));

function fixture() {
  const now = new Date();
  const state = new Map();
  const put = (path, data) => state.set(path, {
    name: `projects/${projectId}/databases/(default)/documents/${path}`,
    fields: fields(data),
    updateTime: new Date().toISOString()
  });
  put("users/patient1", { accountType: "patient", name: "Patient", phone: "000" });
  put("doctors/doctor1", {
    uid: "doctor1", name: "Doctor", specialty: "General", clinicName: "Clinic",
    city: "Test", address: "Street", approved: true,
    startHour: 9, endHour: 17, slotMinutes: 30, dailyCapacity: 10
  });
  put("subscriptions/doctor1", {
    uid: "doctor1", status: "ACTIVE",
    expiresAt: new Date(now.getTime() + 86400000)
  });
  return state;
}

function installFetchMock(state) {
  globalThis.fetch = async (input, init = {}) => {
    const url = new URL(String(input));
    if (url.hostname === "identitytoolkit.googleapis.com") {
      return Response.json({ users: [{ localId: "patient1" }] });
    }
    if (url.hostname === "oauth2.googleapis.com") {
      return Response.json({ access_token: "test-access-token" });
    }
    if (url.hostname !== "firestore.googleapis.com") {
      throw new Error("Unexpected mocked request: " + url.href);
    }
    if (url.pathname.endsWith("/documents:beginTransaction")) {
      return Response.json({ transaction: "test-transaction" });
    }
    if (url.pathname.endsWith("/documents:runQuery")) {
      return Response.json([]);
    }
    if (url.pathname.endsWith("/documents:commit")) {
      const body = JSON.parse(init.body);
      for (const write of body.writes) {
        const fullName = write.update.name;
        const path = fullName.split("/documents/")[1];
        const previous = state.get(path);
        const condition = write.currentDocument;
        if (condition?.exists === false && previous) {
          return new Response("already exists", { status: 409 });
        }
        if (condition?.updateTime && previous?.updateTime !== condition.updateTime) {
          return new Response("version conflict", { status: 409 });
        }
        state.set(path, {
          name: fullName,
          fields: write.update.fields,
          updateTime: new Date(Date.now() + 1).toISOString()
        });
      }
      return Response.json({ writeResults: [] });
    }
    if (init.method === "POST") {
      throw new Error("Unexpected Firestore POST: " + url.pathname);
    }
    const path = decodeURIComponent(url.pathname.split("/documents/")[1] || "");
    const doc = state.get(path);
    return doc ? Response.json(doc) : new Response("not found", { status: 404 });
  };
}

const env = {
  FIREBASE_SERVICE_ACCOUNT_JSON: serviceAccount,
  FIREBASE_PROJECT_ID: projectId,
  FIREBASE_WEB_API_KEY: "test-web-api-key"
};

async function callBooking(payload) {
  return worker.fetch(new Request("https://sehati-chat-push.test/book-appointment", {
    method: "POST",
    headers: {
      authorization: "Bearer test-firebase-id-token",
      "content-type": "application/json"
    },
    body: JSON.stringify(payload)
  }), env);
}

test("trusted booking endpoint creates consultation and follow-up with consecutive shared queue numbers", async () => {
  const state = fixture();
  installFetchMock(state);
  const base = {
    doctorId: "doctor1",
    date: "2099-01-01",
    note: "",
    beneficiaryName: "",
    beneficiaryRelation: "",
    isToday: false,
    nowMinutes: 0
  };

  const consultation = await callBooking({ ...base, visitType: "CONSULTATION" });
  assert.equal(consultation.status, 200);
  const first = await consultation.json();
  assert.equal(first.status, "OK");
  assert.equal(first.number, 1);
  assert.equal(first.time, "09:00");

  const followUp = await callBooking({ ...base, visitType: "FOLLOW_UP" });
  assert.equal(followUp.status, 200);
  const second = await followUp.json();
  assert.equal(second.status, "OK");
  assert.equal(second.number, 2);
  assert.equal(second.time, "09:30");

  const queue = decode(state.get("queues/doctor1_2099-01-01").fields);
  assert.equal(queue.last, 2);
  assert.equal(queue.lastAppointmentId, "doctor1_2099-01-01_patient1_FOLLOW_UP");
  assert.equal(decode(state.get("appointments/doctor1_2099-01-01_patient1_CONSULTATION").fields).status, "PENDING");
  assert.equal(decode(state.get("appointments/doctor1_2099-01-01_patient1_FOLLOW_UP").fields).status, "PENDING");
});

test("trusted booking endpoint does not duplicate an active visit type", async () => {
  const state = fixture();
  installFetchMock(state);
  const base = {
    doctorId: "doctor1", date: "2099-01-02", visitType: "CONSULTATION",
    note: "", beneficiaryName: "", beneficiaryRelation: "", isToday: false, nowMinutes: 0
  };
  const first = await (await callBooking(base)).json();
  const duplicate = await (await callBooking(base)).json();
  assert.equal(first.status, "OK");
  assert.equal(duplicate.status, "PENDING_EXIST");
  assert.equal(decode(state.get("queues/doctor1_2099-01-02").fields).last, 1);
});

test("booking endpoint rejects missing Firebase authentication", async () => {
  const response = await worker.fetch(new Request("https://sehati-chat-push.test/book-appointment", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: "{}"
  }), env);
  assert.equal(response.status, 401);
});

test("booking endpoint supports a legacy base URL path prefix without bypassing authentication", async () => {
  const response = await worker.fetch(new Request("https://sehati-chat-push.test/send/book-appointment", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: "{}"
  }), env);
  assert.equal(response.status, 401);
});
