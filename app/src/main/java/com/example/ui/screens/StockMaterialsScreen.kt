package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AccountingEngine
import com.example.domain.DefaultUnits
import com.example.domain.Material
import com.example.domain.MaterialCategories
import com.example.domain.StockMovement
import com.example.ui.CrusherViewModel
import com.example.ui.CrusherWorkspaceState
import com.example.ui.components.AdminPinDialog
import com.example.ui.components.DropdownSelector
import com.example.ui.components.EmptyStateView
import com.example.ui.components.SearchInputField
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonAlertBg
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoNumberStyle
import com.example.ui.theme.QuarryAmber

@Composable
fun StockMaterialsScreen(
    state: CrusherWorkspaceState,
    viewModel: CrusherViewModel
) {
    val strings = LocalAppStrings.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Materials & Stock, 1 = Stock Movements
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyLowStock by remember { mutableStateOf(false) }

    var editingMaterial by remember { mutableStateOf<Material?>(null) }
    var showMaterialDialog by remember { mutableStateOf(false) }
    var pendingPinAdjustMaterial by remember { mutableStateOf<Material?>(null) }
    var adjustingMaterial by remember { mutableStateOf<Material?>(null) }

    val filteredMaterials = remember(state.materials, selectedCategory, searchQuery, showOnlyLowStock) {
        state.materials.filter { mat ->
            !mat.archived &&
                (selectedCategory == "All" || mat.category == selectedCategory) &&
                (!showOnlyLowStock || mat.currentStock <= mat.minStockAlert) &&
                (searchQuery.isBlank() ||
                    mat.name.contains(searchQuery, true) ||
                    mat.specification.contains(searchQuery, true) ||
                    mat.category.contains(searchQuery, true))
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        editingMaterial = null
                        showMaterialDialog = true
                    },
                    modifier = Modifier.testTag("add_material_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Custom Material")
                }
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
                    Text("${strings.materialsAndStockTab} (${state.materials.count { !it.archived }})")
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) {
                    Text("${strings.movementsTab} (${state.stockMovements.size})")
                }
            }

            if (selectedTab == 0) {
                SearchInputField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = strings.searchMaterialsPlaceholder,
                    tag = "materials_search"
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == "All",
                            onClick = { selectedCategory = "All" },
                            label = { Text(strings.all) }
                        )
                    }
                    items(MaterialCategories.all) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(strings.materialCategoryLabel(cat)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = showOnlyLowStock,
                            onClick = { showOnlyLowStock = !showOnlyLowStock },
                            label = { Text("${strings.lowStockFilterPrefix} (${state.metrics.lowStockCount})") }
                        )
                    }
                }

                if (filteredMaterials.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.Inventory2,
                        title = "No matching materials",
                        message = "Adjust your search or add a custom material.",
                        actionLabel = strings.addMaterial,
                        onAction = {
                            editingMaterial = null
                            showMaterialDialog = true
                        }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(filteredMaterials, key = { it.id }) { mat ->
                            MaterialStockCard(
                                material = mat,
                                adjustLabel = strings.adjustStock,
                                onEdit = {
                                    editingMaterial = mat
                                    showMaterialDialog = true
                                },
                                onAdjustStock = {
                                    if (state.settings.requireAdminPin && state.settings.adminPin.isNotBlank()) {
                                        pendingPinAdjustMaterial = mat
                                    } else {
                                        adjustingMaterial = mat
                                    }
                                },
                                onDeleteCustom = { viewModel.deleteCustomMaterial(mat) }
                            )
                        }
                    }
                }
            } else {
                if (state.stockMovements.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.History,
                        title = "No Stock Movements Yet",
                        message = "Purchases, sales, and manual stock adjustments are recorded here automatically."
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(state.stockMovements, key = { it.id }) { mov ->
                            StockMovementCard(mov)
                        }
                    }
                }
            }
        }
    }

    if (showMaterialDialog) {
        MaterialEditorDialog(
            initial = editingMaterial,
            defaultUnit = state.settings.defaultUnit,
            onDismiss = { showMaterialDialog = false },
            onSave = { mat, isNew ->
                viewModel.saveMaterial(mat, isNew) { showMaterialDialog = false }
            }
        )
    }

    pendingPinAdjustMaterial?.let { mat ->
        AdminPinDialog(
            expectedPin = state.settings.adminPin,
            onDismiss = { pendingPinAdjustMaterial = null },
            onAuthorized = {
                pendingPinAdjustMaterial = null
                adjustingMaterial = mat
            }
        )
    }

    adjustingMaterial?.let { mat ->
        StockAdjustDialog(
            material = mat,
            allowNegativeDefault = state.settings.allowNegativeStock,
            onDismiss = { adjustingMaterial = null },
            onConfirm = { delta, reason, authNeg ->
                viewModel.adjustStock(state.settings, mat, delta, reason, authNeg) {
                    adjustingMaterial = null
                }
            }
        )
    }
}

