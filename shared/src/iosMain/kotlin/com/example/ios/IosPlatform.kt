package com.example.ios

import com.example.data.gateway.CallableFunctionsGateway
import com.example.data.gateway.CallableTransport
import com.example.data.gateway.FunctionsGateway

/**
 * The iOS app's platform services, installed once by the Swift app delegate (`ProHostAppDelegate.swift`) after
 * `FirebaseApp.configure()`. Shared screens and view models reach Firebase only through these gateways.
 */
object IosPlatform {
    var functions: FunctionsGateway? = null
        private set

    fun installFunctions(transport: CallableTransport) {
        functions = CallableFunctionsGateway(transport)
    }
}
