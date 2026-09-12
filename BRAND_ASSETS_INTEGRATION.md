# ProHost Brand Assets Integration Guide

## Overview
All brand assets have been successfully integrated into the Android project's resource directories. This document describes what was copied, how they're configured, and important platform-specific notes.

## Asset Inventory

### 1. App Icons (Launcher)

#### Legacy Icons (5 Densities)
- **Location**: `app/src/main/res/mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
- **Purpose**: Fallback launcher icons for older Android devices (pre-API 26)
- **Sizes**: 
  - `mdpi`: 48×48 px
  - `hdpi`: 72×72 px
  - `xhdpi`: 96×96 px
  - `xxhdpi`: 144×144 px
  - `xxxhdpi`: 192×192 px

#### Adaptive Icons (5 Densities + XML)
- **Location**: `app/src/main/res/mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/{ic_launcher_foreground,ic_launcher_background}.png`
- **XML Wiring**: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`
- **Purpose**: Modern launcher icons (API 26+) with separate foreground/background layers
- **Sizes** (per density):
  - `mdpi`: 108×108 px (foreground + background)
  - `hdpi`: 162×162 px
  - `xhdpi`: 216×216 px
  - `xxhdpi`: 324×324 px
  - `xxxhdpi`: 432×432 px

**Status**: ✅ Already wired in `AndroidManifest.xml`:
```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```
The adaptive icon XMLs automatically layer the foreground and background for modern devices; legacy devices fall back to the single PNG files.

### 2. Splash Screens (5 Densities)

- **Location**: `app/src/main/res/drawable-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/splash.png`
- **Purpose**: App startup splash screen
- **Sizes**:
  - `mdpi`: 320×480 px
  - `hdpi`: 480×720 px
  - `xhdpi`: 720×1280 px
  - `xxhdpi`: 1080×1920 px
  - `xxxhdpi`: 1440×2560 px

**Design**: Grey abstract diagonal-shaded background with ProHost checkmark logo and brand wordmark centered.

#### Important: Android 12+ Splash Screen API Limitation ⚠️
Android 12+ (API 31+) introduced a new system splash screen API that **only supports solid background colors**, not custom images. The `splash.png` files work perfectly as:
1. **App launch background** (displayed immediately after system splash)
2. **Full-screen drawable** for the initial activity

**To implement splash screens on Android 12+:**
1. Set a solid color in `styles.xml` for the system splash (e.g., the grey from the background):
   ```xml
   <item name="android:windowSplashScreenBackground">#F5F5F5</item>
   ```
2. Display the custom `splash.png` drawable as a full-screen composable or ImageView in your launch activity (e.g., `MainActivity`)

See: [Android 12 Splash Screen](https://developer.android.com/about/versions/12/splash-screen)

### 3. Logos

- **Location**: `app/src/main/res/drawable/`

#### Login Lockup (Primary)
- **File**: `prohost_login_lockup.png`
- **Size**: ~550×360 px (transparent background)
- **Usage**: Login screen header; stack vertically above the form (checkmark + "ProHost" wordmark)
- **Notes**: Pre-composed and ready to use; maintains proper visual hierarchy

#### Individual Marks (Reference)
- `prohost_checkmark_logo.png` — ProHost checkmark only (~403 px wide)
- `prohost_brand_name.png` — Brand wordmark only (~512 px wide)

**Resolution Note**: The checkmark logo is 403×273 px, which is relatively small. A higher-resolution source would be beneficial for:
- Large banner/poster designs
- Print collateral
- High-DPI display assets (density > xxxhdpi)

If future needs require larger-format use cases, consider sourcing a higher-resolution original.

### 4. Play Store Assets

- **Files** (in scratchpad, not copied to project):
  - `ic_playstore_512x512.png` — Google Play Store app icon
  - `feature_graphic_1024x500.png` — Play Store feature graphic

**Usage**: These are metadata assets for Google Play Store listing, not embedded in the app. Upload directly to Play Console under **App content > Store listing**.

## Integration Checklist

- [x] Legacy app icons (5 densities) copied
- [x] Adaptive app icons (10 files + 2 XML) copied
- [x] Splash screens (5 densities) copied
- [x] Logo assets copied to `drawable/`
- [ ] **TODO**: Wire splash screen into launch activity (see Android 12+ section above)
- [ ] **TODO**: Integrate login lockup into `LoginAuthScreen.kt`
- [ ] **TODO**: Upload Play Store assets to Google Play Console
- [ ] **TODO**: Test on devices/emulator across densities

## Technical Notes

### Adaptive Icon XML Structure
The adaptive icon XMLs reference layers at density-specific paths:
```xml
<adaptive-icon>
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
```
Android automatically resolves `@mipmap/` references to the correct density at runtime.

### Fallback Behavior
- **API 26+**: Uses adaptive icon (XML + layered PNGs)
- **API 21-25**: Falls back to legacy `ic_launcher.png` at appropriate density
- **Backwards compatible**: No manifest changes needed

### Logo Asset Naming
Resources follow Android naming conventions:
- Lowercase with underscores (`prohost_login_lockup.png`)
- Unique, descriptive names
- Drawable format (PNG with transparency where applicable)

## Next Steps

1. **Splash Screen Implementation**: Add splash screen display logic to `MainActivity.kt` (display `splash.png` for 1-3 seconds before routing to auth/main flow)
2. **Login Screen Integration**: Replace any placeholder logo with `prohost_login_lockup.png` in `LoginAuthScreen.kt`
3. **Play Store Upload**: Use the provided `ic_playstore_512x512.png` and `feature_graphic_1024x500.png` for your store listing
4. **Testing**: Verify icons and splash screens render correctly across multiple device densities and API levels

## File Locations Reference

```
app/src/main/res/
├── mipmap-hdpi/
│   ├── ic_launcher.png (legacy)
│   ├── ic_launcher_foreground.png (adaptive)
│   └── ic_launcher_background.png (adaptive)
├── mipmap-mdpi/
│   ├── ic_launcher.png
│   ├── ic_launcher_foreground.png
│   └── ic_launcher_background.png
├── mipmap-xhdpi/
│   ├── ic_launcher.png
│   ├── ic_launcher_foreground.png
│   └── ic_launcher_background.png
├── mipmap-xxhdpi/
│   ├── ic_launcher.png
│   ├── ic_launcher_foreground.png
│   └── ic_launcher_background.png
├── mipmap-xxxhdpi/
│   ├── ic_launcher.png
│   ├── ic_launcher_foreground.png
│   └── ic_launcher_background.png
├── mipmap-anydpi-v26/
│   ├── ic_launcher.xml
│   └── ic_launcher_round.xml
├── drawable-hdpi/
│   └── splash.png
├── drawable-mdpi/
│   └── splash.png
├── drawable-xhdpi/
│   └── splash.png
├── drawable-xxhdpi/
│   └── splash.png
├── drawable-xxxhdpi/
│   └── splash.png
└── drawable/
    ├── prohost_login_lockup.png
    ├── prohost_checkmark_logo.png
    └── prohost_brand_name.png
```

## Questions or Issues?

- **Android 12+ splash screen specifics**: See [Android Developer Docs - Splash Screen](https://developer.android.com/about/versions/12/splash-screen)
- **Adaptive icon guidelines**: See [Android Developer Docs - Adaptive Icons](https://developer.android.com/guide/practices/ui_guidelines/app_icons)
- **Logo resolution**: Consider sourcing a higher-res original if future large-format use is needed (current: 403×273 px)
