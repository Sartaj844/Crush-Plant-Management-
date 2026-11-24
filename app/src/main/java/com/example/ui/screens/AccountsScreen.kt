package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.domain.Customer
import com.example.domain.LedgerEntry
import com.example.domain.PartyPayment
import com.example.domain.PaymentMethods
import com.example.domain.Supplier
import com.example.export.PdfDocumentGenerator
import com.example.ui.CrusherViewModel
import com.example.ui.CrusherWorkspaceState
import com.example.ui.components.DatePickerButton
import com.example.ui.components.DropdownSelector
import com.example.ui.components.EmptyStateView
import com.example.ui.components.SearchInputField
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoNumberStyle
import com.example.ui.theme.SlateNavy
import java.io.File

@Composable
fun AccountsScreen(
    state: CrusherWorkspaceState,
    viewModel: CrusherViewModel
) {
    val strings = LocalAppStrings.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Customers, 1 = Suppliers
    var searchQuery by remember { mutableStateOf("") }
    var selectedCustomerId by remember { mutableStateOf<String?>(null) }
    var selectedSupplierId by remember { mutableStateOf<String?>(null) }

    var showAddPartyDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }
    var editingSupplier by remember { mutableStateOf<Supplier?>(null) }

    val selectedCustomer = remember(selectedCustomerId, state.customers) {
        selectedCustomerId?.let { id -> state.customers.firstOrNull { it.id == id } }
    }
    val selectedSupplier = remember(selectedSupplierId, state.suppliers) {
        selectedSupplierId?.let { id -> state.suppliers.firstOrNull { it.id == id } }
    }

    if (selectedCustomer != null) {
        BackHandler { selectedCustomerId = null }
        val entries = remember(selectedCustomer, state.sales, state.payments) {
            AccountingEngine.computeCustomerLedger(selectedCustomer, state.sales, state.payments)
        }
        PartyLedgerDetailView(
            state = state,
            partyType = "CUSTOMER",
            partyId = selectedCustomer.id,
            partyName = selectedCustomer.name,
            partyPhone = selectedCustomer.phone,
            partyAddress = selectedCustomer.address,
            currentBalance = selectedCustomer.currentBalance,
            entries = entries,
            viewModel = viewModel,
            onBack = { selectedCustomerId = null }
        )
        return
    }

    if (selectedSupplier != null) {
        BackHandler { selectedSupplierId = null }
        val entries = remember(selectedSupplier, state.purchases, state.payments) {
            AccountingEngine.computeSupplierLedger(selectedSupplier, state.purchases, state.payments)
        }
        PartyLedgerDetailView(
            state = state,
            partyType = "SUPPLIER",
            partyId = selectedSupplier.id,
            partyName = selectedSupplier.name,
            partyPhone = selectedSupplier.phone,
            partyAddress = selectedSupplier.address,
            currentBalance = selectedSupplier.currentBalance,
            entries = entries,
            viewModel = viewModel,
            onBack = { selectedSupplierId = null }
        )
        return
    }

    val filteredCustomers = remember(state.customers, searchQuery) {
        state.customers.filter {
            !it.archived && (searchQuery.isBlank() || it.name.contains(searchQuery, true) || it.phone.contains(searchQuery, true))
        }
    }
    val filteredSuppliers = remember(state.suppliers, searchQuery) {
        state.suppliers.filter {
            !it.archived && (searchQuery.isBlank() || it.name.contains(searchQuery, true) || it.phone.contains(searchQuery, true))
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCustomer = null
                    editingSupplier = null
                    showAddPartyDialog = true
                },
                modifier = Modifier.testTag("add_party_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Account")
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
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) {
                    Text("${strings.customersTab} (${filteredCustomers.size})")
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) {
                    Text("${strings.suppliersTab} (${filteredSuppliers.size})")
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedTab == 0) strings.totalReceivableLabel else strings.totalPayableLabel,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = AccountingEngine.formatPkr(
                            if (selectedTab == 0) state.metrics.customerOutstanding else state.metrics.supplierOutstanding
                        ),
                        style = MonoNumberStyle.copy(fontSize = 15.sp),
                        color = if (selectedTab == 0) EmeraldProfit else CrimsonAlert
                    )
                }
            }

            SearchInputField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = if (selectedTab == 0) strings.searchCustomersPlaceholder else strings.searchSuppliersPlaceholder,
                tag = "accounts_search"
            )

            if (selectedTab == 0) {
                if (filteredCustomers.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.People,
                        title = "No Customers Yet",
                        message = "Add contractors, builders, or dealers to track sales, recoveries, and Khata balances.",
                        actionLabel = strings.addCustomer,
                        onAction = { showAddPartyDialog = true }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(filteredCustomers, key = { it.id }) { cust ->
                            PartyAccountCard(
                                name = cust.name,
                                phone = cust.phone,
                                address = cust.address,
                                balance = cust.currentBalance,
                                balanceLabel = if (cust.currentBalance >= 0) "Receivable" else "Advance",
                                positiveIsGreen = true,
                                onOpenLedger = { selectedCustomerId = cust.id },
                                onEdit = {
                                    editingCustomer = cust
                                    showAddPartyDialog = true
                                }
                            )
                        }
                    }
                }
            } else {
                if (filteredSuppliers.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.People,
                        title = "No Suppliers Yet",
                        message = "Add stone crushers, cement agencies, or steel suppliers to manage purchases and payables.",
                        actionLabel = strings.addSupplier,
                        onAction = { showAddPartyDialog = true }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(filteredSuppliers, key = { it.id }) { sup ->
                            PartyAccountCard(
                                name = sup.name,
                                phone = sup.phone,
                                address = sup.address,
                                balance = sup.currentBalance,
                                balanceLabel = if (sup.currentBalance >= 0) "Payable" else "Advance",
                                positiveIsGreen = false,
                                onOpenLedger = { selectedSupplierId = sup.id },
                                onEdit = {
                                    editingSupplier = sup
                                    showAddPartyDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddPartyDialog) {
        if (selectedTab == 0) {
            PartyEditorDialog(
                title = if (editingCustomer == null) "Add Customer" else "Edit Customer",
                initialName = editingCustomer?.name ?: "",
                initialPhone = editingCustomer?.phone ?: "",
                initialAddress = editingCustomer?.address ?: "",
                initialOpening = editingCustomer?.openingBalance?.toString() ?: "0",
                initialNotes = editingCustomer?.notes ?: "",
                isNew = editingCustomer == null,
                onDismiss = { showAddPartyDialog = false },
                onSave = { name, phone, address, opening, notes ->
                    val target = (editingCustomer ?: Customer()).copy(
                        name = name,
                        phone = phone,
                        address = address,
                        openingBalance = if (editingCustomer == null) opening else editingCustomer!!.openingBalance,
                        currentBalance = if (editingCustomer == null) opening else editingCustomer!!.currentBalance,
                        notes = notes
                    )
                    viewModel.saveCustomer(target, editingCustomer == null) { showAddPartyDialog = false }
                }
            )
        } else {
            PartyEditorDialog(
                title = if (editingSupplier == null) "Add Supplier" else "Edit Supplier",
                initialName = editingSupplier?.name ?: "",
                initialPhone = editingSupplier?.phone ?: "",
                initialAddress = editingSupplier?.address ?: "",
                initialOpening = editingSupplier?.openingBalance?.toString() ?: "0",
                initialNotes = editingSupplier?.notes ?: "",
                isNew = editingSupplier == null,
                onDismiss = { showAddPartyDialog = false },
                onSave = { name, phone, address, opening, notes ->
                    val target = (editingSupplier ?: Supplier()).copy(
                        name = name,
                        phone = phone,
                        address = address,
                        openingBalance = if (editingSupplier == null) opening else editingSupplier!!.openingBalance,
                        currentBalance = if (editingSupplier == null) opening else editingSupplier!!.currentBalance,
                        notes = notes
                    )
                    viewModel.saveSupplier(target, editingSupplier == null) { showAddPartyDialog = false }
                }
            )
        }
    }
}

@Composable
private fun PartyAccountCard(
    name: String,
    phone: String,
    address: String,
    balance: Double,
    balanceLabel: String,
    positiveIsGreen: Boolean,
    onOpenLedger: () -> Unit,
    onEdit: () -> Unit
) {
    val balColor = when {
        balance == 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
        positiveIsGreen && balance > 0 -> EmeraldProfit
        !positiveIsGreen && balance > 0 -> CrimsonAlert
        else -> SlateNavy
    }

    Card(
        onClick = onOpenLedger,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = listOf(phone, address).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Tap to view ledger" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(balanceLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(AccountingEngine.formatPkr(balance), style = MonoNumberStyle.copy(fontSize = 15.sp), color = balColor)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Account")
            }
        }
    }
}

