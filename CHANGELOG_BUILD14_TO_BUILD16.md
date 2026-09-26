# ProHost Changelog & Technical Implementation Summary (Build 14 to Build 16)

This document provides a detailed technical overview of all changes, implementations, architectural improvements, and bug fixes delivered across recent development cycles (from **Build 14 / v1.0.13** up to **Build 16 / v1.0.15**), as well as upcoming requested features pending implementation.

---

## Version Summary & Release Timeline

| Build / Version | Release Tag / Commit | Key Highlights |
| :--- | :--- | :--- |
| **Build 14** (v1.0.13) | `d9cb05e` | Initial baseline with release bundle and native debug symbols setup. |
| **Build 15** (v1.0.14) | `b40a456` | Map gesture lock, 3-dots drawer header fixes, and UI contrast improvements. |
| **Build 16** (v1.0.15) | `2734283` | Drawer back arrow, uncoupled initial phone SMS, and streamlined Google Sign-In registration flow. |

---

## Major Architectural & Feature Implementations

### 1. Google Maps SDK Migration & Osmdroid Removal
- **Migration from Osmdroid**: Fully removed legacy `osmdroid` mapping libraries and dependencies from `build.gradle.kts`.
- **Google Maps Compose**: Integrated `com.google.maps.android:maps-compose`, `play-services-maps`, and `play-services-location`.
- **Secure API Key Management**:
  - Implemented dynamic `manifestPlaceholders` in Gradle to inject `MAPS_API_KEY` securely from `local.properties` (local builds) and GitHub repository secrets (CI/CD workflows).
- **Map Components Overhaul**:
  - **`LebanonMapCanvas.kt`**: Rewritten using `GoogleMap` composable with custom colored markers categorized by `SpaceType` (Studio, Clinic, Office).
  - **`ListingLocationMapPicker.kt`**: Upgraded with a central crosshair pin selector and robust Android Play Services Geocoder integration for precise address lookup.

### 2. Role-Based Access Control (RBAC) & ProHost Security
- **ProHost vs. Specialist Separation**:
  - Restricted Specialist-exclusive operational workflows (such as booking, contacting via WhatsApp, and renting) for users with `UserRole.PRO_HOST`.
  - Maintained full read-only "Explore Workspaces" browsing access for ProHosts with a clear preview banner.
- **KYC Verification Gate**:
  - Added `KycVerificationDialog` as a strict operational gate during ProHost upgrade and publishing requests.

### 3. Post-Publish Subdivisions & Room Management (Recommendation 4.8)
- **Shared Subdivision Editor**:
  - Wired the standalone `SubdivisionEditorSection` component into both `CreateListingDialog` (creation wizard) and `SpaceScheduleEditorDialog` (post-publish schedule/room control editor).
- **Repository & State Preservation**:
  - Added `updateSpaceSubdivisions` in `ProHostRepository` and `ProHostViewModel`.
  - Ensured critical server-managed fields (`isVerified`, `isActiveSubscription`, `subscriptionExpiryMillis`, `ownerIsIdVerified`, `isOwnerSuspended`) are strictly protected and preserved during updates.

### 4. Authentication & Security Overhauls
- **In-App PIN Removal**:
  - Completely excised legacy in-app PIN validation logic in favor of modern security patterns.
- **Dynamic Password & OTP Flows**:
  - Implemented dynamic repeat password verification fields and robust email/Google OTP verification flows.
- **Onboarding Streamlining**:
  - Uncoupled initial phone SMS verification to allow friction-free onboarding steps.
  - Enabled direct registration screen opening upon Google Sign-In authentication.

---

## UI/UX Refinements & Material 3 Polish

### 1. Theme-Aware Color Tokens
- Replaced hardcoded text colors across all app screens with dynamic, WCAG AAA-compliant `MaterialTheme.colorScheme` tokens, ensuring perfect visual contrast and legibility across both Light and Dark themes.

### 2. Navigation Drawer & Map Gesture Fixes
- **Gesture Conflict Resolution**: Disabled edge-swipe drawer opening gestures to eliminate accidental gesture conflicts while panning and zooming maps in the Explore view.
- **Manual Drawer Controls**:
  - Added a dedicated 3-dots header button for manual drawer opening.
  - Added a back-arrow collapse icon inside the drawer header with optimized high-contrast styling.

### 3. Popup & Spinner Enhancements
- Enhanced selection popups, bottom navigation gating, and added smooth progress indicators (spinners) during OTP verification and authentication steps.

---

## Build & Release Configuration
- **Version Code Increments**:
  - Upgraded project `versionCode` to `15` (v1.0.14) and subsequently `16` (v1.0.15).
- **R8 & Performance**:
  - Enabled advanced R8 DEX code shrinking and obfuscation while preserving native debug symbols (`nativeDebugSymbol`) for robust crash reporting and analysis.

---

## Upcoming & Requested Features (Pending Implementation)

The following requested architecture and verification workflows are scheduled for upcoming implementation cycles:

### 1. Dedicated Phone Number Verification for KYC
- **Purpose**: A standalone phone number verification flow specifically dedicated to **KYC (Know Your Customer) identity verification**, completely uncoupled from authentication sign-up/login flows.
- **Workflow**: Users provide and verify their phone number via SMS OTP specifically when completing host verification or security tiers rather than during initial app entry.

### 2. Smart Email Authentication Gate & Account Lookup
- **Workflow**:
  - **Email Entry**: User enters their email address.
  - **Account Existence Check**: System queries backend/auth provider to check if an account already exists for that email.
  - **Branching**:
    - *If No Account Exists (Sign-Up)*: Dynamically prompt the user for **Password** and **Confirm Password** fields to create a new account.
    - *If Account Exists (Sign-In)*: Prompt for password / redirect to existing account login.

### 3. Google Authentication Best Practices (Credential Manager API)
- **Guidelines**: Adopt Google’s officially recommended **Credential Manager API** and One Tap sign-in/sign-up flows.
- **Implementation Goal**: Streamline Google Sign-In to be fully compliant with modern Android identity standards, eliminating legacy deprecations and ensuring a seamless, native cryptographic token exchange.
