package com.example.ui.components

import com.example.R
import com.example.ui.util.UiMessages
import android.net.Uri
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
            UiMessages.show(context, context.getString(R.string.saved))
        } catch (e: Exception) {
            UiMessages.show(context,
                com.example.util.friendlyErrorMessage(e, context.getString(R.string.couldn_t_save_file_please_try_again)),
                long = true)
        }
    }

    return { fileName, content ->
        pendingContent = content
        launcher.launch(fileName)
    }
}
