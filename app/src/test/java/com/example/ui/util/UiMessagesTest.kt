package com.example.ui.util

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** P2.2a: the context-free UiMessages.show keeps the old contract (Snackbar when a host is on screen, Toast fallback otherwise, blanks ignored). */
class UiMessagesTest {
    private val toasts = mutableListOf<Pair<String, Boolean>>()

    @Before
    fun setUp() {
        UiMessages.hosts = 0
        UiMessages.setToastFallback { text, long -> toasts += text.toString() to long }
    }

    @After
    fun tearDown() {
        UiMessages.hosts = 0
        UiMessages.setToastFallback(null)
    }

    @Test
    fun blankAndNullMessagesAreIgnored() {
        UiMessages.show(null)
        UiMessages.show("")
        UiMessages.show("   ")
        assertTrue(toasts.isEmpty())
    }

    @Test
    fun withoutASnackbarHostTheToastFallbackIsUsedWithTheLengthFlag() {
        UiMessages.show("Saved")
        UiMessages.show("Long one", long = true)
        assertEquals(listOf("Saved" to false, "Long one" to true), toasts)
    }

    @Test
    fun withASnackbarHostTheMessageGoesToTheFlowWithItsActionAndNoToast() {
        UiMessages.hosts = 1
        var undone = false
        val received = runBlocking {
            val pending = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { UiMessages.messages.first() } }
            UiMessages.show("Removed", long = true, actionLabel = "Undo") { undone = true }
            pending.await()
        }
        assertEquals("Removed", received.text)
        assertTrue(received.long)
        assertEquals("Undo", received.actionLabel)
        assertNotNull(received.onAction)
        received.onAction?.invoke()
        assertTrue(undone)
        assertTrue("no Toast when a Snackbar host took the message", toasts.isEmpty())
    }

    @Test
    fun noFallbackRegisteredDropsTheMessageInsteadOfCrashing() {
        UiMessages.setToastFallback(null)
        UiMessages.show("Nobody is listening")
        assertTrue(toasts.isEmpty())
    }
}
