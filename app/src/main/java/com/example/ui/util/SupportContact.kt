package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.AppUser
import com.example.data.model.publicCode

const val SUPPORT_EMAIL = "admin@pro-host.tech"

/**
 * Opens the person's email app with a support message to [SUPPORT_EMAIL], prefilled with
 * their name, email and U- code (never the Firebase UID). Used by Profile › More and the
 * Pro Host / Admin drawer.
 */
fun launchSupportEmail(context: Context, user: AppUser?, roleName: String) {
    val subject = Uri.encode("ProHost Support — $roleName account")
    val body = Uri.encode(
        "Account: ${user?.fullName ?: ""} (${user?.email ?: ""})\n" +
            "Account code: ${user?.publicCode ?: ""}\n\nDescribe your question or issue below:\n"
    )
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SUPPORT_EMAIL?subject=$subject&body=$body")
    }
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No email app found — you can also reach us at $SUPPORT_EMAIL", Toast.LENGTH_LONG).show()
    }
}
