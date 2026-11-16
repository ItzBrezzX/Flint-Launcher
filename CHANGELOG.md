# Changelog

## Incomplete — CI workflow optimization (no APK)

Not a release. No CI run from this environment. Not READY TO PLAY.

- Optimized `.github/workflows/android-build.yml` for GitHub Actions:
  - Sequential native → tests → APK stages
  - Gradle memory/worker caps (`-Xmx2g`, `workers.max=2`, `--no-daemon`)
  - Uses committed `./gradlew` (Gradle 8.9)
  - Pins JDK 17, platform 35, build-tools 35.0.0, NDK 27.0.12077973, CMake 3.22.1
  - Verifies `libflint_native.so`, `libGL.so`, `libglfw.so` per ABI and inside the APK
  - Artifact upload via `actions/upload-artifact@v4`
- Local low-RAM NDK builds are not repeated here; use GitHub Actions.
- Microsoft authentication unchanged and paused.
- Gradle wrapper files remain as previously added (official Gradle 8.9).

## Earlier

- Official Gradle 8.9 wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`).
