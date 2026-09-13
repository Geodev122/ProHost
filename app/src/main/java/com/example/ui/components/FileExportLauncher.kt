package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Every "export" button in the Admin Console (Transactions CSV, Security/Audit CSV, the
 * "One-Click System Exports" JSON/TXT dumps) previously only ever copied the generated
 * string to the clipboard or handed it to Android's share sheet — none of them wrote a
 * real file, despite the CSV/JSON/TXT generation itself already being correct. This wraps
 * Android's Storage Access Framework "Save As" picker (`ACTION_CREATE_DOCUMENT`) so the
 * admin actually chooses a real save location and a real file is written there — no new
 * backend needed, since every export's content is already correct client-side data.
 *
 * Returns a function to call from a button's onClick: `exportFile(suggestedFileName, content)`.
 * The [mimeType] is fixed per launcher instance (SAF requires it up front); [content] is
 * supplied per-call since it depends on filters (date range, etc.) chosen at click time.
 */
@Composable
fun rememberFileExportLauncher(mimeType: String): (fileName: String, content: String) -> Unit {
    val context = LocalContext.current
    var pendingContent by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mimeType)
    ) { uri: Uri? ->
        val content = pendingContent
        pendingContent = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(context, "Saved.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                com.example.util.friendlyErrorMessage(e, "Couldn't save file. Please try again."),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    return { fileName, content ->
        pendingContent = content
        launcher.launch(fileName)
    }
}
