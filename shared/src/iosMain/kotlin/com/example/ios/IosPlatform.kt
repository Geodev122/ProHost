package com.example.ios

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
