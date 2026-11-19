const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_merchant_1";
const BOB_UID = "bob_merchant_2";

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

function pastTime(msOffset = 5000) {
  return new Date(Date.now() - msOffset);
}

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("products").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sales").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("products").doc("p1").set({
      userId: ALICE_UID,
      name: "Café",
      category: "BEBIDAS",
      priceCents: 4500,
      stock: 10,
      active: true,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );
});

test("StoreProfile: owner create & update succeed; cross-user & ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastTime();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      storeName: "MiTienda",
      ownerEmail: "alice@example.com",
      catalogSeeded: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Update storeName only
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      storeName: "Boutique Sol",
      updatedAt: pastTime(1000),
    })
  );

  // Reject cross-user read or write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      storeName: "Hacked",
      updatedAt: pastTime(500),
    })
  );

  // Reject ghost field on create
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      storeName: "Bob",
      catalogSeeded: true,
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Products: valid Kotlin create, partial stock update, full edit, delete, and security bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastTime(10000);

  // Valid create matching ProductRepository (no 'id' field in body)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .set({
        userId: ALICE_UID,
        name: "Audífonos Estudio",
        description: "Audio inalámbrico cálido",
        category: "ELECTRONICA",
        priceCents: 189900,
        stock: 15,
        sku: "AUD-001",
        imageKey: "headphones",
        active: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Valid list query matching ProductRepository
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").get()
  );

  // Valid stock decrement update (simulating checkout batch)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .update({
        stock: 12,
        updatedAt: pastTime(2000),
      })
  );

  // Valid active toggle update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .update({
        active: false,
        updatedAt: pastTime(1500),
      })
  );

  // Valid full edit update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .update({
        name: "Audífonos Estudio Pro",
        description: "",
        category: "ELECTRONICA",
        priceCents: 199900,
        stock: 20,
        sku: "AUD-002",
        imageKey: "headphones",
        active: true,
        updatedAt: pastTime(1000),
      })
  );

  // Reject negative stock
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .update({
        stock: -1,
        updatedAt: pastTime(500),
      })
  );

  // Reject @DocumentId 'id' inside document body
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_2")
      .set({
        id: "prod_2",
        userId: ALICE_UID,
        name: "Mochila",
        category: "MODA",
        priceCents: 99000,
        stock: 5,
        active: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject invalid category or future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_3")
      .set({
        userId: ALICE_UID,
        name: "Prueba",
        category: "INVALID_CAT",
        priceCents: 500,
        stock: 5,
        active: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_4")
      .set({
        userId: ALICE_UID,
        name: "Future",
        category: "OTROS",
        priceCents: 500,
        stock: 5,
        active: true,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: created,
      })
  );

  // Reject cross-user read, list, update, delete
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("products").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .delete()
  );

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .delete()
  );
});

test("Sales: valid create, terminal state CANCELADA lock, no delete, cross-user denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastTime(10000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sales")
      .doc("sale_1")
      .set({
        userId: ALICE_UID,
        customerName: "María López",
        paymentMethod: "TARJETA",
        status: "COMPLETADA",
        items: [
          {
            productId: "prod_1",
            productName: "Audífonos Estudio",
            quantity: 2,
            unitPriceCents: 189900,
            subtotalCents: 379800,
          },
        ],
        totalCents: 379800,
        totalItems: 2,
        notes: "Envío el mismo día",
        createdAt: created,
        updatedAt: created,
      })
  );

  // List query succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sales").get()
  );

  // Cross-user list fails
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sales").get()
  );

  // Cancel sale (COMPLETADA -> CANCELADA) succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sales")
      .doc("sale_1")
      .update({
        status: "CANCELADA",
        updatedAt: pastTime(1000),
      })
  );

  // Terminal state lock: cannot revert CANCELADA -> COMPLETADA
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sales")
      .doc("sale_1")
      .update({
        status: "COMPLETADA",
        updatedAt: pastTime(500),
      })
  );

  // Cannot delete sales
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sales")
      .doc("sale_1")
      .delete()
  );
});
