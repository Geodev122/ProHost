package com.example.ios

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.data.auth.AuthBridge
import com.example.data.auth.AuthDefaults
import com.example.data.auth.GatewaySessionFinisher
import com.example.data.auth.SessionAuth
import com.example.data.auth.refreshIdTokenOf
import com.example.data.auth.signedInUserOf
import com.example.data.repository.RepositoryDefaults
import com.example.data.auth.AuthGateway
import com.example.data.auth.BridgedAuthGateway
import com.example.data.gateway.CallableFunctionsGateway
import com.example.data.gateway.BridgedFirestoreGateway
import com.example.data.gateway.BridgedStorageGateway
import com.example.data.gateway.StorageGateway
import com.example.data.gateway.CallableTransport
import com.example.data.gateway.FirestoreBridge
import com.example.data.gateway.FirestoreGateway
import com.example.data.gateway.FunctionsGateway

/**
 * The iOS app's platform services, installed once by the Swift app delegate (`ProHostAppDelegate.swift`) after
 * `FirebaseApp.configure()`. Shared screens and view models reach Firebase only through these gateways.
 */
object IosPlatform {
    var functions: FunctionsGateway? = null
        private set

    var auth: AuthGateway? = null
        private set

    /** StoreKit 2 for the Premium page: every ProHostViewModel gets an [IosAppBilling] over [bridge]. */
    fun installStoreKit(bridge: StoreKitBridge) {
        com.example.ui.viewmodel.BillingDefaults.create = { scope, repository, currentUser, _ ->
            IosAppBilling(bridge, scope, repository, currentUser)
        }
    }

    fun installFunctions(transport: CallableTransport) {
        val gateway = CallableFunctionsGateway(transport)
        functions = gateway
        RepositoryDefaults.functions = { gateway }
    }

    var firestore: FirestoreGateway? = null
        private set

    fun installAuth(bridge: AuthBridge) {
        val gateway = BridgedAuthGateway(bridge)
        auth = gateway
        AuthDefaults.gateway = { gateway }
        AuthDefaults.sessionFinisher = { repository, functions -> GatewaySessionFinisher(repository, functions) }
        SessionAuth.currentUser = { signedInUserOf(bridge) }
        SessionAuth.refreshIdToken = { refreshIdTokenOf(bridge) }
        SessionAuth.signOut = { bridge.signOut() }
    }

    var storage: StorageGateway? = null
        private set

    fun installFirestore(bridge: FirestoreBridge) {
        val gateway = BridgedFirestoreGateway(bridge)
        firestore = gateway
        RepositoryDefaults.firestore = { gateway }
    }

    fun installStorage(native: IosStorageNative) {
        val gateway = BridgedStorageGateway(IosStorageBridge(native))
        storage = gateway
        RepositoryDefaults.storage = { gateway }
    }

    var calendar: CalendarBridge? = null
        private set

    fun installCalendar(bridge: CalendarBridge) {
        calendar = bridge
        com.example.ui.util.PaymentReminders.add = { booking, ref -> IosPaymentReminders.add(booking, ref) }
    }

    var location: LocationBridge? = null
        private set

    fun installLocation(bridge: LocationBridge) {
        location = bridge
        com.example.util.LocationProvider.current = IosLocationProvider(bridge)
    }

    var mediaPicker: MediaPickerBridge? = null
        private set

    fun installMediaPicker(bridge: MediaPickerBridge) {
        mediaPicker = bridge
    }

    var maps: NativeMapFactory? = null
        private set

    fun installMaps(factory: NativeMapFactory) {
        maps = factory
    }

    /**
     * Firebase Analytics on iOS (Swift `FirebaseAnalyticsSink`): opt-in like Android. Loads the stored choice (the shared
     * consent dialog asks on first launch); once the person has chosen, [onConsentDecided] runs once, which is where
     * the notification prompt is asked, so the two prompts never stack (as Android's MainActivity).
     */
    fun installAnalytics(sink: com.example.analytics.AnalyticsSink, onConsentDecided: () -> Unit) {
        com.example.util.KeyValueStores.open = { name -> IosKeyValueStore(name) }
        com.example.analytics.AnalyticsTracker.sink = sink
        com.example.analytics.AnalyticsConsent.load()
        kotlinx.coroutines.MainScope().launch {
            com.example.analytics.AnalyticsConsent.state.first { it != com.example.analytics.ConsentState.UNKNOWN }
            onConsentDecided()
        }
    }

    var appleSignIn: AppleSignInBridge? = null
        private set

    fun installAppleSignIn(bridge: AppleSignInBridge) {
        appleSignIn = bridge
        // Apple accounts revoke their Sign in with Apple tokens before the account is deleted; cancelling the Apple sheet
        // stops the deletion, an Apple error does not (the account is still deleted, and the error is logged).
        com.example.data.auth.AccountDeletion.prepare = {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                bridge.revokeAppleTokenIfNeeded(RevokeCallback { outcome ->
                    if (outcome == RevokeCallback.FAILED) com.example.util.AppLog.w("IosPlatform", "Apple token revocation failed")
                    if (cont.isActive) cont.resumeWith(Result.success(outcome != RevokeCallback.CANCELLED))
                })
            }
        }
    }

    var googleSignIn: GoogleSignInBridge? = null
        private set

    fun installGoogleSignIn(bridge: GoogleSignInBridge) {
        googleSignIn = bridge
    }

    private var seamsInstalled = false

    /** Runs [install] once (the Compose root can be recreated). */
    internal fun installSeamsOnce(install: () -> Unit) {
        if (seamsInstalled) return
        seamsInstalled = true
        install()
    }
}
