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
const ADMIN_UID = "admin_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

function validUserPayload(uid, username = "SteveCraft") {
  const now = new Date();
  return {
    userId: uid,
    username,
    isPublicProfile: true,
    packsCount: 0,
    createdAt: now,
    updatedAt: now,
  };
}

function validPackPayload(authorId, authorName = "SteveCraft") {
  const now = new Date();
  return {
    title: "Emerald PvP 16x",
    description: "Sharp swords and smooth FPS",
    imageUrl: "https://example.com/pack.png",
    downloadUrl: "https://example.com/download.zip",
    authorId,
    authorName,
    downloadsCount: 0,
    likesCount: 0,
    visibility: "public",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user: cannot read or write users or packs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("packs").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("users").where("isPublicProfile", "==", true).get());
  await assertFails(unauthDb.collection("packs").doc("p1").set(validPackPayload(ALICE_UID)));
});

test("User profile: owner can create valid profile, non-owner cannot spoof or delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID))
  );

  // Shadow field / privilege escalation fails
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      ...validUserPayload(BOB_UID),
      isAdmin: true,
    })
  );

  // Cross-user delete fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).delete());
});

test("ResourcePack: cannot publish without valid downloadUrl or imageUrl", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("packs").doc("bad_pack").set({
      ...validPackPayload(ALICE_UID),
      downloadUrl: "not-a-url",
    })
  );
  await assertFails(
    aliceDb.collection("packs").doc("bad_pack2").set({
      ...validPackPayload(ALICE_UID),
      imageUrl: "",
    })
  );
});

test("ResourcePack: owner can create, any signed-in user can increment download, only owner or admin can delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_1").set(validPackPayload(ALICE_UID))
  );

  // Public query succeeds
  await assertSucceeds(
    bobDb.collection("packs").where("visibility", "==", "public").get()
  );

  // Download increment by +1 succeeds
  await assertSucceeds(
    bobDb.collection("packs").doc("pack_1").update({
      downloadsCount: 1,
      updatedAt: new Date(),
    })
  );

  // Arbitrary jump (+5) fails
  await assertFails(
    bobDb.collection("packs").doc("pack_1").update({
      downloadsCount: 6,
      updatedAt: new Date(),
    })
  );

  // Non-owner cannot delete
  await assertFails(bobDb.collection("packs").doc("pack_1").delete());

  // Bootstrapped admin email (with email_verified) can delete any pack
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "ibrahimkhatta914@gmail.com",
    email_verified: true,
  }).firestore();
  await assertSucceeds(adminDb.collection("packs").doc("pack_1").delete());
});

test("Email spoofing test: unverified admin email is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_2").set(validPackPayload(ALICE_UID))
  );

  const spoofAdminDb = testEnv.authenticatedContext(BOB_UID, {
    email: "ibrahimkhatta914@gmail.com",
    email_verified: false,
  }).firestore();
  await assertFails(spoofAdminDb.collection("packs").doc("pack_2").delete());
});
