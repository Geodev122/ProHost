package com.example.util

import android.content.Context
import android.net.Uri

/** Android side of [PickedFile]: the handle is the URI string. */
fun Uri.toPickedFile(): PickedFile = PickedFile(toString())

fun PickedFile.toUri(): Uri = Uri.parse(handle)

/** Android [FileAccess]: asks the content resolver, exactly what `guessFileExtension` always did (it is the single implementation). */
class AndroidFileAccess(context: Context) : FileAccess {
    private val app = context.applicationContext

    override fun extension(file: PickedFile, fallback: String): String = guessFileExtension(app, file.toUri(), fallback)
}
