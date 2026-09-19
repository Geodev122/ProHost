package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class ProHostApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("ProHostApplication", "FirebaseApp initialized successfully in Application.onCreate()")
            }
            // Initialize Firebase App Check with Play Integrity provider unconditionally
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.d("ProHostApplication", "FirebaseAppCheck Play Integrity initialized")
        } catch (e: Exception) {
            Log.e("ProHostApplication", "Failed to initialize FirebaseApp or App Check: ${e.message}", e)
        }
    }
}
