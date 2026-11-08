package com.example.util

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun knownCases() {
        assertEquals("pdf", access.extension(PickedFile("content://files.test/doc"), "bin"))
        // Android's own MimeTypeMap decides between "jpg" and "jpeg"; exactness is covered by extensionEqualsTheOldFunctionForEveryInput.
        assertTrue(access.extension(PickedFile("content://files.test/pic"), "bin") in setOf("jpg", "jpeg"))
        assertEquals("png", access.extension(PickedFile("content://files.test/png"), "bin"))
        assertEquals("docx", access.extension(PickedFile("content://files.test/unknown.docx"), "bin"))
        assertEquals("bin", access.extension(PickedFile("content://files.test/noext"), "bin"))
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
