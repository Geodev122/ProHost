package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android side of [PickedFile]: the handle is the URI string. */
fun Uri.toPickedFile(): PickedFile = PickedFile(toString())

fun PickedFile.toUri(): Uri = Uri.parse(handle)

/** Android [FileAccess]: asks the content resolver, exactly what `guessFileExtension` always did (it is the single implementation). */
class AndroidFileAccess(context: Context) : FileAccess {
    private val app = context.applicationContext

    override fun extension(file: PickedFile, fallback: String): String = guessFileExtension(app, file.toUri(), fallback)

    override suspend fun readBytes(file: PickedFile): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { app.contentResolver.openInputStream(file.toUri())?.use { it.readBytes() } }.getOrNull()
    }

    override suspend fun sizeBytes(file: PickedFile): Long? = withContext(Dispatchers.IO) {
        runCatching {
            app.contentResolver.query(file.toUri(), arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.getOrNull()
    }
}
