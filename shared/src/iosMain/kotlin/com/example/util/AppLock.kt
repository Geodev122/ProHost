package com.example.util

import platform.Foundation.NSRecursiveLock

actual class AppLock actual constructor() {
    private val lock = NSRecursiveLock()
    actual fun lock() = lock.lock()
    actual fun unlock() = lock.unlock()
}
