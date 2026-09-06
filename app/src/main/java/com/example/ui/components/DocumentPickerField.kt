package com.example.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.OxfordBlue
import com.example.ui.theme.OxfordBlueContainer

/**
 * A required-document picker: tap to pick a file (PDF or image) from the device, shows
 * the picked file's name once selected with a Remove action. Does NOT upload anything
 * itself — the caller uploads [DocumentPickerState.uri] wherever/whenever it has the
 * id (uid or spaceId) that upload needs to be keyed on, then discards this state. This
 * intentionally has no document-type dropdown, license-number field, or expiry date —
 * unlike the old CredentialUploadDialog it replaces, nothing here is reviewed by anyone,
 * so there's nothing to classify beyond "this is the document for this required slot."
 */
data class DocumentPickerState(
    val uri: Uri? = null,
    val fileName: String? = null
) {
    val isSelected: Boolean get() = uri != null
}

@Composable
fun DocumentPickerField(
    label: String,
    helperText: String,
    state: DocumentPickerState,
    onStateChanged: (DocumentPickerState) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = true
) {
    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            var resolvedName: String? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) resolvedName = cursor.getString(nameIndex)
            }
            onStateChanged(DocumentPickerState(uri, resolvedName ?: (uri.lastPathSegment ?: "document")))
        }
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            if (required) {
                Spacer(modifier = Modifier.width(4.dp))
                Text("*", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.5.dp,
                    color = if (state.isSelected) OxfordBlue else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .background(if (state.isSelected) OxfordBlueContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable { filePickerLauncher.launch("application/pdf,image/*") },
            shape = RoundedCornerShape(12.dp)
        ) {
            if (!state.isSelected) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text("Tap to Upload Document / PDF / Image", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(helperText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = OxfordBlue, modifier = Modifier.size(28.dp))
                    Text(
                        text = state.fileName ?: "Document selected",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onStateChanged(DocumentPickerState()) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove file", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * Circular profile-picture picker used on the registration form — tap to choose an
 * image, shows a live thumbnail once picked. Like [DocumentPickerField], upload itself
 * happens later (once a uid exists to key the Storage path on), not here.
 */
@Composable
fun ProfilePicturePickerField(
    pictureUri: Uri?,
    onPictureSelected: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> if (uri != null) onPictureSelected(uri) }

    Box(
        modifier = modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(OxfordBlueContainer.copy(alpha = 0.4f))
            .border(1.5.dp, OxfordBlue, CircleShape)
            .clickable { pickerLauncher.launch("image/*") },
        contentAlignment = Alignment.Center
    ) {
        if (pictureUri != null) {
            AsyncImage(
                model = pictureUri,
                contentDescription = "Profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AddAPhoto, contentDescription = "Add profile picture", tint = OxfordBlue, modifier = Modifier.size(26.dp))
            }
        }
    }
}
