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
const appointmentId = `${doctorId}_${date}_${patientId}_CONSULTATION`;
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

  // Family member records are private to the owning patient and can be removed by that owner.
  const familyMemberRef = doc(patient, "familyProfiles", patientId, "members", "mother-1");
  await assertSucceeds(setDoc(familyMemberRef, {
    name: "Family Member",
    relation: "Mother",
    createdAt: serverTimestamp()
  }));
  await assertSucceeds(getDoc(familyMemberRef));
  await assertFails(getDoc(doc(outsider, "familyProfiles", patientId, "members", "mother-1")));
  await assertFails(getDoc(doc(doctor, "familyProfiles", patientId, "members", "mother-1")));
  await assertSucceeds(updateDoc(familyMemberRef, { relation: "Parent" }));
  await assertSucceeds(deleteDoc(familyMemberRef));

  // Health profile data is private to the patient; doctors and other patients cannot read it.
  const healthProfileRef = doc(patient, "healthProfiles", patientId);
  await assertSucceeds(setDoc(healthProfileRef, {
    uid: patientId,
    bloodType: "O+",
    allergies: "Pollen",
    conditions: "None",
    emergencyContact: "Family contact",
    notes: "Test profile",
    updatedAt: serverTimestamp()
  }));
  await assertSucceeds(getDoc(healthProfileRef));
  await assertFails(getDoc(doc(outsider, "healthProfiles", patientId)));
  await assertFails(getDoc(doc(doctor, "healthProfiles", patientId)));
  await assertFails(updateDoc(doc(doctor, "healthProfiles", patientId), {
    notes: "Unauthorized", updatedAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(healthProfileRef, {
    notes: "Updated by owner", updatedAt: serverTimestamp()
  }));
  await assertFails(deleteDoc(healthProfileRef));

  // A doctor may read the health profile only while the patient has an active explicit grant.
  const grantRef = doc(patient, "healthAccessGrants", patientId, "doctors", doctorId);
  await assertSucceeds(setDoc(grantRef, {
    patientUid: patientId,
    doctorUid: doctorId,
    expiresAt: Timestamp.fromDate(new Date(Date.now() + 30 * 86400000)),
    grantedAt: serverTimestamp(),
    revoked: false
  }));
  await assertSucceeds(getDoc(doc(doctor, "healthProfiles", patientId)));
  await assertFails(getDoc(doc(outsider, "healthProfiles", patientId)));
  await assertSucceeds(deleteDoc(grantRef));
  await assertFails(getDoc(doc(doctor, "healthProfiles", patientId)));

  // Approved doctors can manage their own branches; active branch locations are public to signed-in users.
  const branchRef = doc(doctor, "doctorBranches", doctorId, "items", "clinic-1");
  await assertSucceeds(setDoc(branchRef, {
    name: "Main Clinic", city: "Test City", address: "Test Street",
    active: true, updatedAt: serverTimestamp()
  }));
  await assertSucceeds(getDoc(doc(patient, "doctorBranches", doctorId, "items", "clinic-1")));
  await assertFails(setDoc(doc(patient, "doctorBranches", doctorId, "items", "bad-branch"), {
    name: "Fake", city: "Test", address: "", active: true, updatedAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(branchRef, { active: false, updatedAt: serverTimestamp() }));
  await assertFails(getDoc(doc(patient, "doctorBranches", doctorId, "items", "clinic-1")));
  await assertSucceeds(deleteDoc(branchRef));

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

  // Medication plans are private to the patient, mutable only by their owner, and retained.
  const medicationRef = doc(patient, "medicationReminders", patientId, "items", "med-1");
  await assertSucceeds(setDoc(medicationRef, {
    medicine: "Medicine A",
    dose: "One tablet",
    time: "08:00",
    active: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  }));
  await assertSucceeds(getDoc(medicationRef));
  await assertFails(getDoc(doc(outsider, "medicationReminders", patientId, "items", "med-1")));
  await assertFails(setDoc(doc(doctor, "medicationReminders", doctorId, "items", "med-1"), {
    medicine: "Medicine A", dose: "One tablet", time: "08:00", active: true,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(doc(outsider, "medicationReminders", patientId, "items", "med-1"), {
    active: false, updatedAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(medicationRef, {
    active: false, updatedAt: serverTimestamp()
  }));
  await assertFails(deleteDoc(medicationRef));

  const apptRef = doc(patient, "appointments", appointmentId);
  const queueRef = doc(patient, "queues", `${doctorId}_${date}`);

  // Direct client writes are blocked; the trusted Worker owns booking transactions.
  await assertFails(setDoc(queueRef, { doctorId, date, last: 1, current: 0, lastAppointmentId: appointmentId }));
  await assertFails(setDoc(apptRef, {
    patientId, patientName: "Patient", patientPhone: "", doctorId,
    doctorName: "Doctor", specialty: "General", clinicName: "", address: "",
    city: "Test", date, slot: 540, time: "09:00", number: 1,
    status: "PENDING", note: "", visitType: "CONSULTATION",
    beneficiaryRelation: "Mother", createdAt: serverTimestamp()
  }));
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "queues", `${doctorId}_${date}`), {
      doctorId, date, last: 1, current: 0, lastAppointmentId: appointmentId
    });
    await setDoc(doc(db, "appointments", appointmentId), {
      patientId, patientName: "Patient", patientPhone: "", doctorId,
      doctorName: "Doctor", specialty: "General", clinicName: "", address: "",
      city: "Test", date, slot: 540, time: "09:00", number: 1,
      status: "PENDING", note: "", visitType: "CONSULTATION",
      beneficiaryRelation: "Mother", createdAt: Timestamp.now()
    });
  });

  // Only the treating doctor may schedule a follow-up on a confirmed or completed visit.
  console.log("TEST_APPOINTMENT_CONFIRM");
  await assertSucceeds(updateDoc(doc(doctor, "appointments", appointmentId), { status: "CONFIRMED" }));
  console.log("TEST_PATIENT_CANNOT_SET_FOLLOWUP");
  await assertFails(updateDoc(doc(patient, "appointments", appointmentId), {
    followUpDate: "2099-01-02", followUpTime: "10:00", followUpSetAt: serverTimestamp()
  }));
  console.log("TEST_DOCTOR_SET_FOLLOWUP");
  await assertSucceeds(updateDoc(doc(doctor, "appointments", appointmentId), {
    followUpDate: "2099-01-02",
    followUpTime: "10:00"
  }));
  // Payment analytics use a separate completed visit so later queue tests can keep using the first appointment.
  const paymentDate = "2099-01-04";
  const paymentAppointmentId = `${doctorId}_${paymentDate}_${patientId}_CONSULTATION`;
  const paymentAppointmentRef = doc(patient, "appointments", paymentAppointmentId);
  const paymentQueueRef = doc(patient, "queues", `${doctorId}_${paymentDate}`);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "queues", `${doctorId}_${paymentDate}`), {
      doctorId, date: paymentDate, last: 1, current: 0,
      lastAppointmentId: paymentAppointmentId
    });
    await setDoc(doc(db, "appointments", paymentAppointmentId), {
      patientId, patientName: "Patient", patientPhone: "",
      doctorId, doctorName: "Doctor", specialty: "General",
      clinicName: "", address: "", city: "Test",
      date: paymentDate, slot: 540, time: "09:00",
      number: 1, status: "PENDING", note: "",
      visitType: "CONSULTATION", createdAt: Timestamp.now()
    });
  });
  await assertSucceeds(updateDoc(doc(doctor, "appointments", paymentAppointmentId), { status: "CONFIRMED" }));
  await assertSucceeds(updateDoc(doc(doctor, "appointments", paymentAppointmentId), { status: "DONE" }));
  console.log("TEST_PATIENT_CANNOT_RECORD_PAYMENT");
  await assertFails(updateDoc(doc(patient, "appointments", paymentAppointmentId), {
    paidAmount: 125, paidCurrency: "SYP", paidRecordedAt: serverTimestamp()
  }));
  console.log("TEST_DOCTOR_RECORD_PAYMENT");
  await assertSucceeds(updateDoc(doc(doctor, "appointments", paymentAppointmentId), {
    paidAmount: 125, paidCurrency: "SYP", paidRecordedAt: serverTimestamp()
  }));
  console.log("TEST_PAYMENT_IMMUTABLE");
  await assertFails(updateDoc(doc(doctor, "appointments", paymentAppointmentId), {
    paidAmount: 150, paidCurrency: "SYP", paidRecordedAt: serverTimestamp()
  }));

  // A direct client cannot create the second visit type or mutate the shared queue.
  const followUpId = `${doctorId}_${date}_${patientId}_FOLLOW_UP`;
  const followUpRef = doc(patient, "appointments", followUpId);
  await assertFails(setDoc(followUpRef, {
    patientId, patientName: "Patient", patientPhone: "", doctorId,
    doctorName: "Doctor", specialty: "General", clinicName: "", address: "",
    city: "Test", date, slot: 570, time: "09:30", number: 2,
    status: "PENDING", note: "", visitType: "FOLLOW_UP", createdAt: serverTimestamp()
  }));
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await updateDoc(doc(db, "queues", `${doctorId}_${date}`), {
      last: 2, lastAppointmentId: followUpId
    });
    await setDoc(doc(db, "appointments", followUpId), {
      patientId, patientName: "Patient", patientPhone: "", doctorId,
      doctorName: "Doctor", specialty: "General", clinicName: "", address: "",
      city: "Test", date, slot: 570, time: "09:30", number: 2,
      status: "PENDING", note: "", visitType: "FOLLOW_UP", createdAt: Timestamp.now()
    });
  });
  await assertSucceeds(getDoc(followUpRef));
  await assertSucceeds(getDoc(doc(doctor, "appointments", followUpId)));
  await assertFails(getDoc(doc(outsider, "appointments", followUpId)));

  // A first-ever typed booking must also create its queue atomically.
  const secondDate = "2099-01-02";
  const firstTypedId = `${doctorId}_${secondDate}_${patientId}_CONSULTATION`;
  const firstTypedRef = doc(patient, "appointments", firstTypedId);
  const firstTypedQueue = doc(patient, "queues", `${doctorId}_${secondDate}`);
  await assertFails(setDoc(firstTypedQueue, {
    doctorId, date: secondDate, last: 1, current: 0, lastAppointmentId: firstTypedId
  }));
  await assertFails(setDoc(firstTypedRef, {
    patientId, patientName: "Patient", patientPhone: "", doctorId,
    doctorName: "Doctor", specialty: "General", clinicName: "", address: "",
    city: "Test", date: secondDate, slot: 540, time: "09:00", number: 1,
    status: "PENDING", note: "", visitType: "CONSULTATION", createdAt: serverTimestamp()
  }));

  // Legacy clients can still create/read the old appointment and queue shape.
  const legacyDate = "2099-01-03";
  const legacyId = `${doctorId}_${legacyDate}_${patientId}`;
  const legacyRef = doc(patient, "appointments", legacyId);
  const legacyQueue = doc(patient, "queues", `${doctorId}_${legacyDate}`);
  await assertFails(setDoc(legacyQueue, { doctorId, date: legacyDate, last: 1, current: 0 }));
  await assertFails(setDoc(legacyRef, {
    patientId, patientName: "Patient", patientPhone: "", doctorId,
    doctorName: "Doctor", specialty: "General", clinicName: "", address: "",
    city: "Test", date: legacyDate, slot: 540, time: "09:00", number: 1,
    status: "PENDING", note: "", createdAt: serverTimestamp()
  }));
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "queues", `${doctorId}_${legacyDate}`), {
      doctorId, date: legacyDate, last: 1, current: 0
    });
    await setDoc(doc(db, "appointments", legacyId), {
      patientId, patientName: "Patient", patientPhone: "", doctorId,
      doctorName: "Doctor", specialty: "General", clinicName: "", address: "",
      city: "Test", date: legacyDate, slot: 540, time: "09:00", number: 1,
      status: "PENDING", note: "", createdAt: Timestamp.now()
    });
  });
  await assertSucceeds(getDoc(legacyRef));

  // The legacy appointment remains readable; all additional booking writes go through the Worker.
  const additionalTypedId = `${doctorId}_${legacyDate}_${patientId}_FOLLOW_UP`;
  const additionalTypedRef = doc(patient, "appointments", additionalTypedId);
  await assertFails(setDoc(additionalTypedRef, {
    patientId, patientName: "Patient", patientPhone: "", doctorId,
    doctorName: "Doctor", specialty: "General", clinicName: "", address: "",
    city: "Test", date: legacyDate, slot: 570, time: "09:30", number: 2,
    status: "PENDING", note: "", visitType: "FOLLOW_UP", createdAt: serverTimestamp()
  }));
  await assertFails(updateDoc(legacyQueue, { last: 2, lastAppointmentId: additionalTypedId }));

  // Only the patient and treating doctor can read the appointment.
  await assertSucceeds(getDoc(apptRef));
  await assertSucceeds(getDoc(doc(doctor, "appointments", appointmentId)));
  await assertFails(getDoc(doc(outsider, "appointments", appointmentId)));
  await assertFails(getDoc(doc(admin, "appointments", appointmentId)));

  // A patient cannot modify queue position or advance the doctor's counter.
  await assertFails(updateDoc(queueRef, { current: 1 }));
  await assertFails(updateDoc(queueRef, { last: 2 }));

  // A doctor cannot delete their own trial subscription to recreate another trial.
  await assertFails(deleteDoc(doc(doctor, "subscriptions", doctorId)));
  await assertSucceeds(getDoc(doc(doctor, "subscriptions", doctorId)));

  // Inspect the exact state used by the doctor queue-advance rule before exercising it.
  const doctorUserCheck = await getDoc(doc(doctor, "users", doctorId));
  const doctorProfileCheck = await getDoc(doc(doctor, "doctors", doctorId));
  const doctorSubCheck = await getDoc(doc(doctor, "subscriptions", doctorId));
  const queueBeforeAdvance = await getDoc(doc(doctor, "queues", `${doctorId}_${date}`));
  console.log("QUEUE_ADVANCE_DIAGNOSTIC", JSON.stringify({ user: doctorUserCheck.data(), profile: doctorProfileCheck.data(), subscription: doctorSubCheck.data(), queue: queueBeforeAdvance.data() }));
  // The appointment was already confirmed above; the approved doctor can now advance the queue.
  await assertSucceeds(updateDoc(doc(doctor, "queues", `${doctorId}_${date}`), { current: 1 }));

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
  await assertFails(updateDoc(rxRef, {
    status: "VOIDED",
    voidedAt: serverTimestamp(),
    voidedBy: doctorId
  }));
  await assertSucceeds(updateDoc(rxRef, {
    status: "VOIDED",
    voidedAt: serverTimestamp(),
    voidedBy: doctorId,
    voidReason: "Duplicate entry"
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

  // Cancellation remains patient-accessible; direct rebooking is denied and must use the Worker.
  await assertSucceeds(updateDoc(doc(doctor, "appointments", appointmentId), {
    status: "CANCELLED"
  }));
  await assertFails(updateDoc(apptRef, {
    status: "PENDING", number: 3, time: "10:00", slot: 600, createdAt: serverTimestamp()
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

  console.log("PASS: favorites privacy, medication plan privacy and retention, booking, approval, queue permissions, private chat, prescriptions, doctor approval, FCM token privacy, and rebooking.");
} finally {
  await env.cleanup();
}
