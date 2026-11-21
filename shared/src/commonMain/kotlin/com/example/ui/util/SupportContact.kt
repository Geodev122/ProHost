package com.example.ui.util

import com.example.data.model.AppUser
import com.example.data.model.publicCode

const val SUPPORT_EMAIL = "admin@pro-host.tech"

/**
 * Opens the person's email app with a support message to [SUPPORT_EMAIL], prefilled with
 * their name, email and U- code (never the Firebase UID). Used by Profile › More and the
 * Pro Host / Admin drawer.
 */
fun launchSupportEmail(user: AppUser?, roleName: String) {
    val subject = com.example.util.uriEncode("ProHost Support — $roleName account")
    val body = com.example.util.uriEncode(
        "Account: ${user?.fullName ?: ""} (${user?.email ?: ""})\n" +
            "Account code: ${user?.publicCode ?: ""}\n\nDescribe your question or issue below:\n"
    )
    try {
        PlatformActions.require().sendMail("mailto:$SUPPORT_EMAIL?subject=$subject&body=$body")
    } catch (e: Exception) {
        if (!PlatformActions.require().isNoHandler(e)) throw e
        UiMessages.show(sharedText("no_email_app_reach_us_x", SUPPORT_EMAIL), long = true)
    }
}
