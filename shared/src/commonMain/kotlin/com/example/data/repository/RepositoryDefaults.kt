package com.example.data.repository

import com.example.data.gateway.FirestoreGateway
import com.example.data.gateway.FunctionsGateway
import com.example.data.gateway.StorageGateway
import kotlin.concurrent.Volatile

/**
 * The live Firebase gateways a repository uses when none is passed in. Each platform installs them once at start
 * (Android: `ProHostApplication` with FirestoreService / FirebaseFunctionsClient / FirebaseStorageService, the same singletons
 * the repository created itself before; iOS: `IosPlatform`). Unit tests pass their own (`hermeticRepository()`).
 */
object RepositoryDefaults {
    @Volatile
    var firestore: () -> FirestoreGateway = { error("RepositoryDefaults.firestore is not installed (ProHostApplication.onCreate)") }

    @Volatile
    var functions: () -> FunctionsGateway = { error("RepositoryDefaults.functions is not installed (ProHostApplication.onCreate)") }

    @Volatile
    var storage: () -> StorageGateway = { error("RepositoryDefaults.storage is not installed (ProHostApplication.onCreate)") }
}
