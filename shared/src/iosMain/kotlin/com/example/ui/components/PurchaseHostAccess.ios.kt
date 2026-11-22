package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.ui.viewmodel.PurchaseHost

/** StoreKit presents its own sheets over the active window scene, so iOS needs no per-screen handle: one shared token. */
private val IosPurchaseHost = PurchaseHost("ios")

@Composable
actual fun rememberPurchaseHost(): PurchaseHost? = IosPurchaseHost

@Composable
actual fun rememberApplicationHost(): PurchaseHost? = IosPurchaseHost
