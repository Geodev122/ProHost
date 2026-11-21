package com.example.data.auth

import com.google.firebase.auth.FirebaseUser

/** The shared flow's view of a Firebase Android user; the role claim is read exactly as before. */
fun FirebaseUser.toSignedInUser(): SignedInUser =
    SignedInUser(uid = uid, email = email, phoneNumber = phoneNumber) { force -> FirebaseFunctionsClient.readRoleClaim(this, forceRefresh = force) }
