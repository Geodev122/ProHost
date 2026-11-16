package com.example.ios

import com.example.data.auth.AuthBridge
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

    fun installFunctions(transport: CallableTransport) {
        functions = CallableFunctionsGateway(transport)
    }

    var firestore: FirestoreGateway? = null
        private set

    fun installAuth(bridge: AuthBridge) {
        auth = BridgedAuthGateway(bridge)
    }

    var storage: StorageGateway? = null
        private set

    fun installFirestore(bridge: FirestoreBridge) {
        firestore = BridgedFirestoreGateway(bridge)
    }

    fun installStorage(native: IosStorageNative) {
        storage = BridgedStorageGateway(IosStorageBridge(native))
    }
}
