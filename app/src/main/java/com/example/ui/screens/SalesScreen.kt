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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AccountingEngine
import com.example.domain.BusinessSettings
import com.example.domain.Customer
import com.example.domain.DefaultUnits
import com.example.domain.LineItem
import com.example.domain.Material
import com.example.domain.PaymentMethods
import com.example.domain.ReportDateFilter
import com.example.domain.Sale
import com.example.domain.TxStatuses
import com.example.export.PdfDocumentGenerator
import com.example.ui.CrusherViewModel
import com.example.ui.CrusherWorkspaceState
import com.example.ui.components.AdminPinDialog
import com.example.ui.components.DatePickerButton
import com.example.ui.components.DateRangeFilterRow
import com.example.ui.components.DropdownSelector
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ReasonInputDialog
import com.example.ui.components.SearchInputField
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonAlertBg
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.EmeraldProfitBg
import com.example.ui.theme.MonoNumberStyle
import com.example.ui.theme.QuarryAmber
import com.example.ui.theme.QuarryAmberContainer
import com.example.ui.theme.SlateNavy
import java.io.File

@Composable
fun SalesScreen(
    state: CrusherWorkspaceState,
    viewModel: CrusherViewModel,
    openNewTrigger: Boolean = false,
    onConsumeNewTrigger: () -> Unit = {}
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf(ReportDateFilter.MONTH) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }

    var showSaleForm by remember(openNewTrigger) { mutableStateOf(openNewTrigger) }
    if (openNewTrigger) {
        onConsumeNewTrigger()
    }
    var editingSale by remember { mutableStateOf<Sale?>(null) }
    var previewingSale by remember { mutableStateOf<Sale?>(null) }
    var pendingPinVoidSale by remember { mutableStateOf<Sale?>(null) }
    var voidingSale by remember { mutableStateOf<Sale?>(null) }
    var pendingPdfFileToSave by remember { mutableStateOf<File?>(null) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val file = pendingPdfFileToSave
        if (uri != null && file != null && file.exists()) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
        }
        pendingPdfFileToSave = null
    }

    val (rangeStart, rangeEnd) = remember(dateFilter, customStart, customEnd) {
        AccountingEngine.dateRangeForFilter(dateFilter, System.currentTimeMillis(), customStart, customEnd)
    }

    val filteredSales = remember(state.sales, searchQuery, rangeStart, rangeEnd) {
        state.sales.filter { s ->
            s.transactionDateMillis in rangeStart..rangeEnd &&
                (searchQuery.isBlank() ||
                    s.invoiceNumber.contains(searchQuery, true) ||
                    s.customerName.contains(searchQuery, true) ||
                    s.items.any { it.materialName.contains(searchQuery, true) })
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingSale = null
                    showSaleForm = true
                },
                modifier = Modifier.testTag("new_sale_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Sale Invoice")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SearchInputField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = strings.searchSalesPlaceholder,
                tag = "sales_search"
            )
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

            if (filteredSales.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.PointOfSale,
                    title = strings.noSalesTitle,
                    message = strings.noSalesMessage,
                    actionLabel = strings.createSaleInvoice,
                    onAction = {
                        editingSale = null
                        showSaleForm = true
                    }
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filteredSales, key = { it.id }) { sale ->
                        SaleInvoiceCard(
                            sale = sale,
                            onPreview = { previewingSale = sale },
                            onEdit = {
                                editingSale = sale
                                showSaleForm = true
                            },
                            onVoid = {
                                if (state.settings.requireAdminPin && state.settings.adminPin.isNotBlank()) {
                                    pendingPinVoidSale = sale
                                } else {
                                    voidingSale = sale
                                }
                            },
                            onPrint = {
                                val pdf = PdfDocumentGenerator.generateSaleInvoicePdf(context, state.settings, sale)
                                PdfDocumentGenerator.printPdfFile(context, pdf, "Invoice_${sale.invoiceNumber}")
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSaleForm) {
        SaleFormDialog(
            state = state,
            editingSale = editingSale,
            viewModel = viewModel,
            onDismiss = { showSaleForm = false },
            onSaleCreated = { created ->
                showSaleForm = false
                previewingSale = created
            },
            onSaleCorrected = {
                showSaleForm = false
            }
        )
    }

    previewingSale?.let { sale ->
        SaleInvoicePreviewDialog(
            settings = state.settings,
            sale = sale,
            onDismiss = { previewingSale = null },
            onPrint = {
                val pdf = PdfDocumentGenerator.generateSaleInvoicePdf(context, state.settings, sale)
                PdfDocumentGenerator.printPdfFile(context, pdf, "Invoice_${sale.invoiceNumber}")
            },
            onDownloadPdf = {
                val pdf = PdfDocumentGenerator.generateSaleInvoicePdf(context, state.settings, sale)
                pendingPdfFileToSave = pdf
                savePdfLauncher.launch("Invoice_${sale.invoiceNumber}.pdf")
            },
            onSharePdf = {
                val pdf = PdfDocumentGenerator.generateSaleInvoicePdf(context, state.settings, sale)
                PdfDocumentGenerator.shareFile(context, pdf, "application/pdf", "Share Invoice ${sale.invoiceNumber}")
            }
        )
    }

    pendingPinVoidSale?.let { sale ->
        AdminPinDialog(
            expectedPin = state.settings.adminPin,
            onDismiss = { pendingPinVoidSale = null },
            onAuthorized = {
                pendingPinVoidSale = null
                voidingSale = sale
            }
        )
    }

    voidingSale?.let { sale ->
        val cust = state.customers.firstOrNull { it.id == sale.customerId }
        val matMap = state.materials.associateBy { it.id }
        ReasonInputDialog(
            title = "Void Invoice ${sale.invoiceNumber}",
            subtitle = "Financial records cannot be deleted. Voiding reverses stock and customer balance with an audit log.",
            confirmLabel = "Void Invoice",
            isDestructive = true,
            onDismiss = { voidingSale = null },
            onConfirm = { reason ->
                viewModel.voidSale(sale, cust, matMap, reason)
                voidingSale = null
            }
        )
    }
}

@Composable
private fun SaleInvoiceCard(
    sale: Sale,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onVoid: () -> Unit,
    onPrint: () -> Unit
) {
    val statusColors = when (sale.status) {
        TxStatuses.VOID -> CrimsonAlertBg to CrimsonAlert
        TxStatuses.CORRECTED -> QuarryAmberContainer to QuarryAmber
        else -> EmeraldProfitBg to EmeraldProfit
    }

    Card(
        onClick = onPreview,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(sale.invoiceNumber, style = MonoNumberStyle.copy(fontSize = 14.sp), color = SlateNavy)
                    Text(sale.customerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${AccountingEngine.formatDate(sale.transactionDateMillis)} • ${sale.paymentMethod}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(color = statusColors.first, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = sale.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColors.second,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = sale.items.joinToString(", ") { "${it.materialName} (${AccountingEngine.formatQty(it.quantity)} ${it.unit})" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Grand Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(AccountingEngine.formatPkr(sale.grandTotal), style = MonoNumberStyle.copy(fontSize = 15.sp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Paid / Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${AccountingEngine.formatPkr(sale.paidAmount)} / ${AccountingEngine.formatPkr(sale.remainingBalance)}",
                        style = MonoNumberStyle.copy(fontSize = 13.sp),
                        color = if (sale.remainingBalance > 0) CrimsonAlert else EmeraldProfit
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onPrint) {
                    Icon(Icons.Default.Print, contentDescription = "Print Invoice")
                }
                if (sale.status != TxStatuses.VOID) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Correct Invoice")
                    }
                    IconButton(onClick = onVoid) {
                        Icon(Icons.Default.Block, contentDescription = "Void Invoice", tint = CrimsonAlert)
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleFormDialog(
    state: CrusherWorkspaceState,
    editingSale: Sale?,
    viewModel: CrusherViewModel,
    onDismiss: () -> Unit,
    onSaleCreated: (Sale) -> Unit,
    onSaleCorrected: () -> Unit
) {
    val activeCustomers = state.customers.filter { !it.archived }
    val activeMaterials = state.materials.filter { !it.archived }
    val matMap = remember(state.materials) { state.materials.associateBy { it.id } }

    var selectedCustomer by remember {
        mutableStateOf(
            activeCustomers.firstOrNull { it.id == editingSale?.customerId } ?: activeCustomers.firstOrNull()
        )
    }
    var showQuickAddCustomer by remember { mutableStateOf(false) }

    val lineItems = remember {
        mutableStateListOf<LineItem>().apply {
            if (editingSale != null) addAll(editingSale.items)
        }
    }
    var selectedMaterial by remember { mutableStateOf(activeMaterials.firstOrNull()) }
    var itemQty by remember { mutableStateOf("") }
    var itemUnit by remember(selectedMaterial) { mutableStateOf(selectedMaterial?.unit ?: state.settings.defaultUnit) }
    var itemRate by remember(selectedMaterial) {
        mutableStateOf(selectedMaterial?.saleRate?.takeIf { it > 0 }?.toString() ?: "")
    }

    var discountText by remember { mutableStateOf(editingSale?.discount?.toString() ?: "0") }
    var paidText by remember { mutableStateOf(editingSale?.paidAmount?.toString() ?: "") }
    var paymentMethod by remember { mutableStateOf(editingSale?.paymentMethod ?: PaymentMethods.CASH) }
    var dateMillis by remember { mutableStateOf(editingSale?.transactionDateMillis ?: System.currentTimeMillis()) }
    var notes by remember { mutableStateOf(editingSale?.notes ?: "") }
    var auditReason by remember { mutableStateOf("") }
    var authorizeNegative by remember { mutableStateOf(state.settings.allowNegativeStock) }

    val discount = discountText.toDoubleOrNull() ?: 0.0
    val previewSubtotal = lineItems.sumOf { it.lineTotal }
    val previewGrandTotal = (previewSubtotal - discount.coerceIn(0.0, previewSubtotal)).coerceAtLeast(0.0)
    val paidAmount = (paidText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, previewGrandTotal)
    val remaining = (previewGrandTotal - paidAmount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (editingSale == null) "New Sales Invoice" else "Correct ${editingSale.invoiceNumber}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DropdownSelector(
                        label = "Customer*",
                        selectedText = selectedCustomer?.name ?: "Select or add customer",
                        options = activeCustomers,
                        optionLabel = { "${it.name} (Bal: ${AccountingEngine.formatPkr(it.currentBalance)})" },
                        onSelect = { selectedCustomer = it },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showQuickAddCustomer = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Quick Add Customer")
                    }
                }

                DatePickerButton(
                    dateMillis = dateMillis,
                    onDateSelected = { dateMillis = it },
                    label = "Invoice Date",
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()
                Text("Add Material Line Item", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                DropdownSelector(
                    label = "Material",
                    selectedText = selectedMaterial?.let { "${it.name} (Stock: ${AccountingEngine.formatQty(it.currentStock)} ${it.unit})" }
                        ?: "Select Material",
                    options = activeMaterials,
                    optionLabel = { "${it.name} • Stock: ${AccountingEngine.formatQty(it.currentStock)} ${it.unit}" },
                    onSelect = { selectedMaterial = it }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = itemQty,
                        onValueChange = { itemQty = it },
                        label = { Text("Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    DropdownSelector(
                        label = "Unit",
                        selectedText = itemUnit,
                        options = DefaultUnits.all,
                        optionLabel = { it },
                        onSelect = { itemUnit = it },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = itemRate,
                        onValueChange = { itemRate = it },
                        label = { Text("Rate") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedButton(
                    onClick = {
                        val mat = selectedMaterial ?: return@OutlinedButton
                        val q = itemQty.toDoubleOrNull() ?: 0.0
                        val r = itemRate.toDoubleOrNull() ?: 0.0
                        if (q > 0.0 && r >= 0.0) {
                            lineItems += LineItem(
                                materialId = mat.id,
                                materialName = mat.name,
                                specification = mat.specification,
                                unit = itemUnit,
                                quantity = q,
                                rate = r,
                                costRate = if (mat.avgCost > 0.0) mat.avgCost else mat.purchaseRate,
                                lineTotal = AccountingEngine.calculateLineTotal(q, r)
                            )
                            itemQty = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Item to Invoice")
                }

                lineItems.forEachIndexed { idx, item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.materialName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "${AccountingEngine.formatQty(item.quantity)} ${item.unit} × ${AccountingEngine.formatPkr(item.rate)} = ${AccountingEngine.formatPkr(item.lineTotal)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { lineItems.removeAt(idx) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Line", tint = CrimsonAlert)
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = discountText,
                        onValueChange = { discountText = it },
                        label = { Text("Discount (PKR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = paidText,
                        onValueChange = { paidText = it },
                        label = { Text("Paid Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                DropdownSelector(
                    label = "Payment Method",
                    selectedText = paymentMethod,
                    options = PaymentMethods.transactionMethods,
                    optionLabel = { it },
                    onSelect = { paymentMethod = it }
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Delivery / Vehicle / Notes") },
                    singleLine = true
                )

                if (editingSale != null) {
                    OutlinedTextField(
                        value = auditReason,
                        onValueChange = { auditReason = it },
                        label = { Text("Correction Audit Reason*") },
                        singleLine = true
                    )
                }

                if (!state.settings.allowNegativeStock) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = authorizeNegative, onCheckedChange = { authorizeNegative = it })
                        Text("Authorize negative stock override", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Grand Total: ${AccountingEngine.formatPkr(previewGrandTotal)}", style = MonoNumberStyle.copy(fontSize = 14.sp))
                        Text("Remaining Balance: ${AccountingEngine.formatPkr(remaining)}", style = MonoNumberStyle.copy(fontSize = 13.sp), color = CrimsonAlert)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cust = selectedCustomer ?: return@Button
                    if (editingSale == null) {
                        viewModel.createSale(
                            settings = state.settings,
                            customer = cust,
                            items = lineItems.toList(),
                            materialsById = matMap,
                            discount = discount,
                            paidAmount = paidAmount,
                            paymentMethod = paymentMethod,
                            dateMillis = dateMillis,
                            notes = notes,
                            authorizeNegativeStock = authorizeNegative,
                            onCreated = onSaleCreated
                        )
                    } else {
                        viewModel.correctSale(
                            settings = state.settings,
                            oldSale = editingSale,
                            customer = cust,
                            newItems = lineItems.toList(),
                            materialsById = matMap,
                            discount = discount,
                            paidAmount = paidAmount,
                            paymentMethod = paymentMethod,
                            dateMillis = dateMillis,
                            notes = notes,
                            auditReason = auditReason,
                            authorizeNegativeStock = authorizeNegative,
                            onDone = onSaleCorrected
                        )
                    }
                },
                enabled = selectedCustomer != null && lineItems.isNotEmpty() && (editingSale == null || auditReason.isNotBlank())
            ) {
                Text(if (editingSale == null) "Confirm Sale" else "Save Correction")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showQuickAddCustomer) {
        QuickPartyDialog(
            title = "Quick Add Customer",
            onDismiss = { showQuickAddCustomer = false },
            onSave = { name, phone, address, opening ->
                val newCust = Customer(name = name, phone = phone, address = address, openingBalance = opening, currentBalance = opening)
                viewModel.saveCustomer(newCust, true) { newId ->
                    selectedCustomer = newCust.copy(id = newId)
                    showQuickAddCustomer = false
                }
            }
        )
    }
}

@Composable
private fun SaleInvoicePreviewDialog(
    settings: BusinessSettings,
    sale: Sale,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onDownloadPdf: () -> Unit,
    onSharePdf: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A4 Invoice ${sale.invoiceNumber}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(color = SlateNavy, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(settings.businessName, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(settings.address, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                        if (settings.phone.isNotBlank()) {
                            Text("Tel: ${settings.phone}", color = Color(0xFFFBBF24), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text("Customer: ${sale.customerName} (${sale.customerPhone.ifBlank { "N/A" }})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Date: ${AccountingEngine.formatDate(sale.transactionDateMillis)} • Status: ${sale.status}", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                sale.items.forEachIndexed { idx, item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${idx + 1}. ${item.materialName} (${AccountingEngine.formatQty(item.quantity)} ${item.unit})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(AccountingEngine.formatPkr(item.lineTotal), style = MonoNumberStyle.copy(fontSize = 12.sp))
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", style = MaterialTheme.typography.bodySmall)
                    Text(AccountingEngine.formatPkr(sale.subtotal), style = MonoNumberStyle.copy(fontSize = 12.sp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Discount", style = MaterialTheme.typography.bodySmall)
                    Text(AccountingEngine.formatPkr(sale.discount), style = MonoNumberStyle.copy(fontSize = 12.sp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Grand Total", fontWeight = FontWeight.Bold)
                    Text(AccountingEngine.formatPkr(sale.grandTotal), style = MonoNumberStyle.copy(fontSize = 14.sp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Paid (${sale.paymentMethod})", color = EmeraldProfit)
                    Text(AccountingEngine.formatPkr(sale.paidAmount), style = MonoNumberStyle.copy(fontSize = 13.sp), color = EmeraldProfit)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Remaining Balance", color = CrimsonAlert, fontWeight = FontWeight.Bold)
                    Text(AccountingEngine.formatPkr(sale.remainingBalance), style = MonoNumberStyle.copy(fontSize = 14.sp), color = CrimsonAlert)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onDownloadPdf) {
                    Icon(Icons.Default.Download, contentDescription = "Download PDF", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF")
                }
                OutlinedButton(onClick = onSharePdf) {
                    Icon(Icons.Default.Share, contentDescription = "Share PDF", modifier = Modifier.size(16.dp))
                }
                Button(onClick = onPrint) {
                    Icon(Icons.Default.Print, contentDescription = "Print", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Print")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun QuickPartyDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name*") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, singleLine = true)
                OutlinedTextField(
                    value = opening,
                    onValueChange = { opening = it },
                    label = { Text("Opening Balance (PKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), phone.trim(), address.trim(), opening.toDoubleOrNull() ?: 0.0) },
                enabled = name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
