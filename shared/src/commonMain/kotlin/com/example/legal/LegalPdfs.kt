package com.example.legal

import com.example.util.PickedFile
import kotlin.concurrent.Volatile

/**
 * Creates the downloadable PDF of a legal document. One implementation per platform: Android draws it with `android.graphics.pdf.PdfDocument`
 * (the unchanged [LegalPdfGenerator]); iOS will use its own PDF renderer. The result is a [PickedFile] that
 * [com.example.ui.util.PlatformActions.openFile] can open.
 */
interface LegalPdfs {
    /** Generates (and saves) the PDF for [document]; null on failure. Blocking: call it off the main thread. */
    fun generate(document: LegalDocument): PickedFile?

    companion object {
        /** Registered once by `ProHostApplication`; tests set their own. */
        @Volatile
        var current: LegalPdfs? = null

        fun require(): LegalPdfs = current ?: error("LegalPdfs is not initialised (ProHostApplication.onCreate)")
    }
}
