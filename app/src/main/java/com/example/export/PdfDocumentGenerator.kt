package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.domain.AccountingEngine
import com.example.domain.BusinessSettings
import com.example.domain.LedgerEntry
import com.example.domain.PartyPayment
import com.example.domain.Purchase
import com.example.domain.Sale
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PdfDocumentGenerator {
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val MARGIN = 36f
    private const val RIGHT_X = A4_WIDTH - MARGIN

    private val paintNavy = Paint().apply {
        color = Color.rgb(26, 36, 51)
        isAntiAlias = true
    }
    private val paintAmber = Paint().apply {
        color = Color.rgb(217, 119, 6)
        isAntiAlias = true
    }
    private val paintBorder = Paint().apply {
        color = Color.rgb(203, 213, 225)
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val paintRowBg = Paint().apply {
        color = Color.rgb(248, 250, 252)
        style = Paint.Style.FILL
    }

    private fun textPaint(
        size: Float,
        bold: Boolean = false,
        colorInt: Int = Color.rgb(15, 23, 42),
        align: Paint.Align = Paint.Align.LEFT
    ): Paint = Paint().apply {
        textSize = size
        color = colorInt
        textAlign = align
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        isAntiAlias = true
    }

    fun generateSaleInvoicePdf(context: Context, settings: BusinessSettings, sale: Sale): File {
        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNum).create())
        var canvas = page.canvas

        var y = drawHeader(canvas, settings, "SALES INVOICE", sale.invoiceNumber, AccountingEngine.formatDate(sale.transactionDateMillis), sale.status)

        // Customer Box
        canvas.drawRect(MARGIN, y, RIGHT_X, y + 60f, paintRowBg)
        canvas.drawRect(MARGIN, y, RIGHT_X, y + 60f, paintBorder)
        canvas.drawText("BILL TO CUSTOMER", MARGIN + 10f, y + 16f, textPaint(8.5f, true, Color.rgb(100, 116, 139)))
        canvas.drawText(sale.customerName, MARGIN + 10f, y + 34f, textPaint(12f, true))
        val phoneText = if (sale.customerPhone.isNotBlank()) "Phone: ${sale.customerPhone}" else "Customer ID: ${sale.customerId}"
        canvas.drawText(phoneText, MARGIN + 10f, y + 50f, textPaint(9.5f, false, Color.rgb(71, 85, 105)))
        canvas.drawText("Payment: ${sale.paymentMethod}", RIGHT_X - 10f, y + 34f, textPaint(10f, true, align = Paint.Align.RIGHT))
        canvas.drawText("Revision #${sale.revision}", RIGHT_X - 10f, y + 50f, textPaint(9f, false, Color.rgb(71, 85, 105), Paint.Align.RIGHT))
        y += 76f

        // Table Header
        y = drawItemsTableHeader(canvas, y)
        sale.items.forEachIndexed { idx, item ->
            if (y > A4_HEIGHT - 190f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = MARGIN + 20f
                y = drawItemsTableHeader(canvas, y)
            }
            if (idx % 2 == 1) {
                canvas.drawRect(MARGIN, y, RIGHT_X, y + 26f, paintRowBg)
            }
            canvas.drawLine(MARGIN, y + 26f, RIGHT_X, y + 26f, paintBorder)
            val title = if (item.specification.isNotBlank()) "${item.materialName} (${item.specification})" else item.materialName
            canvas.drawText("${idx + 1}. ${title.take(36)}", MARGIN + 6f, y + 17f, textPaint(9.5f))
            canvas.drawText("${AccountingEngine.formatQty(item.quantity)} ${item.unit}", 335f, y + 17f, textPaint(9.5f, align = Paint.Align.RIGHT))
            canvas.drawText(AccountingEngine.formatPkr(item.rate), 440f, y + 17f, textPaint(9.5f, align = Paint.Align.RIGHT))
            canvas.drawText(AccountingEngine.formatPkr(item.lineTotal), RIGHT_X - 6f, y + 17f, textPaint(9.5f, true, align = Paint.Align.RIGHT))
            y += 26f
        }

        y += 16f
        // Totals Box
        val totalsX = 330f
        canvas.drawRect(totalsX, y, RIGHT_X, y + 114f, paintRowBg)
        canvas.drawRect(totalsX, y, RIGHT_X, y + 114f, paintBorder)

        var ty = y + 20f
        drawTotalRow(canvas, totalsX + 10f, RIGHT_X - 10f, ty, "Subtotal:", AccountingEngine.formatPkr(sale.subtotal), false)
        ty += 18f
        drawTotalRow(canvas, totalsX + 10f, RIGHT_X - 10f, ty, "Discount:", AccountingEngine.formatPkr(sale.discount), false)
        ty += 20f
        canvas.drawLine(totalsX + 8f, ty - 12f, RIGHT_X - 8f, ty - 12f, paintBorder)
        drawTotalRow(canvas, totalsX + 10f, RIGHT_X - 10f, ty, "GRAND TOTAL:", AccountingEngine.formatPkr(sale.grandTotal), true)
        ty += 20f
        drawTotalRow(canvas, totalsX + 10f, RIGHT_X - 10f, ty, "Paid Amount:", AccountingEngine.formatPkr(sale.paidAmount), false, Color.rgb(5, 150, 105))
        ty += 20f
        drawTotalRow(canvas, totalsX + 10f, RIGHT_X - 10f, ty, "Remaining Balance:", AccountingEngine.formatPkr(sale.remainingBalance), true, Color.rgb(220, 38, 38))

        if (sale.notes.isNotBlank()) {
            canvas.drawText("Notes:", MARGIN, y + 16f, textPaint(9.5f, true))
            canvas.drawText(sale.notes.take(65), MARGIN, y + 32f, textPaint(9f, false, Color.rgb(71, 85, 105)))
        }

        drawSignatureFooter(canvas, settings.businessName)
        doc.finishPage(page)

        val file = File(context.cacheDir, "Invoice_${sale.invoiceNumber.replace("/", "_")}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun generatePurchaseVoucherPdf(context: Context, settings: BusinessSettings, purchase: Purchase): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create())
        val canvas = page.canvas

        var y = drawHeader(canvas, settings, "PURCHASE VOUCHER", purchase.voucherNumber, AccountingEngine.formatDate(purchase.transactionDateMillis), purchase.status)

        canvas.drawRect(MARGIN, y, RIGHT_X, y + 60f, paintRowBg)
        canvas.drawRect(MARGIN, y, RIGHT_X, y + 60f, paintBorder)
        canvas.drawText("SUPPLIER ACCOUNT", MARGIN + 10f, y + 16f, textPaint(8.5f, true, Color.rgb(100, 116, 139)))
        canvas.drawText(purchase.supplierName, MARGIN + 10f, y + 34f, textPaint(12f, true))
        val phoneText = if (purchase.supplierPhone.isNotBlank()) "Phone: ${purchase.supplierPhone}" else "Supplier ID: ${purchase.supplierId}"
        canvas.drawText(phoneText, MARGIN + 10f, y + 50f, textPaint(9.5f, false, Color.rgb(71, 85, 105)))
        canvas.drawText("Payment: ${purchase.paymentMethod}", RIGHT_X - 10f, y + 34f, textPaint(10f, true, align = Paint.Align.RIGHT))
        y += 76f

        y = drawItemsTableHeader(canvas, y)
        purchase.items.forEachIndexed { idx, item ->
            if (y > A4_HEIGHT - 190f) return@forEachIndexed
            if (idx % 2 == 1) canvas.drawRect(MARGIN, y, RIGHT_X, y + 26f, paintRowBg)
            canvas.drawLine(MARGIN, y + 26f, RIGHT_X, y + 26f, paintBorder)
            canvas.drawText("${idx + 1}. ${item.materialName.take(36)}", MARGIN + 6f, y + 17f, textPaint(9.5f))
            canvas.drawText("${AccountingEngine.formatQty(item.quantity)} ${item.unit}", 335f, y + 17f, textPaint(9.5f, align = Paint.Align.RIGHT))
            canvas.drawText(AccountingEngine.formatPkr(item.rate), 440f, y + 17f, textPaint(9.5f, align = Paint.Align.RIGHT))
            canvas.drawText(AccountingEngine.formatPkr(item.lineTotal), RIGHT_X - 6f, y + 17f, textPaint(9.5f, true, align = Paint.Align.RIGHT))
            y += 26f
        }

        y += 16f
        val totalsX = 330f
        canvas.drawRect(totalsX, y, RIGHT_X, y + 114f, paintRowBg)
        canvas.drawRect(totalsX, y, RIGHT_X, y + 114f, paintBorder)
        var ty = y + 20f
        drawTotalRow(canvas, totalsX + 10f, RIGHT_X - 10f, ty, "Subtotal:", AccountingEngine.formatPkr(purchase.subtotal), false)
        ty += 18f
        drawTotalRow(canvas, totalsX + 10f, RIGHT_X - 10f, ty, "Discount:", AccountingEngine.formatPkr(purchase.discount), false)
        ty += 20f
        drawTotalRow(canvas, totalsX + 10f, RIGHT_X - 10f, ty, "GRAND TOTAL:", AccountingEngine.formatPkr(purchase.grandTotal), true)
        ty += 20f
        drawTotalRow(canvas, totalsX + 10f, RIGHT_X - 10f, ty, "Paid Amount:", AccountingEngine.formatPkr(purchase.paidAmount), false, Color.rgb(5, 150, 105))
        ty += 20f
        drawTotalRow(canvas, totalsX + 10f, RIGHT_X - 10f, ty, "Payable Balance:", AccountingEngine.formatPkr(purchase.remainingBalance), true, Color.rgb(220, 38, 38))

        drawSignatureFooter(canvas, settings.businessName)
        doc.finishPage(page)

        val file = File(context.cacheDir, "Voucher_${purchase.voucherNumber.replace("/", "_")}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun generatePartyLedgerPdf(
        context: Context,
        settings: BusinessSettings,
        ledgerTitle: String,
        partyName: String,
        partyPhone: String,
        partyAddress: String,
        entries: List<LedgerEntry>,
        closingBalance: Double
    ): File {
        val headers = listOf("Date", "Ref #", "Description", "Debit", "Credit", "Balance")
        val rows = entries.map { e ->
            listOf(
                if (e.dateMillis == 0L) "Opening" else AccountingEngine.formatDate(e.dateMillis),
                e.referenceNumber.take(14),
                e.description.take(30),
                if (e.debit > 0) AccountingEngine.formatPkr(e.debit) else "-",
                if (e.credit > 0) AccountingEngine.formatPkr(e.credit) else "-",
                AccountingEngine.formatPkr(e.runningBalance)
            )
        }
        val subtitle = "$partyName | ${partyPhone.ifBlank { "No Phone" }} | ${partyAddress.ifBlank { "" }}"
        val summary = listOf(
            "Total Entries: ${entries.size}",
            "Closing Outstanding Balance: ${AccountingEngine.formatPkr(closingBalance)}"
        )
        return generateReportPdf(context, settings, ledgerTitle, subtitle, headers, rows, summary)
    }

    fun generatePaymentReceiptPdf(
        context: Context,
        settings: BusinessSettings,
        payment: PartyPayment,
        partyPhone: String = "",
        partyAddress: String = "",
        closingBalanceAfter: Double? = null
    ): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create())
        val canvas = page.canvas

        val isCustomer = payment.partyType == "CUSTOMER"
        val docTitle = if (isCustomer) "RECOVERY RECEIPT" else "PAYMENT RECEIPT"
        val receiptNo = payment.referenceNumber.ifBlank {
            "RCPT-${payment.id.takeLast(6).uppercase()}"
        }

        var y = drawHeader(
            canvas = canvas,
            settings = settings,
            docType = docTitle,
            docNumber = receiptNo,
            dateText = AccountingEngine.formatDate(payment.transactionDateMillis),
            status = payment.status
        )

        // Party Info Box
        canvas.drawRect(MARGIN, y, RIGHT_X, y + 72f, paintRowBg)
        canvas.drawRect(MARGIN, y, RIGHT_X, y + 72f, paintBorder)
        canvas.drawText(
            if (isCustomer) "RECEIVED FROM CUSTOMER" else "PAID TO SUPPLIER",
            MARGIN + 10f,
            y + 18f,
            textPaint(8.5f, true, Color.rgb(100, 116, 139))
        )
        canvas.drawText(payment.partyName, MARGIN + 10f, y + 38f, textPaint(13f, true))
        val subLine = listOfNotNull(
            partyPhone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
            partyAddress.takeIf { it.isNotBlank() }
        ).joinToString(" | ").ifBlank { "Account ID: ${payment.partyId}" }
        canvas.drawText(subLine.take(58), MARGIN + 10f, y + 56f, textPaint(9f, false, Color.rgb(71, 85, 105)))

        canvas.drawText("Method: ${payment.paymentMethod}", RIGHT_X - 10f, y + 36f, textPaint(10f, true, align = Paint.Align.RIGHT))
        canvas.drawText("Entry: ${payment.entryType}", RIGHT_X - 10f, y + 54f, textPaint(9f, false, Color.rgb(71, 85, 105), Paint.Align.RIGHT))
        y += 94f

        // Receipt Amount Highlight Box
        canvas.drawRect(MARGIN, y, RIGHT_X, y + 116f, paintRowBg)
        canvas.drawRect(MARGIN, y, RIGHT_X, y + 116f, paintBorder)
        var ty = y + 26f
        drawTotalRow(
            canvas,
            MARGIN + 14f,
            RIGHT_X - 14f,
            ty,
            if (isCustomer) "Amount Received:" else "Amount Paid:",
            AccountingEngine.formatPkr(payment.amount),
            true,
            Color.rgb(5, 150, 105)
        )
        ty += 24f
        drawTotalRow(canvas, MARGIN + 14f, RIGHT_X - 14f, ty, "Payment Method:", payment.paymentMethod, false)
        ty += 24f
        drawTotalRow(canvas, MARGIN + 14f, RIGHT_X - 14f, ty, "Reference / Slip #:", receiptNo, false)
        if (closingBalanceAfter != null) {
            ty += 24f
            canvas.drawLine(MARGIN + 12f, ty - 14f, RIGHT_X - 12f, ty - 14f, paintBorder)
            drawTotalRow(
                canvas,
                MARGIN + 14f,
                RIGHT_X - 14f,
                ty,
                "Current Account Balance:",
                AccountingEngine.formatPkr(closingBalanceAfter),
                true,
                if (closingBalanceAfter > 0) Color.rgb(220, 38, 38) else Color.rgb(15, 23, 42)
            )
        }
        y += 136f

        if (payment.notes.isNotBlank()) {
            canvas.drawText("Notes / Remarks:", MARGIN, y, textPaint(10f, true))
            canvas.drawText(payment.notes.take(85), MARGIN, y + 18f, textPaint(9.5f, false, Color.rgb(71, 85, 105)))
        }
        if (settings.operatorName.isNotBlank()) {
            canvas.drawText("Recorded by: ${settings.operatorName}", MARGIN, A4_HEIGHT - 96f, textPaint(9f, false, Color.rgb(100, 116, 139)))
        }

        drawSignatureFooter(canvas, settings.businessName)
        doc.finishPage(page)

        val safeNo = receiptNo.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(context.cacheDir, "Receipt_${safeNo}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun generateReportPdf(
        context: Context,
        settings: BusinessSettings,
        title: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        summaryLines: List<String>
    ): File {
        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNum).create())
        var canvas = page.canvas

        var y = drawHeader(canvas, settings, title, "Report", AccountingEngine.formatDate(System.currentTimeMillis()), "OFFICIAL")
        if (subtitle.isNotBlank()) {
            canvas.drawText(subtitle.take(90), MARGIN, y, textPaint(9.5f, false, Color.rgb(71, 85, 105)))
            y += 18f
        }

        val colCount = headers.size.coerceAtLeast(1)
        val colWidth = (RIGHT_X - MARGIN) / colCount

        fun drawTableHeader() {
            canvas.drawRect(MARGIN, y, RIGHT_X, y + 24f, paintNavy)
            headers.forEachIndexed { idx, h ->
                canvas.drawText(
                    h.take(18),
                    MARGIN + idx * colWidth + 4f,
                    y + 16f,
                    textPaint(8.5f, true, Color.WHITE)
                )
            }
            y += 24f
        }

        drawTableHeader()

        rows.forEachIndexed { rIdx, row ->
            if (y > A4_HEIGHT - 120f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = MARGIN
                drawTableHeader()
            }
            if (rIdx % 2 == 1) canvas.drawRect(MARGIN, y, RIGHT_X, y + 20f, paintRowBg)
            canvas.drawLine(MARGIN, y + 20f, RIGHT_X, y + 20f, paintBorder)
            row.forEachIndexed { cIdx, cell ->
                canvas.drawText(
                    cell.take(22),
                    MARGIN + cIdx * colWidth + 4f,
                    y + 14f,
                    textPaint(8f)
                )
            }
            y += 20f
        }

        y += 16f
        summaryLines.forEach { line ->
            if (y < A4_HEIGHT - 60f) {
                canvas.drawText(line, MARGIN, y, textPaint(10.5f, true))
                y += 18f
            }
        }

        drawSignatureFooter(canvas, settings.businessName)
        doc.finishPage(page)

        val safeName = title.replace(Regex("[^a-zA-Z0-9_]"), "_").take(30)
        val file = File(context.cacheDir, "${safeName}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun drawHeader(
        canvas: Canvas,
        settings: BusinessSettings,
        docType: String,
        docNumber: String,
        dateText: String,
        status: String
    ): Float {
        canvas.drawRect(0f, 0f, A4_WIDTH.toFloat(), 96f, paintNavy)
        canvas.drawRect(0f, 96f, A4_WIDTH.toFloat(), 100f, paintAmber)

        var textStartX = MARGIN
        if (settings.logoLocalPath.isNotBlank()) {
            val logoFile = File(settings.logoLocalPath)
            if (logoFile.exists()) {
                val bmp = BitmapFactory.decodeFile(logoFile.absolutePath)
                if (bmp != null) {
                    val dest = Rect(MARGIN.toInt(), 18, (MARGIN + 58).toInt(), 76)
                    canvas.drawBitmap(bmp, null, dest, null)
                    textStartX = MARGIN + 70f
                }
            }
        }

        canvas.drawText(settings.businessName.take(40), textStartX, 38f, textPaint(16f, true, Color.WHITE))
        canvas.drawText(settings.address.take(55), textStartX, 56f, textPaint(9.5f, false, Color.rgb(203, 213, 225)))
        if (settings.phone.isNotBlank()) {
            canvas.drawText("Tel: ${settings.phone}", textStartX, 72f, textPaint(9.5f, false, Color.rgb(251, 191, 36)))
        }

        canvas.drawText(docType, RIGHT_X, 36f, textPaint(14f, true, Color.rgb(251, 191, 36), Paint.Align.RIGHT))
        canvas.drawText(docNumber, RIGHT_X, 54f, textPaint(11f, true, Color.WHITE, Paint.Align.RIGHT))
        canvas.drawText("Date: $dateText | $status", RIGHT_X, 72f, textPaint(9f, false, Color.rgb(203, 213, 225), Paint.Align.RIGHT))

        return 120f
    }

    private fun drawItemsTableHeader(canvas: Canvas, y: Float): Float {
        canvas.drawRect(MARGIN, y, RIGHT_X, y + 24f, paintNavy)
        canvas.drawText("Material Description", MARGIN + 6f, y + 16f, textPaint(9f, true, Color.WHITE))
        canvas.drawText("Qty / Unit", 335f, y + 16f, textPaint(9f, true, Color.WHITE, Paint.Align.RIGHT))
        canvas.drawText("Rate", 440f, y + 16f, textPaint(9f, true, Color.WHITE, Paint.Align.RIGHT))
        canvas.drawText("Amount", RIGHT_X - 6f, y + 16f, textPaint(9f, true, Color.WHITE, Paint.Align.RIGHT))
        return y + 24f
    }

    private fun drawTotalRow(
        canvas: Canvas,
        leftX: Float,
        rightX: Float,
        y: Float,
        label: String,
        value: String,
        bold: Boolean,
        color: Int = Color.rgb(15, 23, 42)
    ) {
        canvas.drawText(label, leftX, y, textPaint(10f, bold, color))
        canvas.drawText(value, rightX, y, textPaint(10f, true, color, Paint.Align.RIGHT))
    }

    private fun drawSignatureFooter(canvas: Canvas, businessName: String) {
        val sigY = A4_HEIGHT - 68f
        canvas.drawLine(MARGIN, sigY, MARGIN + 160f, sigY, paintBorder)
        canvas.drawText("Customer / Receiver Signature", MARGIN, sigY + 14f, textPaint(8.5f, false, Color.rgb(100, 116, 139)))

        canvas.drawLine(RIGHT_X - 160f, sigY, RIGHT_X, sigY, paintBorder)
        canvas.drawText("Authorized Signature ($businessName)", RIGHT_X, sigY + 14f, textPaint(8.5f, false, Color.rgb(100, 116, 139), Paint.Align.RIGHT))
    }

    fun printPdfFile(context: Context, pdfFile: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .build()
        printManager.print(jobName, adapter, attrs)
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
