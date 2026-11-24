package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.BackupManager
import com.example.domain.AccountingEngine
import com.example.domain.BusinessSettings
import com.example.domain.Customer
import com.example.domain.Expense
import com.example.domain.ExpenseCategories
import com.example.domain.LineItem
import com.example.domain.Material
import com.example.domain.MaterialCategories
import com.example.domain.PartyPayment
import com.example.domain.Purchase
import com.example.domain.Sale
import com.example.domain.StockMovement
import com.example.domain.Supplier
import com.example.export.CsvExporter
import com.example.ui.CrusherWorkspaceState
import com.example.ui.SyncInfo
import com.example.ui.auth.AuthScreen
import com.example.ui.components.AdminPinDialog
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsFor
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.CrusherErpTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h915dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun initFirebaseForTest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:100000000000:android:0000000000000000")
                    .setApiKey("AIzaSyTestPlaceholderKeyForRobolectric")
                    .setProjectId("test-crusher-erp")
                    .build()
            )
        }
    }

    @Test
    fun `app name matches Crusher ERP`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Crusher ERP", context.getString(R.string.app_name))
    }

    @Test
    fun `backup manager exports and parses complete crusher data JSON round-trip`() {
        val settings = BusinessSettings(
            businessName = "Margalla Stone Crushers",
            address = "Taxila Bypass, Rawalpindi",
            phone = "0300-5551234",
            openingCashBalance = 250_000.0,
            nextInvoiceSeq = 12L,
            nextPurchaseSeq = 5L,
            allowNegativeStock = true,
            operatorName = "Haji Tariq",
            requireAdminPin = true,
            adminPin = "2580",
            languageCode = "ur"
        )
        val materials = listOf(
            Material(
                id = "mat_bajri",
                name = "Bajri / Crush",
                category = MaterialCategories.SAND_AGGREGATE,
                unit = "CFT",
                purchaseRate = 75.0,
                saleRate = 110.0,
                avgCost = 72.0,
                currentStock = 4000.0,
                minStockAlert = 1000.0
            )
        )
        val customers = listOf(Customer(id = "c1", name = "Khan Builders", phone = "0321-1112223", currentBalance = 45_000.0))
        val suppliers = listOf(Supplier(id = "sup1", name = "Quarry Co", phone = "0333-4445556", currentBalance = 12_000.0))
        val sales = listOf(
            Sale(
                id = "sale_1",
                invoiceNumber = "INV-2026-0001",
                customerId = "c1",
                customerName = "Khan Builders",
                transactionDateMillis = 1_760_000_000_000L,
                items = listOf(LineItem("mat_bajri", "Bajri / Crush", "3/4", "CFT", 500.0, 110.0, 72.0, 55000.0)),
                subtotal = 55000.0,
                grandTotal = 55000.0,
                paidAmount = 10000.0,
                remainingBalance = 45000.0,
                totalCogs = 36000.0
            )
        )
        val purchases = listOf(
            Purchase(
                id = "pur_1",
                voucherNumber = "PV-2026-0001",
                supplierId = "sup1",
                supplierName = "Quarry Co",
                transactionDateMillis = 1_760_000_000_000L,
                items = listOf(LineItem("mat_bajri", "Bajri / Crush", "3/4", "CFT", 1000.0, 75.0, 75.0, 75000.0)),
                subtotal = 75000.0,
                grandTotal = 75000.0,
                paidAmount = 63000.0,
                remainingBalance = 12000.0
            )
        )
        val payments = listOf(PartyPayment(id = "pay_1", partyType = "CUSTOMER", partyId = "c1", amount = 5000.0))
        val expenses = listOf(Expense(id = "exp_1", category = ExpenseCategories.DIESEL, title = "HSD Diesel", amount = 22_000.0))
        val movements = listOf(StockMovement(id = "mov_1", materialId = "mat_bajri", quantityDelta = -500.0, balanceAfter = 4000.0))

        val json = BackupManager.exportToJson(
            settings = settings,
            materials = materials,
            customers = customers,
            suppliers = suppliers,
            sales = sales,
            purchases = purchases,
            payments = payments,
            expenses = expenses,
            stockMovements = movements
        )
        val restored = BackupManager.parseFromJson(json, "uid_abc")

        assertEquals("Margalla Stone Crushers", restored.settings?.businessName)
        assertEquals(12L, restored.settings?.nextInvoiceSeq)
        assertEquals("Haji Tariq", restored.settings?.operatorName)
        assertEquals(true, restored.settings?.requireAdminPin)
        assertEquals("2580", restored.settings?.adminPin)
        assertEquals("ur", restored.settings?.languageCode)
        assertEquals(1, restored.materials.size)
        assertEquals("Bajri / Crush", restored.materials.first().name)
        assertEquals(1, restored.customers.size)
        assertEquals(1, restored.suppliers.size)
        assertEquals(1, restored.sales.size)
        assertEquals(55000.0, restored.sales.first().grandTotal, 0.01)
        assertEquals(1, restored.purchases.size)
        assertEquals(1, restored.payments.size)
        assertEquals(1, restored.expenses.size)
        assertEquals(1, restored.stockMovements.size)
    }

    @Test
    fun `csv exporter writes UTF-8 BOM and properly escapes commas and quotes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = CsvExporter.generateCsvFile(
            context = context,
            fileNamePrefix = "sales_report",
            headers = listOf("Invoice", "Customer", "Amount"),
            rows = listOf(listOf("INV-001", "Khan, Sons \"Builders\"", "PKR 55,000"))
        )
        assertTrue(file.exists())
        val content = file.readText(Charsets.UTF_8)
        assertTrue(content.startsWith("\uFEFFInvoice,Customer,Amount"))
        assertTrue(content.contains("\"Khan, Sons \"\"Builders\"\"\""))
    }

    @Test
    fun `auth screen renders crusher plant branding and google sign-in CTA`() {
        composeTestRule.setContent {
            CrusherErpTheme {
                AuthScreen(onAuthSuccess = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Crusher Plant Management").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `dashboard screen renders business header and live PKR KPI cards`() {
        val now = System.currentTimeMillis()
        val settings = BusinessSettings(businessName = "Taxila Stone Crusher Plant", address = "GT Road, Taxila")
        val mats = listOf(
            Material(id = "m1", name = "Bajri / Crush", currentStock = 2000.0, minStockAlert = 500.0, avgCost = 70.0)
        )
        val sales = listOf(
            Sale(id = "s1", transactionDateMillis = now, grandTotal = 85_000.0, paidAmount = 50_000.0, totalCogs = 55_000.0)
        )
        val metrics = AccountingEngine.computeDashboardMetrics(
            nowMillis = now,
            settings = settings,
            materials = mats,
            customers = listOf(Customer(id = "c1", name = "Buyer", currentBalance = 35_000.0)),
            suppliers = listOf(Supplier(id = "sup1", name = "Quarry", currentBalance = 18_000.0)),
            sales = sales,
            purchases = emptyList(),
            payments = emptyList(),
            expenses = listOf(Expense(id = "e1", category = ExpenseCategories.DIESEL, amount = 10_000.0, transactionDateMillis = now))
        )
        val state = CrusherWorkspaceState(
            settings = settings,
            materials = mats,
            sales = sales,
            metrics = metrics,
            syncInfo = SyncInfo(isOnline = true, hasPendingWrites = false, fromCache = false)
        )

        composeTestRule.setContent {
            CrusherErpTheme {
                DashboardScreen(
                    state = state,
                    onNewSale = {},
                    onNewPurchase = {},
                    onNewExpense = {},
                    onOpenStock = {},
                    onOpenAccounts = {},
                    onOpenReports = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Taxila Stone Crusher Plant").assertIsDisplayed()
        composeTestRule.onNodeWithText("TODAY'S SALES").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("PKR 85,000").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Monthly Sales").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stock Valuation").assertIsDisplayed()
    }

    @Test
    fun `auth screen language toggle switches to Urdu copy`() {
        composeTestRule.setContent {
            var lang by remember { mutableStateOf(AppLanguage.EN) }
            CompositionLocalProvider(
                LocalAppLanguage provides lang,
                LocalAppStrings provides stringsFor(lang)
            ) {
                CrusherErpTheme {
                    AuthScreen(onAuthSuccess = {}, onLanguageChange = { lang = it })
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Crusher Plant Management").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_language_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("کریشر پلانٹ مینجمنٹ سسٹم").assertIsDisplayed()
    }

    @Test
    fun `admin pin dialog rejects wrong pin and unlocks with exact pin`() {
        var authorized = false
        composeTestRule.setContent {
            CrusherErpTheme {
                AdminPinDialog(
                    expectedPin = "4321",
                    onDismiss = {},
                    onAuthorized = { authorized = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_pin_input").performTextInput("1111")
        composeTestRule.onNodeWithTag("admin_pin_confirm").performClick()
        composeTestRule.waitForIdle()
        assertFalse(authorized)

        composeTestRule.onNodeWithText("Incorrect Admin PIN. Please try again.").assertIsDisplayed()

        // Retry with valid pin by recreating dialog state or clearing? Replace not needed, check second composition
    }

    @Test
    fun `admin pin dialog authorizes valid pin`() {
        var authorized = false
        composeTestRule.setContent {
            CrusherErpTheme {
                AdminPinDialog(
                    expectedPin = "4321",
                    onDismiss = {},
                    onAuthorized = { authorized = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_pin_input").performTextInput("4321")
        composeTestRule.onNodeWithTag("admin_pin_confirm").performClick()
        composeTestRule.waitForIdle()
        assertTrue(authorized)
    }
}
