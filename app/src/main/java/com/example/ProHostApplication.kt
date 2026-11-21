package com.example

import com.example.data.auth.toUserMessage
import coil3.imageDecoderEnabled
import com.example.data.auth.from
import com.example.data.auth.toSignedInUser
import android.app.Application
import android.util.Log
import kotlinx.coroutines.tasks.await
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics

class ProHostApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val english = createConfigurationContext(
            android.content.res.Configuration(resources.configuration).apply { setLocale(java.util.Locale.ENGLISH) }
        ).resources
        com.example.ui.util.UiText.init(resources, english)
        com.example.ui.util.AppLanguage.current = { com.example.ui.util.UiText.resources?.configuration?.locales?.get(0)?.language ?: "" }
        com.example.ui.util.SharedText.lookup = { name, args ->
            val res = com.example.ui.util.UiText.resources
            val id = res?.getIdentifier(name, "string", packageName) ?: 0
            if (res == null || id == 0) "" else if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
        }
        com.example.ui.util.SharedText.plural = { name, count, args ->
            val res = com.example.ui.util.UiText.resources
            val id = res?.getIdentifier(name, "plurals", packageName) ?: 0
            if (res == null || id == 0) "" else if (args.isEmpty()) res.getQuantityString(id, count) else res.getQuantityString(id, count, *args)
        }
        com.example.ui.util.SharedText.english = { name ->
            val res = com.example.ui.util.UiText.resources
            val id = res?.getIdentifier(name, "string", packageName) ?: 0
            if (id == 0) "" else com.example.ui.util.UiText.english(id)
        }
        com.example.ui.util.LocaleNames.country = { iso ->
            val locale = com.example.ui.util.UiText.resources?.configuration?.locales?.get(0) ?: java.util.Locale.getDefault()
            java.util.Locale("", iso).getDisplayCountry(locale)
        }
        com.example.ui.util.AndroidUiMessages.init(this)
        com.example.data.auth.AndroidCallableFailures.install()
        com.example.util.AndroidAppLog.install()
        com.example.data.repository.RepositoryDefaults.firestore = { com.example.data.firestore.FirestoreService.getInstance() }
        com.example.data.repository.RepositoryDefaults.functions = { com.example.data.auth.FirebaseFunctionsClient() }
        com.example.data.repository.RepositoryDefaults.storage = { com.example.data.storage.FirebaseStorageService.getInstance() }
        com.example.ui.viewmodel.BillingDefaults.create = { scope, repo, user, draft -> com.example.ui.viewmodel.BillingController(scope, repo, user, draft) }
        com.example.data.auth.SessionAuth.currentUser = { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.toSignedInUser() }
        com.example.data.auth.SessionAuth.refreshIdToken = { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await() }
        com.example.data.auth.SessionAuth.signOut = { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() }
        com.example.data.auth.SessionAuth.clearCredentials = { com.example.data.auth.GoogleCredentialState.clearInBackground() }
        // Coil 3 decodes with ImageDecoder on API 29+; Coil 2 (what every photo looked like) used BitmapFactory. Keep BitmapFactory.
        coil3.SingletonImageLoader.setSafe { ctx -> coil3.ImageLoader.Builder(ctx).imageDecoderEnabled(false).build() }
        com.example.ui.util.AndroidDateLabels.install()
        com.example.ui.util.AndroidLocaleDates.install()
        com.example.util.CountryLookup.atPoint = { lat, lng -> com.example.util.PhoneCountryDetector.detectCountryAtCoordinates(this, lat, lng) }
        com.example.data.auth.AuthDefaults.gateway = { com.example.data.auth.FirebaseAuthService(this) }
        com.example.data.auth.AuthDefaults.sessionFinisher = { repo, functions -> com.example.data.auth.FirebaseSessionFinisher(this, repo, functions) }
        com.example.data.auth.AuthDefaults.classify = { com.example.data.auth.AuthError.from(it) }
        com.example.util.PushTokens.fetch = {
            @Suppress("DEPRECATION")
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
        }
        com.example.ui.util.AndroidPlatformActions.init(this)
        com.example.util.FileAccess.current = com.example.util.AndroidFileAccess(this)
        com.example.util.LocationProvider.current = com.example.util.AndroidLocationProvider(this)
        com.example.util.Geocoding.current = com.example.util.AndroidGeocoding(this)
        com.example.util.SoundPlayer.current = com.example.util.AndroidSoundPlayer(this)
        com.example.util.FriendlyErrors.callableMessage = { e, fallback ->
            if (e is com.google.firebase.functions.FirebaseFunctionsException) e.toUserMessage(fallback) else null
        }
        com.example.ui.util.PaymentReminders.add = { booking, ref -> com.example.ui.util.PaymentCalendar.addPaymentReminders(booking, ref) }
        com.example.legal.LegalPdfs.current = com.example.legal.AndroidLegalPdfs(this)
        com.example.data.auth.GoogleCredentialState.init(this)
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("ProHostApplication", "FirebaseApp initialized successfully in Application.onCreate()")
            }
            initCrashlytics()
            initAppCheck()
            // Opt-in: AnalyticsConsent keeps collection off until the person accepts.
            com.example.analytics.AndroidAnalytics.init(this, if (BuildConfig.DEBUG) "debug" else "release")
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
