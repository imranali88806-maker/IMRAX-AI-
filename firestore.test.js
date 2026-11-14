const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

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

test("Unauthenticated user cannot read or write user profiles or chats", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("chats").get());
});

test("Authenticated user can create and read own profile, cannot read/write another user's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      preferredLanguage: "en",
      darkTheme: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Shadow update with ghost field or invalid language fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastTimestamp();

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      preferredLanguage: "fr",
      darkTheme: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      preferredLanguage: "en",
      darkTheme: true,
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user can create chat thread and messages, cross-user access denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastTimestamp();

  const chatRef = aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1");
  await assertSucceeds(
    chatRef.set({
      userId: ALICE_UID,
      title: "Assamese Greeting",
      mode: "chat",
      language: "as",
      lastMessagePreview: "নমস্কাৰ",
      messageCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  const msgRef = chatRef.collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      chatId: "chat_1",
      role: "user",
      content: "নমস্কাৰ IMRAX AI",
      mode: "chat",
      attachmentType: "none",
      sources: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    msgRef.update({
      content: "Edited prompt",
      updatedAt: pastTimestamp(),
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("chats").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").collection("messages").get()
  );
});

test("Orphan message without existing parent chat fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastTimestamp();
  const orphanMsgRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("chats")
    .doc("nonexistent_chat")
    .collection("messages")
    .doc("msg_1");

  await assertFails(
    orphanMsgRef.set({
      userId: ALICE_UID,
      chatId: "nonexistent_chat",
      role: "user",
      content: "Hello",
      mode: "chat",
      attachmentType: "none",
      createdAt: now,
      updatedAt: now,
    })
  );
});
