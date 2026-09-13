package com.example

import android.app.Application
import android.preference.PreferenceManager
import android.util.Log
import com.google.firebase.FirebaseApp
import org.osmdroid.config.Configuration
import java.io.File

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

        // osmdroid (OpenStreetMap) — one-time setup for both map surfaces
        // (ListingLocationMapPicker.kt, LebanonMapCanvas.kt). A blank/default user agent
        // gets 403'd by OSM's tile server; osmdroidTileCache under this app's own cache
        // dir avoids needing any storage permission (older osmdroid defaults pointed at
        // external storage, which did need one).
        try {
            val config = Configuration.getInstance()
            config.load(this, PreferenceManager.getDefaultSharedPreferences(this))
            config.userAgentValue = packageName
            config.osmdroidTileCache = File(cacheDir, "osmdroid")
        } catch (e: Exception) {
            Log.e("ProHostApplication", "Failed to configure osmdroid: ${e.message}", e)
        }
    }
}
