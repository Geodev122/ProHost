package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.ui.viewmodel.PurchaseHost

/** The screen's [PurchaseHost] for the store's purchase UI (Android: the current Activity), or null when there is none. */
@Composable
expect fun rememberPurchaseHost(): PurchaseHost?

/** The application-wide handle (Android: the application `Context`) for work that must not hold the Activity. */
@Composable
expect fun rememberApplicationHost(): PurchaseHost?
