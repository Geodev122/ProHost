package com.example.analytics

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
