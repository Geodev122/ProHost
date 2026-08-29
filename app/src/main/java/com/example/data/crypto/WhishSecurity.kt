package com.example.data.crypto

import java.security.MessageDigest

object WhishSecurity {
    const val CHANNEL_ID = "15462415"
    const val SOURCE_EMAIL = "ceo@hopebearer-award.com"
    const val DEFAULT_SECRET_KEY = "23cfc205ed0d4aba83df6b12b0bbd4a1"

    fun generateSignature(
        channel: String = CHANNEL_ID,
        amount: Double,
        currency: String = "USD",
        orderId: String,
        secretKey: String = DEFAULT_SECRET_KEY
    ): String {
        val formattedAmount = String.format(java.util.Locale.US, "%.2f", amount)
        val raw = "$channel|$formattedAmount|$currency|$orderId|$secretKey"
        return sha256(raw)
    }

    fun generateMd5Signature(
        channel: String = CHANNEL_ID,
        amount: Double,
        currency: String = "USD",
        orderId: String,
        secretKey: String = DEFAULT_SECRET_KEY
    ): String {
        val formattedAmount = String.format(java.util.Locale.US, "%.2f", amount)
        val raw = "$channel|$formattedAmount|$currency|$orderId|$secretKey"
        return md5(raw)
    }

    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
