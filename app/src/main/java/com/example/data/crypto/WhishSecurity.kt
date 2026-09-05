package com.example.data.crypto

import com.example.data.config.MerchantConfig
import java.security.MessageDigest

object WhishSecurity {
    const val CHANNEL_ID = MerchantConfig.WHISH_CHANNEL_ID
    const val SOURCE_EMAIL = MerchantConfig.WHISH_MERCHANT_EMAIL

    /**
     * The client never holds the real merchant signing secret — it moved server-side
     * into Cloud Functions in remediation plan Phase 5 (see
     * functions/src/lib/whishClient.ts's own generateSignature). There is no default
     * secret here on purpose: any caller of the two functions below must supply its
     * own (e.g. a test fixture value), which makes it structurally impossible for
     * this object to produce a signature Whish would actually accept.
     */

    fun generateSignature(
        channel: String = CHANNEL_ID,
        amount: Double,
        currency: String = "USD",
        orderId: String,
        secretKey: String
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
        secretKey: String
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
