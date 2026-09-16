package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.Governorate
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

@Composable
fun ListingLocationMapPicker(
    initialLat: Double?,
    initialLng: Double?,
    onLocationConfirmed: (lat: Double, lng: Double, address: String, governorate: Governorate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLocating by remember { mutableStateOf(false) }

    val defaultCenter = LatLng(
        initialLat ?: 33.8886,
        initialLng ?: 35.5184
    )
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, if (initialLat != null) 15f else 12f)
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
                    Toast.makeText(context, "GPS signal unavailable", Toast.LENGTH_SHORT).show()
                }
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            requestHighAccuracyLocation()
        } else {
            Toast.makeText(context, "Location permission declined.", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    suspend fun resolveLocationAndConfirm(latLng: LatLng) {
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale("en", "LB"))
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    
                    // Construct a clean, readable address string
                    val streetStr = address.thoroughfare ?: address.subLocality ?: ""
                    val cityStr = address.locality ?: address.subAdminArea ?: ""
                    val resolvedAddress = listOf(streetStr, cityStr).filter { it.isNotBlank() }.joinToString(", ")
                    val finalAddress = resolvedAddress.ifBlank { "Unknown Area" }

                    val adminArea = address.adminArea ?: ""
                    val matchedGov = Governorate.entries.find { gov ->
                        adminArea.contains(gov.displayName, ignoreCase = true) ||
                        gov.displayName.contains(adminArea, ignoreCase = true)
                    } ?: Governorate.BEIRUT

                    withContext(Dispatchers.Main) {
                        onLocationConfirmed(latLng.latitude, latLng.longitude, finalAddress, matchedGov)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onLocationConfirmed(latLng.latitude, latLng.longitude, "Unknown Location", Governorate.BEIRUT)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onLocationConfirmed(latLng.latitude, latLng.longitude, "Coordinates: ${latLng.latitude}, ${latLng.longitude}", Governorate.BEIRUT)
                }
            }
        }
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
                myLocationButtonEnabled = false, // We use our own FAB
                zoomControlsEnabled = false,
                compassEnabled = true
            )
        )
        
        // Custom Crosshair Overlay
        Icon(
            imageVector = Icons.Default.MyLocation,
            contentDescription = "Center Crosshair",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Center)
                .size(36.dp)
                // Offset slightly up because the anchor of a pin is its bottom tip
                .offset(y = (-18).dp)
        )

        // Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(
                onClick = {
                    val finePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    val coarsePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (finePerm == PackageManager.PERMISSION_GRANTED || coarsePerm == PackageManager.PERMISSION_GRANTED) {
                        requestHighAccuracyLocation()
                    } else {
                        requestPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                if (isLocating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.MyLocation, contentDescription = "Locate Me")
                }
            }

            ExtendedFloatingActionButton(
                onClick = {
                    val currentCenter = cameraPositionState.position.target
                    coroutineScope.launch {
                        resolveLocationAndConfirm(currentCenter)
                    }
                },
                icon = { Icon(Icons.Default.Check, contentDescription = "Mark Pin") },
                text = { Text("Mark Pin") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
