package com.example.ui.components

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import com.example.util.PickedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P2.5d: the picker functions use the same system contracts and MIME filters as the screens did, hand results back as PickedFile, and call nothing
 * when the person cancels. A fake ActivityResultRegistry plays the role of the system: it records what is launched and answers with the given result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaPickersTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before
    fun resetFileProvider() = com.example.util.FileProviderTestSupport.reset()

    private class FakeRegistry(private val answer: (Any?) -> Any?) : ActivityResultRegistry() {
        val launched = mutableListOf<Any?>()

        @Suppress("UNCHECKED_CAST")
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launched += input
            dispatchResult(requestCode, answer(input) as O)
        }
    }

    /** Sets [content] with the fake registry in place and returns the launch function the picker produced. */
    private fun launchOf(registry: FakeRegistry, content: @Composable () -> () -> Unit): () -> Unit {
        var launch: (() -> Unit)? = null
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }) { launch = content() }
        }
        compose.waitForIdle()
        return launch!!
    }

    @Test
    fun imagePickerUsesTheImageMimeFilterAndReturnsAPickedFile() {
        val got = mutableListOf<PickedFile>()
        val registry = FakeRegistry { Uri.parse("content://media/photo/7") }
        val launch = launchOf(registry) { rememberImagePicker { got += it } }
        compose.runOnIdle { launch() }
        compose.waitForIdle()
        assertEquals(listOf<Any?>("image/*"), registry.launched)
        assertEquals(listOf(PickedFile("content://media/photo/7")), got)
    }

    @Test
    fun cancellingCallsNothing() {
        val got = mutableListOf<PickedFile>()
        val registry = FakeRegistry { null }
        val launch = launchOf(registry) { rememberImagePicker { got += it } }
        compose.runOnIdle { launch() }
        compose.waitForIdle()
        assertTrue(got.isEmpty())
    }

    @Test
    fun visualMediaPickerAsksForImagesOnly() {
        val got = mutableListOf<PickedFile>()
        val registry = FakeRegistry { Uri.parse("content://media/photo/9") }
        val launch = launchOf(registry) { rememberVisualMediaImagePicker { got += it } }
        compose.runOnIdle { launch() }
        compose.waitForIdle()
        val request = registry.launched.single() as PickVisualMediaRequest
        assertEquals(ActivityResultContracts.PickVisualMedia.ImageOnly, request.mediaType)
        assertEquals(listOf(PickedFile("content://media/photo/9")), got)
    }

    @Test
    fun multiplePickerReturnsAllInOrderAndNothingForAnEmptySelection() {
        val got = mutableListOf<List<PickedFile>>()
        var result: List<Uri> = listOf(Uri.parse("content://m/1"), Uri.parse("content://m/2"))
        val registry = FakeRegistry { result }
        val launch = launchOf(registry) { rememberImagesPicker { got += it } }
        compose.runOnIdle { launch() }
        compose.waitForIdle()
        assertEquals(listOf<Any?>("image/*"), registry.launched)
        assertEquals(listOf(listOf(PickedFile("content://m/1"), PickedFile("content://m/2"))), got)
        result = emptyList()
        compose.runOnIdle { launch() }
        compose.waitForIdle()
        assertEquals(1, got.size) // the empty selection did not call back
    }

    @Test
    fun documentPickerAllowsPdfAndImagesAndFallsBackToTheLastPathSegmentForTheName() {
        val got = mutableListOf<Pair<PickedFile, String>>()
        val registry = FakeRegistry { Uri.parse("content://nobody.home/docs/lease.pdf") }
        val launch = launchOf(registry) { rememberDocumentPicker { file, name -> got += file to name } }
        compose.runOnIdle { launch() }
        compose.waitForIdle()
        assertEquals(listOf<Any?>("application/pdf,image/*"), registry.launched)
        assertEquals(listOf(PickedFile("content://nobody.home/docs/lease.pdf") to "lease.pdf"), got)
    }

    @Test
    fun cameraCaptureAsksTheCameraAppForAFileProviderUriAndReportsOnlySuccess() {
        val got = mutableListOf<PickedFile>()
        var success = true
        val registry = FakeRegistry { success }
        val launch = launchOf(registry) { rememberCameraCapture { got += it } }
        compose.runOnIdle { launch() }
        compose.waitForIdle()
        val target = registry.launched.single() as Uri
        assertEquals("${compose.activity.packageName}.fileprovider", target.authority)
        assertEquals(listOf(PickedFile(target.toString())), got)
        success = false
        compose.runOnIdle { launch() }
        compose.waitForIdle()
        assertEquals(1, got.size) // a cancelled capture reports nothing
    }
}
