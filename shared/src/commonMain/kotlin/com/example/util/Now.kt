package com.example.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Wall-clock time in epoch milliseconds: what `System.currentTimeMillis()` returns, available on every target. */
@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
