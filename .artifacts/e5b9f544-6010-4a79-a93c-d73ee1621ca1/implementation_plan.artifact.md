# Implementation Plan - Complete Recommendation 4.8 (Subdivisions Editable After Publish)

Finish Recommendation 4.8 by wiring the standalone `SubdivisionEditorSection` composable into `CreateListingDialog.kt` (creation wizard) and `SpaceScheduleEditorDialog.kt` (post-publish availability control editor), and backing it with `ProHostRepository` and `ProHostViewModel` functions that preserve protected listing fields upon update.

## Proposed Changes

### UI Components

#### [MODIFY] [CreateListingDialog.kt](file:///C:/Users/Dell/StudioProjects/ProHost/app/src/main/java/com/example/ui/components/CreateListingDialog.kt)
- Replace Step 2's inline subdivision room-builder form (~lines 500–720) with a single call to `SubdivisionEditorSection(subdivisionsList = subdivisionsList, onSubdivisionsChange = { subdivisionsList = it })`.
- Remove redundant local state variables (`subName`, `subType`, `subAmenitiesSelected`, `subHourlyRate`, `subHourlyEnabled`, `subShiftRate`, `subShiftHours`, `subShiftEnabled`, `subDailyRate`, `subDailyEnabled`, `subMonthlyRate`, `subMonthlyEnabled`, `subAmenitiesPreset`) that now reside internally within `SubdivisionEditorSection`.

#### [MODIFY] [SpaceScheduleEditorDialog.kt](file:///C:/Users/Dell/StudioProjects/ProHost/app/src/main/java/com/example/ui/components/SpaceScheduleEditorDialog.kt)
- Add a 4th "Rooms & Subdivisions" section (rendered when `space.spaceType != SpaceType.PRIVATE_OFFICE` or `space.subdivisions.isNotEmpty()`).
- Maintain local state `var currentSubdivisions by remember(space) { mutableStateOf(space.subdivisions) }`.
- Embed `SubdivisionEditorSection(subdivisionsList = currentSubdivisions, onSubdivisionsChange = { currentSubdivisions = it })`.
- Update the dialog's save controls to persist updated subdivisions along with schedules and pricing.

---

### Data & State Management

#### [MODIFY] [ProHostRepository.kt](file:///C:/Users/Dell/StudioProjects/ProHost/app/src/main/java/com/example/data/repository/ProHostRepository.kt)
- Add `updateSpaceSubdivisions(spaceId: String, newSubdivisions: List<Subdivision>): Boolean`.
- Fetch existing listing from `_spaces`, update its `subdivisions` list while protecting server-managed fields (`isVerified`, `isActiveSubscription`, `subscriptionExpiryMillis`, `ownerIsIdVerified`, `isOwnerSuspended`), update local state `_spaces`, and persist via `firestoreService.saveWorkspace`.

#### [MODIFY] [ProHostViewModel.kt](file:///C:/Users/Dell/StudioProjects/ProHost/app/src/main/java/com/example/ui/viewmodel/ProHostViewModel.kt)
- Add ViewModel wrapper `fun updateSpaceSubdivisions(spaceId: String, subdivisions: List<Subdivision>): Boolean` delegating to `ProHostRepository`.

---

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify zero Kotlin syntax or type mismatch errors across all modified composables and ViewModel/Repository methods.
- Run `./gradlew assembleDebug` to confirm full APK build completes cleanly.

### Manual Verification
- Verify `CreateListingDialog` Step 2 allows adding and removing subdivisions using the shared `SubdivisionEditorSection`.
- Verify `SpaceScheduleEditorDialog` displays the Rooms & Subdivisions section and saves new/updated subdivisions on an existing listing.
