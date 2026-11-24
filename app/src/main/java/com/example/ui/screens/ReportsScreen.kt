package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AccountingEngine
import com.example.domain.ReportDateFilter
import com.example.domain.TxStatuses
import com.example.export.CsvExporter
import com.example.export.PdfDocumentGenerator
import com.example.ui.CrusherWorkspaceState
import com.example.ui.components.DateRangeFilterRow
import com.example.ui.components.DropdownSelector
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoNumberStyle
import java.io.File

enum class ReportKind(val title: String, val defaultFilter: ReportDateFilter) {
    DAILY_SALES("Daily Sales", ReportDateFilter.TODAY),
    WEEKLY_SALES("Weekly Sales", ReportDateFilter.WEEK),
    MONTHLY_SALES("Monthly Sales", ReportDateFilter.MONTH),
    YEARLY_SALES("Yearly Sales", ReportDateFilter.YEAR),
    PURCHASES("Purchases", ReportDateFilter.MONTH),
    STOCK("Material Stock", ReportDateFilter.ALL),
    CUSTOMER_LEDGER("Customer Ledger", ReportDateFilter.ALL),
    SUPPLIER_LEDGER("Supplier Ledger", ReportDateFilter.ALL),
    EXPENSES("Expenses", ReportDateFilter.MONTH),
    CASH_FLOW("Cash Flow", ReportDateFilter.MONTH),
    PROFIT_LOSS("Profit & Loss", ReportDateFilter.MONTH);

    fun localizedTitle(strings: AppStrings): String = when (this) {
        DAILY_SALES -> strings.reportDailySales
        WEEKLY_SALES -> strings.reportWeeklySales
        MONTHLY_SALES -> strings.reportMonthlySales
        YEARLY_SALES -> strings.reportYearlySales
        PURCHASES -> strings.reportPurchases
        STOCK -> strings.reportStock
        CUSTOMER_LEDGER -> strings.reportCustomerLedger
        SUPPLIER_LEDGER -> strings.reportSupplierLedger
        EXPENSES -> strings.reportExpenses
        CASH_FLOW -> strings.reportCashFlow
        PROFIT_LOSS -> strings.reportProfitLoss
    }
}

private data class BuiltReport(
    val title: String,
    val subtitle: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val summaryLines: List<String>
)

