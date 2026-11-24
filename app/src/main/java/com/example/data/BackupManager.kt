package com.example.data

import com.example.domain.BusinessSettings
import com.example.domain.Customer
import com.example.domain.Expense
import com.example.domain.LineItem
import com.example.domain.Material
import com.example.domain.PartyPayment
import com.example.domain.Purchase
import com.example.domain.Sale
import com.example.domain.StockMovement
import com.example.domain.Supplier
import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
    val settings: BusinessSettings?,
    val materials: List<Material>,
    val customers: List<Customer>,
    val suppliers: List<Supplier>,
    val sales: List<Sale>,
    val purchases: List<Purchase>,
    val payments: List<PartyPayment>,
    val expenses: List<Expense>,
    val stockMovements: List<StockMovement>
)

object BackupManager {

    fun exportToJson(
        settings: BusinessSettings,
        materials: List<Material>,
        customers: List<Customer>,
        suppliers: List<Supplier>,
        sales: List<Sale>,
        purchases: List<Purchase>,
        payments: List<PartyPayment>,
        expenses: List<Expense>,
        stockMovements: List<StockMovement>
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAtMillis", System.currentTimeMillis())

        root.put("businessSettings", JSONObject().apply {
            put("businessName", settings.businessName)
            put("address", settings.address)
            put("phone", settings.phone)
            put("openingCashBalance", settings.openingCashBalance)
            put("financialYear", settings.financialYear)
            put("defaultUnit", settings.defaultUnit)
            put("invoicePrefix", settings.invoicePrefix)
            put("nextInvoiceSeq", settings.nextInvoiceSeq)
            put("purchasePrefix", settings.purchasePrefix)
            put("nextPurchaseSeq", settings.nextPurchaseSeq)
            put("allowNegativeStock", settings.allowNegativeStock)
            put("operatorName", settings.operatorName)
            put("requireAdminPin", settings.requireAdminPin)
            put("adminPin", settings.adminPin)
            put("languageCode", settings.languageCode)
        })

        root.put("materials", JSONArray().apply {
            materials.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("name", m.name)
                    put("category", m.category)
                    put("specification", m.specification)
                    put("unit", m.unit)
                    put("purchaseRate", m.purchaseRate)
                    put("saleRate", m.saleRate)
                    put("avgCost", m.avgCost)
                    put("currentStock", m.currentStock)
                    put("minStockAlert", m.minStockAlert)
                    put("customItem", m.customItem)
                    put("archived", m.archived)
                })
            }
        })

        root.put("customers", JSONArray().apply {
            customers.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("phone", c.phone)
                    put("address", c.address)
                    put("openingBalance", c.openingBalance)
                    put("currentBalance", c.currentBalance)
                    put("notes", c.notes)
                    put("archived", c.archived)
                })
            }
        })

        root.put("suppliers", JSONArray().apply {
            suppliers.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("phone", s.phone)
                    put("address", s.address)
                    put("openingBalance", s.openingBalance)
                    put("currentBalance", s.currentBalance)
                    put("notes", s.notes)
                    put("archived", s.archived)
                })
            }
        })

        root.put("sales", JSONArray().apply {
            sales.forEach { sale ->
                put(JSONObject().apply {
                    put("id", sale.id)
                    put("invoiceNumber", sale.invoiceNumber)
                    put("customerId", sale.customerId)
                    put("customerName", sale.customerName)
                    put("customerPhone", sale.customerPhone)
                    put("transactionDateMillis", sale.transactionDateMillis)
                    put("items", lineItemsToJson(sale.items))
                    put("subtotal", sale.subtotal)
                    put("discount", sale.discount)
                    put("grandTotal", sale.grandTotal)
                    put("paidAmount", sale.paidAmount)
                    put("remainingBalance", sale.remainingBalance)
                    put("totalCogs", sale.totalCogs)
                    put("paymentMethod", sale.paymentMethod)
                    put("notes", sale.notes)
                    put("status", sale.status)
                    put("revision", sale.revision)
                })
            }
        })

        root.put("purchases", JSONArray().apply {
            purchases.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("voucherNumber", p.voucherNumber)
                    put("supplierId", p.supplierId)
                    put("supplierName", p.supplierName)
                    put("supplierPhone", p.supplierPhone)
                    put("transactionDateMillis", p.transactionDateMillis)
                    put("items", lineItemsToJson(p.items))
                    put("subtotal", p.subtotal)
                    put("discount", p.discount)
                    put("grandTotal", p.grandTotal)
                    put("paidAmount", p.paidAmount)
                    put("remainingBalance", p.remainingBalance)
                    put("paymentMethod", p.paymentMethod)
                    put("notes", p.notes)
                    put("status", p.status)
                    put("revision", p.revision)
                })
            }
        })

        root.put("partyPayments", JSONArray().apply {
            payments.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("partyType", p.partyType)
                    put("partyId", p.partyId)
                    put("partyName", p.partyName)
                    put("entryType", p.entryType)
                    put("amount", p.amount)
                    put("paymentMethod", p.paymentMethod)
                    put("referenceNumber", p.referenceNumber)
                    put("notes", p.notes)
                    put("transactionDateMillis", p.transactionDateMillis)
                    put("status", p.status)
                })
            }
        })

        root.put("expenses", JSONArray().apply {
            expenses.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("category", e.category)
                    put("title", e.title)
                    put("amount", e.amount)
                    put("paymentMethod", e.paymentMethod)
                    put("paidTo", e.paidTo)
                    put("notes", e.notes)
                    put("transactionDateMillis", e.transactionDateMillis)
                    put("status", e.status)
                })
            }
        })

        root.put("stockMovements", JSONArray().apply {
            stockMovements.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("materialId", m.materialId)
                    put("materialName", m.materialName)
                    put("movementType", m.movementType)
                    put("quantityDelta", m.quantityDelta)
                    put("balanceAfter", m.balanceAfter)
                    put("unit", m.unit)
                    put("referenceId", m.referenceId)
                    put("referenceNumber", m.referenceNumber)
                    put("reason", m.reason)
                    put("transactionDateMillis", m.transactionDateMillis)
                })
            }
        })

        return root.toString(2)
    }

    fun parseFromJson(jsonString: String, userId: String): BackupPayload {
        val root = JSONObject(jsonString)

        val settingsObj = root.optJSONObject("businessSettings")
        val settings = settingsObj?.let { s ->
            BusinessSettings(
                id = "default",
                userId = userId,
                businessName = s.optString("businessName", "Pakistan Crusher & Building Supply"),
                address = s.optString("address", ""),
                phone = s.optString("phone", ""),
                openingCashBalance = s.optDouble("openingCashBalance", 0.0),
                financialYear = s.optString("financialYear", "2026-2027"),
                defaultUnit = s.optString("defaultUnit", "CFT"),
                invoicePrefix = s.optString("invoicePrefix", "INV-2026-"),
                nextInvoiceSeq = s.optLong("nextInvoiceSeq", 1L),
                purchasePrefix = s.optString("purchasePrefix", "PV-2026-"),
                nextPurchaseSeq = s.optLong("nextPurchaseSeq", 1L),
                allowNegativeStock = s.optBoolean("allowNegativeStock", false),
                defaultMaterialsSeeded = true,
                operatorName = s.optString("operatorName", "Plant Owner"),
                requireAdminPin = s.optBoolean("requireAdminPin", false),
                adminPin = s.optString("adminPin", ""),
                languageCode = s.optString("languageCode", "en")
            )
        }

        val materials = parseArray(root.optJSONArray("materials")) { o ->
            Material(
                id = o.optString("id"),
                userId = userId,
                name = o.optString("name"),
                category = o.optString("category"),
                specification = o.optString("specification"),
                unit = o.optString("unit", "CFT"),
                purchaseRate = o.optDouble("purchaseRate", 0.0),
                saleRate = o.optDouble("saleRate", 0.0),
                avgCost = o.optDouble("avgCost", 0.0),
                currentStock = o.optDouble("currentStock", 0.0),
                minStockAlert = o.optDouble("minStockAlert", 0.0),
                customItem = o.optBoolean("customItem", true),
                archived = o.optBoolean("archived", false)
            )
        }

        val customers = parseArray(root.optJSONArray("customers")) { o ->
            Customer(
                id = o.optString("id"),
                userId = userId,
                name = o.optString("name"),
                phone = o.optString("phone"),
                address = o.optString("address"),
                openingBalance = o.optDouble("openingBalance", 0.0),
                currentBalance = o.optDouble("currentBalance", 0.0),
                notes = o.optString("notes"),
                archived = o.optBoolean("archived", false)
            )
        }

        val suppliers = parseArray(root.optJSONArray("suppliers")) { o ->
            Supplier(
                id = o.optString("id"),
                userId = userId,
                name = o.optString("name"),
                phone = o.optString("phone"),
                address = o.optString("address"),
                openingBalance = o.optDouble("openingBalance", 0.0),
                currentBalance = o.optDouble("currentBalance", 0.0),
                notes = o.optString("notes"),
                archived = o.optBoolean("archived", false)
            )
        }

        val sales = parseArray(root.optJSONArray("sales")) { o ->
            Sale(
                id = o.optString("id"),
                userId = userId,
                invoiceNumber = o.optString("invoiceNumber"),
                customerId = o.optString("customerId"),
                customerName = o.optString("customerName"),
                customerPhone = o.optString("customerPhone"),
                transactionDateMillis = o.optLong("transactionDateMillis", System.currentTimeMillis()),
                items = jsonToLineItems(o.optJSONArray("items")),
                subtotal = o.optDouble("subtotal", 0.0),
                discount = o.optDouble("discount", 0.0),
                grandTotal = o.optDouble("grandTotal", 0.0),
                paidAmount = o.optDouble("paidAmount", 0.0),
                remainingBalance = o.optDouble("remainingBalance", 0.0),
                totalCogs = o.optDouble("totalCogs", 0.0),
                paymentMethod = o.optString("paymentMethod", "Cash"),
                notes = o.optString("notes"),
                status = o.optString("status", "CONFIRMED"),
                revision = o.optLong("revision", 1L)
            )
        }

        val purchases = parseArray(root.optJSONArray("purchases")) { o ->
            Purchase(
                id = o.optString("id"),
                userId = userId,
                voucherNumber = o.optString("voucherNumber"),
                supplierId = o.optString("supplierId"),
                supplierName = o.optString("supplierName"),
                supplierPhone = o.optString("supplierPhone"),
                transactionDateMillis = o.optLong("transactionDateMillis", System.currentTimeMillis()),
                items = jsonToLineItems(o.optJSONArray("items")),
                subtotal = o.optDouble("subtotal", 0.0),
                discount = o.optDouble("discount", 0.0),
                grandTotal = o.optDouble("grandTotal", 0.0),
                paidAmount = o.optDouble("paidAmount", 0.0),
                remainingBalance = o.optDouble("remainingBalance", 0.0),
                paymentMethod = o.optString("paymentMethod", "Cash"),
                notes = o.optString("notes"),
                status = o.optString("status", "CONFIRMED"),
                revision = o.optLong("revision", 1L)
            )
        }

        val payments = parseArray(root.optJSONArray("partyPayments")) { o ->
            PartyPayment(
                id = o.optString("id"),
                userId = userId,
                partyType = o.optString("partyType", "CUSTOMER"),
                partyId = o.optString("partyId"),
                partyName = o.optString("partyName"),
                entryType = o.optString("entryType", "RECOVERY"),
                amount = o.optDouble("amount", 0.0),
                paymentMethod = o.optString("paymentMethod", "Cash"),
                referenceNumber = o.optString("referenceNumber"),
                notes = o.optString("notes"),
                transactionDateMillis = o.optLong("transactionDateMillis", System.currentTimeMillis()),
                status = o.optString("status", "CONFIRMED")
            )
        }

        val expenses = parseArray(root.optJSONArray("expenses")) { o ->
            Expense(
                id = o.optString("id"),
                userId = userId,
                category = o.optString("category", "Diesel"),
                title = o.optString("title"),
                amount = o.optDouble("amount", 0.0),
                paymentMethod = o.optString("paymentMethod", "Cash"),
                paidTo = o.optString("paidTo"),
                notes = o.optString("notes"),
                transactionDateMillis = o.optLong("transactionDateMillis", System.currentTimeMillis()),
                status = o.optString("status", "CONFIRMED")
            )
        }

        val stockMovements = parseArray(root.optJSONArray("stockMovements")) { o ->
            StockMovement(
                id = o.optString("id"),
                userId = userId,
                materialId = o.optString("materialId"),
                materialName = o.optString("materialName"),
                movementType = o.optString("movementType", "ADJUSTMENT_IN"),
                quantityDelta = o.optDouble("quantityDelta", 0.0),
                balanceAfter = o.optDouble("balanceAfter", 0.0),
                unit = o.optString("unit", "CFT"),
                referenceId = o.optString("referenceId"),
                referenceNumber = o.optString("referenceNumber"),
                reason = o.optString("reason"),
                transactionDateMillis = o.optLong("transactionDateMillis", System.currentTimeMillis())
            )
        }

        return BackupPayload(
            settings = settings,
            materials = materials,
            customers = customers,
            suppliers = suppliers,
            sales = sales,
            purchases = purchases,
            payments = payments,
            expenses = expenses,
            stockMovements = stockMovements
        )
    }

    private fun lineItemsToJson(items: List<LineItem>): JSONArray = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("materialId", item.materialId)
                put("materialName", item.materialName)
                put("specification", item.specification)
                put("unit", item.unit)
                put("quantity", item.quantity)
                put("rate", item.rate)
                put("costRate", item.costRate)
                put("lineTotal", item.lineTotal)
            })
        }
    }

    private fun jsonToLineItems(array: JSONArray?): List<LineItem> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { idx ->
            val o = array.optJSONObject(idx) ?: return@mapNotNull null
            LineItem(
                materialId = o.optString("materialId"),
                materialName = o.optString("materialName"),
                specification = o.optString("specification"),
                unit = o.optString("unit", "CFT"),
                quantity = o.optDouble("quantity", 0.0),
                rate = o.optDouble("rate", 0.0),
                costRate = o.optDouble("costRate", 0.0),
                lineTotal = o.optDouble("lineTotal", 0.0)
            )
        }
    }

    private inline fun <T> parseArray(array: JSONArray?, transform: (JSONObject) -> T): List<T> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { idx ->
            array.optJSONObject(idx)?.let(transform)
        }
    }
}
