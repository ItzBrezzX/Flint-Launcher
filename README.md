# Flint Launcher

An open-source Minecraft: Java Edition launcher for Android, ARM64 first. Package: `com.flintlauncher.android`.

> **Status: development — not yet READY TO PLAY.** The source now contains a production launch path from PLAY → Android Surface → EGL/GL4ES → Flint GLFW C ABI → LWJGL → in-process `JLI_Launch`. CI and physical-device acceptance are tracked separately. Minecraft title-screen and world-entry success have **not** been confirmed on hardware.

Flint is an unofficial project and is not affiliated with Mojang or Microsoft.

## Current implementation

| Area | State |
|---|---|
| Compose launcher shell, navigation and theme | Implemented |
| Version catalog and Minecraft install pipeline | Implemented in source with unit and fake-server tests; current CI is the release gate |
| Mojang launch metadata and JVM/game argument construction | Implemented and connected to the launch request |
| Offline account storage and launch identity | Implemented; offline account path is the currently wired path |
| Microsoft authentication | OAuth/Xbox/Minecraft client pieces exist; refresh/session exchange and full login UI remain incomplete |
| Profiles, mods and modpacks | Domain pieces exist; several screens and full install flows remain incomplete |
| Renderer preview | GL4ES/EGL/GLFW preview path exists |
| Production game window | Surface-backed `GameActivity` and same-thread EGL/GL4ES/GLFW/JLI setup are wired in source |
| Android LWJGL natives | LWJGL 3.3.3 core/OpenGL Android builds plus Flint GLFW/GL4ES are integrated in native CMake |
| Touch input | Reference-style HUD with top toolbar and 3×3 action pad; settings can add/remove buttons, bind keyboard or mouse actions, drag buttons freely, and persist the layout; Android keyboard text is forwarded to the native GLFW FIFO |
| Graceful STOP | Enqueues a GLFW close request; not a force-kill if the game stops processing events |
| Crash reports | Redacted rotating reports stored in app-private `files/crash-reports/` |
| Minecraft title screen, single-player world and audio | **Not verified on a physical device** |

## Runtime prerequisites

Flint does not bundle a Java runtime in the APK. A compatible mobile OpenJDK/JRE for the selected Minecraft version must be installed from the pinned catalog or imported from a local archive with a supplied SHA-256 checksum. Import checks the required JRE files; native JLI verification occurs when that runtime is selected for launch.

The selected Minecraft version must also have its client JAR, libraries, assets and Android-compatible native dependencies installed. MobileJreManager can download the catalogued Android OpenJDK archives with pinned byte-size and SHA-256 checks, or import a local `.tar.xz`/`.tar.gz` archive after verifying its supplied SHA-256 checksum. No JRE is bundled in the APK. The GLFW backend implements the Android-facing subset required by the current LWJGL binding; it is not a complete desktop window manager.

See [docs/ANDROID_RUNTIME_INTEGRATION.md](docs/ANDROID_RUNTIME_INTEGRATION.md) for the full Surface, EGL ownership, input, STOP and validation flow. See [docs/SPIKE0.md](docs/SPIKE0.md) for the device acceptance checklist.

## Build

Requirements: JDK 17, Android SDK platform 35, Android NDK/CMake and Gradle 8.9.

This repository currently pins Gradle 8.9 in GitHub Actions and does not require a committed Gradle wrapper. With Gradle 8.9 installed locally:

```bash
gradle assembleDebug
gradle test
gradle lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## GitHub Actions

- **Android CI** compiles modules, runs unit tests, checks Android lint, packages native libraries and verifies the exported GLFW C ABI symbols.
- **Build APK** produces a debug APK and verifies it can be installed by Android tooling.
- Download the `flint-launcher-debug-apk` artifact from a successful Build APK workflow run.

A successful CI build does not prove that a particular phone's GPU driver, JRE binaries, Minecraft version or touch controls work. Those require physical-device tests.

## Remaining device acceptance

1. Open the APK and reach the Flint PLAY screen without a startup crash.
2. Import a trusted compatible ARM64 JRE and install a supported vanilla Minecraft version.
3. Launch to the Minecraft title screen.
4. Enter a single-player world and verify rendering, camera, joystick, attack/use, jump, inventory, pause and audio.
5. Use STOP and confirm Flint returns without a stuck EGL/JVM thread.
6. Test background/foreground, surface recreation and repeated launches.

Do not describe Flint as READY TO PLAY until those checks pass on the target Android hardware.
