package com.example.data.config

/**
 * Single source of truth for the Whish Money merchant's non-secret identifiers
 * (channel ID, merchant contact email). These are safe to ship in the client.
 *
 * The signing secret is NOT here and must never be added here — see
 * [com.example.data.crypto.WhishSecurity] for why it still (temporarily)
 * lives client-side pending the server-side payment signing migration.
 */
object MerchantConfig {
    const val WHISH_CHANNEL_ID: String = "15462415"
    const val WHISH_MERCHANT_EMAIL: String = "ceo@hopebearer-award.com"
}
