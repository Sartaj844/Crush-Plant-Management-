package com.example.data

import android.content.Context
import com.example.R
import com.example.domain.AuditLog
import com.example.domain.BusinessSettings
import com.example.domain.Customer
import com.example.domain.DefaultMaterialsCatalog
import com.example.domain.Expense
import com.example.domain.Material
import com.example.domain.PartyPayment
import com.example.domain.Purchase
import com.example.domain.Sale
import com.example.domain.StockMovement
import com.example.domain.Supplier
import com.example.domain.TxStatuses
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class SyncSnapshot<T>(
    val items: List<T>,
    val hasPendingWrites: Boolean,
    val fromCache: Boolean
)

class CrusherRepository(
    val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    init {
        try {
            val cacheSettings = firestoreSettings {
                setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            }
            db.firestoreSettings = cacheSettings
        } catch (_: IllegalStateException) {
            // Firestore instance already configured
        }
    }

    internal fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    internal fun userRoot(uid: String) = db.collection("users").document(uid)

    internal suspend fun awaitCommitOfflineSafe(
        task: com.google.android.gms.tasks.Task<Void>,
        operationType: OperationType,
        path: String
    ) {
        task.addOnFailureListener { e ->
            handleFirestoreError(e, operationType, path)
        }
        // When offline, Firestore persists locally immediately and resolves the Task only on reconnect.
        // Wait briefly so online rule rejections throw immediately, while offline writes don't hang UI.
        withTimeoutOrNull(1500L) {
            task.await()
        }
    }

    private fun <T> observeCollection(
        collectionName: String,
        clazz: Class<T>,
        sorter: (List<T>) -> List<T>
    ): Flow<SyncSnapshot<T>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/$collectionName"
        emitAll(
            userRoot(uid).collection(collectionName)
                .snapshots()
                .map { snapshot ->
                    val list = snapshot.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    SyncSnapshot(
                        items = sorter(list),
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                        fromCache = snapshot.metadata.isFromCache
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeBusinessSettings(): Flow<BusinessSettings> = flow {
        val uid = requireUserId()
        val path = "users/$uid/business_settings/default"
        emitAll(
            userRoot(uid).collection("business_settings").document("default")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(BusinessSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?: BusinessSettings(userId = uid)
                    } else {
                        BusinessSettings(userId = uid)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeMaterials(): Flow<SyncSnapshot<Material>> =
        observeCollection("materials", Material::class.java) { list ->
            list.sortedWith(compareBy<Material> { it.category }.thenBy { it.name })
        }

    fun observeCustomers(): Flow<SyncSnapshot<Customer>> =
        observeCollection("customers", Customer::class.java) { list ->
            list.sortedBy { it.name.lowercase() }
        }

    fun observeSuppliers(): Flow<SyncSnapshot<Supplier>> =
        observeCollection("suppliers", Supplier::class.java) { list ->
            list.sortedBy { it.name.lowercase() }
        }

    fun observeSales(): Flow<SyncSnapshot<Sale>> =
        observeCollection("sales", Sale::class.java) { list ->
            list.sortedByDescending { it.transactionDateMillis }
        }

    fun observePurchases(): Flow<SyncSnapshot<Purchase>> =
        observeCollection("purchases", Purchase::class.java) { list ->
            list.sortedByDescending { it.transactionDateMillis }
        }

    fun observePartyPayments(): Flow<SyncSnapshot<PartyPayment>> =
        observeCollection("party_payments", PartyPayment::class.java) { list ->
            list.sortedByDescending { it.transactionDateMillis }
        }

    fun observeExpenses(): Flow<SyncSnapshot<Expense>> =
        observeCollection("expenses", Expense::class.java) { list ->
            list.sortedByDescending { it.transactionDateMillis }
        }

    fun observeStockMovements(): Flow<SyncSnapshot<StockMovement>> =
        observeCollection("stock_movements", StockMovement::class.java) { list ->
            list.sortedByDescending { it.transactionDateMillis }
        }

    fun observeAuditLogs(): Flow<SyncSnapshot<AuditLog>> =
        observeCollection("audit_logs", AuditLog::class.java) { list ->
            list.sortedByDescending { it.createdAt?.seconds ?: 0L }
        }

    suspend fun ensureWorkspaceInitialized(): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/business_settings/default"
        return try {
            val uid = requireUserId()
            val settingsRef = userRoot(uid).collection("business_settings").document("default")
            docPath = settingsRef.path
            val existingSettings = withTimeoutOrNull(2000L) { settingsRef.get().await() }
            if (existingSettings == null || !existingSettings.exists()) {
                val existingMaterials = withTimeoutOrNull(2000L) {
                    userRoot(uid).collection("materials").limit(1).get().await()
                }
                val batch = db.batch()
                val defaultSettings = BusinessSettings(
                    userId = uid,
                    defaultMaterialsSeeded = true
                )
                batch.set(settingsRef, defaultSettings.toCreateMap(uid))
                if (existingMaterials == null || existingMaterials.isEmpty) {
                    DefaultMaterialsCatalog.buildSeedMaterials(uid).forEach { mat ->
                        val matRef = userRoot(uid).collection("materials").document(mat.id)
                        batch.set(matRef, mat.toCreateMap(uid))
                    }
                }
                awaitCommitOfflineSafe(batch.commit(), OperationType.CREATE, docPath)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateBusinessSettings(settings: BusinessSettings): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/business_settings/default"
        return try {
            val uid = requireUserId()
            val ref = userRoot(uid).collection("business_settings").document("default")
            docPath = ref.path
            val snapshot = withTimeoutOrNull(1500L) { ref.get().await() }
            if (snapshot != null && snapshot.exists()) {
                val updates = mapOf(
                    "businessName" to settings.businessName.trim().ifBlank { "Pakistan Crusher & Building Supply" }.take(120),
                    "address" to settings.address.trim().take(300),
                    "phone" to settings.phone.trim().take(40),
                    "openingCashBalance" to settings.openingCashBalance.coerceAtLeast(0.0),
                    "financialYear" to settings.financialYear.trim().ifBlank { "2026-2027" }.take(30),
                    "defaultUnit" to settings.defaultUnit.trim().ifBlank { "CFT" }.take(30),
                    "invoicePrefix" to settings.invoicePrefix.trim().ifBlank { "INV-2026-" }.take(20),
                    "nextInvoiceSeq" to settings.nextInvoiceSeq.coerceAtLeast(1L),
                    "purchasePrefix" to settings.purchasePrefix.trim().ifBlank { "PV-2026-" }.take(20),
                    "nextPurchaseSeq" to settings.nextPurchaseSeq.coerceAtLeast(1L),
                    "allowNegativeStock" to settings.allowNegativeStock,
                    "logoLocalPath" to settings.logoLocalPath.take(300),
                    "operatorName" to settings.operatorName.trim().ifBlank { "Plant Owner" }.take(80),
                    "requireAdminPin" to settings.requireAdminPin,
                    "adminPin" to settings.adminPin.trim().take(12),
                    "languageCode" to (if (settings.languageCode == "ur") "ur" else "en"),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                awaitCommitOfflineSafe(ref.update(updates), OperationType.UPDATE, docPath)
            } else {
                awaitCommitOfflineSafe(
                    ref.set(settings.copy(defaultMaterialsSeeded = true).toCreateMap(uid)),
                    OperationType.CREATE,
                    docPath
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertMaterial(material: Material, isNew: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/materials"
        return try {
            val uid = requireUserId()
            require(material.name.isNotBlank()) { "Material name cannot be empty" }
            val matId = if (isNew || material.id.isBlank()) "mat_${UUID.randomUUID().toString().replace("-", "").take(16)}" else material.id
            val ref = userRoot(uid).collection("materials").document(matId)
            docPath = ref.path
            if (isNew || material.id.isBlank()) {
                val toCreate = material.copy(id = matId, userId = uid, customItem = true, archived = false)
                awaitCommitOfflineSafe(ref.set(toCreate.toCreateMap(uid)), OperationType.CREATE, docPath)
            } else {
                val updates = mapOf(
                    "name" to material.name.trim().take(120),
                    "category" to material.category,
                    "specification" to material.specification.trim().take(120),
                    "unit" to material.unit.trim().ifBlank { "CFT" }.take(30),
                    "purchaseRate" to material.purchaseRate.coerceAtLeast(0.0),
                    "saleRate" to material.saleRate.coerceAtLeast(0.0),
                    "minStockAlert" to material.minStockAlert.coerceAtLeast(0.0),
                    "archived" to material.archived,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                awaitCommitOfflineSafe(ref.update(updates), OperationType.UPDATE, docPath)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomMaterial(material: Material): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/materials/${material.id}"
        return try {
            val uid = requireUserId()
            require(material.customItem) { "Default materials cannot be deleted; you can edit or archive them." }
            val ref = userRoot(uid).collection("materials").document(material.id)
            docPath = ref.path
            awaitCommitOfflineSafe(ref.delete(), OperationType.DELETE, docPath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertCustomer(customer: Customer, isNew: Boolean): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/customers"
        return try {
            val uid = requireUserId()
            require(customer.name.isNotBlank()) { "Customer name is required" }
            val id = if (isNew || customer.id.isBlank()) "cust_${UUID.randomUUID().toString().replace("-", "").take(16)}" else customer.id
            val ref = userRoot(uid).collection("customers").document(id)
            docPath = ref.path
            if (isNew || customer.id.isBlank()) {
                val toCreate = customer.copy(
                    id = id,
                    userId = uid,
                    currentBalance = customer.openingBalance
                )
                awaitCommitOfflineSafe(ref.set(toCreate.toCreateMap(uid)), OperationType.CREATE, docPath)
            } else {
                val updates = mapOf(
                    "name" to customer.name.trim().take(120),
                    "phone" to customer.phone.trim().take(40),
                    "address" to customer.address.trim().take(250),
                    "notes" to customer.notes.trim().take(300),
                    "archived" to customer.archived,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                awaitCommitOfflineSafe(ref.update(updates), OperationType.UPDATE, docPath)
            }
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertSupplier(supplier: Supplier, isNew: Boolean): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/suppliers"
        return try {
            val uid = requireUserId()
            require(supplier.name.isNotBlank()) { "Supplier name is required" }
            val id = if (isNew || supplier.id.isBlank()) "sup_${UUID.randomUUID().toString().replace("-", "").take(16)}" else supplier.id
            val ref = userRoot(uid).collection("suppliers").document(id)
            docPath = ref.path
            if (isNew || supplier.id.isBlank()) {
                val toCreate = supplier.copy(
                    id = id,
                    userId = uid,
                    currentBalance = supplier.openingBalance
                )
                awaitCommitOfflineSafe(ref.set(toCreate.toCreateMap(uid)), OperationType.CREATE, docPath)
            } else {
                val updates = mapOf(
                    "name" to supplier.name.trim().take(120),
                    "phone" to supplier.phone.trim().take(40),
                    "address" to supplier.address.trim().take(250),
                    "notes" to supplier.notes.trim().take(300),
                    "archived" to supplier.archived,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                awaitCommitOfflineSafe(ref.update(updates), OperationType.UPDATE, docPath)
            }
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createExpense(expense: Expense): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/expenses"
        return try {
            val uid = requireUserId()
            require(expense.title.isNotBlank()) { "Expense description is required" }
            require(expense.amount > 0.0) { "Expense amount must be greater than zero" }
            val expId = "exp_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val ref = userRoot(uid).collection("expenses").document(expId)
            docPath = ref.path
            val batch = db.batch()
            batch.set(ref, expense.copy(id = expId, userId = uid, status = TxStatuses.CONFIRMED).toCreateMap(uid))
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val logRef = userRoot(uid).collection("audit_logs").document(logId)
            batch.set(
                logRef,
                AuditLog(
                    entityType = "EXPENSE",
                    entityId = expId,
                    referenceNumber = expense.category,
                    action = "CREATE",
                    reason = "Expense recorded: ${expense.title}",
                    beforeSummary = "",
                    afterSummary = "${expense.category} - PKR ${expense.amount}"
                ).toCreateMap(uid)
            )
            awaitCommitOfflineSafe(batch.commit(), OperationType.CREATE, docPath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun correctExpense(oldExpense: Expense, updatedExpense: Expense, reason: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/expenses/${oldExpense.id}"
        return try {
            val uid = requireUserId()
            require(oldExpense.status != TxStatuses.VOID) { "Voided expenses cannot be modified" }
            require(updatedExpense.title.isNotBlank()) { "Expense title is required" }
            require(updatedExpense.amount > 0.0) { "Amount must be greater than zero" }
            require(reason.isNotBlank()) { "Audit reason is required for correction" }

            val ref = userRoot(uid).collection("expenses").document(oldExpense.id)
            docPath = ref.path
            val batch = db.batch()
            batch.update(
                ref,
                mapOf(
                    "category" to updatedExpense.category,
                    "title" to updatedExpense.title.trim().take(120),
                    "amount" to updatedExpense.amount,
                    "paymentMethod" to updatedExpense.paymentMethod,
                    "paidTo" to updatedExpense.paidTo.trim().take(120),
                    "notes" to updatedExpense.notes.trim().take(300),
                    "transactionDateMillis" to updatedExpense.transactionDateMillis,
                    "status" to TxStatuses.CORRECTED,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                userRoot(uid).collection("audit_logs").document(logId),
                AuditLog(
                    entityType = "EXPENSE",
                    entityId = oldExpense.id,
                    referenceNumber = updatedExpense.category,
                    action = "CORRECT",
                    reason = reason.trim().take(300),
                    beforeSummary = "${oldExpense.category}: ${oldExpense.title} (PKR ${oldExpense.amount})",
                    afterSummary = "${updatedExpense.category}: ${updatedExpense.title} (PKR ${updatedExpense.amount})"
                ).toCreateMap(uid)
            )
            awaitCommitOfflineSafe(batch.commit(), OperationType.UPDATE, docPath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun voidExpense(expense: Expense, reason: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/expenses/${expense.id}"
        return try {
            val uid = requireUserId()
            require(expense.status != TxStatuses.VOID) { "Expense already voided" }
            require(reason.isNotBlank()) { "Audit reason is required to void an expense" }
            val ref = userRoot(uid).collection("expenses").document(expense.id)
            docPath = ref.path
            val batch = db.batch()
            batch.update(ref, mapOf("status" to TxStatuses.VOID, "updatedAt" to FieldValue.serverTimestamp()))
            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            batch.set(
                userRoot(uid).collection("audit_logs").document(logId),
                AuditLog(
                    entityType = "EXPENSE",
                    entityId = expense.id,
                    referenceNumber = expense.category,
                    action = "VOID",
                    reason = reason.trim().take(300),
                    beforeSummary = "${expense.category}: ${expense.title} (PKR ${expense.amount})",
                    afterSummary = "VOID"
                ).toCreateMap(uid)
            )
            awaitCommitOfflineSafe(batch.commit(), OperationType.UPDATE, docPath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun restoreBackupJson(
        jsonString: String,
        existingMaterialIds: Set<String> = emptySet(),
        existingCustomerIds: Set<String> = emptySet(),
        existingSupplierIds: Set<String> = emptySet(),
        existingSaleIds: Set<String> = emptySet(),
        existingPurchaseIds: Set<String> = emptySet(),
        existingPaymentIds: Set<String> = emptySet(),
        existingExpenseIds: Set<String> = emptySet(),
        existingMovementIds: Set<String> = emptySet()
    ): Result<Int> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid/restore"
            val payload = BackupManager.parseFromJson(jsonString, uid)

            var restoredCount = 0
            payload.settings?.let {
                updateBusinessSettings(it).getOrThrow()
                restoredCount++
            }

            val ops = mutableListOf<RestoreBatchOp>()

            payload.materials.filter { it.id.isNotBlank() }.forEach { m ->
                val ref = userRoot(uid).collection("materials").document(m.id)
                if (m.id in existingMaterialIds) {
                    ops += RestoreBatchOp.UpdateOp(
                        ref,
                        mapOf(
                            "name" to m.name.trim().take(120),
                            "category" to m.category,
                            "specification" to m.specification.trim().take(120),
                            "unit" to m.unit.trim().ifBlank { "CFT" }.take(30),
                            "purchaseRate" to m.purchaseRate.coerceAtLeast(0.0),
                            "saleRate" to m.saleRate.coerceAtLeast(0.0),
                            "avgCost" to m.avgCost.coerceAtLeast(0.0),
                            "currentStock" to m.currentStock,
                            "minStockAlert" to m.minStockAlert.coerceAtLeast(0.0),
                            "archived" to m.archived,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    ops += RestoreBatchOp.SetOp(ref, m.toCreateMap(uid))
                }
            }

            payload.customers.filter { it.id.isNotBlank() }.forEach { c ->
                val ref = userRoot(uid).collection("customers").document(c.id)
                if (c.id in existingCustomerIds) {
                    ops += RestoreBatchOp.UpdateOp(
                        ref,
                        mapOf(
                            "name" to c.name.trim().take(120),
                            "phone" to c.phone.trim().take(40),
                            "address" to c.address.trim().take(250),
                            "openingBalance" to c.openingBalance,
                            "currentBalance" to c.currentBalance,
                            "notes" to c.notes.trim().take(300),
                            "archived" to c.archived,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    ops += RestoreBatchOp.SetOp(ref, c.toCreateMap(uid))
                }
            }

            payload.suppliers.filter { it.id.isNotBlank() }.forEach { s ->
                val ref = userRoot(uid).collection("suppliers").document(s.id)
                if (s.id in existingSupplierIds) {
                    ops += RestoreBatchOp.UpdateOp(
                        ref,
                        mapOf(
                            "name" to s.name.trim().take(120),
                            "phone" to s.phone.trim().take(40),
                            "address" to s.address.trim().take(250),
                            "openingBalance" to s.openingBalance,
                            "currentBalance" to s.currentBalance,
                            "notes" to s.notes.trim().take(300),
                            "archived" to s.archived,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    ops += RestoreBatchOp.SetOp(ref, s.toCreateMap(uid))
                }
            }

            payload.sales.filter { it.id.isNotBlank() && it.id !in existingSaleIds }.forEach {
                ops += RestoreBatchOp.SetOp(userRoot(uid).collection("sales").document(it.id), it.toCreateMap(uid))
            }
            payload.purchases.filter { it.id.isNotBlank() && it.id !in existingPurchaseIds }.forEach {
                ops += RestoreBatchOp.SetOp(userRoot(uid).collection("purchases").document(it.id), it.toCreateMap(uid))
            }
            payload.payments.filter { it.id.isNotBlank() && it.id !in existingPaymentIds }.forEach {
                ops += RestoreBatchOp.SetOp(userRoot(uid).collection("party_payments").document(it.id), it.toCreateMap(uid))
            }
            payload.expenses.filter { it.id.isNotBlank() && it.id !in existingExpenseIds }.forEach {
                ops += RestoreBatchOp.SetOp(userRoot(uid).collection("expenses").document(it.id), it.toCreateMap(uid))
            }
            payload.stockMovements.filter { it.id.isNotBlank() && it.id !in existingMovementIds }.forEach {
                ops += RestoreBatchOp.SetOp(userRoot(uid).collection("stock_movements").document(it.id), it.toCreateMap(uid))
            }

            // Firestore batches support up to 500 writes per batch
            ops.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { op ->
                    when (op) {
                        is RestoreBatchOp.SetOp -> batch.set(op.ref, op.data)
                        is RestoreBatchOp.UpdateOp -> batch.update(op.ref, op.data)
                    }
                }
                awaitCommitOfflineSafe(batch.commit(), OperationType.WRITE, docPath)
            }
            restoredCount += ops.size

            val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            awaitCommitOfflineSafe(
                userRoot(uid).collection("audit_logs").document(logId).set(
                    AuditLog(
                        entityType = "SETTINGS",
                        entityId = "default",
                        referenceNumber = "BACKUP_RESTORE",
                        action = "RESTORE",
                        reason = "Restored backup ($restoredCount records)",
                        beforeSummary = "",
                        afterSummary = "Restored $restoredCount records"
                    ).toCreateMap(uid)
                ),
                OperationType.CREATE,
                "users/$uid/audit_logs/$logId"
            )
            Result.success(restoredCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun reconcilePartyBalances(
        customerUpdates: List<Pair<String, Double>>,
        supplierUpdates: List<Pair<String, Double>>
    ): Result<Int> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/reconcile"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid/reconcile"
            val totalFixed = customerUpdates.size + supplierUpdates.size
            if (totalFixed > 0) {
                val batch = db.batch()
                customerUpdates.forEach { (custId, exactBalance) ->
                    batch.update(
                        userRoot(uid).collection("customers").document(custId),
                        mapOf("currentBalance" to exactBalance, "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
                supplierUpdates.forEach { (supId, exactBalance) ->
                    batch.update(
                        userRoot(uid).collection("suppliers").document(supId),
                        mapOf("currentBalance" to exactBalance, "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
                val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                batch.set(
                    userRoot(uid).collection("audit_logs").document(logId),
                    AuditLog(
                        entityType = "SETTINGS",
                        entityId = "default",
                        referenceNumber = "LEDGER_RECONCILE",
                        action = "ADJUST",
                        reason = "Admin ledger balance reconciliation",
                        beforeSummary = "$totalFixed account balances checked",
                        afterSummary = "Synchronized $totalFixed accounts with ledger totals"
                    ).toCreateMap(uid)
                )
                awaitCommitOfflineSafe(batch.commit(), OperationType.UPDATE, docPath)
            }
            Result.success(totalFixed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private sealed interface RestoreBatchOp {
        data class SetOp(val ref: com.google.firebase.firestore.DocumentReference, val data: Map<String, Any>) : RestoreBatchOp
        data class UpdateOp(val ref: com.google.firebase.firestore.DocumentReference, val data: Map<String, Any>) : RestoreBatchOp
    }
}
