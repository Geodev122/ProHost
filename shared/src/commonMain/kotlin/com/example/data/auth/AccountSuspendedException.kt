package com.example.data.auth

/**
 * Thrown when assignInitialRole.ts rejects a sign-in because the account is
 * suspended (see setAccountSuspended.ts). Callers must sign the (already
 * Firebase-Auth-authenticated) user back out and surface [message] instead of
 * completing sign-in — see ProHostViewModel's phone/Google sign-in flows.
 */
class AccountSuspendedException(message: String) : Exception(message)
