package com.example.ios

/**
 * Kotlin values made on the Kotlin side for Swift. Swift must never construct `KotlinLong`/`KotlinDouble`/`KotlinBoolean`
 * itself: their base class is not emitted in the static framework and the app fails to link (`_OBJC_CLASS_$_PHSNumber`).
 */
object KotlinValues {
    fun long(v: Long): Any = v
    fun double(v: Double): Any = v
    fun bool(v: Boolean): Any = v
}
