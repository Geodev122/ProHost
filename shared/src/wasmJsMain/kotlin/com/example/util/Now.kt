package com.example.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
actual fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
