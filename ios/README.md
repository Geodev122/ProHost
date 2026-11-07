# ProHost iOS (SwiftUI)

Plan, milestones, owner steps and secret names: `docs/IOS_PLAN.md`. The Xcode project is generated (`brew install xcodegen && cd ios && xcodegen generate`); never commit `*.xcodeproj` or `ProHost/Info.plist`.
CI: `.github/workflows/ios-ci.yml` (build + unit tests, no secrets). TestFlight: `ios-testflight.yml` (manual, needs the Apple secrets).
Status: M0 skeleton (splash screen only).
