package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AccountingEngine
import com.example.domain.Expense
import com.example.domain.ExpenseCategories
import com.example.domain.PaymentMethods
import com.example.domain.ReportDateFilter
import com.example.domain.TxStatuses
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
import com.example.ui.theme.MonoNumberStyle
import com.example.ui.theme.SkyInfoBg

@Composable
fun ExpensesScreen(
    state: CrusherWorkspaceState,
    viewModel: CrusherViewModel,
    openNewTrigger: Boolean = false,
    onConsumeNewTrigger: () -> Unit = {}
) {
    val strings = LocalAppStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var dateFilter by remember { mutableStateOf(ReportDateFilter.MONTH) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }

    var showExpenseDialog by remember(openNewTrigger) { mutableStateOf(openNewTrigger) }
    if (openNewTrigger) {
        onConsumeNewTrigger()
    }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var voidingExpense by remember { mutableStateOf<Expense?>(null) }
    var pinAuthorizingVoidFor by remember { mutableStateOf<Expense?>(null) }

    val (rangeStart, rangeEnd) = remember(dateFilter, customStart, customEnd) {
        AccountingEngine.dateRangeForFilter(dateFilter, System.currentTimeMillis(), customStart, customEnd)
    }

    val filteredExpenses = remember(state.expenses, searchQuery, selectedCategory, rangeStart, rangeEnd) {
        state.expenses.filter { e ->
            e.transactionDateMillis in rangeStart..rangeEnd &&
                (selectedCategory == "All" || e.category == selectedCategory) &&
                (searchQuery.isBlank() ||
                    e.title.contains(searchQuery, true) ||
                    e.paidTo.contains(searchQuery, true) ||
                    e.category.contains(searchQuery, true))
        }
    }

    val totalFiltered = remember(filteredExpenses) {
        filteredExpenses.filter { it.status != TxStatuses.VOID }.sumOf { it.amount }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingExpense = null
                    showExpenseDialog = true
                },
                modifier = Modifier.testTag("new_expense_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = strings.addExpense)
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
            Surface(
                color = SkyInfoBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = strings.expenseOnlyBanner,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            SearchInputField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search expense title, payee, or category...",
                tag = "expense_search"
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory == "All",
                        onClick = { selectedCategory = "All" },
                        label = { Text(strings.all) }
                    )
                }
                items(ExpenseCategories.all) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(strings.expenseCategoryLabel(cat)) }
                    )
                }
            }

            Surface(
                color = CrimsonAlertBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.filteredExpensesTotal, style = MaterialTheme.typography.titleSmall)
                    Text(AccountingEngine.formatPkr(totalFiltered), style = MonoNumberStyle.copy(fontSize = 15.sp), color = CrimsonAlert)
                }
            }

            if (filteredExpenses.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Payments,
                    title = "No Expenses Recorded",
                    message = "Log crusher diesel, labour wages, salaries, electricity bills, transport, or maintenance costs.",
                    actionLabel = strings.addExpense,
                    onAction = {
                        editingExpense = null
                        showExpenseDialog = true
                    }
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filteredExpenses, key = { it.id }) { exp ->
                        ExpenseCard(
                            expense = exp,
                            onEdit = {
                                editingExpense = exp
                                showExpenseDialog = true
                            },
                            onVoid = {
                                if (state.settings.requireAdminPin && state.settings.adminPin.isNotBlank()) {
                                    pinAuthorizingVoidFor = exp
                                } else {
                                    voidingExpense = exp
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showExpenseDialog) {
        ExpenseEditorDialog(
            initial = editingExpense,
            onDismiss = { showExpenseDialog = false },
            onSave = { newExp, auditReason ->
                if (editingExpense == null) {
                    viewModel.createExpense(newExp) { showExpenseDialog = false }
                } else {
                    viewModel.correctExpense(editingExpense!!, newExp, auditReason) { showExpenseDialog = false }
                }
            }
        )
    }

    pinAuthorizingVoidFor?.let { exp ->
        AdminPinDialog(
            expectedPin = state.settings.adminPin,
            onDismiss = { pinAuthorizingVoidFor = null },
            onAuthorized = {
                pinAuthorizingVoidFor = null
                voidingExpense = exp
            }
        )
    }

    voidingExpense?.let { exp ->
        ReasonInputDialog(
            title = "Void Expense",
            subtitle = "Expense records cannot be deleted. Enter an audit reason to void ${exp.title}.",
            confirmLabel = "Void Expense",
            isDestructive = true,
            onDismiss = { voidingExpense = null },
            onConfirm = { reason ->
                viewModel.voidExpense(exp, reason)
                voidingExpense = null
            }
        )
    }
}

@Composable
private fun ExpenseCard(
    expense: Expense,
    onEdit: () -> Unit,
    onVoid: () -> Unit
) {
    val isVoid = expense.status == TxStatuses.VOID
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(expense.category, style = MaterialTheme.typography.labelSmall, color = CrimsonAlert, fontWeight = FontWeight.Bold)
                Text(expense.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${AccountingEngine.formatDate(expense.transactionDateMillis)} • ${expense.paymentMethod}" +
                        if (expense.paidTo.isNotBlank()) " • To: ${expense.paidTo}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = AccountingEngine.formatPkr(expense.amount),
                    style = MonoNumberStyle.copy(fontSize = 15.sp),
                    color = if (isVoid) MaterialTheme.colorScheme.onSurfaceVariant else CrimsonAlert
                )
                if (!isVoid) {
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Correct Expense")
                        }
                        IconButton(onClick = onVoid) {
                            Icon(Icons.Default.Block, contentDescription = "Void Expense", tint = CrimsonAlert)
                        }
                    }
                } else {
                    Text("VOID", style = MaterialTheme.typography.labelSmall, color = CrimsonAlert)
                }
            }
        }
    }
}

