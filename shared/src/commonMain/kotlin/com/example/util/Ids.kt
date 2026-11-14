package com.example.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Random identifiers without `java.util.UUID`, so the data models and repositories can move to shared (commonMain) code. [uuid] returns what
 * `UUID.randomUUID().toString()` always returned: a random version-4 UUID in canonical lowercase form (`xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx`),
 * from a cryptographically secure source. The single opt-in site for `kotlin.uuid` (still experimental in Kotlin 2.2).
 */
@OptIn(ExperimentalUuidApi::class)
object Ids {
    fun uuid(): String = Uuid.random().toString()
}
