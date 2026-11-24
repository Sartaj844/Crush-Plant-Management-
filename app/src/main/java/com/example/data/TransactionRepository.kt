package com.example.data

import com.example.domain.AccountingEngine
import com.example.domain.AuditLog
import com.example.domain.BusinessSettings
import com.example.domain.Customer
import com.example.domain.LineItem
import com.example.domain.Material
import com.example.domain.MovementTypes
import com.example.domain.PartyPayment
import com.example.domain.Purchase
import com.example.domain.Sale
import com.example.domain.StockMovement
import com.example.domain.Supplier
import com.example.domain.TxStatuses
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CancellationException
import java.util.UUID

class TransactionRepository(
    private val repo: CrusherRepository
) {
    private val db get() = repo.db

    private fun newId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    suspend fun createSale(
        settings: BusinessSettings,
        customer: Customer,
        rawItems: List<LineItem>,
        materialsById: Map<String, Material>,
        discount: Double,
        paidAmount: Double,
        paymentMethod: String,
        transactionDateMillis: Long,
        notes: String,
        authorizeNegativeStock: Boolean
    ): Result<Sale> {
        var docPath = "users/${customer.userId}/sales"
        return try {
            val uid = repo.requireUserId()
            require(rawItems.isNotEmpty()) { "At least one material item is required" }

            // Aggregate quantities per material to validate negative stock accurately
            val requestedByMat = rawItems.groupBy { it.materialId }
                .mapValues { (_, list) -> list.sumOf { it.quantity } }

            val allowNeg = settings.allowNegativeStock || authorizeNegativeStock
            val updatedStocks = mutableMapOf<String, Double>()
            requestedByMat.forEach { (matId, totalQty) ->
                val mat = materialsById[matId] ?: error("Selected material not found")
                val newStock = AccountingEngine.round2(mat.currentStock - totalQty)
                if (newStock < 0.0 && !allowNeg) {
                    error("Insufficient stock for ${mat.name} (Available: ${AccountingEngine.formatQty(mat.currentStock)} ${mat.unit}, Requested: ${AccountingEngine.formatQty(totalQty)})")
                }
                updatedStocks[matId] = newStock
            }

            val resolvedItems = rawItems.map { item ->
                val mat = materialsById[item.materialId] ?: error("Material not found")
                val unitCost = if (mat.avgCost > 0.0) mat.avgCost else mat.purchaseRate
                item.copy(
                    costRate = unitCost,
                    lineTotal = AccountingEngine.calculateLineTotal(item.quantity, item.rate)
                )
            }

            val totals = AccountingEngine.calculateSaleTotals(resolvedItems, discount, paidAmount)
            val saleId = newId("sale")
            val invoiceNumber = AccountingEngine.generateDocNumber(settings.invoicePrefix, settings.nextInvoiceSeq)
            val sale = Sale(
                id = saleId,
                userId = uid,
                invoiceNumber = invoiceNumber,
                customerId = customer.id,
                customerName = customer.name,
                customerPhone = customer.phone,
                transactionDateMillis = transactionDateMillis,
                items = resolvedItems,
                subtotal = totals.subtotal,
                discount = totals.discount,
                grandTotal = totals.grandTotal,
                paidAmount = totals.paidAmount,
                remainingBalance = totals.remainingBalance,
                totalCogs = totals.totalCogs,
                paymentMethod = paymentMethod,
                notes = notes.trim(),
                status = TxStatuses.CONFIRMED,
                revision = 1L
            )

            val root = repo.userRoot(uid)
            val saleRef = root.collection("sales").document(saleId)
            docPath = saleRef.path
            val batch = db.batch()
            batch.set(saleRef, sale.toCreateMap(uid))

            // Track running stock per material across multiple line items
            val runningStock = materialsById.mapValues { it.value.currentStock }.toMutableMap()
            resolvedItems.forEach { item ->
                val mat = materialsById[item.materialId]!!
                val after = AccountingEngine.round2((runningStock[item.materialId] ?: 0.0) - item.quantity)
                runningStock[item.materialId] = after
                val movId = newId("mov")
                batch.set(
                    root.collection("stock_movements").document(movId),
                    StockMovement(
                        materialId = item.materialId,
                        materialName = item.materialName,
                        movementType = MovementTypes.SALE_OUT,
                        quantityDelta = -item.quantity,
                        balanceAfter = after,
                        unit = item.unit,
                        referenceId = saleId,
                        referenceNumber = invoiceNumber,
                        reason = "Sale to ${customer.name}",
                        transactionDateMillis = transactionDateMillis
                    ).toCreateMap(uid)
                )
            }

            updatedStocks.forEach { (matId, finalStock) ->
                batch.update(
                    root.collection("materials").document(matId),
                    mapOf("currentStock" to finalStock, "updatedAt" to FieldValue.serverTimestamp())
                )
            }

            val newCustomerBalance = AccountingEngine.round2(customer.currentBalance + totals.remainingBalance)
            batch.update(
                root.collection("customers").document(customer.id),
                mapOf("currentBalance" to newCustomerBalance, "updatedAt" to FieldValue.serverTimestamp())
            )

            batch.update(
                root.collection("business_settings").document("default"),
                mapOf(
                    "nextInvoiceSeq" to (settings.nextInvoiceSeq + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val logId = newId("log")
            batch.set(
                root.collection("audit_logs").document(logId),
                AuditLog(
                    entityType = "SALE",
                    entityId = saleId,
                    referenceNumber = invoiceNumber,
                    action = "CREATE",
                    reason = "Confirmed sale invoice to ${customer.name}",
                    beforeSummary = "",
                    afterSummary = "Total ${AccountingEngine.formatPkr(totals.grandTotal)}, Paid ${AccountingEngine.formatPkr(totals.paidAmount)}"
                ).toCreateMap(uid)
            )

            repo.awaitCommitOfflineSafe(batch.commit(), OperationType.CREATE, docPath)
            Result.success(sale)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun correctSale(
        settings: BusinessSettings,
        oldSale: Sale,
        customer: Customer,
        newItems: List<LineItem>,
        materialsById: Map<String, Material>,
        newDiscount: Double,
        newPaidAmount: Double,
        newPaymentMethod: String,
        newDateMillis: Long,
        newNotes: String,
        auditReason: String,
        authorizeNegativeStock: Boolean
    ): Result<Unit> {
        var docPath = "users/${oldSale.userId}/sales/${oldSale.id}"
        return try {
            val uid = repo.requireUserId()
            require(oldSale.status != TxStatuses.VOID) { "Voided invoice cannot be edited" }
            require(newItems.isNotEmpty()) { "At least one item is required" }
            require(auditReason.isNotBlank()) { "Correction reason is required for audit trail" }

            val resolvedItems = newItems.map { item ->
                val mat = materialsById[item.materialId] ?: error("Material not found")
                val existingItemCost = oldSale.items.firstOrNull { it.materialId == item.materialId }?.costRate
                val unitCost = existingItemCost ?: (if (mat.avgCost > 0.0) mat.avgCost else mat.purchaseRate)
                item.copy(
                    costRate = unitCost,
                    lineTotal = AccountingEngine.calculateLineTotal(item.quantity, item.rate)
                )
            }
            val totals = AccountingEngine.calculateSaleTotals(resolvedItems, newDiscount, newPaidAmount)

            // Stock delta per material: positive means stock returns to warehouse; negative means more stock sold
            val oldQtyByMat = oldSale.items.groupBy { it.materialId }.mapValues { it.value.sumOf { i -> i.quantity } }
            val newQtyByMat = resolvedItems.groupBy { it.materialId }.mapValues { it.value.sumOf { i -> i.quantity } }
            val allMatIds = (oldQtyByMat.keys + newQtyByMat.keys).toSet()
            val allowNeg = settings.allowNegativeStock || authorizeNegativeStock

            val root = repo.userRoot(uid)
            val saleRef = root.collection("sales").document(oldSale.id)
            docPath = saleRef.path
            val batch = db.batch()

            allMatIds.forEach { matId ->
                val delta = AccountingEngine.round2((oldQtyByMat[matId] ?: 0.0) - (newQtyByMat[matId] ?: 0.0))
                if (delta != 0.0) {
                    val mat = materialsById[matId] ?: error("Material $matId not found")
                    val after = AccountingEngine.round2(mat.currentStock + delta)
                    if (after < 0.0 && !allowNeg) {
                        error("Insufficient stock for ${mat.name} after edit (Available: ${mat.currentStock})")
                    }
                    batch.update(
                        root.collection("materials").document(matId),
                        mapOf("currentStock" to after, "updatedAt" to FieldValue.serverTimestamp())
                    )
                    batch.set(
                        root.collection("stock_movements").document(newId("mov")),
                        StockMovement(
                            materialId = matId,
                            materialName = mat.name,
                            movementType = MovementTypes.CORRECTION_DELTA,
                            quantityDelta = delta,
                            balanceAfter = after,
                            unit = mat.unit,
                            referenceId = oldSale.id,
                            referenceNumber = oldSale.invoiceNumber,
                            reason = "Invoice correction: ${auditReason.trim()}",
                            transactionDateMillis = newDateMillis
                        ).toCreateMap(uid)
                    )
                }
            }

            val balDelta = AccountingEngine.round2(totals.remainingBalance - oldSale.remainingBalance)
            if (balDelta != 0.0) {
                batch.update(
                    root.collection("customers").document(customer.id),
                    mapOf(
                        "currentBalance" to AccountingEngine.round2(customer.currentBalance + balDelta),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.update(
                saleRef,
                mapOf(
                    "customerId" to customer.id,
                    "customerName" to customer.name,
                    "customerPhone" to customer.phone,
                    "transactionDateMillis" to newDateMillis,
                    "items" to resolvedItems.map { it.toMap() },
                    "subtotal" to totals.subtotal,
                    "discount" to totals.discount,
                    "grandTotal" to totals.grandTotal,
                    "paidAmount" to totals.paidAmount,
                    "remainingBalance" to totals.remainingBalance,
                    "totalCogs" to totals.totalCogs,
                    "paymentMethod" to newPaymentMethod,
                    "notes" to newNotes.trim().take(300),
                    "status" to TxStatuses.CORRECTED,
                    "revision" to (oldSale.revision + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.set(
                root.collection("audit_logs").document(newId("log")),
                AuditLog(
                    entityType = "SALE",
                    entityId = oldSale.id,
                    referenceNumber = oldSale.invoiceNumber,
                    action = "CORRECT",
                    reason = auditReason.trim().take(300),
                    beforeSummary = "Total ${AccountingEngine.formatPkr(oldSale.grandTotal)}, Paid ${AccountingEngine.formatPkr(oldSale.paidAmount)}",
                    afterSummary = "Total ${AccountingEngine.formatPkr(totals.grandTotal)}, Paid ${AccountingEngine.formatPkr(totals.paidAmount)}"
                ).toCreateMap(uid)
            )

            repo.awaitCommitOfflineSafe(batch.commit(), OperationType.UPDATE, docPath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun voidSale(
        sale: Sale,
        customer: Customer?,
        materialsById: Map<String, Material>,
        reason: String
    ): Result<Unit> {
        var docPath = "users/${sale.userId}/sales/${sale.id}"
        return try {
            val uid = repo.requireUserId()
            require(sale.status != TxStatuses.VOID) { "Invoice is already voided" }
            require(reason.isNotBlank()) { "Audit reason is required to void an invoice" }

            val root = repo.userRoot(uid)
            val saleRef = root.collection("sales").document(sale.id)
            docPath = saleRef.path
            val batch = db.batch()

            sale.items.groupBy { it.materialId }.forEach { (matId, list) ->
                val qtyToReturn = list.sumOf { it.quantity }
                val mat = materialsById[matId]
                if (mat != null) {
                    val after = AccountingEngine.round2(mat.currentStock + qtyToReturn)
                    batch.update(
                        root.collection("materials").document(matId),
                        mapOf("currentStock" to after, "updatedAt" to FieldValue.serverTimestamp())
                    )
                    batch.set(
                        root.collection("stock_movements").document(newId("mov")),
                        StockMovement(
                            materialId = matId,
                            materialName = mat.name,
                            movementType = MovementTypes.VOID_REVERSAL,
                            quantityDelta = qtyToReturn,
                            balanceAfter = after,
                            unit = mat.unit,
                            referenceId = sale.id,
                            referenceNumber = sale.invoiceNumber,
                            reason = "Void invoice: ${reason.trim()}",
                            transactionDateMillis = System.currentTimeMillis()
                        ).toCreateMap(uid)
                    )
                }
            }

            if (customer != null && sale.remainingBalance != 0.0) {
                batch.update(
                    root.collection("customers").document(customer.id),
                    mapOf(
                        "currentBalance" to AccountingEngine.round2(customer.currentBalance - sale.remainingBalance),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.update(
                saleRef,
                mapOf(
                    "status" to TxStatuses.VOID,
                    "revision" to (sale.revision + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                root.collection("audit_logs").document(newId("log")),
                AuditLog(
                    entityType = "SALE",
                    entityId = sale.id,
                    referenceNumber = sale.invoiceNumber,
                    action = "VOID",
                    reason = reason.trim().take(300),
                    beforeSummary = "Total ${AccountingEngine.formatPkr(sale.grandTotal)}",
                    afterSummary = "VOID"
                ).toCreateMap(uid)
            )

            repo.awaitCommitOfflineSafe(batch.commit(), OperationType.UPDATE, docPath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createPurchase(
        settings: BusinessSettings,
        supplier: Supplier,
        rawItems: List<LineItem>,
        materialsById: Map<String, Material>,
        discount: Double,
        paidAmount: Double,
        paymentMethod: String,
        transactionDateMillis: Long,
        notes: String
    ): Result<Purchase> {
        var docPath = "users/${supplier.userId}/purchases"
        return try {
            val uid = repo.requireUserId()
            require(rawItems.isNotEmpty()) { "At least one material item is required" }

            val resolvedItems = rawItems.map { item ->
                item.copy(
                    costRate = item.rate,
                    lineTotal = AccountingEngine.calculateLineTotal(item.quantity, item.rate)
                )
            }
            val totals = AccountingEngine.calculatePurchaseTotals(resolvedItems, discount, paidAmount)
            val purchaseId = newId("pur")
            val voucherNumber = AccountingEngine.generateDocNumber(settings.purchasePrefix, settings.nextPurchaseSeq)

            val purchase = Purchase(
                id = purchaseId,
                userId = uid,
                voucherNumber = voucherNumber,
                supplierId = supplier.id,
                supplierName = supplier.name,
                supplierPhone = supplier.phone,
                transactionDateMillis = transactionDateMillis,
                items = resolvedItems,
                subtotal = totals.subtotal,
                discount = totals.discount,
                grandTotal = totals.grandTotal,
                paidAmount = totals.paidAmount,
                remainingBalance = totals.remainingBalance,
                paymentMethod = paymentMethod,
                notes = notes.trim(),
                status = TxStatuses.CONFIRMED,
                revision = 1L
            )

            val root = repo.userRoot(uid)
            val purchaseRef = root.collection("purchases").document(purchaseId)
            docPath = purchaseRef.path
            val batch = db.batch()
            batch.set(purchaseRef, purchase.toCreateMap(uid))

            val runningStock = materialsById.mapValues { it.value.currentStock }.toMutableMap()
            val runningAvg = materialsById.mapValues { it.value.avgCost }.toMutableMap()
            val lastRate = mutableMapOf<String, Double>()

            resolvedItems.forEach { item ->
                val curStock = runningStock[item.materialId] ?: 0.0
                val curAvg = runningAvg[item.materialId] ?: 0.0
                val newAvg = AccountingEngine.calculateWeightedAvgCost(curStock, curAvg, item.quantity, item.rate)
                val after = AccountingEngine.round2(curStock + item.quantity)
                runningStock[item.materialId] = after
                runningAvg[item.materialId] = newAvg
                lastRate[item.materialId] = item.rate

                batch.set(
                    root.collection("stock_movements").document(newId("mov")),
                    StockMovement(
                        materialId = item.materialId,
                        materialName = item.materialName,
                        movementType = MovementTypes.PURCHASE_IN,
                        quantityDelta = item.quantity,
                        balanceAfter = after,
                        unit = item.unit,
                        referenceId = purchaseId,
                        referenceNumber = voucherNumber,
                        reason = "Purchase from ${supplier.name}",
                        transactionDateMillis = transactionDateMillis
                    ).toCreateMap(uid)
                )
            }

            lastRate.forEach { (matId, rate) ->
                batch.update(
                    root.collection("materials").document(matId),
                    mapOf(
                        "currentStock" to (runningStock[matId] ?: 0.0),
                        "avgCost" to (runningAvg[matId] ?: rate),
                        "purchaseRate" to rate,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.update(
                root.collection("suppliers").document(supplier.id),
                mapOf(
                    "currentBalance" to AccountingEngine.round2(supplier.currentBalance + totals.remainingBalance),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.update(
                root.collection("business_settings").document("default"),
                mapOf(
                    "nextPurchaseSeq" to (settings.nextPurchaseSeq + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.set(
                root.collection("audit_logs").document(newId("log")),
                AuditLog(
                    entityType = "PURCHASE",
                    entityId = purchaseId,
                    referenceNumber = voucherNumber,
                    action = "CREATE",
                    reason = "Confirmed purchase from ${supplier.name}",
                    beforeSummary = "",
                    afterSummary = "Total ${AccountingEngine.formatPkr(totals.grandTotal)}, Paid ${AccountingEngine.formatPkr(totals.paidAmount)}"
                ).toCreateMap(uid)
            )

            repo.awaitCommitOfflineSafe(batch.commit(), OperationType.CREATE, docPath)
            Result.success(purchase)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun voidPurchase(
        purchase: Purchase,
        supplier: Supplier?,
        materialsById: Map<String, Material>,
        reason: String
    ): Result<Unit> {
        var docPath = "users/${purchase.userId}/purchases/${purchase.id}"
        return try {
            val uid = repo.requireUserId()
            require(purchase.status != TxStatuses.VOID) { "Purchase voucher is already voided" }
            require(reason.isNotBlank()) { "Audit reason is required" }

            val root = repo.userRoot(uid)
            val purchaseRef = root.collection("purchases").document(purchase.id)
            docPath = purchaseRef.path
            val batch = db.batch()

            purchase.items.groupBy { it.materialId }.forEach { (matId, list) ->
                val qtyToRemove = list.sumOf { it.quantity }
                val mat = materialsById[matId]
                if (mat != null) {
                    val after = AccountingEngine.round2(mat.currentStock - qtyToRemove)
                    batch.update(
                        root.collection("materials").document(matId),
                        mapOf("currentStock" to after, "updatedAt" to FieldValue.serverTimestamp())
                    )
                    batch.set(
                        root.collection("stock_movements").document(newId("mov")),
                        StockMovement(
                            materialId = matId,
                            materialName = mat.name,
                            movementType = MovementTypes.VOID_REVERSAL,
                            quantityDelta = -qtyToRemove,
                            balanceAfter = after,
                            unit = mat.unit,
                            referenceId = purchase.id,
                            referenceNumber = purchase.voucherNumber,
                            reason = "Void purchase: ${reason.trim()}",
                            transactionDateMillis = System.currentTimeMillis()
                        ).toCreateMap(uid)
                    )
                }
            }

            if (supplier != null && purchase.remainingBalance != 0.0) {
                batch.update(
                    root.collection("suppliers").document(supplier.id),
                    mapOf(
                        "currentBalance" to AccountingEngine.round2(supplier.currentBalance - purchase.remainingBalance),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.update(
                purchaseRef,
                mapOf(
                    "status" to TxStatuses.VOID,
                    "revision" to (purchase.revision + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                root.collection("audit_logs").document(newId("log")),
                AuditLog(
                    entityType = "PURCHASE",
                    entityId = purchase.id,
                    referenceNumber = purchase.voucherNumber,
                    action = "VOID",
                    reason = reason.trim().take(300),
                    beforeSummary = "Total ${AccountingEngine.formatPkr(purchase.grandTotal)}",
                    afterSummary = "VOID"
                ).toCreateMap(uid)
            )

            repo.awaitCommitOfflineSafe(batch.commit(), OperationType.UPDATE, docPath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adjustStock(
        settings: BusinessSettings,
        material: Material,
        quantityDelta: Double,
        reason: String,
        authorizeNegative: Boolean
    ): Result<Unit> {
        var docPath = "users/${material.userId}/materials/${material.id}"
        return try {
            val uid = repo.requireUserId()
            require(quantityDelta != 0.0) { "Adjustment quantity cannot be zero" }
            require(reason.isNotBlank()) { "Adjustment reason is required" }

            val after = AccountingEngine.round2(material.currentStock + quantityDelta)
            if (after < 0.0 && !settings.allowNegativeStock && !authorizeNegative) {
                error("Adjustment would result in negative stock (${AccountingEngine.formatQty(after)} ${material.unit}).")
            }

            val root = repo.userRoot(uid)
            val matRef = root.collection("materials").document(material.id)
            docPath = matRef.path
            val batch = db.batch()
            batch.update(matRef, mapOf("currentStock" to after, "updatedAt" to FieldValue.serverTimestamp()))
            batch.set(
                root.collection("stock_movements").document(newId("mov")),
                StockMovement(
                    materialId = material.id,
                    materialName = material.name,
                    movementType = if (quantityDelta > 0) MovementTypes.ADJUSTMENT_IN else MovementTypes.ADJUSTMENT_OUT,
                    quantityDelta = quantityDelta,
                    balanceAfter = after,
                    unit = material.unit,
                    referenceId = material.id,
                    referenceNumber = "ADJ",
                    reason = reason.trim().take(300),
                    transactionDateMillis = System.currentTimeMillis()
                ).toCreateMap(uid)
            )
            batch.set(
                root.collection("audit_logs").document(newId("log")),
                AuditLog(
                    entityType = "STOCK",
                    entityId = material.id,
                    referenceNumber = material.name,
                    action = "ADJUST",
                    reason = reason.trim().take(300),
                    beforeSummary = "Stock ${AccountingEngine.formatQty(material.currentStock)} ${material.unit}",
                    afterSummary = "Stock ${AccountingEngine.formatQty(after)} ${material.unit}"
                ).toCreateMap(uid)
            )
            repo.awaitCommitOfflineSafe(batch.commit(), OperationType.UPDATE, docPath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordPartyPayment(
        partyType: String,
        partyId: String,
        partyName: String,
        currentBalance: Double,
        entryType: String,
        amount: Double,
        paymentMethod: String,
        referenceNumber: String,
        notes: String,
        transactionDateMillis: Long
    ): Result<PartyPayment> {
        var docPath = "users/unknown/party_payments"
        return try {
            val uid = repo.requireUserId()
            require(amount > 0.0) { "Amount must be greater than zero" }
            val payId = newId("pay")
            val root = repo.userRoot(uid)
            val payRef = root.collection("party_payments").document(payId)
            docPath = payRef.path

            val newBalance = when (partyType) {
                "CUSTOMER" -> if (entryType == "DEBIT_ADJUSTMENT") {
                    AccountingEngine.round2(currentBalance + amount)
                } else {
                    AccountingEngine.round2(currentBalance - amount)
                }
                else -> if (entryType == "CREDIT_ADJUSTMENT") {
                    AccountingEngine.round2(currentBalance + amount)
                } else {
                    AccountingEngine.round2(currentBalance - amount)
                }
            }

            val payment = PartyPayment(
                id = payId,
                userId = uid,
                partyType = partyType,
                partyId = partyId,
                partyName = partyName,
                entryType = entryType,
                amount = amount,
                paymentMethod = paymentMethod,
                referenceNumber = referenceNumber.trim(),
                notes = notes.trim(),
                transactionDateMillis = transactionDateMillis,
                status = TxStatuses.CONFIRMED
            )

            val batch = db.batch()
            batch.set(payRef, payment.toCreateMap(uid))
            val partyColl = if (partyType == "CUSTOMER") "customers" else "suppliers"
            batch.update(
                root.collection(partyColl).document(partyId),
                mapOf("currentBalance" to newBalance, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.set(
                root.collection("audit_logs").document(newId("log")),
                AuditLog(
                    entityType = "PAYMENT",
                    entityId = payId,
                    referenceNumber = referenceNumber.ifBlank { entryType },
                    action = "CREATE",
                    reason = "$partyType $entryType for $partyName",
                    beforeSummary = "Balance ${AccountingEngine.formatPkr(currentBalance)}",
                    afterSummary = "Balance ${AccountingEngine.formatPkr(newBalance)} (${AccountingEngine.formatPkr(amount)})"
                ).toCreateMap(uid)
            )
            repo.awaitCommitOfflineSafe(batch.commit(), OperationType.CREATE, docPath)
            Result.success(payment)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }
}
