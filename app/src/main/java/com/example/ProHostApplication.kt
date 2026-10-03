package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics

class ProHostApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("ProHostApplication", "FirebaseApp initialized successfully in Application.onCreate()")
            }
            initCrashlytics()
            initAppCheck()
            // Opt-in: AnalyticsConsent keeps collection off until the person accepts.
            com.example.analytics.AnalyticsTracker.init(this, if (BuildConfig.DEBUG) "debug" else "release")
        } catch (e: Exception) {
            Log.e("ProHostApplication", "Failed to initialize FirebaseApp or App Check: ${e.message}", e)
        }
        com.example.service.ProHostMessagingService.ensureNotificationChannel(this)
    }

    // Collection stays on in debug builds too: CI distributes debug APKs to testers
    // through Firebase App Distribution, and those crashes are the ones we need.
    private fun initCrashlytics() {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.isCrashlyticsCollectionEnabled = true
        crashlytics.setCustomKey("build_type", if (BuildConfig.DEBUG) "debug" else "release")
        crashlytics.setCustomKey("version_code", BuildConfig.VERSION_CODE)
        // No setUserId: public/privacy.html promises crash logs are anonymised.
    }

    private fun initAppCheck() {
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        val providerName = if (BuildConfig.DEBUG) {
            try {
                // Loaded reflectively: firebase-appcheck-debug is debugImplementation only.
                // Without a shared token each install generates its own secret (printed to
                // logcat by DebugAppCheckProvider) that must be registered in Firebase
                // console > App Check > Manage debug tokens, or enforced APIs reject it.
                seedSharedDebugToken()
                val debugFactoryClass = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                val debugFactory = debugFactoryClass.getMethod("getInstance").invoke(null)
                    as com.google.firebase.appcheck.AppCheckProviderFactory
                firebaseAppCheck.installAppCheckProviderFactory(debugFactory)
                "debug"
            } catch (e: Exception) {
                firebaseAppCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
                "play_integrity"
            }
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            "play_integrity"
        }
        firebaseAppCheck.setTokenAutoRefreshEnabled(true)
        FirebaseCrashlytics.getInstance().setCustomKey("app_check_provider", providerName)
        Log.d("ProHostApplication", "FirebaseAppCheck initialized with $providerName provider")
    }

    /**
     * Stores BuildConfig.APP_CHECK_DEBUG_TOKEN where DebugAppCheckProvider reads its secret
     * (firebase-appcheck-debug StorageHelper), so every tester build shares the one token
     * registered in the console. No-op when the token is blank (local builds).
     */
    private fun seedSharedDebugToken() {
        val token = BuildConfig.APP_CHECK_DEBUG_TOKEN
        if (token.isBlank()) return
        val prefsName = "com.google.firebase.appcheck.debug.store.${FirebaseApp.getInstance().persistenceKey}"
        getSharedPreferences(prefsName, MODE_PRIVATE).edit()
            .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", token)
            .commit()
    }
}
