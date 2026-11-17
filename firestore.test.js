const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_101";
const BOB_UID = "bob_uid_202";
const ADMIN_UID = "admin_uid_999";
const ADMIN_EMAIL = "superservice.kolkata@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("broadcasts").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").set({
      userId: ALICE_UID,
      title: "Unauth Task",
      description: "",
      taskType: "task_assignment",
      status: "pending",
      dueDateLabel: "Today",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("User profile: guest signup allowed, role self-escalation blocked, email spoofing blocked", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();
  const now = new Date(Date.now() - 2000);

  // Guest profile creation succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Guest",
      email: "alice@example.com",
      portalRole: "guest",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Self-assigning admin role on create fails
  await assertFails(
    aliceDb.collection("users").doc("alice_other").set({
      userId: "alice_other",
      displayName: "Hacker",
      email: "alice@example.com",
      portalRole: "admin",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Escalating own portalRole on update fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      portalRole: "admin",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Ghost field update fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      isVerified: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Unverified admin email spoof cannot act as admin
  const spoofDb = testEnv
    .authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false })
    .firestore();
  await assertFails(spoofDb.collection("users").get());
  await assertFails(
    spoofDb.collection("broadcasts").doc("b_spoof").set({
      authorId: "spoof_uid",
      authorEmail: ADMIN_EMAIL,
      title: "Spoof",
      body: "Spoofed broadcast",
      targetAudience: "All Three",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("User profile PII isolation: non-owner cannot read another user's profile", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true })
    .firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      portalRole: "guest",
      phoneNumber: "+919800000000",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").get());
});

test("Tasks: owner CRUD, terminal state locking, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "AC Maintenance Check",
      description: "Inspect unit B2",
      taskType: "task_assignment",
      status: "pending",
      dueDateLabel: "Today, 4:00 PM",
      contactPhone: "+919876543210",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject @DocumentId 'id' leakage in create payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_2").set({
      id: "task_2",
      userId: ALICE_UID,
      title: "Bad payload",
      description: "",
      taskType: "task_assignment",
      status: "pending",
      dueDateLabel: "Today",
      createdAt: now,
      updatedAt: now,
    })
  );

  // List own tasks succeeds (even when empty for Bob)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("tasks").get()
  );

  // Cross-user read/write blocked
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );

  // Owner marks task completed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      status: "completed",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: non-admin cannot mutate completed task
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      title: "Modified after completion",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Messages & Notifications: Kotlin-aligned payloads, isRead partial update, and delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      senderName: "Alice",
      recipientName: "Dispatch Desk",
      channel: "message",
      body: "Arrived at client site",
      isUrgent: true,
      recipientPhone: "+919876543210",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").set({
      userId: ALICE_UID,
      title: "Shift Check-Out Reminder",
      body: "Complete checklist before 6 PM",
      type: "shift_checkout",
      isRead: false,
      isBroadcast: false,
      targetAudience: "Personal",
      relatedTaskId: "task_1",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Mark read succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").update({
      isRead: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Changing title on notification update blocked
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").update({
      title: "Tampered",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Cross-user delete blocked
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").delete()
  );

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").delete()
  );
});

test("Broadcasts & BroadcastStates: only verified admin can publish broadcasts; users track own read/dismiss state", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();
  const now = new Date(Date.now() - 3000);

  // Non-admin cannot create broadcast
  await assertFails(
    aliceDb.collection("broadcasts").doc("b_1").set({
      authorId: ALICE_UID,
      authorEmail: "alice@example.com",
      title: "Fake Alert",
      body: "Should fail",
      targetAudience: "All Three",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Verified admin creates broadcast
  await assertSucceeds(
    adminDb.collection("broadcasts").doc("b_1").set({
      authorId: ADMIN_UID,
      authorEmail: ADMIN_EMAIL,
      title: "Monsoon Dispatch Update",
      body: "All field teams please confirm safety gear.",
      targetAudience: "All Three",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Authenticated user queries broadcasts matching audience
  await assertSucceeds(
    aliceDb
      .collection("broadcasts")
      .where("targetAudience", "in", ["Guests", "All Three"])
      .get()
  );

  // Alice marks broadcast as read/dismissed in her own broadcast_states
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("broadcast_states").doc("b_1").set({
      userId: ALICE_UID,
      broadcastId: "b_1",
      isRead: true,
      isDismissed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("broadcast_states").doc("b_1").update({
      isDismissed: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