@Composable
private fun PartyLedgerDetailView(
    state: CrusherWorkspaceState,
    partyType: String,
    partyId: String,
    partyName: String,
    partyPhone: String,
    partyAddress: String,
    currentBalance: Double,
    entries: List<LedgerEntry>,
    viewModel: CrusherViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var showPaymentDialog by remember { mutableStateOf(false) }
    var previewingPayment by remember { mutableStateOf<PartyPayment?>(null) }
    var pendingPdf by remember { mutableStateOf<File?>(null) }

    val paymentsByLedgerId = remember(state.payments, partyId, partyType) {
        state.payments
            .filter { it.partyId == partyId && it.partyType == partyType }
            .associateBy { "pay_${it.id}" }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val file = pendingPdf
        if (uri != null && file != null && file.exists()) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
        }
        pendingPdf = null
    }

    val ledgerTitle = if (partyType == "CUSTOMER") "CUSTOMER LEDGER" else "SUPPLIER LEDGER"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backToAccounts)
                }
                Column {
                    Text(partyName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${if (partyType == "CUSTOMER") strings.customersTab else strings.suppliersTab} • ${partyPhone.ifBlank { "No Phone" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                IconButton(onClick = {
                    val pdf = PdfDocumentGenerator.generatePartyLedgerPdf(
                        context, state.settings, ledgerTitle, partyName, partyPhone, partyAddress, entries, currentBalance
                    )
                    pendingPdf = pdf
                    savePdfLauncher.launch("Ledger_${partyName.replace(" ", "_")}.pdf")
                }) {
                    Icon(Icons.Default.Download, contentDescription = strings.savePdf)
                }
                IconButton(onClick = {
                    val pdf = PdfDocumentGenerator.generatePartyLedgerPdf(
                        context, state.settings, ledgerTitle, partyName, partyPhone, partyAddress, entries, currentBalance
                    )
                    PdfDocumentGenerator.printPdfFile(context, pdf, "Ledger_$partyName")
                }) {
                    Icon(Icons.Default.Print, contentDescription = strings.print)
                }
            }
        }

        Surface(
            color = SlateNavy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.outstandingBalanceUpper, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondaryContainer)
                    Text(
                        AccountingEngine.formatPkr(currentBalance),
                        style = MonoNumberStyle.copy(fontSize = 20.sp),
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
                Button(
                    onClick = { showPaymentDialog = true },
                    modifier = Modifier.testTag("record_party_payment_button")
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (partyType == "CUSTOMER") strings.recordRecovery else strings.recordPayment)
                }
            }
        }

        if (entries.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Payments,
                title = "No Ledger Transactions",
                message = "Sales, purchases, and payments for $partyName will appear here."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(entries.reversed(), key = { it.id }) { entry ->
                    val linkedPayment = paymentsByLedgerId[entry.id]
                    Card(
                        onClick = { if (linkedPayment != null) previewingPayment = linkedPayment },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "${if (entry.dateMillis == 0L) "Opening" else AccountingEngine.formatDate(entry.dateMillis)} • ${entry.referenceNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    if (entry.debit > 0) {
                                        Text("Dr ${AccountingEngine.formatPkr(entry.debit)}", style = MonoNumberStyle.copy(fontSize = 12.sp), color = CrimsonAlert)
                                    }
                                    if (entry.credit > 0) {
                                        Text("Cr ${AccountingEngine.formatPkr(entry.credit)}", style = MonoNumberStyle.copy(fontSize = 12.sp), color = EmeraldProfit)
                                    }
                                    Text(
                                        "Bal: ${AccountingEngine.formatPkr(entry.runningBalance)}",
                                        style = MonoNumberStyle.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (linkedPayment != null) {
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.receipt,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val pdf = PdfDocumentGenerator.generatePaymentReceiptPdf(
                                                    context = context,
                                                    settings = state.settings,
                                                    payment = linkedPayment,
                                                    partyPhone = partyPhone,
                                                    partyAddress = partyAddress,
                                                    closingBalanceAfter = entry.runningBalance
                                                )
                                                pendingPdf = pdf
                                                savePdfLauncher.launch("Receipt_${linkedPayment.referenceNumber.ifBlank { linkedPayment.id }}.pdf")
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = strings.saveReceiptPdf, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.pdf, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Button(
                                            onClick = { previewingPayment = linkedPayment },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.ReceiptLong, contentDescription = strings.printReceipt, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.receipt, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPaymentDialog) {
        PartyPaymentDialog(
            partyType = partyType,
            partyName = partyName,
            currentBalance = currentBalance,
            onDismiss = { showPaymentDialog = false },
            onSave = { entryType, amount, method, ref, notes, dateMillis ->
                viewModel.recordPartyPayment(
                    partyType = partyType,
                    partyId = partyId,
                    partyName = partyName,
                    currentBalance = currentBalance,
                    entryType = entryType,
                    amount = amount,
                    paymentMethod = method,
                    referenceNumber = ref,
                    notes = notes,
                    dateMillis = dateMillis
                ) { createdPayment ->
                    showPaymentDialog = false
                    previewingPayment = createdPayment
                }
            }
        )
    }

    previewingPayment?.let { payment ->
        val receiptFile = remember(payment, state.settings, currentBalance) {
            PdfDocumentGenerator.generatePaymentReceiptPdf(
                context = context,
                settings = state.settings,
                payment = payment,
                partyPhone = partyPhone,
                partyAddress = partyAddress,
                closingBalanceAfter = currentBalance
            )
        }
        PaymentReceiptPreviewDialog(
            payment = payment,
            closingBalance = currentBalance,
            onPrint = {
                PdfDocumentGenerator.printPdfFile(
                    context = context,
                    pdfFile = receiptFile,
                    jobName = "Receipt_${payment.referenceNumber.ifBlank { payment.id }}"
                )
            },
            onSavePdf = {
                pendingPdf = receiptFile
                savePdfLauncher.launch("Receipt_${payment.referenceNumber.ifBlank { payment.id }}.pdf")
            },
            onShare = {
                PdfDocumentGenerator.shareFile(
                    context = context,
                    file = receiptFile,
                    mimeType = "application/pdf",
                    chooserTitle = "Share ${strings.paymentReceiptTitle}"
                )
            },
            onDismiss = { previewingPayment = null }
        )
    }
}

@Composable
private fun PaymentReceiptPreviewDialog(
    payment: PartyPayment,
    closingBalance: Double,
    onPrint: () -> Unit,
    onSavePdf: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val isCustomer = payment.partyType == "CUSTOMER"
    val receiptTitle = if (isCustomer) strings.recoveryReceiptTitle else strings.paymentReceiptTitle
    val refNo = payment.referenceNumber.ifBlank { "RCPT-${payment.id.takeLast(6).uppercase()}" }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = EmeraldProfit) },
        title = { Text(receiptTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(payment.partyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${AccountingEngine.formatDate(payment.transactionDateMillis)} • $refNo", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isCustomer) "Received:" else "Paid:", style = MaterialTheme.typography.bodyMedium)
                    Text(AccountingEngine.formatPkr(payment.amount), style = MonoNumberStyle, color = EmeraldProfit)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Method:", style = MaterialTheme.typography.bodySmall)
                    Text(strings.paymentMethodLabel(payment.paymentMethod), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(strings.outstandingBalanceUpper, style = MaterialTheme.typography.bodySmall)
                    Text(AccountingEngine.formatPkr(closingBalance), style = MonoNumberStyle.copy(fontSize = 13.sp))
                }
                if (payment.notes.isNotBlank()) {
                    Text(payment.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = strings.share, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.share)
                }
                OutlinedButton(onClick = onSavePdf) {
                    Icon(Icons.Default.Download, contentDescription = strings.savePdf, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.pdf)
                }
                Button(onClick = onPrint) {
                    Icon(Icons.Default.Print, contentDescription = strings.print, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.print)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )
}

@Composable
private fun PartyEditorDialog(
    title: String,
    initialName: String,
    initialPhone: String,
    initialAddress: String,
    initialOpening: String,
    initialNotes: String,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var address by remember { mutableStateOf(initialAddress) }
    var opening by remember { mutableStateOf(initialOpening) }
    var notes by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Account Name*") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address / Site") }, singleLine = true)
                if (isNew) {
                    OutlinedTextField(
                        value = opening,
                        onValueChange = { opening = it },
                        label = { Text("Opening Balance (PKR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), phone.trim(), address.trim(), opening.toDoubleOrNull() ?: 0.0, notes.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PartyPaymentDialog(
    partyType: String,
    partyName: String,
    currentBalance: Double,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String, String, Long) -> Unit
) {
    val defaultEntryType = if (partyType == "CUSTOMER") "RECOVERY" else "PAYMENT"
    val entryOptions = if (partyType == "CUSTOMER") {
        listOf("RECOVERY", "DEBIT_ADJUSTMENT", "CREDIT_ADJUSTMENT")
    } else {
        listOf("PAYMENT", "DEBIT_ADJUSTMENT", "CREDIT_ADJUSTMENT")
    }
    var entryType by remember { mutableStateOf(defaultEntryType) }
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethods.CASH) }
    var ref by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${if (partyType == "CUSTOMER") "Recovery" else "Payment"}: $partyName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current Balance: ${AccountingEngine.formatPkr(currentBalance)}", style = MaterialTheme.typography.bodySmall)
                DropdownSelector(
                    label = "Entry Type",
                    selectedText = entryType,
                    options = entryOptions,
                    optionLabel = { it },
                    onSelect = { entryType = it }
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (PKR)*") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                DropdownSelector(
                    label = "Method",
                    selectedText = method,
                    options = PaymentMethods.paymentMethods,
                    optionLabel = { it },
                    onSelect = { method = it }
                )
                DatePickerButton(dateMillis = dateMillis, onDateSelected = { dateMillis = it }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ref, onValueChange = { ref = it }, label = { Text("Receipt / Cheque #") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) {
                        onSave(entryType, amt, method, ref, notes, dateMillis)
                    }
                },
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0.0
            ) { Text("Save Entry") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
