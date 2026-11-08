package com.example.util

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P2.4a: PickedFile / FileAccess. AndroidFileAccess.extension must equal the old guessFileExtension(context, uri, fallback) for every input
 * (content type first, then the URI's last segment, then the fallback), and a URI must survive the PickedFile round trip unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PickedFileAccessTest {
    /** A content provider that reports a MIME type by path, like the system pickers' providers do. */
    class TypeProvider : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri): String? = when (uri.lastPathSegment) {
            "doc" -> "application/pdf"
            "pic" -> "image/jpeg"
            "png" -> "image/png"
            else -> null
        }
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var access: AndroidFileAccess

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(TypeProvider::class.java, "files.test")
        access = AndroidFileAccess(context)
    }

    private val handles = listOf(
        "content://files.test/doc", "content://files.test/pic", "content://files.test/png",
        "content://files.test/unknown.docx", "content://files.test/report.final.pdf", "content://files.test/noext",
        "content://files.test/", "content://files.test", "file:///storage/emulated/0/Download/lease.pdf", "https://example.com/photo.jpg", ""
    )

    @Test
    fun extensionEqualsTheOldFunctionForEveryInput() {
        for (handle in handles) for (fallback in listOf("pdf", "bin", "jpg")) {
            assertEquals("$handle / $fallback", guessFileExtension(context, Uri.parse(handle), fallback), access.extension(PickedFile(handle), fallback))
        }
    }

    @Test
    fun nameBasedCasesDoNotDependOnTheContentProvider() {
        // (the MIME-type path is exercised by extensionEqualsTheOldFunctionForEveryInput with the fake provider; its exact answers belong to
        // Android's MimeTypeMap and to how Robolectric wires a provider, not to this code)
        assertEquals("docx", access.extension(PickedFile("content://files.test/unknown.docx"), "bin"))
        assertEquals("pdf", access.extension(PickedFile("content://files.test/report.final.pdf"), "bin"))
        assertEquals("bin", access.extension(PickedFile("content://files.test/noext"), "bin"))
        assertEquals("jpg", access.extension(PickedFile("https://example.com/photo.jpg"), "bin"))
        assertEquals("pdf", access.extension(PickedFile("file:///storage/emulated/0/Download/lease.pdf"), "bin"))
    }

    @Test
    fun aUriSurvivesThePickedFileRoundTrip() {
        for (handle in handles.filter { it.isNotEmpty() }) {
            val uri = Uri.parse(handle)
            assertEquals(handle, uri, uri.toPickedFile().toUri())
            assertEquals(handle, uri.toString(), uri.toPickedFile().handle)
        }
    }
}
