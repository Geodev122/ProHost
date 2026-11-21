package com.example.util

// The admin web console has no Google sign-in (email + password only), so nothing there asks for nonces.
actual fun secureRandomBytes(size: Int): ByteArray =
    throw UnsupportedOperationException("secure random bytes are not used in the admin web console")
