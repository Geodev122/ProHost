package com.example.util

private val random = java.security.SecureRandom()

actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }
