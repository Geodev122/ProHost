package com.example.legal

import android.content.Context
import com.example.util.PickedFile

/** Android [LegalPdfs]: the existing generator, saving under the app's external-files "documents/" directory. */
class AndroidLegalPdfs(context: Context) : LegalPdfs {
    private val app = context.applicationContext

    override fun generate(document: LegalDocument): PickedFile? = LegalPdfGenerator.generate(app, document)?.let { PickedFile(it.absolutePath) }
}
