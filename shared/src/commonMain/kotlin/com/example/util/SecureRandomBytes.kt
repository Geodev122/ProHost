package com.example.util

/** [size] bytes from the platform's cryptographically secure generator (Android SecureRandom, iOS SecRandomCopyBytes). */
expect fun secureRandomBytes(size: Int): ByteArray
