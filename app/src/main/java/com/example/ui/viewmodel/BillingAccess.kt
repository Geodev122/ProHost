package com.example.ui.viewmodel

/** The Google Play billing controller the shared [ProHostViewModel] owns on Android (installed in ProHostApplication). */
val ProHostViewModel.billing: BillingController
    get() = platformBilling as BillingController
