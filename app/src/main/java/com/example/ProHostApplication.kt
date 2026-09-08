package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class ProHostApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("ProHostApplication", "FirebaseApp initialized successfully in Application.onCreate()")
            }
        } catch (e: Exception) {
            Log.e("ProHostApplication", "Failed to initialize FirebaseApp: ${e.message}", e)
        }
    }
}
