package com.example.legal

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a [LegalDocument] to a real, paginated A5 PDF using Android's built-in
 * `android.graphics.pdf.PdfDocument` — no third-party PDF/HTML-to-PDF library
 * dependency needed. A5 at 72 points/inch is 419 x 595 points (148 x 210 mm);
 * pagination is manual (measuring wrapped-line height against the remaining page
 * space) since PdfDocument only exposes a raw Canvas per page, not a flowing
 * layout engine.
 */
object LegalPdfGenerator {

    private const val PAGE_WIDTH = 419
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2)

    private val titlePaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textSize = 18f
        isAntiAlias = true
    }
    private val metaPaint = Paint().apply {
        typeface = Typeface.DEFAULT
        textSize = 9f
        color = 0xFF666666.toInt()
        isAntiAlias = true
    }
    private val headingPaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textSize = 12.5f
        isAntiAlias = true
    }
    private val bodyPaint = Paint().apply {
        typeface = Typeface.DEFAULT
        textSize = 10f
        isAntiAlias = true
    }

    /** Wraps [text] to fit [maxWidth] using [paint], returning one string per line. */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "${current} $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    /**
     * Generates the A5 PDF for [document] and saves it under this app's
     * external-files "documents/" directory (no storage permission required on
     * any API level — see file_paths.xml). Returns the saved [File], or null on
     * failure.
     */
    fun generate(context: Context, document: LegalDocument): File? {
        return try {
            val pdf = PdfDocument()
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pdf.pages.size + 1).create())
            var canvas = page.canvas
            var y = MARGIN

            fun newPageIfNeeded(neededHeight: Float) {
                if (y + neededHeight > PAGE_HEIGHT - MARGIN) {
                    pdf.finishPage(page)
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pdf.pages.size + 1).create())
                    canvas = page.canvas
                    y = MARGIN
                }
            }

            // Title block
            newPageIfNeeded(titlePaint.textSize + metaPaint.textSize + 12f)
            canvas.drawText(document.title, MARGIN, y + titlePaint.textSize, titlePaint)
            y += titlePaint.textSize + 4f
            canvas.drawText("ProHost — Effective ${document.effectiveDate}", MARGIN, y + metaPaint.textSize, metaPaint)
            y += metaPaint.textSize + 16f

            for (section in document.sections) {
                newPageIfNeeded(headingPaint.textSize + 10f)
                canvas.drawText(section.heading, MARGIN, y + headingPaint.textSize, headingPaint)
                y += headingPaint.textSize + 8f

                for (paragraph in section.paragraphs) {
                    val lines = wrapText(paragraph, bodyPaint, CONTENT_WIDTH)
                    val lineHeight = bodyPaint.textSize + 4f
                    for (line in lines) {
                        newPageIfNeeded(lineHeight)
                        canvas.drawText(line, MARGIN, y + bodyPaint.textSize, bodyPaint)
                        y += lineHeight
                    }
                    y += 6f
                }
                y += 8f
            }

            pdf.finishPage(page)

            val dir = File(context.getExternalFilesDir("documents"), "").apply { mkdirs() }
            val outFile = File(dir, "ProHost-${document.id}.pdf")
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()
            outFile
        } catch (e: Exception) {
            null
        }
    }

    /** Builds a content:// Uri (via FileProvider) and a share/open Intent for [file]. */
    fun buildOpenIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
