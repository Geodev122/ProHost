package com.example.legal

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.WhishTransaction
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WhishReceiptPdfGenerator {

    private const val PAGE_WIDTH = 297 // A6 width in points (105 mm)
    private const val PAGE_HEIGHT = 419 // A6 height in points (148 mm)
    private const val MARGIN = 20f

    private val brandPaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textSize = 14f
        color = 0xFF1C2D42.toInt() // OxfordBlue
        isAntiAlias = true
    }
    private val titlePaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textSize = 11f
        color = 0xFF1C2D42.toInt()
        isAntiAlias = true
    }
    private val metaPaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textSize = 8.5f
        color = 0xFF666666.toInt()
        isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textSize = 8.5f
        color = 0xFF444444.toInt()
        isAntiAlias = true
    }
    private val valuePaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textSize = 8.5f
        color = 0xFF222222.toInt()
        isAntiAlias = true
    }
    private val amountPaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textSize = 13f
        color = 0xFF43A047.toInt() // Success Green
        isAntiAlias = true
    }

    /**
     * Generates a branded A6 PDF receipt for [tx] and saves it under this app's
     * external-files "documents/" directory. Returns the saved [File], or null on failure.
     */
    fun generate(context: Context, tx: WhishTransaction): File? {
        return try {
            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            var y = MARGIN

            // 1. Branded Header
            canvas.drawText("PROHOST LEBANON", MARGIN, y + brandPaint.textSize, brandPaint)
            y += brandPaint.textSize + 2f
            canvas.drawText("Verified Specialist Workspace Grid", MARGIN, y + metaPaint.textSize, metaPaint)
            y += metaPaint.textSize + 10f

            // Divider line
            val linePaint = Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 1f }
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 10f

            // Document Title
            canvas.drawText("WHISH PAYMENT SETTLEMENT BILL", MARGIN, y + titlePaint.textSize, titlePaint)
            y += titlePaint.textSize + 10f

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val dateStr = dateFormat.format(Date(tx.timestamp))

            val details = listOf(
                "Order ID" to "#${tx.orderId}",
                "Date / Time" to dateStr,
                "Space Title" to tx.spaceTitle,
                "Payer Name" to tx.payerName,
                "Payer Phone" to tx.payerPhone,
                "Channel ID" to tx.channelId,
                "Status" to tx.status.name,
                "Access Duration" to "${tx.daysGranted} Days"
            )

            for ((label, value) in details) {
                canvas.drawText(label, MARGIN, y + labelPaint.textSize, labelPaint)
                canvas.drawText(value, MARGIN + 75f, y + valuePaint.textSize, valuePaint)
                y += 15f
            }

            y += 2f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 12f

            // Total Amount Block
            canvas.drawText("Total Paid:", MARGIN, y + titlePaint.textSize, titlePaint)
            val amountStr = "$${tx.amountUsd.toInt()} ${tx.currency}"
            canvas.drawText(amountStr, PAGE_WIDTH - MARGIN - amountPaint.measureText(amountStr), y + amountPaint.textSize, amountPaint)
            y += amountPaint.textSize + 18f

            // Footer
            val footerPaint = Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                textSize = 7f
                color = 0xFF888888.toInt()
                isAntiAlias = true
            }
            canvas.drawText("Electronically generated secure Whish bill.", MARGIN, y, footerPaint)
            y += 9f
            canvas.drawText("ProHost Platform — https://pro-host.tech", MARGIN, y, footerPaint)

            pdf.finishPage(page)

            val dir = File(context.getExternalFilesDir("documents"), "").apply { mkdirs() }
            val outFile = File(dir, "ProHost-WhishBill-${tx.orderId}.pdf")
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()
            outFile
        } catch (e: Exception) {
            null
        }
    }

    fun buildOpenIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
