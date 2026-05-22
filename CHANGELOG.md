# Changelog

All notable changes to SENDA are documented in this file.

## [v2.0.0] – 2026-05-22

This is a major release representing a complete overhaul of the SENDA codebase
and a significant expansion of features since v1.3.0.

### Breaking changes
- The app has been **fully rewritten in Kotlin** (previously Java). The minimum
  supported Android version remains Android 11.
- The internal package structure was reorganised for improved modularity.

### New features
- **Dark theme** support (follows system setting).
- **In-app tutorial** with multiple pages guiding new users through setup.
- **About dialog** accessible from the action bar.
- **Movella DOT** sensor support: connects once on discovery to resolve the
  human-readable name, then reconnects for measurement.

### Improvements & refactoring
- Migrated to **MVVM architecture** with ViewModel and Repository pattern.
- Foreground service and wakelock handling refactored for `targetSdk 34+`.
- Permissions handling streamlined; new transient *starting* / *stopping*
  UI states provide clearer feedback.
- **16 KB ELF page-size compatibility**: native code compiled with the required
  alignment flags (Google Play requirement since November 2025). Note that the
  bundled Movella SDK library is not yet compatible.
- Main layout simplified to `LinearLayout`; old landscape / splash layouts and
  unused preference XMLs removed.
- `compileSdk` / `targetSdk` raised to **35**; NDK updated to **r28**.
- Gradle plugin updated to **8.1.2 / 8.10**; `setup-gradle` action bumped; CI
  workflow split into separate debug and release APK jobs.
- LSL service start/stop made concurrency-safe.
- LocationBridge crash on startup fixed.
- MainActivity crash fixed by lazy-initialising the ViewModel.
- Sensor outlet race condition fixed (sensor stream now starts only after the
  LSL outlet is initialised).
- Unit test added for `MainViewModel`.
- App correctly stops the foreground service when swiped away from recents.

### UI / UX
- Stream availability status text updated.
- Layout margins adjusted in `activity_main.xml`.
- Settings/gear menu icon removed (no configurable settings at this time).
- Tutorial layout constraints and text corrected.

### Build & CI
- APK signed correctly via GitHub Actions using repository secrets.
- Separate CI jobs for debug builds (feature branches) and signed release APKs
  (master branch).
- Signing configuration now checks for keystore existence before applying
  settings, preventing local-build failures.

---

## [v1.3.0] – see [NeuropsyOL/SENDA](https://github.com/NeuropsyOL/SENDA/releases/tag/v1.3.0)

Previous releases are tagged in the public repository at
https://github.com/NeuropsyOL/SENDA/releases.

