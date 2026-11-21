package com.example.data.auth

import com.example.data.gateway.FunctionsGateway
import com.example.data.repository.ProHostRepository
import kotlin.concurrent.Volatile

/** The platform pieces [com.example.ui.viewmodel.AuthViewModel] uses unless a test passes its own (installed at app start). */
object AuthDefaults {
    @Volatile
    var gateway: () -> AuthGateway = { error("AuthDefaults.gateway is not installed (ProHostApplication.onCreate)") }

    @Volatile
    var sessionFinisher: (ProHostRepository, FunctionsGateway) -> SessionFinisher =
        { _, _ -> error("AuthDefaults.sessionFinisher is not installed (ProHostApplication.onCreate)") }

    /** The [AuthError] of a platform SDK exception (Android: Firebase exception types). */
    @Volatile
    var classify: (Throwable) -> AuthError = { AuthError.UNKNOWN }
}
