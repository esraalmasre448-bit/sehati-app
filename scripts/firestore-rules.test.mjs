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
  deleteDoc,
  deleteField,
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

const patientId = "patient1";
const doctorId = "doctor1";
const outsiderId = "outsider1";
const adminId = "admin1";
const unapprovedDoctorId = "doctor2";
const expiredDoctorId = "doctor3";
const date = "2099-01-01";
const appointmentId = `${doctorId}_${date}_${patientId}`;
const conversationId = `${doctorId}_${patientId}`;

const patient = env.authenticatedContext(patientId).firestore();
const doctor = env.authenticatedContext(doctorId).firestore();
const outsider = env.authenticatedContext(outsiderId).firestore();
const admin = env.authenticatedContext(adminId).firestore();
const unapprovedDoctor = env.authenticatedContext(unapprovedDoctorId).firestore();
const expiredDoctor = env.authenticatedContext(expiredDoctorId).firestore();

try {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [uid, accountType, name] of [
      [patientId, "patient", "Patient"],
      [doctorId, "doctor", "Doctor"],
      [outsiderId, "patient", "Outsider"],
      [adminId, "admin", "Admin"],
      [unapprovedDoctorId, "doctor", "Unapproved Doctor"],
      [expiredDoctorId, "doctor", "Expired Doctor"]
    ]) {
      await setDoc(doc(db, "users", uid), { accountType, name });
    }
    await setDoc(doc(db, "doctors", doctorId), {
      uid: doctorId, name: "Doctor", approved: true
    });
    await setDoc(doc(db, "doctors", unapprovedDoctorId), {
      uid: unapprovedDoctorId, name: "Unapproved Doctor", approved: false
    });
    await setDoc(doc(db, "doctors", expiredDoctorId), {
      uid: expiredDoctorId, name: "Expired Doctor", approved: true
    });
    await setDoc(doc(db, "subscriptions", doctorId), {
      uid: doctorId,
      status: "ACTIVE",
      trial: true,
      paymentMethod: "Trial",
      startedAt: Timestamp.now(),
      expiresAt: Timestamp.fromDate(new Date(Date.now() + 30 * 86400000))
    });
    await setDoc(doc(db, "config", "admin"), { uid: adminId });
    // Seed historical conversations for expired and unapproved doctors.
    for (const did of [expiredDoctorId, unapprovedDoctorId]) {
      const aid = `${did}_${date}_${patientId}`;
      await setDoc(doc(db, "appointments", aid), {
        patientId, doctorId: did, status: "CONFIRMED"
      });
      await setDoc(doc(db, "conversations", `${did}_${patientId}`), {
        doctorId: did, patientId, doctorName: did, patientName: "Patient",
        appointmentId: aid, lastMessage: "History", lastSenderId: patientId,
        lastAt: Timestamp.now(), createdAt: Timestamp.now()
      });
    }
  });

  // Patient favorites are private, limited to approved doctors, and removable by their owner.
  const favoriteRef = doc(patient, "favorites", patientId, "doctors", doctorId);
  await assertSucceeds(setDoc(favoriteRef, {
    doctorId,
    savedAt: serverTimestamp()
  }));
  await assertSucceeds(getDoc(favoriteRef));
  await assertFails(getDoc(doc(outsider, "favorites", patientId, "doctors", doctorId)));
  await assertFails(setDoc(doc(patient, "favorites", outsiderId, "doctors", doctorId), {
    doctorId,
    savedAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(patient, "favorites", patientId, "doctors", unapprovedDoctorId), {
    doctorId: unapprovedDoctorId,
    savedAt: serverTimestamp()
  }));
  await assertSucceeds(deleteDoc(favoriteRef));

  const apptRef = doc(patient, "appointments", appointmentId);
  const queueRef = doc(patient, "queues", `${doctorId}_${date}`);

  // Patient booking and queue creation must succeed atomically.
  await assertSucceeds(runTransaction(patient, async (tx) => {
    const oldAppointment = await tx.get(apptRef);
    const oldQueue = await tx.get(queueRef);
    if (oldAppointment.exists() || oldQueue.exists()) {
      throw new Error("Unexpected seeded booking");
    }
    tx.set(queueRef, { doctorId, date, last: 1, current: 0 });
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

  // Only the patient and treating doctor can read the appointment.
  await assertSucceeds(getDoc(apptRef));
  await assertSucceeds(getDoc(doc(doctor, "appointments", appointmentId)));
  await assertFails(getDoc(doc(outsider, "appointments", appointmentId)));
  await assertFails(getDoc(doc(admin, "appointments", appointmentId)));

  // A patient cannot modify queue position or advance the doctor's counter.
  await assertFails(updateDoc(queueRef, { current: 1 }));
  await assertFails(updateDoc(queueRef, { last: 2 }));

  // Only the approved, subscribed doctor can approve and advance the queue.
  await assertSucceeds(updateDoc(doc(doctor, "appointments", appointmentId), {
    status: "CONFIRMED"
  }));
  await assertSucceeds(updateDoc(doc(doctor, "queues", `${doctorId}_${date}`), {
    current: 1
  }));

  // Private two-way conversation and messages.
  await assertSucceeds(setDoc(doc(patient, "conversations", conversationId), {
    doctorId,
    patientId,
    doctorName: "Doctor",
    patientName: "Patient",
    appointmentId,
    lastMessage: "Hello",
    lastSenderId: patientId,
    lastAt: serverTimestamp(),
    createdAt: serverTimestamp()
  }));
  await assertSucceeds(setDoc(doc(patient, "messages", "message-1"), {
    convId: conversationId,
    senderId: patientId,
    text: "Hello",
    createdAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(doc(doctor, "conversations", conversationId), {
    lastMessage: "Reply",
    lastSenderId: doctorId,
    lastAt: serverTimestamp()
  }));
  await assertSucceeds(setDoc(doc(doctor, "messages", "message-2"), {
    convId: conversationId,
    senderId: doctorId,
    text: "Reply",
    createdAt: serverTimestamp()
  }));
  await assertSucceeds(getDoc(doc(doctor, "messages", "message-1")));
  await assertFails(getDoc(doc(outsider, "messages", "message-1")));

  // Patients retain access to existing conversations when a doctor's subscription expires.
  const expiredConv = `${expiredDoctorId}_${patientId}`;
  await assertSucceeds(setDoc(doc(patient, "messages", "patient-to-expired-doctor"), {
    convId: expiredConv, senderId: patientId, text: "Follow-up", createdAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(doc(patient, "conversations", expiredConv), {
    lastMessage: "Follow-up", lastSenderId: patientId, lastAt: serverTimestamp()
  }));
  // Expired or unapproved doctors cannot send messages or update conversation previews.
  await assertFails(setDoc(doc(expiredDoctor, "messages", "expired-doctor-message"), {
    convId: expiredConv, senderId: expiredDoctorId, text: "Blocked", createdAt: serverTimestamp()
  }));
  await assertFails(updateDoc(doc(expiredDoctor, "conversations", expiredConv), {
    lastMessage: "Blocked", lastSenderId: expiredDoctorId, lastAt: serverTimestamp()
  }));
  const unapprovedConv = `${unapprovedDoctorId}_${patientId}`;
  await assertFails(setDoc(doc(unapprovedDoctor, "messages", "unapproved-doctor-message"), {
    convId: unapprovedConv, senderId: unapprovedDoctorId, text: "Blocked", createdAt: serverTimestamp()
  }));
  await assertFails(updateDoc(doc(unapprovedDoctor, "conversations", unapprovedConv), {
    lastMessage: "Blocked", lastSenderId: unapprovedDoctorId, lastAt: serverTimestamp()
  }));

  // Prescriptions are limited to the treating doctor and patient and cannot be deleted.
  const rxRef = doc(doctor, "prescriptions", "rx-1");
  await assertSucceeds(setDoc(rxRef, {
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
  await assertFails(updateDoc(doc(patient, "prescriptions", "rx-1"), {
    text: "tamper"
  }));
  await assertFails(updateDoc(rxRef, { text: "doctor edit" }));
  await assertSucceeds(updateDoc(rxRef, {
    status: "VOIDED",
    voidedAt: serverTimestamp(),
    voidedBy: doctorId
  }));
  await assertFails(deleteDoc(doc(patient, "prescriptions", "rx-1")));
  await assertFails(deleteDoc(rxRef));
  // Account deletion anonymizes patient identity but retains the medical content.
  await assertSucceeds(updateDoc(doc(patient, "prescriptions", "rx-1"), {
    patientId: deleteField(),
    patientName: "حساب محذوف",
    appointmentId: deleteField()
  }));
  await assertSucceeds(getDoc(doc(doctor, "prescriptions", "rx-1")));
  await assertFails(getDoc(doc(patient, "prescriptions", "rx-1")));

  // An unapproved doctor cannot expose their profile or approve themselves.
  await assertFails(getDoc(doc(patient, "doctors", unapprovedDoctorId)));
  await assertSucceeds(getDoc(doc(unapprovedDoctor, "doctors", unapprovedDoctorId)));
  await assertFails(updateDoc(doc(unapprovedDoctor, "doctors", unapprovedDoctorId), {
    approved: true
  }));
  await assertFails(updateDoc(doc(unapprovedDoctor, "doctors", unapprovedDoctorId), {
    available: true,
    availableUpdatedAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(doc(admin, "doctors", unapprovedDoctorId), {
    approved: true,
    approvedBy: adminId,
    approvedAt: serverTimestamp()
  }));
  await assertSucceeds(getDoc(doc(patient, "doctors", unapprovedDoctorId)));
  // Availability requires both admin approval and an active subscription.
  await assertSucceeds(updateDoc(doc(doctor, "doctors", doctorId), {
    available: true,
    availableUpdatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(doc(expiredDoctor, "doctors", expiredDoctorId), {
    available: true,
    availableUpdatedAt: serverTimestamp()
  }));

  // Device tokens are private to their owning account.
  const tokenRef = doc(patient, "fcmTokens", `${patientId}_device-1`);
  await assertSucceeds(setDoc(tokenRef, {
    uid: patientId,
    token: "fake-fcm-token-for-emulator",
    updatedAt: serverTimestamp()
  }));
  await assertSucceeds(getDoc(tokenRef));
  await assertFails(getDoc(doc(outsider, "fcmTokens", `${patientId}_device-1`)));
  await assertFails(setDoc(doc(patient, "fcmTokens", "spoofed-token"), {
    uid: outsiderId,
    token: "fake",
    updatedAt: serverTimestamp()
  }));
  await assertSucceeds(deleteDoc(tokenRef));

  // A cancelled visit can be rebooked with the next queue number.
  await assertSucceeds(updateDoc(doc(doctor, "appointments", appointmentId), {
    status: "CANCELLED"
  }));
  await assertSucceeds(runTransaction(patient, async (tx) => {
    const appointment = await tx.get(apptRef);
    const queue = await tx.get(queueRef);
    if (!appointment.exists() || !queue.exists() ||
        appointment.data().status !== "CANCELLED") {
      throw new Error("Expected a cancelled appointment");
    }
    const next = (queue.data().last || 0) + 1;
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

  // Account deletion first anonymizes appointment details but retains the visit.
  await assertSucceeds(updateDoc(apptRef, {
    patientName: "حساب محذوف",
    patientPhone: "",
    note: ""
  }));

  // Account deletion is self-service, but the configured admin account is protected.
  await assertSucceeds(deleteDoc(doc(patient, "users", patientId)));
  await assertFails(deleteDoc(doc(admin, "users", adminId)));

  console.log("PASS: favorites privacy, booking, approval, queue permissions, private chat, prescriptions, doctor approval, FCM token privacy, and rebooking.");
} finally {
  await env.cleanup();
}
