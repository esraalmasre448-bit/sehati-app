import { readFileSync } from "node:fs";
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails
} from "@firebase/rules-unit-testing";
import {
  doc,
  setDoc,
  updateDoc,
  getDoc,
  runTransaction,
  serverTimestamp,
  Timestamp
} from "firebase/firestore";

const [host, portText] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080").split(":");
const env = await initializeTestEnvironment({
  projectId: "demo-sehati",
  firestore: {
    host,
    port: Number(portText),
    rules: readFileSync("firestore.rules", "utf8")
  }
});

const date = "2099-01-01";
const patientId = "patient-1";
const doctorId = "doctor-1";
const appointmentId = `${doctorId}_${date}_${patientId}`;
const conversationId = `${doctorId}_${patientId}`;

try {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "users", patientId), { accountType: "patient", name: "Patient" });
    await setDoc(doc(db, "users", doctorId), { accountType: "doctor", name: "Doctor" });
    await setDoc(doc(db, "users", "outsider"), { accountType: "patient", name: "Outsider" });
    await setDoc(doc(db, "doctors", doctorId), { uid: doctorId, approved: true });
    await setDoc(doc(db, "subscriptions", doctorId), {
      status: "ACTIVE",
      expiresAt: Timestamp.fromDate(new Date(Date.now() + 30 * 86400000))
    });
    await setDoc(doc(db, "config", "admin"), { uid: "admin-1" });
  });

  const patient = env.authenticatedContext(patientId).firestore();
  const doctor = env.authenticatedContext(doctorId).firestore();
  const outsider = env.authenticatedContext("outsider").firestore();
  const admin = env.authenticatedContext("admin-1").firestore();

  const apptRef = doc(patient, "appointments", appointmentId);
  const queueRef = doc(patient, "queues", `${doctorId}_${date}`);

  // A patient creates the appointment and queue atomically.
  await assertSucceeds(runTransaction(patient, async (tx) => {
    const oldAppointment = await tx.get(apptRef);
    const oldQueue = await tx.get(queueRef);
    if (oldAppointment.exists() || oldQueue.exists()) throw new Error("Unexpected seeded booking");
    tx.set(queueRef, {
      doctorId,
      date,
      last: 1,
      current: 0
    });
    tx.set(apptRef, {
      patientId,
      patientName: "Patient",
      patientPhone: "",
      doctorId,
      doctorName: "Doctor",
      specialty: "General",
      clinicName: "",
      address: "",
      city: "Test",
      date,
      slot: 540,
      time: "09:00",
      number: 1,
      status: "PENDING",
      note: "",
      createdAt: serverTimestamp()
    });
  }));

  // Role boundaries and appointment visibility.
  await assertFails(setDoc(doc(doctor, "appointments", `${doctorId}_2099-01-02_${doctorId}`), {
    patientId: doctorId, patientName: "Doctor", patientPhone: "",
    doctorId, doctorName: "Doctor", specialty: "General", clinicName: "",
    address: "", city: "Test", date: "2099-01-02", slot: 540,
    time: "09:00", number: 1, status: "PENDING", note: "",
    createdAt: serverTimestamp()
  }));
  await assertFails(getDoc(doc(outsider, "appointments", appointmentId)));
  await assertSucceeds(getDoc(doc(doctor, "appointments", appointmentId)));
  await assertSucceeds(getDoc(doc(admin, "appointments", appointmentId)));

  // Patients cannot alter the doctor's current queue position.
  await assertFails(updateDoc(queueRef, { current: 1 }));
  await assertFails(updateDoc(queueRef, { last: 2 }));

  // The approved, subscribed doctor may confirm the request and advance the queue.
  await assertSucceeds(updateDoc(doc(doctor, "appointments", appointmentId), { status: "CONFIRMED" }));
  await assertSucceeds(updateDoc(doc(doctor, "queues", `${doctorId}_${date}`), { current: 1 }));

  // A conversation may be created for an active appointment; outsiders stay excluded.
  await assertSucceeds(setDoc(doc(patient, "conversations", conversationId), {
    doctorId,
    patientId,
    doctorName: "Doctor",
    patientName: "Patient",
    appointmentId,
    lastMessage: "",
    lastAt: serverTimestamp(),
    createdAt: serverTimestamp()
  }));
  await assertSucceeds(setDoc(doc(patient, "messages", "message-1"), {
    convId: conversationId,
    senderId: patientId,
    text: "Hello",
    createdAt: serverTimestamp()
  }));
  await assertFails(getDoc(doc(outsider, "messages", "message-1")));

  // Prescription access is restricted to the patient and treating doctor.
  await assertSucceeds(setDoc(doc(doctor, "prescriptions", "rx-1"), {
    doctorId,
    doctorName: "Doctor",
    patientId,
    patientName: "Patient",
    appointmentId,
    kind: "rx",
    text: "Test prescription",
    createdAt: serverTimestamp()
  }));
  await assertSucceeds(getDoc(doc(patient, "prescriptions", "rx-1")));
  await assertFails(getDoc(doc(outsider, "prescriptions", "rx-1")));
  await assertFails(getDoc(doc(admin, "prescriptions", "rx-1")));
  await assertFails(updateDoc(doc(patient, "prescriptions", "rx-1"), { text: "tamper" }));

  // A doctor cannot change the UID bound to their profile.
  await assertFails(updateDoc(doc(doctor, "doctors", doctorId), { uid: "spoofed-user" }));

  // Patient rebooking after cancellation must update appointment and queue in one transaction.
  await assertSucceeds(updateDoc(doc(doctor, "appointments", appointmentId), { status: "CANCELLED" }));
  await assertSucceeds(runTransaction(patient, async (tx) => {
    const a = await tx.get(apptRef);
    const q = await tx.get(queueRef);
    if (!a.exists() || !q.exists() || a.data().status !== "CANCELLED") throw new Error("Expected cancelled appointment");
    const next = (q.data().last || 0) + 1;
    tx.update(queueRef, { last: next });
    tx.set(apptRef, {
      patientId,
      patientName: "Patient",
      patientPhone: "",
      doctorId,
      doctorName: "Doctor",
      specialty: "General",
      clinicName: "",
      address: "",
      city: "Test",
      date,
      slot: 570,
      time: "09:30",
      number: next,
      status: "PENDING",
      note: "Rebooked",
      createdAt: serverTimestamp()
    });
  }));

  console.log("PASS: patient booking, doctor approval, queue roles, privacy, prescriptions, UID binding, and rebooking.");
} finally {
  await env.cleanup();
}
