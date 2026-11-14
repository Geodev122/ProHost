package com.example.util

/**
 * Wall-clock time in epoch milliseconds. Android keeps calling `System.currentTimeMillis()` exactly as the models always did (so tests
 * that control the clock, e.g. Robolectric, still do); the other targets use the Kotlin clock.
 */
expect fun nowMillis(): Long
