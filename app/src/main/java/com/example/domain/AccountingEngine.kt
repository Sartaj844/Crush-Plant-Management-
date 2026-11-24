package com.example.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

enum class ReportDateFilter(val label: String) {
    TODAY("Today"),
    WEEK("This Week"),
    MONTH("This Month"),
    YEAR("This Year"),
    ALL("All Time"),
    CUSTOM("Custom")
}

data class SaleTotals(
    val subtotal: Double,
    val discount: Double,
    val grandTotal: Double,
    val paidAmount: Double,
    val remainingBalance: Double,
    val totalCogs: Double
)

data class ProfitAndLossStatement(
    val totalSales: Double,
    val totalCogs: Double,
    val grossProfit: Double,
    val totalExpenses: Double,
    val netProfit: Double,
    val cashCollectedFromSales: Double,
    val creditOutstandingFromSales: Double,
    val expenseByCategory: Map<String, Double>
)

data class CashFlowStatement(
    val openingCash: Double,
    val cashFromSales: Double,
    val customerRecoveries: Double,
    val totalCashIn: Double,
    val cashForPurchases: Double,
    val supplierPayments: Double,
    val cashForExpenses: Double,
    val totalCashOut: Double,
    val netCashMovement: Double,
    val closingCashBalance: Double
)

data class DashboardMetrics(
    val todaySales: Double,
    val monthlySales: Double,
    val yearlySales: Double,
    val totalPurchases: Double,
    val stockValue: Double,
    val totalStockQuantity: Double,
    val lowStockCount: Int,
    val totalExpenses: Double,
    val customerOutstanding: Double,
    val supplierOutstanding: Double,
    val cashReceived: Double,
    val cashPaid: Double,
    val dailyPnl: ProfitAndLossStatement,
    val monthlyPnl: ProfitAndLossStatement,
    val yearlyPnl: ProfitAndLossStatement
)

data class LedgerEntry(
    val id: String,
    val dateMillis: Long,
    val referenceNumber: String,
    val description: String,
    val debit: Double,
    val credit: Double,
    val runningBalance: Double,
    val status: String
)

object AccountingEngine {

    private val pkrSymbols = DecimalFormatSymbols(Locale.US)
    private val pkrIntFormat = DecimalFormat("#,##0", pkrSymbols)
    private val pkrDecFormat = DecimalFormat("#,##0.##", pkrSymbols)
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)

    fun formatPkr(amount: Double): String {
        val rounded = (amount * 100.0).roundToLong() / 100.0
        val absVal = kotlin.math.abs(rounded)
        val formatted = if (absVal % 1.0 == 0.0) pkrIntFormat.format(absVal) else pkrDecFormat.format(absVal)
        return if (rounded < 0) "-PKR $formatted" else "PKR $formatted"
    }

    fun formatQty(qty: Double): String {
        val rounded = (qty * 100.0).roundToLong() / 100.0
        return if (rounded % 1.0 == 0.0) pkrIntFormat.format(rounded) else pkrDecFormat.format(rounded)
    }

    fun formatDate(millis: Long): String {
        if (millis <= 0L) return "-"
        return dateFormat.format(Date(millis))
    }

    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return "-"
        return dateTimeFormat.format(Date(millis))
    }

    fun generateDocNumber(prefix: String, sequence: Long): String {
        val cleanPrefix = prefix.ifBlank { "DOC-" }
        val padded = sequence.coerceAtLeast(1L).toString().padStart(4, '0')
        return "$cleanPrefix$padded"
    }

    fun calculateLineTotal(quantity: Double, rate: Double): Double {
        if (quantity <= 0.0 || rate < 0.0) return 0.0
        return ((quantity * rate) * 100.0).roundToLong() / 100.0
    }

    /**
     * Weighted Average Inventory Costing Method:
     * If existing stock is <= 0, new avg cost becomes the purchase rate.
     * Otherwise, (currentStock * currentAvgCost + purchasedQty * purchaseRate) / (currentStock + purchasedQty).
     */
    fun calculateWeightedAvgCost(
        currentStock: Double,
        currentAvgCost: Double,
        purchasedQty: Double,
        purchaseRate: Double
    ): Double {
        if (purchasedQty <= 0.0) return max(0.0, currentAvgCost)
        if (currentStock <= 0.0) return max(0.0, purchaseRate)
        val effectiveOldCost = if (currentAvgCost > 0.0) currentAvgCost else purchaseRate
        val totalQty = currentStock + purchasedQty
        if (totalQty <= 0.0) return max(0.0, purchaseRate)
        val newAvg = ((currentStock * effectiveOldCost) + (purchasedQty * purchaseRate)) / totalQty
        return ((newAvg * 100.0).roundToLong() / 100.0).coerceAtLeast(0.0)
    }

    fun calculateSaleTotals(
        items: List<LineItem>,
        discount: Double,
        paidAmount: Double
    ): SaleTotals {
        val subtotal = items.sumOf { calculateLineTotal(it.quantity, it.rate) }
        val safeDiscount = discount.coerceIn(0.0, subtotal)
        val grandTotal = max(0.0, subtotal - safeDiscount)
        val safePaid = paidAmount.coerceIn(0.0, grandTotal)
        val remaining = max(0.0, grandTotal - safePaid)
        val totalCogs = items.sumOf { calculateLineTotal(it.quantity, it.costRate) }
        return SaleTotals(
            subtotal = round2(subtotal),
            discount = round2(safeDiscount),
            grandTotal = round2(grandTotal),
            paidAmount = round2(safePaid),
            remainingBalance = round2(remaining),
            totalCogs = round2(totalCogs)
        )
    }

    fun calculatePurchaseTotals(
        items: List<LineItem>,
        discount: Double,
        paidAmount: Double
    ): SaleTotals {
        val subtotal = items.sumOf { calculateLineTotal(it.quantity, it.rate) }
        val safeDiscount = discount.coerceIn(0.0, subtotal)
        val grandTotal = max(0.0, subtotal - safeDiscount)
        val safePaid = paidAmount.coerceIn(0.0, grandTotal)
        val remaining = max(0.0, grandTotal - safePaid)
        return SaleTotals(
            subtotal = round2(subtotal),
            discount = round2(safeDiscount),
            grandTotal = round2(grandTotal),
            paidAmount = round2(safePaid),
            remainingBalance = round2(remaining),
            totalCogs = 0.0
        )
    }

    fun dateRangeForFilter(
        filter: ReportDateFilter,
        nowMillis: Long,
        customStart: Long? = null,
        customEnd: Long? = null
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (filter) {
            ReportDateFilter.TODAY -> {
                val start = startOfDay(cal)
                val end = endOfDay(cal)
                start to end
            }
            ReportDateFilter.WEEK -> {
                val end = endOfDay(cal)
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val start = startOfDay(cal)
                start to end
            }
            ReportDateFilter.MONTH -> {
                val end = endOfDay(cal)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(cal)
                start to end
            }
            ReportDateFilter.YEAR -> {
                val end = endOfDay(cal)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                val start = startOfDay(cal)
                start to end
            }
            ReportDateFilter.ALL -> 0L to Long.MAX_VALUE
            ReportDateFilter.CUSTOM -> {
                val s = customStart ?: 0L
                val e = customEnd ?: Long.MAX_VALUE
                minOf(s, e) to maxOf(s, e)
            }
        }
    }

    fun computeProfitAndLoss(
        startMillis: Long,
        endMillis: Long,
        sales: List<Sale>,
        expenses: List<Expense>
    ): ProfitAndLossStatement {
        val activeSales = sales.filter {
            it.status != TxStatuses.VOID && it.transactionDateMillis in startMillis..endMillis
        }
        val activeExpenses = expenses.filter {
            it.status != TxStatuses.VOID && it.transactionDateMillis in startMillis..endMillis
        }

        val totalSales = activeSales.sumOf { it.grandTotal }
        val totalCogs = activeSales.sumOf { it.totalCogs }
        val grossProfit = totalSales - totalCogs
        val totalExpenses = activeExpenses.sumOf { it.amount }
        val netProfit = grossProfit - totalExpenses

        val cashCollected = activeSales.sumOf { it.paidAmount }
        val creditOutstanding = activeSales.sumOf { it.remainingBalance }
        val byCat = ExpenseCategories.all.associateWith { cat ->
            round2(activeExpenses.filter { it.category == cat }.sumOf { it.amount })
        }.filterValues { it > 0.0 }

        return ProfitAndLossStatement(
            totalSales = round2(totalSales),
            totalCogs = round2(totalCogs),
            grossProfit = round2(grossProfit),
            totalExpenses = round2(totalExpenses),
            netProfit = round2(netProfit),
            cashCollectedFromSales = round2(cashCollected),
            creditOutstandingFromSales = round2(creditOutstanding),
            expenseByCategory = byCat
        )
    }

    fun computeCashFlow(
        startMillis: Long,
        endMillis: Long,
        openingCash: Double,
        sales: List<Sale>,
        purchases: List<Purchase>,
        payments: List<PartyPayment>,
        expenses: List<Expense>
    ): CashFlowStatement {
        val activeSales = sales.filter {
            it.status != TxStatuses.VOID && it.transactionDateMillis in startMillis..endMillis
        }
        val activePurchases = purchases.filter {
            it.status != TxStatuses.VOID && it.transactionDateMillis in startMillis..endMillis
        }
        val activePayments = payments.filter {
            it.status != TxStatuses.VOID && it.transactionDateMillis in startMillis..endMillis
        }
        val activeExpenses = expenses.filter {
            it.status != TxStatuses.VOID && it.transactionDateMillis in startMillis..endMillis
        }

        val cashFromSales = activeSales.sumOf { it.paidAmount }
        val recoveries = activePayments
            .filter { it.partyType == "CUSTOMER" && it.entryType == "RECOVERY" }
            .sumOf { it.amount }
        val totalIn = cashFromSales + recoveries

        val cashForPurchases = activePurchases.sumOf { it.paidAmount }
        val supplierPay = activePayments
            .filter { it.partyType == "SUPPLIER" && it.entryType == "PAYMENT" }
            .sumOf { it.amount }
        val cashForExpenses = activeExpenses.sumOf { it.amount }
        val totalOut = cashForPurchases + supplierPay + cashForExpenses

        val net = totalIn - totalOut
        val closing = openingCash + net

        return CashFlowStatement(
            openingCash = round2(openingCash),
            cashFromSales = round2(cashFromSales),
            customerRecoveries = round2(recoveries),
            totalCashIn = round2(totalIn),
            cashForPurchases = round2(cashForPurchases),
            supplierPayments = round2(supplierPay),
            cashForExpenses = round2(cashForExpenses),
            totalCashOut = round2(totalOut),
            netCashMovement = round2(net),
            closingCashBalance = round2(closing)
        )
    }

    fun computeDashboardMetrics(
        nowMillis: Long,
        settings: BusinessSettings,
        materials: List<Material>,
        customers: List<Customer>,
        suppliers: List<Supplier>,
        sales: List<Sale>,
        purchases: List<Purchase>,
        payments: List<PartyPayment>,
        expenses: List<Expense>
    ): DashboardMetrics {
        val (todayStart, todayEnd) = dateRangeForFilter(ReportDateFilter.TODAY, nowMillis)
        val (monthStart, monthEnd) = dateRangeForFilter(ReportDateFilter.MONTH, nowMillis)
        val (yearStart, yearEnd) = dateRangeForFilter(ReportDateFilter.YEAR, nowMillis)

        val activeSales = sales.filter { it.status != TxStatuses.VOID }
        val activePurchases = purchases.filter { it.status != TxStatuses.VOID }
        val activeExpenses = expenses.filter { it.status != TxStatuses.VOID }
        val activePayments = payments.filter { it.status != TxStatuses.VOID }
        val activeMaterials = materials.filter { !it.archived }

        val todaySales = activeSales.filter { it.transactionDateMillis in todayStart..todayEnd }.sumOf { it.grandTotal }
        val monthlySales = activeSales.filter { it.transactionDateMillis in monthStart..monthEnd }.sumOf { it.grandTotal }
        val yearlySales = activeSales.filter { it.transactionDateMillis in yearStart..yearEnd }.sumOf { it.grandTotal }

        val totalPurchases = activePurchases.sumOf { it.grandTotal }
        val totalExpenses = activeExpenses.sumOf { it.amount }

        val stockValue = activeMaterials.sumOf {
            val unitCost = if (it.avgCost > 0.0) it.avgCost else it.purchaseRate
            max(0.0, it.currentStock) * unitCost
        }
        val totalStockQty = activeMaterials.sumOf { max(0.0, it.currentStock) }
        val lowStockCount = activeMaterials.count { it.currentStock <= it.minStockAlert }

        val customerOutstanding = customers.filter { !it.archived }.sumOf { it.currentBalance }
        val supplierOutstanding = suppliers.filter { !it.archived }.sumOf { it.currentBalance }

        val cashReceived = activeSales.sumOf { it.paidAmount } +
            activePayments.filter { it.partyType == "CUSTOMER" && it.entryType == "RECOVERY" }.sumOf { it.amount }
        val cashPaid = activePurchases.sumOf { it.paidAmount } +
            activePayments.filter { it.partyType == "SUPPLIER" && it.entryType == "PAYMENT" }.sumOf { it.amount } +
            activeExpenses.sumOf { it.amount }

        return DashboardMetrics(
            todaySales = round2(todaySales),
            monthlySales = round2(monthlySales),
            yearlySales = round2(yearlySales),
            totalPurchases = round2(totalPurchases),
            stockValue = round2(stockValue),
            totalStockQuantity = round2(totalStockQty),
            lowStockCount = lowStockCount,
            totalExpenses = round2(totalExpenses),
            customerOutstanding = round2(customerOutstanding),
            supplierOutstanding = round2(supplierOutstanding),
            cashReceived = round2(cashReceived),
            cashPaid = round2(cashPaid),
            dailyPnl = computeProfitAndLoss(todayStart, todayEnd, sales, expenses),
            monthlyPnl = computeProfitAndLoss(monthStart, monthEnd, sales, expenses),
            yearlyPnl = computeProfitAndLoss(yearStart, yearEnd, sales, expenses)
        )
    }

    /**
     * Customer Ledger:
     * Positive balance = Customer owes business (Debit increases balance, Credit decreases balance).
     */
    fun computeCustomerLedger(
        customer: Customer,
        sales: List<Sale>,
        payments: List<PartyPayment>
    ): List<LedgerEntry> {
        val rawEvents = mutableListOf<LedgerEntry>()

        if (customer.openingBalance != 0.0) {
            rawEvents += LedgerEntry(
                id = "opening_${customer.id}",
                dateMillis = 0L,
                referenceNumber = "OPENING",
                description = "Opening Balance",
                debit = if (customer.openingBalance > 0) customer.openingBalance else 0.0,
                credit = if (customer.openingBalance < 0) -customer.openingBalance else 0.0,
                runningBalance = 0.0,
                status = TxStatuses.CONFIRMED
            )
        }

        sales.filter { it.customerId == customer.id && it.status != TxStatuses.VOID }.forEach { sale ->
            val desc = sale.items.joinToString(", ") { "${it.materialName} (${formatQty(it.quantity)} ${it.unit})" }
            rawEvents += LedgerEntry(
                id = "sale_dr_${sale.id}",
                dateMillis = sale.transactionDateMillis,
                referenceNumber = sale.invoiceNumber,
                description = "Sale Invoice: $desc",
                debit = sale.grandTotal,
                credit = 0.0,
                runningBalance = 0.0,
                status = sale.status
            )
            if (sale.paidAmount > 0.0) {
                rawEvents += LedgerEntry(
                    id = "sale_cr_${sale.id}",
                    dateMillis = sale.transactionDateMillis + 1L,
                    referenceNumber = sale.invoiceNumber,
                    description = "Cash/Paid on Invoice (${sale.paymentMethod})",
                    debit = 0.0,
                    credit = sale.paidAmount,
                    runningBalance = 0.0,
                    status = sale.status
                )
            }
        }

        payments.filter { it.partyType == "CUSTOMER" && it.partyId == customer.id && it.status != TxStatuses.VOID }
            .forEach { p ->
                val isDebit = p.entryType == "DEBIT_ADJUSTMENT"
                rawEvents += LedgerEntry(
                    id = "pay_${p.id}",
                    dateMillis = p.transactionDateMillis,
                    referenceNumber = p.referenceNumber.ifBlank { p.entryType },
                    description = p.notes.ifBlank { "${p.entryType} (${p.paymentMethod})" },
                    debit = if (isDebit) p.amount else 0.0,
                    credit = if (!isDebit) p.amount else 0.0,
                    runningBalance = 0.0,
                    status = p.status
                )
            }

        var running = 0.0
        return rawEvents.sortedBy { it.dateMillis }.map { entry ->
            running = round2(running + entry.debit - entry.credit)
            entry.copy(runningBalance = running)
        }
    }

    /**
     * Supplier Ledger:
     * Positive balance = Business owes Supplier (Credit increases payable, Debit decreases payable).
     */
    fun computeSupplierLedger(
        supplier: Supplier,
        purchases: List<Purchase>,
        payments: List<PartyPayment>
    ): List<LedgerEntry> {
        val rawEvents = mutableListOf<LedgerEntry>()

        if (supplier.openingBalance != 0.0) {
            rawEvents += LedgerEntry(
                id = "opening_${supplier.id}",
                dateMillis = 0L,
                referenceNumber = "OPENING",
                description = "Opening Payable Balance",
                debit = if (supplier.openingBalance < 0) -supplier.openingBalance else 0.0,
                credit = if (supplier.openingBalance > 0) supplier.openingBalance else 0.0,
                runningBalance = 0.0,
                status = TxStatuses.CONFIRMED
            )
        }

        purchases.filter { it.supplierId == supplier.id && it.status != TxStatuses.VOID }.forEach { purchase ->
            val desc = purchase.items.joinToString(", ") { "${it.materialName} (${formatQty(it.quantity)} ${it.unit})" }
            rawEvents += LedgerEntry(
                id = "pur_cr_${purchase.id}",
                dateMillis = purchase.transactionDateMillis,
                referenceNumber = purchase.voucherNumber,
                description = "Purchase Voucher: $desc",
                debit = 0.0,
                credit = purchase.grandTotal,
                runningBalance = 0.0,
                status = purchase.status
            )
            if (purchase.paidAmount > 0.0) {
                rawEvents += LedgerEntry(
                    id = "pur_dr_${purchase.id}",
                    dateMillis = purchase.transactionDateMillis + 1L,
                    referenceNumber = purchase.voucherNumber,
                    description = "Paid on Purchase (${purchase.paymentMethod})",
                    debit = purchase.paidAmount,
                    credit = 0.0,
                    runningBalance = 0.0,
                    status = purchase.status
                )
            }
        }

        payments.filter { it.partyType == "SUPPLIER" && it.partyId == supplier.id && it.status != TxStatuses.VOID }
            .forEach { p ->
                val isCredit = p.entryType == "CREDIT_ADJUSTMENT"
                rawEvents += LedgerEntry(
                    id = "pay_${p.id}",
                    dateMillis = p.transactionDateMillis,
                    referenceNumber = p.referenceNumber.ifBlank { p.entryType },
                    description = p.notes.ifBlank { "${p.entryType} (${p.paymentMethod})" },
                    debit = if (!isCredit) p.amount else 0.0,
                    credit = if (isCredit) p.amount else 0.0,
                    runningBalance = 0.0,
                    status = p.status
                )
            }

        var running = 0.0
        return rawEvents.sortedBy { it.dateMillis }.map { entry ->
            running = round2(running + entry.credit - entry.debit)
            entry.copy(runningBalance = running)
        }
    }

    fun round2(value: Double): Double = (value * 100.0).roundToLong() / 100.0

    private fun startOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun endOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }
}
