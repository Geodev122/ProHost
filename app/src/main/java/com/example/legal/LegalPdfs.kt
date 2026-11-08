package com.example.legal

import android.content.Context
import com.example.util.PickedFile

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

/** Android [LegalPdfs]: the existing generator, saving under the app's external-files "documents/" directory. */
class AndroidLegalPdfs(context: Context) : LegalPdfs {
    private val app = context.applicationContext

    override fun generate(document: LegalDocument): PickedFile? = LegalPdfGenerator.generate(app, document)?.let { PickedFile(it.absolutePath) }
}