@Composable
private fun ExpenseEditorDialog(
    initial: Expense?,
    onDismiss: () -> Unit,
    onSave: (Expense, String) -> Unit
) {
    val isNew = initial == null
    var category by remember { mutableStateOf(initial?.category ?: ExpenseCategories.DIESEL) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amountText by remember { mutableStateOf(initial?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var paymentMethod by remember { mutableStateOf(initial?.paymentMethod ?: PaymentMethods.CASH) }
    var paidTo by remember { mutableStateOf(initial?.paidTo ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var dateMillis by remember { mutableStateOf(initial?.transactionDateMillis ?: System.currentTimeMillis()) }
    var auditReason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Record Plant Expense" else "Correct Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector(
                    label = "Category*",
                    selectedText = category,
                    options = ExpenseCategories.all,
                    optionLabel = { it },
                    onSelect = { category = it }
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Description / Title*") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (PKR)*") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                DropdownSelector(
                    label = "Payment Method",
                    selectedText = paymentMethod,
                    options = PaymentMethods.expenseMethods,
                    optionLabel = { it },
                    onSelect = { paymentMethod = it }
                )
                DatePickerButton(dateMillis = dateMillis, onDateSelected = { dateMillis = it }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = paidTo, onValueChange = { paidTo = it }, label = { Text("Paid To / Vendor") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, singleLine = true)
                if (!isNew) {
                    OutlinedTextField(value = auditReason, onValueChange = { auditReason = it }, label = { Text("Correction Audit Reason*") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0 && title.isNotBlank()) {
                        onSave(
                            (initial ?: Expense()).copy(
                                category = category,
                                title = title.trim(),
                                amount = amt,
                                paymentMethod = paymentMethod,
                                paidTo = paidTo.trim(),
                                notes = notes.trim(),
                                transactionDateMillis = dateMillis
                            ),
                            auditReason.trim()
                        )
                    }
                },
                enabled = title.isNotBlank() && (amountText.toDoubleOrNull() ?: 0.0) > 0.0 && (isNew || auditReason.isNotBlank())
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
