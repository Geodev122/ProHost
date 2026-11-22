package com.example.data.auth

/**
 * Platform step run before the account is deleted. iOS installs the Sign in with Apple token revocation (App Review
 * 5.1.1(v)); Android has none. Returns false when the person cancelled it, which stops the deletion.
 */
object AccountDeletion {
    var prepare: (suspend () -> Boolean)? = null
}

/** The person cancelled the platform step ([AccountDeletion.prepare]); nothing was deleted and no error is shown. */
class AccountDeletionCancelled : Exception("Account deletion cancelled")
