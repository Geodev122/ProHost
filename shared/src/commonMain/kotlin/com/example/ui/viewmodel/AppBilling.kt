package com.example.ui.viewmodel

import com.example.data.model.AppUser
import com.example.data.repository.ProHostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.Volatile

/**
 * The platform's subscription store as [ProHostViewModel] owns it. Android: BillingController (Google Play),
 * reached by the app as `viewModel.billing`. iOS gets its StoreKit 2 implementation with the Premium wave;
 * until then it has none ([NoBilling]).
 */
interface AppBilling {
    fun endConnection()
}

object NoBilling : AppBilling {
    override fun endConnection() {}
}

object BillingDefaults {
    @Volatile
    var create: (scope: CoroutineScope, repository: ProHostRepository, currentUser: StateFlow<AppUser?>, pendingAutoPublishDraftId: StateFlow<String?>) -> AppBilling =
        { _, _, _, _ -> NoBilling }
}
