package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BackupManager
import com.example.data.CrusherRepository
import com.example.data.NetworkMonitor
import com.example.data.SyncSnapshot
import com.example.data.TransactionRepository
import com.example.domain.AccountingEngine
import com.example.domain.AuditLog
import com.example.domain.BusinessSettings
import com.example.domain.Customer
import com.example.domain.DashboardMetrics
import com.example.domain.Expense
import com.example.domain.LineItem
import com.example.domain.Material
import com.example.domain.PartyPayment
import com.example.domain.Purchase
import com.example.domain.Sale
import com.example.domain.StockMovement
import com.example.domain.Supplier
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class SyncInfo(
    val isOnline: Boolean = true,
    val hasPendingWrites: Boolean = false,
    val fromCache: Boolean = false
)

data class CrusherWorkspaceState(
    val settings: BusinessSettings = BusinessSettings(),
    val materials: List<Material> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val sales: List<Sale> = emptyList(),
    val purchases: List<Purchase> = emptyList(),
    val payments: List<PartyPayment> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val stockMovements: List<StockMovement> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val metrics: DashboardMetrics = AccountingEngine.computeDashboardMetrics(
        nowMillis = System.currentTimeMillis(),
        settings = BusinessSettings(),
        materials = emptyList(),
        customers = emptyList(),
        suppliers = emptyList(),
        sales = emptyList(),
        purchases = emptyList(),
        payments = emptyList(),
        expenses = emptyList()
    ),
    val syncInfo: SyncInfo = SyncInfo()
)

