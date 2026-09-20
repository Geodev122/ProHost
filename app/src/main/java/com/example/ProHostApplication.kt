package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class ProHostApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("ProHostApplication", "FirebaseApp initialized successfully in Application.onCreate()")
            }
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                // Debug builds (emulator / CI) use the debug provider so App Check
                // does not block Firestore/Functions calls on non-hardware devices.
                // Register the matching debug token in Firebase Console → App Check.
                firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d("ProHostApplication", "FirebaseAppCheck DEBUG provider initialized")
            } else {
                firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.d("ProHostApplication", "FirebaseAppCheck Play Integrity initialized")
            }
        } catch (e: Exception) {
            Log.e("ProHostApplication", "Failed to initialize FirebaseApp or App Check: ${e.message}", e)
        }
    }
}
