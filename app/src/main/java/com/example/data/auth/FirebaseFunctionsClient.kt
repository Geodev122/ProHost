package com.example.data.auth

import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around the server-verified role Cloud Functions
 * (functions/src/roles/*.ts). This — and reading the signed-in user's own ID
 * token claims — is the ONLY way this app ever learns or changes a user's
 * role. Nothing here accepts a role as a trusted client-side value.
 */
class FirebaseFunctionsClient {

    private val tag = "FirebaseFunctionsClient"

    // Must match the region functions are deployed to (see firebase.json /
    // functions/src/index.ts setGlobalOptions).
    private val functions: FirebaseFunctions by lazy {
        FirebaseFunctions.getInstance("europe-west1")
    }

    /**
     * Ensures the signed-in user has a role claim, assigning the default
     * (PROFESSIONAL) server-side on first call. Safe/idempotent to call on
     * every sign-in.
     */
    suspend fun ensureInitialRole(): Result<String> {
        return try {
            val result = functions.getHttpsCallable("assignInitialRole").call().await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val role = data?.get("role") as? String
                ?: return Result.failure(IllegalStateException("assignInitialRole returned no role."))
            Result.success(role)
        } catch (e: Exception) {
            Log.e(tag, "ensureInitialRole failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** The only self-service role change available — SPACE_OWNER only, never ADMIN. */
    suspend fun requestSpaceOwnerUpgrade(): Result<String> {
        return try {
            val result = functions.getHttpsCallable("requestRoleUpgrade")
                .call(mapOf("targetRole" to "SPACE_OWNER"))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val role = data?.get("role") as? String
                ?: return Result.failure(IllegalStateException("requestRoleUpgrade returned no role."))
            Result.success(role)
        } catch (e: FirebaseFunctionsException) {
            Log.e(tag, "requestSpaceOwnerUpgrade denied: ${e.code} ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "requestSpaceOwnerUpgrade failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Only succeeds when the CALLER already has the Admin role server-side. */
    suspend fun grantAdminRole(targetEmail: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("grantAdminRole")
                .call(mapOf("targetEmail" to targetEmail))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "grantAdminRole failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Reads the role custom claim from the given user's current ID token,
         * force-refreshing so a just-granted claim is picked up immediately
         * rather than waiting for the token's normal ~1 hour cache window.
         */
        suspend fun readRoleClaim(user: FirebaseUser, forceRefresh: Boolean = true): String? {
            val tokenResult = user.getIdToken(forceRefresh).await()
            return tokenResult.claims["role"] as? String
        }
    }
}