@Composable
fun ReportsScreen(state: CrusherWorkspaceState) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var selectedKind by remember { mutableStateOf(ReportKind.PROFIT_LOSS) }
    var dateFilter by remember { mutableStateOf(ReportDateFilter.MONTH) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }

    var selectedCustomer by remember(state.customers) { mutableStateOf(state.customers.firstOrNull()) }
    var selectedSupplier by remember(state.suppliers) { mutableStateOf(state.suppliers.firstOrNull()) }

    var pendingFileToSave by remember { mutableStateOf<File?>(null) }
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val f = pendingFileToSave
        if (uri != null && f != null && f.exists()) {
            context.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        }
        pendingFileToSave = null
    }
    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val f = pendingFileToSave
        if (uri != null && f != null && f.exists()) {
            context.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        }
        pendingFileToSave = null
    }

    val (rangeStart, rangeEnd) = remember(dateFilter, customStart, customEnd) {
        AccountingEngine.dateRangeForFilter(dateFilter, System.currentTimeMillis(), customStart, customEnd)
    }

    val report = remember(state, selectedKind, rangeStart, rangeEnd, selectedCustomer, selectedSupplier) {
        buildReportData(state, selectedKind, rangeStart, rangeEnd, selectedCustomer, selectedSupplier)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("reports_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReportKind.entries) { kind ->
                FilterChip(
                    selected = selectedKind == kind,
                    onClick = {
                        selectedKind = kind
                        dateFilter = kind.defaultFilter
                    },
                    label = { Text(kind.localizedTitle(strings)) }
                )
            }
        }

        if (selectedKind != ReportKind.STOCK && selectedKind != ReportKind.CUSTOMER_LEDGER && selectedKind != ReportKind.SUPPLIER_LEDGER) {
            DateRangeFilterRow(
                selected = dateFilter,
                customStart = customStart,
                customEnd = customEnd,
                onSelect = { f, s, e ->
                    dateFilter = f
                    customStart = s
                    customEnd = e
                }
            )
        }

        if (selectedKind == ReportKind.CUSTOMER_LEDGER && state.customers.isNotEmpty()) {
            DropdownSelector(
                label = "Select Customer",
                selectedText = selectedCustomer?.name ?: "Select Customer",
                options = state.customers,
                optionLabel = { "${it.name} (${AccountingEngine.formatPkr(it.currentBalance)})" },
                onSelect = { selectedCustomer = it }
            )
        }
        if (selectedKind == ReportKind.SUPPLIER_LEDGER && state.suppliers.isNotEmpty()) {
            DropdownSelector(
                label = "Select Supplier",
                selectedText = selectedSupplier?.name ?: "Select Supplier",
                options = state.suppliers,
                optionLabel = { "${it.name} (${AccountingEngine.formatPkr(it.currentBalance)})" },
                onSelect = { selectedSupplier = it }
            )
        }

        // Export Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val csv = CsvExporter.generateCsvFile(context, report.title, report.headers, report.rows)
                    pendingFileToSave = csv
                    saveCsvLauncher.launch("${report.title.replace(" ", "_")}.csv")
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.TableView, contentDescription = strings.excelCsv, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.excelCsv)
            }
            OutlinedButton(
                onClick = {
                    val pdf = PdfDocumentGenerator.generateReportPdf(
                        context, state.settings, report.title, report.subtitle, report.headers, report.rows, report.summaryLines
                    )
                    pendingFileToSave = pdf
                    savePdfLauncher.launch("${report.title.replace(" ", "_")}.pdf")
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = strings.savePdf, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.savePdf)
            }
            Button(
                onClick = {
                    val pdf = PdfDocumentGenerator.generateReportPdf(
                        context, state.settings, report.title, report.subtitle, report.headers, report.rows, report.summaryLines
                    )
                    PdfDocumentGenerator.printPdfFile(context, pdf, report.title)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Print, contentDescription = strings.print, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.print)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(report.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(report.subtitle, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        report.summaryLines.forEach { line ->
                            Text(line, style = MonoNumberStyle.copy(fontSize = 13.sp))
                        }
                    }
                }
            }

            items(report.rows) { row ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        report.headers.forEachIndexed { idx, header ->
                            val cell = row.getOrElse(idx) { "" }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(header, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    cell,
                                    style = MonoNumberStyle.copy(fontSize = 12.sp),
                                    color = when {
                                        header.contains("Net", true) && cell.startsWith("-") -> CrimsonAlert
                                        header.contains("Net", true) -> EmeraldProfit
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildReportData(
    state: CrusherWorkspaceState,
    kind: ReportKind,
    start: Long,
    end: Long,
    selectedCustomer: com.example.domain.Customer?,
    selectedSupplier: com.example.domain.Supplier?
): BuiltReport {
    val rangeLabel = if (start == 0L && end == Long.MAX_VALUE) {
        "All Dates"
    } else {
        "${AccountingEngine.formatDate(start)} to ${AccountingEngine.formatDate(end)}"
    }

    return when (kind) {
        ReportKind.DAILY_SALES, ReportKind.WEEKLY_SALES, ReportKind.MONTHLY_SALES, ReportKind.YEARLY_SALES -> {
            val sales = state.sales.filter { it.status != TxStatuses.VOID && it.transactionDateMillis in start..end }
            val headers = listOf("Invoice #", "Date", "Customer", "Grand Total", "Paid", "Balance")
            val rows = sales.map {
                listOf(
                    it.invoiceNumber,
                    AccountingEngine.formatDate(it.transactionDateMillis),
                    it.customerName,
                    AccountingEngine.formatPkr(it.grandTotal),
                    AccountingEngine.formatPkr(it.paidAmount),
                    AccountingEngine.formatPkr(it.remainingBalance)
                )
            }
            BuiltReport(
                title = "${kind.title} Report",
                subtitle = rangeLabel,
                headers = headers,
                rows = rows,
                summaryLines = listOf(
                    "Invoices: ${sales.size}",
                    "Total Sales: ${AccountingEngine.formatPkr(sales.sumOf { it.grandTotal })}",
                    "Cash Collected: ${AccountingEngine.formatPkr(sales.sumOf { it.paidAmount })}",
                    "Credit Remaining: ${AccountingEngine.formatPkr(sales.sumOf { it.remainingBalance })}"
                )
            )
        }
        ReportKind.PURCHASES -> {
            val list = state.purchases.filter { it.status != TxStatuses.VOID && it.transactionDateMillis in start..end }
            val headers = listOf("Voucher #", "Date", "Supplier", "Total", "Paid", "Payable")
            val rows = list.map {
                listOf(
                    it.voucherNumber,
                    AccountingEngine.formatDate(it.transactionDateMillis),
                    it.supplierName,
                    AccountingEngine.formatPkr(it.grandTotal),
                    AccountingEngine.formatPkr(it.paidAmount),
                    AccountingEngine.formatPkr(it.remainingBalance)
                )
            }
            BuiltReport(
                title = "Purchase Report",
                subtitle = rangeLabel,
                headers = headers,
                rows = rows,
                summaryLines = listOf(
                    "Vouchers: ${list.size}",
                    "Total Purchases: ${AccountingEngine.formatPkr(list.sumOf { it.grandTotal })}",
                    "Paid: ${AccountingEngine.formatPkr(list.sumOf { it.paidAmount })}",
                    "Payable: ${AccountingEngine.formatPkr(list.sumOf { it.remainingBalance })}"
                )
            )
        }
        ReportKind.STOCK -> {
            val mats = state.materials.filter { !it.archived }
            val headers = listOf("Material", "Category", "Stock", "Avg Cost", "Sale Rate", "Stock Value")
            val rows = mats.map {
                val cost = if (it.avgCost > 0) it.avgCost else it.purchaseRate
                val value = (it.currentStock.coerceAtLeast(0.0)) * cost
                listOf(
                    it.name,
                    it.category,
                    "${AccountingEngine.formatQty(it.currentStock)} ${it.unit}",
                    AccountingEngine.formatPkr(cost),
                    AccountingEngine.formatPkr(it.saleRate),
                    AccountingEngine.formatPkr(value)
                )
            }
            BuiltReport(
                title = "Material-Wise Stock Report",
                subtitle = "Current Inventory Valuation (Weighted Average Cost)",
                headers = headers,
                rows = rows,
                summaryLines = listOf(
                    "Total Materials: ${mats.size}",
                    "Low Stock Items: ${mats.count { it.currentStock <= it.minStockAlert }}",
                    "Total Stock Value: ${AccountingEngine.formatPkr(state.metrics.stockValue)}"
                )
            )
        }
        ReportKind.CUSTOMER_LEDGER -> {
            val c = selectedCustomer
            val entries = if (c != null) AccountingEngine.computeCustomerLedger(c, state.sales, state.payments) else emptyList()
            val headers = listOf("Date", "Ref #", "Description", "Debit", "Credit", "Balance")
            val rows = entries.map {
                listOf(
                    if (it.dateMillis == 0L) "Opening" else AccountingEngine.formatDate(it.dateMillis),
                    it.referenceNumber,
                    it.description,
                    AccountingEngine.formatPkr(it.debit),
                    AccountingEngine.formatPkr(it.credit),
                    AccountingEngine.formatPkr(it.runningBalance)
                )
            }
            BuiltReport(
                title = "Customer Ledger: ${c?.name ?: "None"}",
                subtitle = c?.phone ?: "",
                headers = headers,
                rows = rows,
                summaryLines = listOf("Closing Receivable: ${AccountingEngine.formatPkr(c?.currentBalance ?: 0.0)}")
            )
        }
        ReportKind.SUPPLIER_LEDGER -> {
            val s = selectedSupplier
            val entries = if (s != null) AccountingEngine.computeSupplierLedger(s, state.purchases, state.payments) else emptyList()
            val headers = listOf("Date", "Ref #", "Description", "Debit", "Credit", "Balance")
            val rows = entries.map {
                listOf(
                    if (it.dateMillis == 0L) "Opening" else AccountingEngine.formatDate(it.dateMillis),
                    it.referenceNumber,
                    it.description,
                    AccountingEngine.formatPkr(it.debit),
                    AccountingEngine.formatPkr(it.credit),
                    AccountingEngine.formatPkr(it.runningBalance)
                )
            }
            BuiltReport(
                title = "Supplier Ledger: ${s?.name ?: "None"}",
                subtitle = s?.phone ?: "",
                headers = headers,
                rows = rows,
                summaryLines = listOf("Closing Payable: ${AccountingEngine.formatPkr(s?.currentBalance ?: 0.0)}")
            )
        }
        ReportKind.EXPENSES -> {
            val list = state.expenses.filter { it.status != TxStatuses.VOID && it.transactionDateMillis in start..end }
            val headers = listOf("Date", "Category", "Description", "Paid To", "Method", "Amount")
            val rows = list.map {
                listOf(
                    AccountingEngine.formatDate(it.transactionDateMillis),
                    it.category,
                    it.title,
                    it.paidTo.ifBlank { "-" },
                    it.paymentMethod,
                    AccountingEngine.formatPkr(it.amount)
                )
            }
            BuiltReport(
                title = "Expense Report",
                subtitle = rangeLabel,
                headers = headers,
                rows = rows,
                summaryLines = listOf(
                    "Entries: ${list.size}",
                    "Total Expenses: ${AccountingEngine.formatPkr(list.sumOf { it.amount })}"
                )
            )
        }
        ReportKind.CASH_FLOW -> {
            val cf = AccountingEngine.computeCashFlow(
                start, end, state.settings.openingCashBalance,
                state.sales, state.purchases, state.payments, state.expenses
            )
            val headers = listOf("Cash Flow Component", "Amount (PKR)")
            val rows = listOf(
                listOf("Opening Cash Balance", AccountingEngine.formatPkr(cf.openingCash)),
                listOf("(+) Cash Collected on Sales", AccountingEngine.formatPkr(cf.cashFromSales)),
                listOf("(+) Customer Recoveries", AccountingEngine.formatPkr(cf.customerRecoveries)),
                listOf("Total Cash Inflow", AccountingEngine.formatPkr(cf.totalCashIn)),
                listOf("(-) Cash Paid on Purchases", AccountingEngine.formatPkr(cf.cashForPurchases)),
                listOf("(-) Supplier Payments", AccountingEngine.formatPkr(cf.supplierPayments)),
                listOf("(-) Operating Expenses", AccountingEngine.formatPkr(cf.cashForExpenses)),
                listOf("Total Cash Outflow", AccountingEngine.formatPkr(cf.totalCashOut)),
                listOf("Net Cash Movement", AccountingEngine.formatPkr(cf.netCashMovement)),
                listOf("Closing Cash Balance", AccountingEngine.formatPkr(cf.closingCashBalance))
            )
            BuiltReport(
                title = "Cash Flow Report",
                subtitle = rangeLabel,
                headers = headers,
                rows = rows,
                summaryLines = listOf(
                    "Cash In: ${AccountingEngine.formatPkr(cf.totalCashIn)}",
                    "Cash Out: ${AccountingEngine.formatPkr(cf.totalCashOut)}",
                    "Closing Cash: ${AccountingEngine.formatPkr(cf.closingCashBalance)}"
                )
            )
        }
        ReportKind.PROFIT_LOSS -> {
            val pnl = AccountingEngine.computeProfitAndLoss(start, end, state.sales, state.expenses)
            val headers = listOf("Line Item", "Amount (PKR)")
            val rows = mutableListOf(
                listOf("Total Sales (Invoiced Revenue)", AccountingEngine.formatPkr(pnl.totalSales)),
                listOf("  Cash Collected from Sales", AccountingEngine.formatPkr(pnl.cashCollectedFromSales)),
                listOf("  Unpaid Credit (Receivable)", AccountingEngine.formatPkr(pnl.creditOutstandingFromSales)),
                listOf("(-) Cost of Goods Sold (Weighted Avg)", AccountingEngine.formatPkr(pnl.totalCogs)),
                listOf("Gross Profit", AccountingEngine.formatPkr(pnl.grossProfit)),
                listOf("(-) Total Operating Expenses", AccountingEngine.formatPkr(pnl.totalExpenses))
            )
            pnl.expenseByCategory.forEach { (cat, amt) ->
                rows += listOf("    • $cat", AccountingEngine.formatPkr(amt))
            }
            rows += listOf("Net Profit / (Loss)", AccountingEngine.formatPkr(pnl.netProfit))

            BuiltReport(
                title = "Profit & Loss Statement",
                subtitle = "$rangeLabel • Unpaid invoices excluded from cash collection",
                headers = headers,
                rows = rows,
                summaryLines = listOf(
                    "Gross Profit: ${AccountingEngine.formatPkr(pnl.grossProfit)}",
                    "Total Expenses: ${AccountingEngine.formatPkr(pnl.totalExpenses)}",
                    "Net Profit / Loss: ${AccountingEngine.formatPkr(pnl.netProfit)}"
                )
            )
        }
    }
}