class CrusherViewModel(
    private val repo: CrusherRepository,
    private val txRepo: TransactionRepository,
    networkMonitor: NetworkMonitor,
    private val currentUserId: String
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            if (currentUserId.isNotBlank()) {
                repo.ensureWorkspaceInitialized().onFailure { e ->
                    _events.tryEmit("Workspace init note: ${e.localizedMessage ?: "offline mode"}")
                }
            }
        }
    }

    private data class CoreStreams(
        val settings: BusinessSettings,
        val materials: SyncSnapshot<Material>,
        val customers: SyncSnapshot<Customer>,
        val suppliers: SyncSnapshot<Supplier>,
        val sales: SyncSnapshot<Sale>
    )

    private data class LedgerStreams(
        val purchases: SyncSnapshot<Purchase>,
        val payments: SyncSnapshot<PartyPayment>,
        val expenses: SyncSnapshot<Expense>,
        val stockMovements: SyncSnapshot<StockMovement>,
        val auditLogs: SyncSnapshot<AuditLog>
    )

    private val coreFlow = combine(
        repo.observeBusinessSettings(),
        repo.observeMaterials(),
        repo.observeCustomers(),
        repo.observeSuppliers(),
        repo.observeSales()
    ) { settings, materials, customers, suppliers, sales ->
        CoreStreams(settings, materials, customers, suppliers, sales)
    }

    private val ledgerFlow = combine(
        repo.observePurchases(),
        repo.observePartyPayments(),
        repo.observeExpenses(),
        repo.observeStockMovements(),
        repo.observeAuditLogs()
    ) { purchases, payments, expenses, movements, logs ->
        LedgerStreams(purchases, payments, expenses, movements, logs)
    }

    val workspaceState: StateFlow<UiState<CrusherWorkspaceState>> = combine(
        coreFlow,
        ledgerFlow,
        networkMonitor.observeOnlineState()
    ) { core, ledger, isOnline ->
        val pending = core.materials.hasPendingWrites ||
            core.customers.hasPendingWrites ||
            core.suppliers.hasPendingWrites ||
            core.sales.hasPendingWrites ||
            ledger.purchases.hasPendingWrites ||
            ledger.payments.hasPendingWrites ||
            ledger.expenses.hasPendingWrites ||
            ledger.stockMovements.hasPendingWrites

        val fromCache = core.materials.fromCache || core.sales.fromCache

        val metrics = AccountingEngine.computeDashboardMetrics(
            nowMillis = System.currentTimeMillis(),
            settings = core.settings,
            materials = core.materials.items,
            customers = core.customers.items,
            suppliers = core.suppliers.items,
            sales = core.sales.items,
            purchases = ledger.purchases.items,
            payments = ledger.payments.items,
            expenses = ledger.expenses.items
        )

        UiState.Success(
            CrusherWorkspaceState(
                settings = core.settings,
                materials = core.materials.items,
                customers = core.customers.items,
                suppliers = core.suppliers.items,
                sales = core.sales.items,
                purchases = ledger.purchases.items,
                payments = ledger.payments.items,
                expenses = ledger.expenses.items,
                stockMovements = ledger.stockMovements.items,
                auditLogs = ledger.auditLogs.items,
                metrics = metrics,
                syncInfo = SyncInfo(
                    isOnline = isOnline,
                    hasPendingWrites = pending,
                    fromCache = fromCache
                )
            )
        ) as UiState<CrusherWorkspaceState>
    }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing Crusher ERP streams", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load Crusher ERP data"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private fun launchAction(successMsg: String, block: suspend () -> Result<Any?>, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val res = block()
            res.onSuccess {
                _events.tryEmit(successMsg)
                onSuccess()
            }.onFailure { e ->
                _events.tryEmit(e.localizedMessage ?: "Operation failed")
            }
        }
    }

    fun saveMaterial(material: Material, isNew: Boolean, onDone: () -> Unit = {}) {
        launchAction(if (isNew) "Material added" else "Material updated", { repo.upsertMaterial(material, isNew) }, onDone)
    }

    fun deleteCustomMaterial(material: Material) {
        launchAction("Material deleted", { repo.deleteCustomMaterial(material) })
    }

    fun adjustStock(
        settings: BusinessSettings,
        material: Material,
        delta: Double,
        reason: String,
        authorizeNegative: Boolean,
        onDone: () -> Unit = {}
    ) {
        launchAction("Stock adjusted for ${material.name}", {
            txRepo.adjustStock(settings, material, delta, reason, authorizeNegative)
        }, onDone)
    }

    fun saveCustomer(customer: Customer, isNew: Boolean, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            repo.upsertCustomer(customer, isNew)
                .onSuccess { id ->
                    _events.tryEmit(if (isNew) "Customer added" else "Customer updated")
                    onDone(id)
                }
                .onFailure { e ->
                    _events.tryEmit(e.localizedMessage ?: "Failed to save customer")
                }
        }
    }

    fun saveSupplier(supplier: Supplier, isNew: Boolean, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            repo.upsertSupplier(supplier, isNew)
                .onSuccess { id ->
                    _events.tryEmit(if (isNew) "Supplier added" else "Supplier updated")
                    onDone(id)
                }
                .onFailure { e ->
                    _events.tryEmit(e.localizedMessage ?: "Failed to save supplier")
                }
        }
    }

    fun createSale(
        settings: BusinessSettings,
        customer: Customer,
        items: List<LineItem>,
        materialsById: Map<String, Material>,
        discount: Double,
        paidAmount: Double,
        paymentMethod: String,
        dateMillis: Long,
        notes: String,
        authorizeNegativeStock: Boolean,
        onCreated: (Sale) -> Unit
    ) {
        viewModelScope.launch {
            txRepo.createSale(
                settings, customer, items, materialsById, discount, paidAmount,
                paymentMethod, dateMillis, notes, authorizeNegativeStock
            ).onSuccess { sale ->
                _events.tryEmit("Invoice ${sale.invoiceNumber} saved")
                onCreated(sale)
            }.onFailure { e ->
                _events.tryEmit(e.localizedMessage ?: "Failed to save sale")
            }
        }
    }

    fun correctSale(
        settings: BusinessSettings,
        oldSale: Sale,
        customer: Customer,
        newItems: List<LineItem>,
        materialsById: Map<String, Material>,
        discount: Double,
        paidAmount: Double,
        paymentMethod: String,
        dateMillis: Long,
        notes: String,
        auditReason: String,
        authorizeNegativeStock: Boolean,
        onDone: () -> Unit
    ) {
        launchAction("Invoice ${oldSale.invoiceNumber} corrected", {
            txRepo.correctSale(
                settings, oldSale, customer, newItems, materialsById, discount,
                paidAmount, paymentMethod, dateMillis, notes, auditReason, authorizeNegativeStock
            )
        }, onDone)
    }

    fun voidSale(sale: Sale, customer: Customer?, materialsById: Map<String, Material>, reason: String) {
        launchAction("Invoice ${sale.invoiceNumber} voided & stock reversed", {
            txRepo.voidSale(sale, customer, materialsById, reason)
        })
    }

    fun createPurchase(
        settings: BusinessSettings,
        supplier: Supplier,
        items: List<LineItem>,
        materialsById: Map<String, Material>,
        discount: Double,
        paidAmount: Double,
        paymentMethod: String,
        dateMillis: Long,
        notes: String,
        onCreated: (Purchase) -> Unit
    ) {
        viewModelScope.launch {
            txRepo.createPurchase(
                settings, supplier, items, materialsById, discount, paidAmount,
                paymentMethod, dateMillis, notes
            ).onSuccess { purchase ->
                _events.tryEmit("Purchase ${purchase.voucherNumber} saved")
                onCreated(purchase)
            }.onFailure { e ->
                _events.tryEmit(e.localizedMessage ?: "Failed to save purchase")
            }
        }
    }

    fun voidPurchase(purchase: Purchase, supplier: Supplier?, materialsById: Map<String, Material>, reason: String) {
        launchAction("Voucher ${purchase.voucherNumber} voided & stock reversed", {
            txRepo.voidPurchase(purchase, supplier, materialsById, reason)
        })
    }

    fun recordPartyPayment(
        partyType: String,
        partyId: String,
        partyName: String,
        currentBalance: Double,
        entryType: String,
        amount: Double,
        paymentMethod: String,
        referenceNumber: String,
        notes: String,
        dateMillis: Long,
        onDone: (PartyPayment) -> Unit = {}
    ) {
        viewModelScope.launch {
            txRepo.recordPartyPayment(
                partyType, partyId, partyName, currentBalance, entryType,
                amount, paymentMethod, referenceNumber, notes, dateMillis
            ).onSuccess { payment ->
                _events.tryEmit("Payment recorded for $partyName")
                onDone(payment)
            }.onFailure { e ->
                _events.tryEmit(e.localizedMessage ?: "Failed to record payment")
            }
        }
    }

    fun reconcilePartyBalances(state: CrusherWorkspaceState) {
        viewModelScope.launch {
            val custUpdates = state.customers.mapNotNull { cust ->
                val entries = AccountingEngine.computeCustomerLedger(cust, state.sales, state.payments)
                val exact = entries.lastOrNull()?.runningBalance ?: AccountingEngine.round2(cust.openingBalance)
                if (kotlin.math.abs(exact - cust.currentBalance) > 0.01) cust.id to exact else null
            }
            val supUpdates = state.suppliers.mapNotNull { sup ->
                val entries = AccountingEngine.computeSupplierLedger(sup, state.purchases, state.payments)
                val exact = entries.lastOrNull()?.runningBalance ?: AccountingEngine.round2(sup.openingBalance)
                if (kotlin.math.abs(exact - sup.currentBalance) > 0.01) sup.id to exact else null
            }
            if (custUpdates.isEmpty() && supUpdates.isEmpty()) {
                _events.tryEmit("Verified: All ${state.customers.size} customers & ${state.suppliers.size} suppliers match ledger totals.")
            } else {
                repo.reconcilePartyBalances(custUpdates, supUpdates)
                    .onSuccess { count -> _events.tryEmit("Reconciled $count party balances to match ledger entries.") }
                    .onFailure { e -> _events.tryEmit("Reconciliation failed: ${e.localizedMessage}") }
            }
        }
    }

    fun createExpense(expense: Expense, onDone: () -> Unit = {}) {
        launchAction("Expense recorded", { repo.createExpense(expense) }, onDone)
    }

    fun correctExpense(oldExpense: Expense, newExpense: Expense, reason: String, onDone: () -> Unit = {}) {
        launchAction("Expense corrected", { repo.correctExpense(oldExpense, newExpense, reason) }, onDone)
    }

    fun voidExpense(expense: Expense, reason: String) {
        launchAction("Expense voided", { repo.voidExpense(expense, reason) })
    }

    fun updateSettings(settings: BusinessSettings) {
        launchAction("Business settings saved", { repo.updateBusinessSettings(settings) })
    }

    fun buildBackupJson(state: CrusherWorkspaceState): String =
        BackupManager.exportToJson(
            settings = state.settings,
            materials = state.materials,
            customers = state.customers,
            suppliers = state.suppliers,
            sales = state.sales,
            purchases = state.purchases,
            payments = state.payments,
            expenses = state.expenses,
            stockMovements = state.stockMovements
        )

    fun restoreBackupJson(jsonString: String, state: CrusherWorkspaceState) {
        viewModelScope.launch {
            repo.restoreBackupJson(
                jsonString = jsonString,
                existingMaterialIds = state.materials.map { it.id }.toSet(),
                existingCustomerIds = state.customers.map { it.id }.toSet(),
                existingSupplierIds = state.suppliers.map { it.id }.toSet(),
                existingSaleIds = state.sales.map { it.id }.toSet(),
                existingPurchaseIds = state.purchases.map { it.id }.toSet(),
                existingPaymentIds = state.payments.map { it.id }.toSet(),
                existingExpenseIds = state.expenses.map { it.id }.toSet(),
                existingMovementIds = state.stockMovements.map { it.id }.toSet()
            )
                .onSuccess { count -> _events.tryEmit("Restored $count records from backup") }
                .onFailure { e -> _events.tryEmit("Restore failed: ${e.localizedMessage}") }
        }
    }

    private companion object {
        const val TAG = "CrusherVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
