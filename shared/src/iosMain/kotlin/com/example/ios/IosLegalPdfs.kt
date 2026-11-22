package com.example.ios

import com.example.legal.LegalDocument
import com.example.legal.LegalPdfs
import com.example.util.AppLog
import com.example.util.PickedFile
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectInset
import platform.CoreGraphics.CGRectMake
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync

/**
 * iOS [LegalPdfs]: the document as simple HTML (title, effective date, sections), printed to an A4 PDF with UIKit's print
 * renderer and saved in the temporary folder; [com.example.ui.util.PlatformActions.openFile] then shares it. Null on failure.
 */
@OptIn(ExperimentalForeignApi::class)
object IosLegalPdfs : LegalPdfs {
    override fun generate(document: LegalDocument): PickedFile? {
        var result: PickedFile? = null
        // UIKit printing runs on the main thread; callers are on a background dispatcher.
        if (NSThread.isMainThread) result = render(document) else dispatch_sync(dispatch_get_main_queue()) { result = render(document) }
        return result
    }

    private fun render(document: LegalDocument): PickedFile? = try {
        val renderer = UIPrintPageRenderer()
        renderer.addPrintFormatter(UIMarkupTextPrintFormatter(markupText = html(document)), startingAtPageAtIndex = 0)
        val page = CGRectMake(0.0, 0.0, 595.2, 841.8)
        renderer.setValue(NSValue.valueWithCGRect(page), forKey = "paperRect")
        renderer.setValue(NSValue.valueWithCGRect(CGRectInset(page, 40.0, 48.0)), forKey = "printableRect")
        val data = NSMutableData()
        UIGraphicsBeginPDFContextToData(data, page, null)
        for (i in 0 until renderer.numberOfPages) {
            UIGraphicsBeginPDFPage()
            renderer.drawPageAtIndex(i, inRect = UIGraphicsGetPDFContextBounds())
        }
        UIGraphicsEndPDFContext()
        val path = NSTemporaryDirectory() + "prohost-${document.id}.pdf"
        if (data.writeToFile(path, atomically = true)) NSURL.fileURLWithPath(path).absoluteString?.let(::PickedFile) else null
    } catch (e: Exception) {
        AppLog.w("IosLegalPdfs", "PDF failed: ${e.message}")
        null
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun html(d: LegalDocument): String = buildString {
        append("<html><body dir=\"auto\" style=\"font-family:-apple-system,Helvetica;font-size:11pt;line-height:1.45;color:#1b1b1f\">")
        append("<h1 style=\"font-size:18pt;color:#2B5A8C;margin:0 0 4pt\">").append(esc(d.title)).append("</h1>")
        append("<p style=\"color:#5f6368;margin:0 0 14pt\">").append(esc(d.effectiveDate)).append("</p>")
        d.sections.forEach { s ->
            if (s.heading.isNotBlank()) append("<h2 style=\"font-size:13pt;margin:14pt 0 4pt\">").append(esc(s.heading)).append("</h2>")
            s.paragraphs.forEach { p -> append("<p style=\"margin:0 0 6pt\">").append(esc(p)).append("</p>") }
        }
        append("</body></html>")
    }
}
