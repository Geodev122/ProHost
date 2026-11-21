package com.example.util

/** A re-entrant lock for shared code (Android: `ReentrantLock`, the same semantics as the `synchronized` blocks it replaces; iOS: `NSRecursiveLock`). */
expect class AppLock() {
    fun lock()
    fun unlock()
}

/** Runs [block] holding the lock; inline so `return` inside the block leaves the caller, exactly like Kotlin's `synchronized`. */
inline fun <T> AppLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
