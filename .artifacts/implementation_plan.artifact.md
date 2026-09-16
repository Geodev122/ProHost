# ProHost Role Restrictions & Google Maps Migration

This plan covers two major objectives:
1. Restricting Specialist-exclusive features (booking, contacting, renting) from `PRO_HOST` users, while maintaining their ability to "Explore" workspaces.
2. Completely removing `osmdroid` and migrating mapping capabilities to the native Google Maps SDK using Jetpack Compose, including robust marker designs and local/CI API key secret management.

## User Review Required

> [!IMPORTANT]
> **Google Maps API Key Setup:**
> You will need to generate a Google Maps API Key from the [Google Cloud Console](https://console.cloud.google.com/) with the **Maps SDK for Android** and **Places API** (if needed) enabled.
>
> To keep it secure and out of version control:
> 1. **Locally:** Add `MAPS_API_KEY=AIzaSyYourKeyHere` to your `local.properties` file (or a `.env` file if you prefer, though Android natively reads `local.properties`).
> 2. **In GitHub (for CI/CD deployments):** Add a repository secret named `MAPS_API_KEY`.

## Proposed Changes

### 1. Dependency & Manifest Configuration
- **[MODIFY] `build.gradle.kts`**:
  - Remove `libs.osmdroid.android`.
  - Add `com.google.maps.android:maps-compose`, `play-services-maps`, and `play-services-location`.
  - Implement dynamic `manifestPlaceholders` to inject `MAPS_API_KEY` from `local.properties` or environment variables securely.
- **[MODIFY] `AndroidManifest.xml`**:
  - Insert the `<meta-data android:name="com.google.android.geo.API_KEY" android:value="${MAPS_API_KEY}" />` tag.

### 2. Role-Based Access Restrictions
- **[MODIFY] `SpaceDetailsScreen.kt`**:
  - Hide the "Request Rent", "Check Availability", and "WhatsApp" buttons if the current user role is `UserRole.PRO_HOST`.
  - Add visual feedback or an alternate read-only layout (e.g., "Preview Mode" banner) so Hosts understand why they can't book.
- **[MODIFY] `AppDrawerContent.kt`**:
  - Refine the side menu to ensure `PRO_HOST` can still see "Explore Workspaces".
  - Ensure "My Bookings" and "My Favorites" (Specialist views) are properly hidden or segregated so ProHosts don't get stuck in empty renting flows.

### 3. Google Maps UI Migration
- **[MODIFY] `LebanonMapCanvas.kt`**:
  - Completely rewrite using `GoogleMap` from the `maps-compose` library.
  - Implement a custom Compose marker factory that colors pins differently based on the `SpaceListing.type` (e.g., Studio, Clinic, Office).
- **[MODIFY] `ListingLocationMapPicker.kt`**:
  - Rewrite to use Google Maps with a central crosshair for precise location selection.
  - Upgrade the Geocoder implementation (Android's built-in `Geocoder` wrapping Google Play Services) to fetch and parse accurate addresses, dropping Nominatim calls.
- **[MODIFY] `libs.versions.toml`**:
  - Declare the new Google Maps Compose and Play Services dependencies.

## Verification Plan

### Automated/Build Verification
- Verify the project successfully syncs and builds with the new Maps Compose dependencies.
- Confirm `osmdroid` is fully excised from the Gradle dependency tree.

### Manual Verification
1. **As a ProHost**: Log in, open the side menu, tap "Explore Workspaces". Tap on a listing. Verify that rent and contact buttons are entirely hidden.
2. **As a Specialist**: Log in, verify the renting buttons are still fully accessible.
3. **Map Rendering**: Verify the map loads using the embedded Google Maps SDK, custom colored markers appear, and the location picker accurately resolves addresses.