package com.example.domain

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

object MaterialCategories {
    const val CEMENT = "Cement"
    const val SAND_AGGREGATE = "Sand & Aggregate"
    const val BLOCKS = "Blocks"
    const val STEEL_OTHER = "Steel & Other"

    val all = listOf(CEMENT, SAND_AGGREGATE, BLOCKS, STEEL_OTHER)
}

object ExpenseCategories {
    const val DIESEL = "Diesel"
    const val LABOUR = "Labour"
    const val SALARY = "Salary"
    const val ELECTRICITY = "Electricity"
    const val TRANSPORT = "Transport"
    const val MACHINE_MAINTENANCE = "Machine Maintenance"
    const val MACHINE_REPAIRING = "Machine Repairing"
    const val OTHER_EXPENSES = "Other Expenses"

    val all = listOf(
        DIESEL,
        LABOUR,
        SALARY,
        ELECTRICITY,
        TRANSPORT,
        MACHINE_MAINTENANCE,
        MACHINE_REPAIRING,
        OTHER_EXPENSES
    )
}

object PaymentMethods {
    const val CASH = "Cash"
    const val BANK_TRANSFER = "Bank Transfer"
    const val CHEQUE = "Cheque"
    const val CREDIT = "Credit"

    val transactionMethods = listOf(CASH, BANK_TRANSFER, CHEQUE, CREDIT)
    val expenseMethods = listOf(CASH, BANK_TRANSFER, CHEQUE)
    val paymentMethods = listOf(CASH, BANK_TRANSFER, CHEQUE, "Adjustment")
}

object TxStatuses {
    const val CONFIRMED = "CONFIRMED"
    const val CORRECTED = "CORRECTED"
    const val VOID = "VOID"
}

object MovementTypes {
    const val PURCHASE_IN = "PURCHASE_IN"
    const val SALE_OUT = "SALE_OUT"
    const val ADJUSTMENT_IN = "ADJUSTMENT_IN"
    const val ADJUSTMENT_OUT = "ADJUSTMENT_OUT"
    const val CORRECTION_DELTA = "CORRECTION_DELTA"
    const val VOID_REVERSAL = "VOID_REVERSAL"
}

object DefaultUnits {
    val all = listOf("CFT", "Bag", "Piece", "Ton", "Kg", "Truck", "Trolley", "Dumper")
}

