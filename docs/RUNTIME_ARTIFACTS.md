# Mobile OpenJDK runtime artifacts — research log (Spike 0.4 + Dola verification 2026-11-15)

## Upstream
- Project: https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch  
- Status: **Archived 19 May 2025** (read-only).  
- Purpose: Build scripts for Android/iOS mobile OpenJDK used by PojavLauncher.  
- License of OpenJDK builds: **GPLv2 with Classpath Exception**.

## Artifact form
Historically packaged as architecture-split tarballs / CI zips named like:
- `jre8-pojav`
- `jre17-pojav`
- `jre21-pojav`
Consumed by Pojav’s own CI via `dawidd6/action-download-artifact` from branches `buildjre8` and `buildjre17-21`.

## SHA-256
Checksums below were computed by Dola on 2026-11-15 from files actually downloaded (not copied from chat logs). Anything marked "(compute after download)" must be verified before use.

## Downloadability
| Source | Still downloadable? | ARM64 Android? | Use in Flint |
|--------|---------------------|----------------|--------------|
| GitHub release `jre17-ec28559` assets | ✅ Yes, stable release URL | ✅ Verified (34 .so, 0 .dylib) | ✅ Use with checksum below |
| GitHub release `jre8-40df388` asset | ✅ Downloadable | ❌ Rejected — contains `.dylib` (macOS/iOS), not Android | ❌ Do not use |
| Actions artifacts on archived repo | Often expired / login required | Yes when named aarch64 | Unreliable for CI URL |
| Desktop Adoptium/Temurin Linux aarch64 | Yes | **No** (not mobile layout / not Android-linked) | **Forbidden** |

## Verified downloadable JRE artifacts (2026-11-15)

These are **separate runtime plugins** (GPLv2 + Classpath Exception) — never bundled
into the APK; the app downloads them on demand and verifies the checksum before
extraction via `MobileJreManager.importLocalArchive` / `RuntimeDownloader`.

| ID | Major | ABI | URL | Size (bytes) | SHA-256 | Status |
|----|-------|-----|-----|--------------|---------|--------|
| jre17-arm64 | 17 | arm64-v8a | https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec28559/jre17-arm64-20210825-release.tar.xz | 38065004 | c64583ac2e0ec8857e43456fa9adcf482c6a8e454a7133173bf15692d2478b8d | ✅ Verified Android. `libjli.so` at `./lib/libjli.so`, `libjvm.so` at `./lib/server/libjvm.so`. Covers MC 1.17–1.20. |
| jre17-arm | 17 | armeabi-v7a | https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec28559/jre17-arm-20210914-release.tar.xz | 37378360 | (compute after download) | Same release tag; not byte-verified here. |
| jre17-x86 | 17 | x86 | https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec28559/jre17-x86-20220225-release.tar.xz | 37930912 | (compute after download) | Same release tag; not byte-verified here. |
| jre17-x86_64 | 17 | x86_64 | https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec28559/jre17-x86_64-20210825-release.tar.xz | 39230848 | (compute after download) | Same release tag; not byte-verified here. |

### Not available as stable releases (BLOCKED — need successor project or CI artifact)
- **JRE 8 for Android**: needed for MC ≤1.16.5. No Android `.so` release found; the `jre8-40df388` arm64 asset is macOS/iOS (`.dylib`) and was rejected.
- **JRE 21 for Android**: needed for MC ≥1.20.5. No release tag in the archived upstream repo; only expired CI artifacts.
- **JRE 25+**: no mobile build known.

## Third-party renderer/audio plugins (dynamic only, never statically linked into flint_native)

| Component | License | Form | Notes |
|-----------|---------|------|-------|
| GL4ES | MIT | `libGL.so` — build from source (NDK) or download-on-demand | Optional desktop-GL→GLES translator. CMake flag `FLINT_BUILD_GL4ES` (default OFF). Pinned source: https://github.com/ptitSeb/gl4es.git @ `8b1a3da5d2331d0f88b70be25963182d46eed857`. Loaded at runtime via `dlopen`; `initialize_gl4es()` called after EGL context is current. |
| OpenAL Soft | LGPL-2.1+ | `libopenal.so` — download-on-demand, `dlopen` only | Dynamic linking only; never statically linked. LWJGL OpenAL compat layer resolves symbols at runtime. |

## Plugin layout on device
- JRE: `<filesDir>/runtime/jre/<id>/...`
- Renderer/audio plugins: `<filesDir>/runtime/plugins/<abi>/libGL.so`, `libopenal.so`

`GameLauncher` sets `LD_LIBRARY_PATH` = `<abi plugin dir>:<jre lib dir>:<flint native lib dir>` and `java.library.path` likewise.

## Conclusion for Flint
- **JRE 17 arm64**: unblocked — stable URL + verified SHA-256 above. App can auto-download.
- **JRE 8 / JRE 21 / JRE 25+**: still BLOCKED for auto-download; `importLocalArchive` remains the supported path until a successor project publishes stable releases.
