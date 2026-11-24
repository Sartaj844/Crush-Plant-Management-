package com.example

import androidx.compose.ui.unit.LayoutDirection
import com.example.domain.AccountingEngine
import com.example.domain.BusinessSettings
import com.example.domain.Customer
import com.example.domain.DefaultMaterialsCatalog
import com.example.domain.Expense
import com.example.domain.ExpenseCategories
import com.example.domain.LineItem
import com.example.domain.Material
import com.example.domain.MaterialCategories
import com.example.domain.PartyPayment
import com.example.domain.Purchase
import com.example.domain.ReportDateFilter
import com.example.domain.Sale
import com.example.domain.Supplier
import com.example.domain.TxStatuses
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.UrduStrings
import com.example.ui.i18n.stringsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `default Pakistani materials catalog includes all required materials and categories`() {
        val mats = DefaultMaterialsCatalog.buildSeedMaterials("user_1")
        val names = mats.map { it.name }.toSet()

        // Cement
        assertTrue("Cement Facto" in names)
        assertTrue("Cement Fauji" in names)
        assertTrue("Lucky Cement" in names)
        assertTrue("Other Cement" in names)

        // Sand & Aggregate
        assertTrue("Rait Arja" in names)
        assertTrue("Rait Kohala" in names)
        assertTrue("Rait Pindi" in names)
        assertTrue("Rait Local" in names)
        assertTrue("Rait Barakot" in names)
        assertTrue("Khaka" in names)
        assertTrue("Pain" in names)
        assertTrue("Bajri / Crush" in names)
        assertTrue("Other Sand and Aggregate" in names)

        // Blocks
        assertTrue("Block 6 Inch" in names)
        assertTrue("Block 4 Inch" in names)

        // Other
        assertTrue("Saria (Deformed Steel)" in names)
        assertTrue("Other Steel Items" in names)
        assertTrue("Other Construction Materials" in names)

        assertTrue(mats.all { it.category in MaterialCategories.all })
        assertEquals(18, mats.size)
    }

    @Test
    fun `expense categories contain plant operational categories and stay separate from stock`() {
        val required = listOf(
            "Diesel",
            "Labour",
            "Salary",
            "Electricity",
            "Transport",
            "Machine Maintenance",
            "Machine Repairing",
            "Other Expenses"
        )
        assertEquals(required, ExpenseCategories.all)
    }

    @Test
    fun `sale and purchase totals calculate subtotal discount grand total paid balance and cogs`() {
        val items = listOf(
            LineItem(
                materialId = "m1",
                materialName = "Bajri / Crush",
                unit = "CFT",
                quantity = 500.0,
                rate = 110.0,
                costRate = 75.0,
                lineTotal = 55000.0
            ),
            LineItem(
                materialId = "m2",
                materialName = "Rait Arja",
                unit = "CFT",
                quantity = 300.0,
                rate = 60.0,
                costRate = 40.0,
                lineTotal = 18000.0
            )
        )
        val saleTotals = AccountingEngine.calculateSaleTotals(
            items = items,
            discount = 3000.0,
            paidAmount = 40000.0
        )
        assertEquals(73000.0, saleTotals.subtotal, 0.01)
        assertEquals(3000.0, saleTotals.discount, 0.01)
        assertEquals(70000.0, saleTotals.grandTotal, 0.01)
        assertEquals(40000.0, saleTotals.paidAmount, 0.01)
        assertEquals(30000.0, saleTotals.remainingBalance, 0.01)
        assertEquals(49500.0, saleTotals.totalCogs, 0.01)

        val purchaseTotals = AccountingEngine.calculatePurchaseTotals(
            items = items,
            discount = 1000.0,
            paidAmount = 50000.0
        )
        assertEquals(72000.0, purchaseTotals.grandTotal, 0.01)
        assertEquals(22000.0, purchaseTotals.remainingBalance, 0.01)
    }

    @Test
    fun `weighted average costing computes accurate inventory cost across purchases`() {
        // 1000 CFT @ 60 avg cost + 500 CFT @ 90 purchase rate => (60000 + 45000) / 1500 = 70
        val newAvg = AccountingEngine.calculateWeightedAvgCost(
            currentStock = 1000.0,
            currentAvgCost = 60.0,
            purchasedQty = 500.0,
            purchaseRate = 90.0
        )
        assertEquals(70.0, newAvg, 0.01)

        // When stock is 0 or negative, weighted avg resets to current purchase rate
        val freshAvg = AccountingEngine.calculateWeightedAvgCost(
            currentStock = 0.0,
            currentAvgCost = 50.0,
            purchasedQty = 200.0,
            purchaseRate = 85.0
        )
        assertEquals(85.0, freshAvg, 0.01)
    }

    @Test
    fun `profit and loss excludes voided transactions and separates unpaid credit from cash collected`() {
        val now = 1_760_000_000_000L
        val sales = listOf(
            Sale(
                id = "s1",
                invoiceNumber = "INV-0001",
                transactionDateMillis = now,
                grandTotal = 100_000.0,
                paidAmount = 60_000.0,
                remainingBalance = 40_000.0,
                totalCogs = 65_000.0,
                status = TxStatuses.CONFIRMED
            ),
            Sale(
                id = "s2",
                invoiceNumber = "INV-0002",
                transactionDateMillis = now,
                grandTotal = 50_000.0,
                paidAmount = 0.0,
                remainingBalance = 50_000.0,
                totalCogs = 30_000.0,
                status = TxStatuses.VOID // must be excluded
            )
        )
        val expenses = listOf(
            Expense(
                id = "e1",
                category = ExpenseCategories.DIESEL,
                title = "Generator Diesel",
                amount = 15_000.0,
                transactionDateMillis = now,
                status = TxStatuses.CONFIRMED
            ),
            Expense(
                id = "e2",
                category = ExpenseCategories.LABOUR,
                title = "Voided entry",
                amount = 5_000.0,
                transactionDateMillis = now,
                status = TxStatuses.VOID
            )
        )

        val pnl = AccountingEngine.computeProfitAndLoss(now - 1000L, now + 1000L, sales, expenses)
        assertEquals(100_000.0, pnl.totalSales, 0.01)
        assertEquals(65_000.0, pnl.totalCogs, 0.01)
        assertEquals(35_000.0, pnl.grossProfit, 0.01)
        assertEquals(15_000.0, pnl.totalExpenses, 0.01)
        assertEquals(20_000.0, pnl.netProfit, 0.01)
        assertEquals(60_000.0, pnl.cashCollectedFromSales, 0.01)
        assertEquals(40_000.0, pnl.creditOutstandingFromSales, 0.01)
    }

    @Test
    fun `cash flow statement tracks recoveries supplier payments and expenses without unpaid credit`() {
        val now = 1_760_000_000_000L
        val sales = listOf(
            Sale(id = "s1", transactionDateMillis = now, grandTotal = 80_000.0, paidAmount = 30_000.0, remainingBalance = 50_000.0)
        )
        val purchases = listOf(
            Purchase(id = "p1", transactionDateMillis = now, grandTotal = 40_000.0, paidAmount = 25_000.0, remainingBalance = 15_000.0)
        )
        val payments = listOf(
            PartyPayment(id = "pay1", partyType = "CUSTOMER", entryType = "RECOVERY", amount = 10_000.0, transactionDateMillis = now),
            PartyPayment(id = "pay2", partyType = "SUPPLIER", entryType = "PAYMENT", amount = 5_000.0, transactionDateMillis = now)
        )
        val expenses = listOf(
            Expense(id = "e1", category = ExpenseCategories.ELECTRICITY, amount = 8_000.0, transactionDateMillis = now)
        )

        val cf = AccountingEngine.computeCashFlow(
            startMillis = now - 1000L,
            endMillis = now + 1000L,
            openingCash = 50_000.0,
            sales = sales,
            purchases = purchases,
            payments = payments,
            expenses = expenses
        )
        assertEquals(40_000.0, cf.totalCashIn, 0.01) // 30k sale paid + 10k recovery
        assertEquals(38_000.0, cf.totalCashOut, 0.01) // 25k purchase paid + 5k supplier payment + 8k expense
        assertEquals(2_000.0, cf.netCashMovement, 0.01)
        assertEquals(52_000.0, cf.closingCashBalance, 0.01)
    }

    @Test
    fun `customer and supplier ledgers compute running balances accurately`() {
        val now = 1_760_000_000_000L
        val customer = Customer(id = "c1", name = "Al-Noor Builders", openingBalance = 10_000.0, currentBalance = 25_000.0)
        val sales = listOf(
            Sale(
                id = "s1",
                invoiceNumber = "INV-0001",
                customerId = "c1",
                transactionDateMillis = now,
                items = listOf(LineItem(materialName = "Bajri / Crush", quantity = 200.0, unit = "CFT")),
                grandTotal = 30_000.0,
                paidAmount = 5_000.0,
                remainingBalance = 25_000.0
            )
        )
        val custPayments = listOf(
            PartyPayment(id = "r1", partyType = "CUSTOMER", partyId = "c1", entryType = "RECOVERY", amount = 10_000.0, transactionDateMillis = now + 1000L)
        )
        val custLedger = AccountingEngine.computeCustomerLedger(customer, sales, custPayments)
        assertEquals(4, custLedger.size)
        assertEquals(10_000.0, custLedger[0].runningBalance, 0.01)
        assertEquals(40_000.0, custLedger[1].runningBalance, 0.01)
        assertEquals(35_000.0, custLedger[2].runningBalance, 0.01)
        assertEquals(25_000.0, custLedger[3].runningBalance, 0.01)

        val supplier = Supplier(id = "sup1", name = "Margalla Quarry", openingBalance = 5_000.0)
        val purchases = listOf(
            Purchase(
                id = "p1",
                voucherNumber = "PV-0001",
                supplierId = "sup1",
                transactionDateMillis = now,
                items = listOf(LineItem(materialName = "Khaka", quantity = 400.0, unit = "CFT")),
                grandTotal = 20_000.0,
                paidAmount = 10_000.0,
                remainingBalance = 10_000.0
            )
        )
        val supPayments = listOf(
            PartyPayment(id = "sp1", partyType = "SUPPLIER", partyId = "sup1", entryType = "PAYMENT", amount = 4_000.0, transactionDateMillis = now + 1000L)
        )
        val supLedger = AccountingEngine.computeSupplierLedger(supplier, purchases, supPayments)
        assertEquals(4, supLedger.size)
        assertEquals(11_000.0, supLedger.last().runningBalance, 0.01)
    }

    @Test
    fun `dashboard metrics and formatting helpers produce valid PKR output`() {
        val now = 1_760_000_000_000L
        val mats = listOf(
            Material(id = "m1", name = "Bajri / Crush", currentStock = 1200.0, minStockAlert = 500.0, avgCost = 80.0),
            Material(id = "m2", name = "Cement Facto", currentStock = 20.0, minStockAlert = 50.0, avgCost = 1200.0)
        )
        val metrics = AccountingEngine.computeDashboardMetrics(
            nowMillis = now,
            settings = BusinessSettings(),
            materials = mats,
            customers = listOf(Customer(id = "c1", currentBalance = 15_000.0)),
            suppliers = listOf(Supplier(id = "s1", currentBalance = 7_500.0)),
            sales = listOf(Sale(id = "s1", transactionDateMillis = now, grandTotal = 45_000.0, paidAmount = 30_000.0, totalCogs = 30_000.0)),
            purchases = listOf(Purchase(id = "p1", transactionDateMillis = now, grandTotal = 20_000.0, paidAmount = 12_500.0)),
            payments = emptyList(),
            expenses = listOf(Expense(id = "e1", category = ExpenseCategories.DIESEL, amount = 3_000.0, transactionDateMillis = now))
        )
        assertEquals(1, metrics.lowStockCount)
        assertEquals(1200.0 * 80.0 + 20.0 * 1200.0, metrics.stockValue, 0.01)
        assertEquals(1220.0, metrics.totalStockQuantity, 0.01)
        assertEquals(15_000.0, metrics.customerOutstanding, 0.01)
        assertEquals(7_500.0, metrics.supplierOutstanding, 0.01)
        assertEquals(30_000.0, metrics.cashReceived, 0.01)
        assertEquals(15_500.0, metrics.cashPaid, 0.01)
        assertEquals("PKR 1,25,000".replace("1,25,000", "125,000"), AccountingEngine.formatPkr(125000.0))
        assertEquals("INV-2026-0007", AccountingEngine.generateDocNumber("INV-2026-", 7L))
        assertFalse(AccountingEngine.dateRangeForFilter(ReportDateFilter.TODAY, now).first == 0L)
    }

    @Test
    fun `urdu and english localization resolves RTL layout and complete category labels`() {
        assertEquals(LayoutDirection.Ltr, AppLanguage.EN.layoutDirection)
        assertEquals(LayoutDirection.Rtl, AppLanguage.UR.layoutDirection)
        assertEquals(AppLanguage.UR, AppLanguage.fromCode("ur"))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("en"))
        assertEquals(UrduStrings, stringsFor(AppLanguage.UR))
        assertEquals(EnglishStrings, stringsFor(AppLanguage.EN))
        assertEquals("ڈیزل", UrduStrings.expenseCategoryLabel(ExpenseCategories.DIESEL))
        assertEquals("سیمنٹ", UrduStrings.materialCategoryLabel(MaterialCategories.CEMENT))
        assertEquals("آج", UrduStrings.dateFilterLabel(ReportDateFilter.TODAY))
    }
}