@Keep
@IgnoreExtraProperties
data class BusinessSettings(
    @DocumentId val id: String = "default",
    val userId: String = "",
    val businessName: String = "Pakistan Crusher & Building Supply",
    val address: String = "Taxila Crusher Zone, Pakistan",
    val phone: String = "",
    val openingCashBalance: Double = 0.0,
    val financialYear: String = "2026-2027",
    val defaultUnit: String = "CFT",
    val invoicePrefix: String = "INV-2026-",
    val nextInvoiceSeq: Long = 1L,
    val purchasePrefix: String = "PV-2026-",
    val nextPurchaseSeq: Long = 1L,
    val allowNegativeStock: Boolean = false,
    val defaultMaterialsSeeded: Boolean = false,
    val logoLocalPath: String = "",
    val operatorName: String = "Plant Owner",
    val requireAdminPin: Boolean = false,
    val adminPin: String = "",
    val languageCode: String = "en",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "businessName" to businessName.ifBlank { "Pakistan Crusher & Building Supply" }.take(120),
        "address" to address.take(300),
        "phone" to phone.take(40),
        "openingCashBalance" to openingCashBalance.coerceAtLeast(0.0),
        "financialYear" to financialYear.ifBlank { "2026-2027" }.take(30),
        "defaultUnit" to defaultUnit.ifBlank { "CFT" }.take(30),
        "invoicePrefix" to invoicePrefix.ifBlank { "INV-2026-" }.take(20),
        "nextInvoiceSeq" to nextInvoiceSeq.coerceAtLeast(1L),
        "purchasePrefix" to purchasePrefix.ifBlank { "PV-2026-" }.take(20),
        "nextPurchaseSeq" to nextPurchaseSeq.coerceAtLeast(1L),
        "allowNegativeStock" to allowNegativeStock,
        "defaultMaterialsSeeded" to defaultMaterialsSeeded,
        "logoLocalPath" to logoLocalPath.take(300),
        "operatorName" to operatorName.trim().ifBlank { "Plant Owner" }.take(80),
        "requireAdminPin" to requireAdminPin,
        "adminPin" to adminPin.trim().take(12),
        "languageCode" to (if (languageCode == "ur") "ur" else "en"),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Material(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = MaterialCategories.SAND_AGGREGATE,
    val specification: String = "",
    val unit: String = "CFT",
    val purchaseRate: Double = 0.0,
    val saleRate: Double = 0.0,
    val avgCost: Double = 0.0,
    val currentStock: Double = 0.0,
    val minStockAlert: Double = 0.0,
    val customItem: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.trim().take(120),
        "category" to (if (category in MaterialCategories.all) category else MaterialCategories.STEEL_OTHER),
        "specification" to specification.trim().take(120),
        "unit" to unit.trim().ifBlank { "CFT" }.take(30),
        "purchaseRate" to purchaseRate.coerceAtLeast(0.0),
        "saleRate" to saleRate.coerceAtLeast(0.0),
        "avgCost" to avgCost.coerceAtLeast(0.0),
        "currentStock" to currentStock,
        "minStockAlert" to minStockAlert.coerceAtLeast(0.0),
        "customItem" to customItem,
        "archived" to archived,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0,
    val notes: String = "",
    val archived: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.trim().take(120),
        "phone" to phone.trim().take(40),
        "address" to address.trim().take(250),
        "openingBalance" to openingBalance,
        "currentBalance" to currentBalance,
        "notes" to notes.trim().take(300),
        "archived" to archived,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Supplier(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0,
    val notes: String = "",
    val archived: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.trim().take(120),
        "phone" to phone.trim().take(40),
        "address" to address.trim().take(250),
        "openingBalance" to openingBalance,
        "currentBalance" to currentBalance,
        "notes" to notes.trim().take(300),
        "archived" to archived,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class LineItem(
    val materialId: String = "",
    val materialName: String = "",
    val specification: String = "",
    val unit: String = "CFT",
    val quantity: Double = 0.0,
    val rate: Double = 0.0,
    val costRate: Double = 0.0,
    val lineTotal: Double = 0.0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "materialId" to materialId,
        "materialName" to materialName.take(120),
        "specification" to specification.take(120),
        "unit" to unit.take(30),
        "quantity" to quantity,
        "rate" to rate,
        "costRate" to costRate,
        "lineTotal" to lineTotal
    )
}

@Keep
@IgnoreExtraProperties
data class Sale(
    @DocumentId val id: String = "",
    val userId: String = "",
    val invoiceNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val transactionDateMillis: Long = 0L,
    val items: List<LineItem> = emptyList(),
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val paidAmount: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val totalCogs: Double = 0.0,
    val paymentMethod: String = PaymentMethods.CASH,
    val notes: String = "",
    val status: String = TxStatuses.CONFIRMED,
    val revision: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "invoiceNumber" to invoiceNumber.take(40),
        "customerId" to customerId.take(128),
        "customerName" to customerName.take(120),
        "customerPhone" to customerPhone.take(40),
        "transactionDateMillis" to transactionDateMillis,
        "items" to items.map { it.toMap() },
        "subtotal" to subtotal,
        "discount" to discount,
        "grandTotal" to grandTotal,
        "paidAmount" to paidAmount,
        "remainingBalance" to remainingBalance,
        "totalCogs" to totalCogs,
        "paymentMethod" to paymentMethod,
        "notes" to notes.take(300),
        "status" to status,
        "revision" to revision,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Purchase(
    @DocumentId val id: String = "",
    val userId: String = "",
    val voucherNumber: String = "",
    val supplierId: String = "",
    val supplierName: String = "",
    val supplierPhone: String = "",
    val transactionDateMillis: Long = 0L,
    val items: List<LineItem> = emptyList(),
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val paidAmount: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val paymentMethod: String = PaymentMethods.CASH,
    val notes: String = "",
    val status: String = TxStatuses.CONFIRMED,
    val revision: Long = 1L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "voucherNumber" to voucherNumber.take(40),
        "supplierId" to supplierId.take(128),
        "supplierName" to supplierName.take(120),
        "supplierPhone" to supplierPhone.take(40),
        "transactionDateMillis" to transactionDateMillis,
        "items" to items.map { it.toMap() },
        "subtotal" to subtotal,
        "discount" to discount,
        "grandTotal" to grandTotal,
        "paidAmount" to paidAmount,
        "remainingBalance" to remainingBalance,
        "paymentMethod" to paymentMethod,
        "notes" to notes.take(300),
        "status" to status,
        "revision" to revision,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PartyPayment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val partyType: String = "CUSTOMER",
    val partyId: String = "",
    val partyName: String = "",
    val entryType: String = "RECOVERY",
    val amount: Double = 0.0,
    val paymentMethod: String = PaymentMethods.CASH,
    val referenceNumber: String = "",
    val notes: String = "",
    val transactionDateMillis: Long = 0L,
    val status: String = TxStatuses.CONFIRMED,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "partyType" to partyType,
        "partyId" to partyId.take(128),
        "partyName" to partyName.take(120),
        "entryType" to entryType,
        "amount" to amount,
        "paymentMethod" to paymentMethod,
        "referenceNumber" to referenceNumber.take(60),
        "notes" to notes.take(300),
        "transactionDateMillis" to transactionDateMillis,
        "status" to status,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Expense(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = ExpenseCategories.DIESEL,
    val title: String = "",
    val amount: Double = 0.0,
    val paymentMethod: String = PaymentMethods.CASH,
    val paidTo: String = "",
    val notes: String = "",
    val transactionDateMillis: Long = 0L,
    val status: String = TxStatuses.CONFIRMED,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "category" to category,
        "title" to title.take(120),
        "amount" to amount,
        "paymentMethod" to paymentMethod,
        "paidTo" to paidTo.take(120),
        "notes" to notes.take(300),
        "transactionDateMillis" to transactionDateMillis,
        "status" to status,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class StockMovement(
    @DocumentId val id: String = "",
    val userId: String = "",
    val materialId: String = "",
    val materialName: String = "",
    val movementType: String = MovementTypes.PURCHASE_IN,
    val quantityDelta: Double = 0.0,
    val balanceAfter: Double = 0.0,
    val unit: String = "CFT",
    val referenceId: String = "",
    val referenceNumber: String = "",
    val reason: String = "",
    val transactionDateMillis: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "materialId" to materialId.take(128),
        "materialName" to materialName.take(120),
        "movementType" to movementType,
        "quantityDelta" to quantityDelta,
        "balanceAfter" to balanceAfter,
        "unit" to unit.take(30),
        "referenceId" to referenceId.take(128),
        "referenceNumber" to referenceNumber.take(60),
        "reason" to reason.take(300),
        "transactionDateMillis" to transactionDateMillis,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val entityType: String = "SALE",
    val entityId: String = "",
    val referenceNumber: String = "",
    val action: String = "CREATE",
    val reason: String = "",
    val beforeSummary: String = "",
    val afterSummary: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "entityType" to entityType,
        "entityId" to entityId.take(128),
        "referenceNumber" to referenceNumber.take(60),
        "action" to action,
        "reason" to reason.ifBlank { "Recorded" }.take(300),
        "beforeSummary" to beforeSummary.take(500),
        "afterSummary" to afterSummary.take(500),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

object DefaultMaterialsCatalog {
    fun buildSeedMaterials(userId: String): List<Material> = listOf(
        // Cement
        Material(id = "mat_cement_facto", userId = userId, name = "Cement Facto", category = MaterialCategories.CEMENT, specification = "50 Kg OPC Bag", unit = "Bag", minStockAlert = 50.0),
        Material(id = "mat_cement_foji", userId = userId, name = "Cement Fauji", category = MaterialCategories.CEMENT, specification = "50 Kg Fauji Cement", unit = "Bag", minStockAlert = 50.0),
        Material(id = "mat_cement_locky", userId = userId, name = "Lucky Cement", category = MaterialCategories.CEMENT, specification = "50 Kg Lucky Cement", unit = "Bag", minStockAlert = 50.0),
        Material(id = "mat_cement_other", userId = userId, name = "Other Cement", category = MaterialCategories.CEMENT, specification = "Custom Cement Brand", unit = "Bag", minStockAlert = 25.0),
        // Sand and Aggregate
        Material(id = "mat_sand_rait_arja", userId = userId, name = "Rait Arja", category = MaterialCategories.SAND_AGGREGATE, specification = "Arja River Sand", unit = "CFT", minStockAlert = 500.0),
        Material(id = "mat_sand_rait_kohala", userId = userId, name = "Rait Kohala", category = MaterialCategories.SAND_AGGREGATE, specification = "Kohala Sand", unit = "CFT", minStockAlert = 500.0),
        Material(id = "mat_sand_rait_pindi", userId = userId, name = "Rait Pindi", category = MaterialCategories.SAND_AGGREGATE, specification = "Rawalpindi Sand", unit = "CFT", minStockAlert = 500.0),
        Material(id = "mat_sand_rait_local", userId = userId, name = "Rait Local", category = MaterialCategories.SAND_AGGREGATE, specification = "Local Quarry Sand", unit = "CFT", minStockAlert = 500.0),
        Material(id = "mat_sand_rait_barakot", userId = userId, name = "Rait Barakot", category = MaterialCategories.SAND_AGGREGATE, specification = "Barakot Sand", unit = "CFT", minStockAlert = 500.0),
        Material(id = "mat_sand_khaka", userId = userId, name = "Khaka", category = MaterialCategories.SAND_AGGREGATE, specification = "Crusher Stone Dust", unit = "CFT", minStockAlert = 500.0),
        Material(id = "mat_sand_pain", userId = userId, name = "Pain", category = MaterialCategories.SAND_AGGREGATE, specification = "Fine Aggregate", unit = "CFT", minStockAlert = 400.0),
        Material(id = "mat_sand_bajri", userId = userId, name = "Bajri / Crush", category = MaterialCategories.SAND_AGGREGATE, specification = "Crushed Aggregate (3/4\" & 1/2\")", unit = "CFT", minStockAlert = 1000.0),
        Material(id = "mat_sand_other", userId = userId, name = "Other Sand and Aggregate", category = MaterialCategories.SAND_AGGREGATE, specification = "Custom Aggregate Grade", unit = "CFT", minStockAlert = 300.0),
        // Blocks
        Material(id = "mat_block_6in", userId = userId, name = "Block 6 Inch", category = MaterialCategories.BLOCKS, specification = "6x8x16 Solid Concrete Block", unit = "Piece", minStockAlert = 500.0),
        Material(id = "mat_block_4in", userId = userId, name = "Block 4 Inch", category = MaterialCategories.BLOCKS, specification = "4x8x16 Partition Block", unit = "Piece", minStockAlert = 500.0),
        // Other Sale/Purchase Items
        Material(id = "mat_other_saria", userId = userId, name = "Saria (Deformed Steel)", category = MaterialCategories.STEEL_OTHER, specification = "3/8\", 1/2\", 5/8\", 3/4\" Sizes", unit = "Ton", minStockAlert = 2.0),
        Material(id = "mat_other_steel", userId = userId, name = "Other Steel Items", category = MaterialCategories.STEEL_OTHER, specification = "Binding Wire / Girder", unit = "Kg", minStockAlert = 100.0),
        Material(id = "mat_other_const", userId = userId, name = "Other Construction Materials", category = MaterialCategories.STEEL_OTHER, specification = "General Construction Item", unit = "Piece", minStockAlert = 50.0)
    )
}
