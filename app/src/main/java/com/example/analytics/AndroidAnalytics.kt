package com.example.analytics

import android.content.Context

/** Android start-up for [AnalyticsTracker] (which lives in :shared): the Firebase sink, the saved consent, and Firebase error codes. */
object AndroidAnalytics {
    fun init(context: Context, buildType: String) {
        AnalyticsTracker.init(buildType)
        AnalyticsTracker.platformErrorCode = { e ->
            when (e) {
                is com.google.firebase.firestore.FirebaseFirestoreException -> e.code.name
                is com.google.firebase.functions.FirebaseFunctionsException -> e.code.name
                is com.google.firebase.auth.FirebaseAuthException -> e.errorCode
                else -> null
            }
        }
        if (AnalyticsTracker.sink == null) AnalyticsTracker.sink = FirebaseAnalyticsSink(context)
        AnalyticsConsent.load(context)
    }
}