@Composable
private fun MaterialStockCard(
    material: Material,
    adjustLabel: String,
    onEdit: () -> Unit,
    onAdjustStock: () -> Unit,
    onDeleteCustom: () -> Unit
) {
    val isLow = material.currentStock <= material.minStockAlert
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
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(material.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (isLow) {
                            Surface(color = CrimsonAlertBg, shape = RoundedCornerShape(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = "Low", tint = CrimsonAlert, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("LOW", style = MaterialTheme.typography.labelSmall, color = CrimsonAlert, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Text(
                        text = "${material.category} • ${material.specification.ifBlank { "Standard" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${AccountingEngine.formatQty(material.currentStock)} ${material.unit}",
                        style = MonoNumberStyle.copy(fontSize = 16.sp),
                        color = if (isLow) CrimsonAlert else EmeraldProfit
                    )
                    Text(
                        text = "Min: ${AccountingEngine.formatQty(material.minStockAlert)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Buy: ${AccountingEngine.formatPkr(material.purchaseRate)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Sale: ${AccountingEngine.formatPkr(material.saleRate)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Avg Cost: ${AccountingEngine.formatPkr(material.avgCost)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onAdjustStock,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = adjustLabel, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(adjustLabel, style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Material")
                }
                if (material.customItem) {
                    IconButton(onClick = onDeleteCustom) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Custom Material", tint = CrimsonAlert)
                    }
                }
            }
        }
    }
}

@Composable
private fun StockMovementCard(mov: StockMovement) {
    val isIn = mov.quantityDelta > 0
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(mov.materialName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${mov.movementType} • ${mov.referenceNumber} • ${AccountingEngine.formatDate(mov.transactionDateMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (mov.reason.isNotBlank()) {
                    Text(mov.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isIn) "+" else ""}${AccountingEngine.formatQty(mov.quantityDelta)} ${mov.unit}",
                    style = MonoNumberStyle.copy(fontSize = 14.sp),
                    color = if (isIn) EmeraldProfit else CrimsonAlert
                )
                Text(
                    text = "Bal: ${AccountingEngine.formatQty(mov.balanceAfter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MaterialEditorDialog(
    initial: Material?,
    defaultUnit: String,
    onDismiss: () -> Unit,
    onSave: (Material, Boolean) -> Unit
) {
    val isNew = initial == null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: MaterialCategories.SAND_AGGREGATE) }
    var spec by remember { mutableStateOf(initial?.specification ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: defaultUnit) }
    var purchaseRate by remember { mutableStateOf(initial?.purchaseRate?.toString() ?: "0") }
    var saleRate by remember { mutableStateOf(initial?.saleRate?.toString() ?: "0") }
    var openingStock by remember { mutableStateOf(initial?.currentStock?.toString() ?: "0") }
    var minAlert by remember { mutableStateOf(initial?.minStockAlert?.toString() ?: "50") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add Custom Material" else "Edit ${initial.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Material Name*") }, singleLine = true)
                DropdownSelector(
                    label = "Category",
                    selectedText = category,
                    options = MaterialCategories.all,
                    optionLabel = { it },
                    onSelect = { category = it }
                )
                OutlinedTextField(value = spec, onValueChange = { spec = it }, label = { Text("Size / Specification") }, singleLine = true)
                DropdownSelector(
                    label = "Unit",
                    selectedText = unit,
                    options = DefaultUnits.all,
                    optionLabel = { it },
                    onSelect = { unit = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = purchaseRate,
                        onValueChange = { purchaseRate = it },
                        label = { Text("Buy Rate") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = saleRate,
                        onValueChange = { saleRate = it },
                        label = { Text("Sale Rate") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isNew) {
                        OutlinedTextField(
                            value = openingStock,
                            onValueChange = { openingStock = it },
                            label = { Text("Initial Stock") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = minAlert,
                        onValueChange = { minAlert = it },
                        label = { Text("Min Stock Alert") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pRate = purchaseRate.toDoubleOrNull() ?: 0.0
                    val sRate = saleRate.toDoubleOrNull() ?: 0.0
                    val stock = openingStock.toDoubleOrNull() ?: 0.0
                    val alert = minAlert.toDoubleOrNull() ?: 0.0
                    val mat = (initial ?: Material()).copy(
                        name = name.trim(),
                        category = category,
                        specification = spec.trim(),
                        unit = unit,
                        purchaseRate = pRate,
                        saleRate = sRate,
                        avgCost = if (initial == null) pRate else initial.avgCost,
                        currentStock = if (initial == null) stock else initial.currentStock,
                        minStockAlert = alert
                    )
                    onSave(mat, isNew)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StockAdjustDialog(
    material: Material,
    allowNegativeDefault: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, Boolean) -> Unit
) {
    var isIncrease by remember { mutableStateOf(true) }
    var qtyText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var authorizeNeg by remember { mutableStateOf(allowNegativeDefault) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Stock: ${material.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current Stock: ${AccountingEngine.formatQty(material.currentStock)} ${material.unit}")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isIncrease,
                        onClick = { isIncrease = true },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Add (+)") }
                    SegmentedButton(
                        selected = !isIncrease,
                        onClick = { isIncrease = false },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Deduct (-)") }
                }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Quantity (${material.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Audit Reason*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isIncrease && !allowNegativeDefault) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = authorizeNeg, onCheckedChange = { authorizeNeg = it })
                        Text("Authorize negative stock if needed", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyText.toDoubleOrNull() ?: 0.0
                    if (qty > 0.0) {
                        onConfirm(if (isIncrease) qty else -qty, reason, authorizeNeg)
                    }
                },
                enabled = (qtyText.toDoubleOrNull() ?: 0.0) > 0.0 && reason.isNotBlank()
            ) {
                Text("Apply Adjustment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
