package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.domain.AccountingEngine
import com.example.domain.DefaultUnits
import com.example.ui.CrusherViewModel
import com.example.ui.CrusherWorkspaceState
import com.example.ui.auth.signOut
import com.example.ui.components.AdminPinDialog
import com.example.ui.components.DropdownSelector
import com.example.ui.components.SyncStatusChip
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.QuarryAmber
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.io.File
import java.io.FileOutputStream

@Composable
fun SettingsScreen(
    state: CrusherWorkspaceState,
    viewModel: CrusherViewModel,
    onLanguageChange: (AppLanguage) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val strings = LocalAppStrings.current
    val currentLanguage = LocalAppLanguage.current

    val s = state.settings
    var businessName by remember(s) { mutableStateOf(s.businessName) }
    var address by remember(s) { mutableStateOf(s.address) }
    var phone by remember(s) { mutableStateOf(s.phone) }
    var openingCash by remember(s) { mutableStateOf(s.openingCashBalance.toString()) }
    var financialYear by remember(s) { mutableStateOf(s.financialYear) }
    var defaultUnit by remember(s) { mutableStateOf(s.defaultUnit) }
    var invoicePrefix by remember(s) { mutableStateOf(s.invoicePrefix) }
    var nextInvSeq by remember(s) { mutableStateOf(s.nextInvoiceSeq.toString()) }
    var purchasePrefix by remember(s) { mutableStateOf(s.purchasePrefix) }
    var nextPurSeq by remember(s) { mutableStateOf(s.nextPurchaseSeq.toString()) }
    var allowNegativeStock by remember(s) { mutableStateOf(s.allowNegativeStock) }
    var logoPath by remember(s) { mutableStateOf(s.logoLocalPath) }
    var operatorName by remember(s) { mutableStateOf(s.operatorName) }
    var requireAdminPin by remember(s) { mutableStateOf(s.requireAdminPin) }
    var adminPin by remember(s) { mutableStateOf(s.adminPin) }

    var showAuditDialog by remember { mutableStateOf(false) }
    var pendingPinAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        val dest = File(context.filesDir, "invoice_logo.png")
                        FileOutputStream(dest).use { out ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                        }
                        logoPath = dest.absolutePath
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val json = viewModel.buildBackupJson(state)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
        }
    }

    val backupRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (!json.isNullOrBlank()) {
                viewModel.restoreBackupJson(json, state)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Account & Sync Card
        item {
            val user = Firebase.auth.currentUser
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user?.displayName ?: "Signed-In Owner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SyncStatusChip(syncInfo = state.syncInfo)
                    }
                    OutlinedButton(
                        onClick = { signOut(credentialManager, scope) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert),
                        modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = strings.signOutLabel, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.signOutLabel)
                    }
                }
            }
        }

        // Language Toggle Card (English / اردو RTL)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(strings.languageSettingsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(strings.languageSettingsSub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AppLanguage.entries.forEachIndexed { idx, lang ->
                            SegmentedButton(
                                selected = currentLanguage == lang,
                                onClick = {
                                    onLanguageChange(lang)
                                    viewModel.updateSettings(s.copy(languageCode = lang.code))
                                },
                                shape = SegmentedButtonDefaults.itemShape(idx, AppLanguage.entries.size),
                                modifier = Modifier.testTag("settings_lang_${lang.code}")
                            ) {
                                Text(lang.displayLabel)
                            }
                        }
                    }
                }
            }
        }

        // Admin & Security Controls Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = QuarryAmber)
                        Column {
                            Text(strings.adminControlsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(strings.adminControlsSub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    OutlinedTextField(
                        value = operatorName,
                        onValueChange = { operatorName = it },
                        label = { Text(strings.operatorNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("operator_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.requireAdminPinLabel, style = MaterialTheme.typography.titleSmall)
                            Text(strings.requireAdminPinSub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = requireAdminPin,
                            onCheckedChange = { wantEnable ->
                                if (!wantEnable && s.requireAdminPin && s.adminPin.isNotBlank()) {
                                    pendingPinAction = { requireAdminPin = false }
                                } else {
                                    requireAdminPin = wantEnable
                                }
                            },
                            modifier = Modifier.testTag("require_admin_pin_switch")
                        )
                    }

                    OutlinedTextField(
                        value = adminPin,
                        onValueChange = { input -> adminPin = input.filter { it.isDigit() }.take(8) },
                        label = { Text(strings.adminPinLabel) },
                        placeholder = { Text(strings.adminPinHint) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_admin_pin_input")
                    )

                    val saveAdminControls = {
                        val cleanPin = adminPin.trim()
                        val validPin = cleanPin.length in 4..8
                        viewModel.updateSettings(
                            s.copy(
                                operatorName = operatorName.trim().ifBlank { "Plant Owner" },
                                requireAdminPin = requireAdminPin && validPin,
                                adminPin = cleanPin,
                                languageCode = currentLanguage.code
                            )
                        )
                    }

                    Button(
                        onClick = {
                            if (s.requireAdminPin && s.adminPin.isNotBlank() && adminPin.trim() != s.adminPin.trim()) {
                                pendingPinAction = saveAdminControls
                            } else {
                                saveAdminControls()
                            }
                        },
                        enabled = !requireAdminPin || adminPin.trim().length in 4..8,
                        modifier = Modifier.fillMaxWidth().testTag("save_admin_controls_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.save)
                    }

                    OutlinedButton(
                        onClick = { viewModel.reconcilePartyBalances(state) },
                        modifier = Modifier.fillMaxWidth().testTag("reconcile_balances_button")
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.verifyBalancesButton)
                    }
                }
            }
        }

        // Business Profile & Invoice Logo
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Business, contentDescription = null)
                        Text(strings.businessAndInvoiceSettings, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val logoBmp = remember(logoPath) {
                            if (logoPath.isNotBlank() && File(logoPath).exists()) BitmapFactory.decodeFile(logoPath) else null
                        }
                        if (logoBmp != null) {
                            Image(
                                bitmap = logoBmp.asImageBitmap(),
                                contentDescription = "Invoice Logo",
                                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp))
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            Icon(Icons.Default.Image, contentDescription = "Pick Invoice Logo", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (logoPath.isBlank()) "Pick Invoice Logo" else "Change Logo")
                        }
                    }

                    OutlinedTextField(value = businessName, onValueChange = { businessName = it }, label = { Text("Business Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Plant Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = openingCash,
                            onValueChange = { openingCash = it },
                            label = { Text("Opening Cash (PKR)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = financialYear,
                            onValueChange = { financialYear = it },
                            label = { Text("Financial Year") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    DropdownSelector(
                        label = "Default Material Unit",
                        selectedText = defaultUnit,
                        options = DefaultUnits.all,
                        optionLabel = { it },
                        onSelect = { defaultUnit = it }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = invoicePrefix,
                            onValueChange = { invoicePrefix = it },
                            label = { Text("Invoice Prefix") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = nextInvSeq,
                            onValueChange = { nextInvSeq = it },
                            label = { Text("Next Inv #") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = purchasePrefix,
                            onValueChange = { purchasePrefix = it },
                            label = { Text("Purchase Prefix") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = nextPurSeq,
                            onValueChange = { nextPurSeq = it },
                            label = { Text("Next Voucher #") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow Negative Stock", style = MaterialTheme.typography.titleSmall)
                            Text("Default off to block overselling stock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = allowNegativeStock, onCheckedChange = { allowNegativeStock = it })
                    }

                    Button(
                        onClick = {
                            val saveAction = {
                                viewModel.updateSettings(
                                    s.copy(
                                        businessName = businessName,
                                        address = address,
                                        phone = phone,
                                        openingCashBalance = openingCash.toDoubleOrNull() ?: 0.0,
                                        financialYear = financialYear,
                                        defaultUnit = defaultUnit,
                                        invoicePrefix = invoicePrefix,
                                        nextInvoiceSeq = (nextInvSeq.toLongOrNull() ?: 1L).coerceAtLeast(1L),
                                        purchasePrefix = purchasePrefix,
                                        nextPurchaseSeq = (nextPurSeq.toLongOrNull() ?: 1L).coerceAtLeast(1L),
                                        allowNegativeStock = allowNegativeStock,
                                        logoLocalPath = logoPath,
                                        operatorName = operatorName.trim().ifBlank { "Plant Owner" },
                                        requireAdminPin = requireAdminPin && adminPin.trim().length in 4..8,
                                        adminPin = adminPin.trim(),
                                        languageCode = currentLanguage.code
                                    )
                                )
                            }
                            if (s.requireAdminPin && s.adminPin.isNotBlank() && (!requireAdminPin || adminPin.trim() != s.adminPin.trim())) {
                                pendingPinAction = saveAction
                            } else {
                                saveAction()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.saveBusinessSettings)
                    }
                }
            }
        }

        // Backup, Restore & Audit Trail
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.backupRestoreAuditTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { backupExportLauncher.launch("CrusherERP_Backup_${System.currentTimeMillis()}.json") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = strings.backupJson, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.backupJson)
                        }
                        OutlinedButton(
                            onClick = {
                                if (s.requireAdminPin && s.adminPin.isNotBlank()) {
                                    pendingPinAction = { backupRestoreLauncher.launch(arrayOf("application/json", "*/*")) }
                                } else {
                                    backupRestoreLauncher.launch(arrayOf("application/json", "*/*"))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = strings.restoreJson, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.restoreJson)
                        }
                    }
                    OutlinedButton(
                        onClick = { showAuditDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.History, contentDescription = strings.viewAuditHistoryPrefix, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${strings.viewAuditHistoryPrefix} (${state.auditLogs.size})")
                    }
                }
            }
        }
    }

    pendingPinAction?.let { action ->
        AdminPinDialog(
            expectedPin = s.adminPin,
            onDismiss = { pendingPinAction = null },
            onAuthorized = {
                pendingPinAction = null
                action()
            }
        )
    }

    if (showAuditDialog) {
        AlertDialog(
            onDismissRequest = { showAuditDialog = false },
            title = { Text("Financial Audit Trail") },
            text = {
                if (state.auditLogs.isEmpty()) {
                    Text("No audit entries yet.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(380.dp)
                    ) {
                        items(state.auditLogs, key = { it.id }) { log ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${log.entityType} • ${log.action} • ${log.referenceNumber}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Reason: ${log.reason}", style = MaterialTheme.typography.bodySmall)
                                    if (log.beforeSummary.isNotBlank()) {
                                        Text("Before: ${log.beforeSummary}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (log.afterSummary.isNotBlank()) {
                                        Text("After: ${log.afterSummary}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    val ts = log.createdAt?.toDate()?.time ?: 0L
                                    if (ts > 0L) {
                                        Text(AccountingEngine.formatDateTime(ts), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAuditDialog = false }) { Text("Close") }
            }
        )
    }
}
