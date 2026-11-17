package com.example.util

import kotlinx.coroutines.CoroutineDispatcher

/** The dispatcher for repository I/O: `Dispatchers.IO` on Android and iOS (as the repositories always used), `Default` on the web. */
expect val ioDispatcher: CoroutineDispatcher
