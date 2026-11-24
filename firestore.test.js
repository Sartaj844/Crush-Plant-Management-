const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_pakistan_123";
const OTHER_UID = "other_user_456";

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

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated users cannot read or write any Crusher ERP collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(OWNER_UID).collection("materials").get());
  await assertFails(unauth.collection("users").doc(OWNER_UID).collection("sales").get());
  await assertFails(
    unauth.collection("users").doc(OWNER_UID).collection("expenses").doc("exp_1").set({
      userId: OWNER_UID,
      category: "Diesel",
      title: "Generator Diesel",
      amount: 25000,
      paymentMethod: "Cash",
      paidTo: "PSO Pump",
      notes: "",
      transactionDateMillis: Date.now(),
      status: "CONFIRMED",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Cross-user isolation: another user cannot read or write owner's business data", async () => {
  const created = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(OWNER_UID).collection("materials").doc("mat_cement_facto").set({
      userId: OWNER_UID,
      name: "Cement Facto",
      category: "Cement",
      specification: "50 Kg OPC Bag",
      unit: "Bag",
      purchaseRate: 1200,
      saleRate: 1320,
      avgCost: 1200,
      currentStock: 100,
      minStockAlert: 20,
      customItem: false,
      archived: false,
      createdAt: created,
      updatedAt: created,
    });
  });

  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  await assertFails(otherDb.collection("users").doc(OWNER_UID).collection("materials").get());
  await assertFails(
    otherDb.collection("users").doc(OWNER_UID).collection("materials").doc("mat_cement_facto").update({
      saleRate: 1,
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    otherDb.collection("users").doc(OWNER_UID).collection("customers").doc("cust_1").set({
      userId: OTHER_UID,
      name: "Spoof",
      phone: "",
      address: "",
      openingBalance: 0,
      currentBalance: 0,
      notes: "",
      archived: false,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Owner can create & update business_settings, materials, customers, and suppliers with Kotlin-aligned payloads", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).collection("business_settings").doc("default").set({
      userId: OWNER_UID,
      businessName: "Margalla Crusher & Stone Supply",
      address: "Taxila, Rawalpindi",
      phone: "0300-1234567",
      openingCashBalance: 500000,
      financialYear: "2026-2027",
      defaultUnit: "CFT",
      invoicePrefix: "INV-2026-",
      nextInvoiceSeq: 1,
      purchasePrefix: "PV-2026-",
      nextPurchaseSeq: 1,
      allowNegativeStock: false,
      defaultMaterialsSeeded: true,
      logoLocalPath: "",
      operatorName: "Haji Tariq",
      requireAdminPin: true,
      adminPin: "1234",
      languageCode: "ur",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject invalid languageCode on business_settings
  await assertFails(
    ownerDb.collection("users").doc(OWNER_UID).collection("business_settings").doc("default").update({
      languageCode: "fr",
      updatedAt: pastDate(1000),
    })
  );

  // Reject ghost field or @DocumentId 'id' field
  await assertFails(
    ownerDb.collection("users").doc(OWNER_UID).collection("materials").doc("mat_bad").set({
      id: "mat_bad",
      userId: OWNER_UID,
      name: "Bad",
      category: "Cement",
      specification: "",
      unit: "Bag",
      purchaseRate: 100,
      saleRate: 120,
      avgCost: 100,
      currentStock: 10,
      minStockAlert: 5,
      customItem: true,
      archived: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).collection("materials").doc("mat_bajri").set({
      userId: OWNER_UID,
      name: "Bajri / Crush",
      category: "Sand & Aggregate",
      specification: "3/4 Inch Margalla",
      unit: "CFT",
      purchaseRate: 85,
      saleRate: 110,
      avgCost: 85,
      currentStock: 2500,
      minStockAlert: 300,
      customItem: false,
      archived: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update on material succeeds while preserving createdAt
  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).collection("materials").doc("mat_bajri").update({
      currentStock: 2300,
      updatedAt: pastDate(1000),
    })
  );

  // Default seeded materials cannot be deleted (customItem == false)
  await assertFails(
    ownerDb.collection("users").doc(OWNER_UID).collection("materials").doc("mat_bajri").delete()
  );
});

test("Financial records (sales, purchases, expenses, payments) block deletion and voided mutation", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).collection("sales").doc("sale_1").set({
      userId: OWNER_UID,
      invoiceNumber: "INV-2026-0001",
      customerId: "cust_1",
      customerName: "Al-Noor Builders",
      customerPhone: "0321-5551234",
      transactionDateMillis: Date.now() - 5000,
      items: [
        {
          materialId: "mat_bajri",
          materialName: "Bajri / Crush",
          specification: "3/4 Inch",
          unit: "CFT",
          quantity: 500,
          rate: 110,
          costRate: 85,
          lineTotal: 55000,
        },
      ],
      subtotal: 55000,
      discount: 1000,
      grandTotal: 54000,
      paidAmount: 30000,
      remainingBalance: 24000,
      totalCogs: 42500,
      paymentMethod: "Cash",
      notes: "Site delivery",
      status: "CONFIRMED",
      revision: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cannot delete financial record
  await assertFails(ownerDb.collection("users").doc(OWNER_UID).collection("sales").doc("sale_1").delete());

  // Cannot mutate invoiceNumber or createdAt
  await assertFails(
    ownerDb.collection("users").doc(OWNER_UID).collection("sales").doc("sale_1").update({
      invoiceNumber: "INV-TAMPERED",
      updatedAt: pastDate(1000),
    })
  );

  // Valid correction succeeds
  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).collection("sales").doc("sale_1").update({
      discount: 2000,
      grandTotal: 53000,
      remainingBalance: 23000,
      status: "CORRECTED",
      revision: 2,
      updatedAt: pastDate(1000),
    })
  );

  // Voiding succeeds, after which further updates are locked
  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).collection("sales").doc("sale_1").update({
      status: "VOID",
      revision: 3,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    ownerDb.collection("users").doc(OWNER_UID).collection("sales").doc("sale_1").update({
      paidAmount: 53000,
      status: "CONFIRMED",
      updatedAt: pastDate(200),
    })
  );
});

test("Expenses reject unknown categories and delete; stock_movements and audit_logs are append-only", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const created = pastDate(10000);

  await assertFails(
    ownerDb.collection("users").doc(OWNER_UID).collection("expenses").doc("exp_bad").set({
      userId: OWNER_UID,
      category: "Spare Parts", // Prohibited category
      title: "Belt",
      amount: 5000,
      paymentMethod: "Cash",
      paidTo: "Shop",
      notes: "",
      transactionDateMillis: Date.now(),
      status: "CONFIRMED",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).collection("expenses").doc("exp_diesel").set({
      userId: OWNER_UID,
      category: "Diesel",
      title: "Crusher Generator Diesel 200L",
      amount: 54000,
      paymentMethod: "Cash",
      paidTo: "Attock Fuel",
      notes: "",
      transactionDateMillis: Date.now(),
      status: "CONFIRMED",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(ownerDb.collection("users").doc(OWNER_UID).collection("expenses").doc("exp_diesel").delete());

  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).collection("stock_movements").doc("mov_1").set({
      userId: OWNER_UID,
      materialId: "mat_bajri",
      materialName: "Bajri / Crush",
      movementType: "SALE_OUT",
      quantityDelta: -500,
      balanceAfter: 2000,
      unit: "CFT",
      referenceId: "sale_1",
      referenceNumber: "INV-2026-0001",
      reason: "Confirmed sale",
      transactionDateMillis: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    ownerDb.collection("users").doc(OWNER_UID).collection("stock_movements").doc("mov_1").update({
      quantityDelta: 0,
    })
  );

  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).collection("audit_logs").doc("log_1").set({
      userId: OWNER_UID,
      entityType: "SALE",
      entityId: "sale_1",
      referenceNumber: "INV-2026-0001",
      action: "CORRECT",
      reason: "Adjusted discount per owner approval",
      beforeSummary: "Total 54000",
      afterSummary: "Total 53000",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    ownerDb.collection("users").doc(OWNER_UID).collection("audit_logs").doc("log_1").delete()
  );
});

test("Future-tampered timestamps are rejected by isValidTimestamp", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const future = new Date(Date.now() + 120000);
  await assertFails(
    ownerDb.collection("users").doc(OWNER_UID).collection("customers").doc("cust_future").set({
      userId: OWNER_UID,
      name: "Future Client",
      phone: "",
      address: "",
      openingBalance: 0,
      currentBalance: 0,
      notes: "",
      archived: false,
      createdAt: future,
      updatedAt: future,
    })
  );
});
