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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AccountingEngine
import com.example.domain.DefaultUnits
import com.example.domain.LineItem
import com.example.domain.PaymentMethods
import com.example.domain.Purchase
import com.example.domain.ReportDateFilter
import com.example.domain.Supplier
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
import com.example.ui.theme.SlateNavy
import java.io.File

@Composable
fun PurchasesScreen(
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

    var showPurchaseForm by remember(openNewTrigger) { mutableStateOf(openNewTrigger) }
    if (openNewTrigger) {
        onConsumeNewTrigger()
    }
    var pendingPinVoidPurchase by remember { mutableStateOf<Purchase?>(null) }
    var voidingPurchase by remember { mutableStateOf<Purchase?>(null) }
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

    val filteredPurchases = remember(state.purchases, searchQuery, rangeStart, rangeEnd) {
        state.purchases.filter { p ->
            p.transactionDateMillis in rangeStart..rangeEnd &&
                (searchQuery.isBlank() ||
                    p.voucherNumber.contains(searchQuery, true) ||
                    p.supplierName.contains(searchQuery, true) ||
                    p.items.any { it.materialName.contains(searchQuery, true) })
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPurchaseForm = true },
                modifier = Modifier.testTag("new_purchase_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Purchase Voucher")
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
                placeholder = strings.searchPurchasesPlaceholder,
                tag = "purchases_search"
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

            if (filteredPurchases.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.ShoppingBag,
                    title = strings.noPurchasesTitle,
                    message = strings.noPurchasesMessage,
                    actionLabel = strings.createPurchaseVoucher,
                    onAction = { showPurchaseForm = true }
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filteredPurchases, key = { it.id }) { purchase ->
                        PurchaseVoucherCard(
                            purchase = purchase,
                            onPrint = {
                                val pdf = PdfDocumentGenerator.generatePurchaseVoucherPdf(context, state.settings, purchase)
                                PdfDocumentGenerator.printPdfFile(context, pdf, "Voucher_${purchase.voucherNumber}")
                            },
                            onDownloadPdf = {
                                val pdf = PdfDocumentGenerator.generatePurchaseVoucherPdf(context, state.settings, purchase)
                                pendingPdfFileToSave = pdf
                                savePdfLauncher.launch("Voucher_${purchase.voucherNumber}.pdf")
                            },
                            onVoid = {
                                if (state.settings.requireAdminPin && state.settings.adminPin.isNotBlank()) {
                                    pendingPinVoidPurchase = purchase
                                } else {
                                    voidingPurchase = purchase
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPurchaseForm) {
        PurchaseFormDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { showPurchaseForm = false },
            onCreated = { showPurchaseForm = false }
        )
    }

    pendingPinVoidPurchase?.let { purchase ->
        AdminPinDialog(
            expectedPin = state.settings.adminPin,
            onDismiss = { pendingPinVoidPurchase = null },
            onAuthorized = {
                pendingPinVoidPurchase = null
                voidingPurchase = purchase
            }
        )
    }

    voidingPurchase?.let { purchase ->
        val sup = state.suppliers.firstOrNull { it.id == purchase.supplierId }
        val matMap = state.materials.associateBy { it.id }
        ReasonInputDialog(
            title = "Void Voucher ${purchase.voucherNumber}",
            subtitle = "Reverses purchased stock and supplier payable balance with an audit log.",
            confirmLabel = "Void Voucher",
            isDestructive = true,
            onDismiss = { voidingPurchase = null },
            onConfirm = { reason ->
                viewModel.voidPurchase(purchase, sup, matMap, reason)
                voidingPurchase = null
            }
        )
    }
}

@Composable
private fun PurchaseVoucherCard(
    purchase: Purchase,
    onPrint: () -> Unit,
    onDownloadPdf: () -> Unit,
    onVoid: () -> Unit
) {
    val isVoid = purchase.status == TxStatuses.VOID
    Card(
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
                    Text(purchase.voucherNumber, style = MonoNumberStyle.copy(fontSize = 14.sp), color = SlateNavy)
                    Text(purchase.supplierName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${AccountingEngine.formatDate(purchase.transactionDateMillis)} • ${purchase.paymentMethod}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = if (isVoid) CrimsonAlertBg else EmeraldProfitBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = purchase.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isVoid) CrimsonAlert else EmeraldProfit,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = purchase.items.joinToString(", ") { "${it.materialName} (+${AccountingEngine.formatQty(it.quantity)} ${it.unit})" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Purchase", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(AccountingEngine.formatPkr(purchase.grandTotal), style = MonoNumberStyle.copy(fontSize = 15.sp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Paid / Payable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${AccountingEngine.formatPkr(purchase.paidAmount)} / ${AccountingEngine.formatPkr(purchase.remainingBalance)}",
                        style = MonoNumberStyle.copy(fontSize = 13.sp),
                        color = if (purchase.remainingBalance > 0) CrimsonAlert else EmeraldProfit
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDownloadPdf) {
                    Icon(Icons.Default.Download, contentDescription = "Save Voucher PDF")
                }
                IconButton(onClick = onPrint) {
                    Icon(Icons.Default.Print, contentDescription = "Print Voucher")
                }
                if (!isVoid) {
                    IconButton(onClick = onVoid) {
                        Icon(Icons.Default.Block, contentDescription = "Void Voucher", tint = CrimsonAlert)
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseFormDialog(
    state: CrusherWorkspaceState,
    viewModel: CrusherViewModel,
    onDismiss: () -> Unit,
    onCreated: (Purchase) -> Unit
) {
    val activeSuppliers = state.suppliers.filter { !it.archived }
    val activeMaterials = state.materials.filter { !it.archived }
    val matMap = remember(state.materials) { state.materials.associateBy { it.id } }

    var selectedSupplier by remember { mutableStateOf(activeSuppliers.firstOrNull()) }
    var showQuickAddSupplier by remember { mutableStateOf(false) }

    val lineItems = remember { mutableStateListOf<LineItem>() }
    var selectedMaterial by remember { mutableStateOf(activeMaterials.firstOrNull()) }
    var itemQty by remember { mutableStateOf("") }
    var itemUnit by remember(selectedMaterial) { mutableStateOf(selectedMaterial?.unit ?: state.settings.defaultUnit) }
    var itemRate by remember(selectedMaterial) {
        mutableStateOf(selectedMaterial?.purchaseRate?.takeIf { it > 0 }?.toString() ?: "")
    }

    var discountText by remember { mutableStateOf("0") }
    var paidText by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(PaymentMethods.CASH) }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }

    val discount = discountText.toDoubleOrNull() ?: 0.0
    val subtotal = lineItems.sumOf { it.lineTotal }
    val grandTotal = (subtotal - discount.coerceIn(0.0, subtotal)).coerceAtLeast(0.0)
    val paidAmount = (paidText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, grandTotal)
    val remaining = (grandTotal - paidAmount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Purchase Voucher", fontWeight = FontWeight.Bold) },
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
                        label = "Supplier*",
                        selectedText = selectedSupplier?.name ?: "Select or add supplier",
                        options = activeSuppliers,
                        optionLabel = { "${it.name} (Payable: ${AccountingEngine.formatPkr(it.currentBalance)})" },
                        onSelect = { selectedSupplier = it },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showQuickAddSupplier = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Quick Add Supplier")
                    }
                }

                DatePickerButton(
                    dateMillis = dateMillis,
                    onDateSelected = { dateMillis = it },
                    label = "Purchase Date",
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()
                DropdownSelector(
                    label = "Material",
                    selectedText = selectedMaterial?.name ?: "Select Material",
                    options = activeMaterials,
                    optionLabel = { "${it.name} (${it.unit})" },
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
                        label = { Text("Buy Rate") },
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
                                costRate = r,
                                lineTotal = AccountingEngine.calculateLineTotal(q, r)
                            )
                            itemQty = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Item to Voucher")
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
                                    "+${AccountingEngine.formatQty(item.quantity)} ${item.unit} × ${AccountingEngine.formatPkr(item.rate)} = ${AccountingEngine.formatPkr(item.lineTotal)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { lineItems.removeAt(idx) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = CrimsonAlert)
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = discountText,
                        onValueChange = { discountText = it },
                        label = { Text("Discount") },
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
                    label = { Text("Bilty / Vehicle / Notes") },
                    singleLine = true
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Grand Total: ${AccountingEngine.formatPkr(grandTotal)}", style = MonoNumberStyle.copy(fontSize = 14.sp))
                        Text("Payable Balance: ${AccountingEngine.formatPkr(remaining)}", style = MonoNumberStyle.copy(fontSize = 13.sp), color = CrimsonAlert)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sup = selectedSupplier ?: return@Button
                    viewModel.createPurchase(
                        settings = state.settings,
                        supplier = sup,
                        items = lineItems.toList(),
                        materialsById = matMap,
                        discount = discount,
                        paidAmount = paidAmount,
                        paymentMethod = paymentMethod,
                        dateMillis = dateMillis,
                        notes = notes,
                        onCreated = onCreated
                    )
                },
                enabled = selectedSupplier != null && lineItems.isNotEmpty()
            ) {
                Text("Confirm Purchase")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showQuickAddSupplier) {
        QuickPartyDialog(
            title = "Quick Add Supplier",
            onDismiss = { showQuickAddSupplier = false },
            onSave = { name, phone, address, opening ->
                val newSup = Supplier(name = name, phone = phone, address = address, openingBalance = opening, currentBalance = opening)
                viewModel.saveSupplier(newSup, true) { newId ->
                    selectedSupplier = newSup.copy(id = newId)
                    showQuickAddSupplier = false
                }
            }
        )
    }
}
