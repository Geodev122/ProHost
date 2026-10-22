package com.example.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree

/**
 * Lets the phone's autofill service (Google Password Manager and others) fill and save a field:
 * email on sign-in, the current password on sign-in, a new password on sign-up.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.autofillHints(types: List<AutofillType>, onFill: (String) -> Unit): Modifier = composed {
    val currentOnFill by rememberUpdatedState(onFill)
    val node = remember(types) { AutofillNode(autofillTypes = types, onFill = { currentOnFill(it) }) }
    val autofill = LocalAutofill.current
    val tree = LocalAutofillTree.current
    DisposableEffect(node) {
        tree += node
        onDispose { tree.children.remove(node.id) }
    }
    this
        .onGloballyPositioned { node.boundingBox = it.boundsInWindow() }
        .onFocusChanged { focus ->
            autofill?.run { if (focus.hasFocus) requestAutofillForNode(node) else cancelAutofillForNode(node) }
        }
}
