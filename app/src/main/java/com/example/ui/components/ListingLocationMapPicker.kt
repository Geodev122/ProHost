package com.example.ui.components

import com.example.ui.theme.Spacing

import com.example.R
import androidx.compose.ui.res.stringResource
import com.example.ui.theme.Elevation
import com.example.ui.util.UiMessages
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * What a pin resolved to. Street, area and city are in the app's language (an Arabic app fills Arabic); the country is its
 * standard English name (filters and search compare English names) and [cityEn] keeps an English copy of the city.
 */
data class ResolvedAddress(
    val lat: Double,
    val lng: Double,
    val street: String = "",
    val area: String = "",
    val city: String = "",
    val country: String = "",
    val countryCode: String = "",
    val cityEn: String = ""
)

/** English country name for an ISO code ("LB" → "Lebanon"), else [fallback]. */
fun englishCountryName(countryCode: String, fallback: String?): String =
    countryCode.takeIf { it.isNotBlank() }?.let { Locale("", it).getDisplayCountry(Locale.ENGLISH) }?.takeIf { it.isNotBlank() }
        ?: fallback.orEmpty()

/** Reverse-geocodes a pin twice when needed: once in the app's language for display, once in English for the copies. */
@Suppress("DEPRECATION")
suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): ResolvedAddress = withContext(Dispatchers.IO) {
    val appLocale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    fun lookup(locale: Locale): Address? =
        runCatching { Geocoder(context, locale).getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
    val local = lookup(appLocale)
    val english = if (appLocale.language == Locale.ENGLISH.language) local else lookup(Locale.ENGLISH) ?: local
    toResolvedAddress(lat, lng, local, english)
}

/** Forward-geocodes a typed address (no pin dropped) the same way; null when nothing matches. */
@Suppress("DEPRECATION")
suspend fun forwardGeocode(context: Context, query: String): ResolvedAddress? = withContext(Dispatchers.IO) {
    val hit = runCatching { Geocoder(context, Locale.ENGLISH).getFromLocationName(query, 1)?.firstOrNull() }.getOrNull()
        ?: return@withContext null
    reverseGeocode(context, hit.latitude, hit.longitude)
}

private fun toResolvedAddress(lat: Double, lng: Double, local: Address?, english: Address?): ResolvedAddress {
    val code = (english ?: local)?.countryCode.orEmpty()
    return ResolvedAddress(
        lat = lat,
        lng = lng,
        street = listOfNotNull(local?.thoroughfare, local?.subThoroughfare).filter { it.isNotBlank() }.joinToString(" "),
        area = (local?.subLocality ?: local?.subAdminArea).orEmpty(),
        city = (local?.locality ?: local?.subAdminArea).orEmpty(),
        country = englishCountryName(code, english?.countryName ?: local?.countryName),
        countryCode = code,
        cityEn = (english?.locality ?: english?.subAdminArea).orEmpty()
    )
}

@Composable
fun ListingLocationMapPicker(
    initialLat: Double?,
    initialLng: Double?,
    onLocationConfirmed: (ResolvedAddress) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLocating by remember { mutableStateOf(false) }

    val defaultCenter = if (initialLat != null && initialLng != null) {
        LatLng(initialLat, initialLng)
    } else {
        LatLng(20.0, 0.0) // world center — no country assumption
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, if (initialLat != null && initialLng != null) 15f else 2f)
    }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun requestHighAccuracyLocation() {
        val finePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (finePerm == PackageManager.PERMISSION_GRANTED || coarsePerm == PackageManager.PERMISSION_GRANTED) {
            isLocating = true
            val cancelToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelToken.token)
                .addOnSuccessListener { location: Location? ->
                    isLocating = false
                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        }
                    }
                }
                .addOnFailureListener {
                    isLocating = false
                    UiMessages.show(context, context.getString(R.string.gps_signal_unavailable))
                }
        }
    }

    // Permission, blocked-permission and location-off prompts are all native (PermissionPrompts.kt).
    val requestLocation = rememberLocationAccess { requestHighAccuracyLocation() }

    suspend fun resolveLocationAndConfirm(latLng: LatLng) {
        val resolved = reverseGeocode(context, latLng.latitude, latLng.longitude)
        onLocationConfirmed(resolved)
    }

    Box(modifier = modifier.clipToBounds()) {
        val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapType = MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                compassEnabled = true
            )
        )
        
        // Custom Crosshair Center Pin Overlay
        Icon(
            imageVector = Icons.Default.MyLocation,
            contentDescription = stringResource(R.string.center_crosshair),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Center)
                .size(36.dp)
                .offset(y = (-18).dp)
        )

        // Ultra-Compact Floating Action Controls (Inside bounds, zero overflow)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = {
                    val currentCenter = cameraPositionState.position.target
                    coroutineScope.launch {
                        resolveLocationAndConfirm(currentCenter)
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = Elevation.level3
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.mark_pin_location), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // GPS Locate FAB (Bottom Right inside bounds)
        FloatingActionButton(
            onClick = { requestLocation() },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(40.dp)
        ) {
            if (isLocating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.locate_me), modifier = Modifier.size(20.dp))
            }
        }
    }
}
