package com.example.util

/** The browser runs shared code on one thread. */
actual class AppLock actual constructor() {
    actual fun lock() {}
    actual fun unlock() {}
}
