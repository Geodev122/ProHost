package com.example.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/** Where sanitised events go. Plain maps keep [AnalyticsTracker] testable on the JVM. */
interface AnalyticsSink {
    fun logEvent(name: String, params: Map<String, Any>)
    fun setUserId(id: String?)
    fun setUserProperty(name: String, value: String?)
    fun setCollectionEnabled(enabled: Boolean)
    fun setAnalyticsConsent(granted: Boolean)
    fun resetData()
    fun appInstanceId(onResult: (String?) -> Unit)
}

/** The only place in the app that touches the Firebase Analytics SDK. */
internal class FirebaseAnalyticsSink(context: Context) : AnalyticsSink {
    private val fa = FirebaseAnalytics.getInstance(context.applicationContext)

    override fun logEvent(name: String, params: Map<String, Any>) {
        fa.logEvent(name, params.toBundle())
    }

    override fun setUserId(id: String?) = fa.setUserId(id)

    override fun setUserProperty(name: String, value: String?) = fa.setUserProperty(name, value)

    override fun setCollectionEnabled(enabled: Boolean) = fa.setAnalyticsCollectionEnabled(enabled)

    override fun setAnalyticsConsent(granted: Boolean) {
        val analytics = if (granted) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
        fa.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to analytics,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED
            )
        )
    }

    override fun resetData() = fa.resetAnalyticsData()

    override fun appInstanceId(onResult: (String?) -> Unit) {
        fa.appInstanceId
            .addOnSuccessListener { onResult(it) }
            .addOnFailureListener { onResult(null) }
    }

    private fun Map<String, Any>.toBundle(): Bundle = Bundle().also { b ->
        forEach { (k, v) ->
            when (v) {
                is String -> b.putString(k, v)
                is Long -> b.putLong(k, v)
                is Int -> b.putLong(k, v.toLong())
                is Double -> b.putDouble(k, v)
                is Float -> b.putDouble(k, v.toDouble())
                is List<*> -> b.putParcelableArray(
                    k,
                    v.filterIsInstance<Map<*, *>>().map { item ->
                        @Suppress("UNCHECKED_CAST")
                        (item as Map<String, Any>).toBundle()
                    }.toTypedArray()
                )
            }
        }
    }
}
