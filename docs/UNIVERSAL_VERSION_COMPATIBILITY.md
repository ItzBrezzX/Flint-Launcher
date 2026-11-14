# Universal Minecraft Java Version Compatibility

## Product requirement

Flint targets the complete Minecraft Java Edition catalogue, not one release:

- Classic, Indev/Infdev, alpha, beta, and every historical release.
- Old and current snapshots, including date-based identifiers.
- Current releases, including the year-based 26.x line.
- Modded profiles when their loader, installer and dependencies are supported.

A catalogue entry is not a launch guarantee. A profile is **Playable** only after its exact metadata, runtime, native libraries, renderer/window path, and input have passed launch tests.

## Version and backend resolution

1. Load the official version manifest. Preserve exact ID, type, timestamp and metadata URL; never hardcode the version catalogue.
2. Fetch and merge the complete version inheritance chain before choosing a backend.
3. Resolve Java from the final `javaVersion.majorVersion`. For metadata that predates this field, use Java 8 only where the legacy compatibility contract permits it.
4. Choose an Android mobile JRE that satisfies the required major and matches the app process ABI. Current ARM64 catalogue targets Java 8, 17, 21 and 25; archive discovery alone is not runtime execution proof.
5. Choose the LWJGL compatibility line from resolved libraries, not from version-ID string parsing:
   - `org.lwjgl.lwjgl:lwjgl` → LWJGL2 compatibility path.
   - `org.lwjgl:lwjgl` → LWJGL3 compatibility path.
   - Unknown or ambiguous metadata → blocked with a diagnostic; do not guess.
6. Choose a renderer only after its ABI library is verified, loaded on the device and tested against a real Android window surface. EGL pbuffer success is diagnostics only.
7. Run the game in an isolated Android game process. Do not load a process-global HotSpot VM from the launcher UI process.

The current metadata-aware compatibility resolver implements the LWJGL library-selection rule. That policy is not yet a working game backend.

## Installer compatibility

The installer must resolve, validate and install per version:

- Every parent in a loader/version inheritance chain.
- OS, architecture and feature rules; artifact and native classifiers.
- Legacy virtual/resource assets and modern content-addressed assets.
- Client JAR, libraries, platform-appropriate natives, logging config and checksums.
- Legacy `minecraftArguments` and modern structured argument objects.
- Version-specific main class, JVM arguments, and environment placeholders.
- Loader metadata, processors and dependencies for supported Fabric, Quilt, Forge and NeoForge versions.

Unsupported required metadata or missing artifacts must produce an actionable blocked state, never a partially installed profile marked ready.

## Runtime and game backend

The Android backend needs distinct tested compatibility implementations for LWJGL2 and LWJGL3, plus:

- Android game Activity/Surface lifecycle backed by `ANativeWindow` and EGL window surfaces.
- A compatible GLFW/LWJGL native layer and version-matched Java classes.
- Renderer integration (GL4ES first for suitable legacy/vanilla paths; other renderers only when device capability tests pass).
- Touch, keyboard, mouse and controller input; audio; resize, pause/resume and context-loss handling.
- Dedicated game-process lifecycle, real started/running/exited/crashed states, logs and safe stop/recovery.

A Java probe succeeding is not a Minecraft launch. A JLI method returning is not a live game session. A live session is not **Playable** until the main menu renders and basic input works.

## Compatibility states

- **Available** — metadata is discoverable.
- **Installing** — artifacts are resolving, downloading or validating.
- **Installed** — required files and integrity checks pass.
- **Runtime verified** — the selected Android JVM executes a probe on the target ABI.
- **Launch tested** — the game starts and reaches a defined startup milestone.
- **Playable** — the main menu renders and basic input works.
- **Blocked** — a specific missing runtime, library, loader, renderer, device capability or unsupported metadata requirement is reported.

Do not collapse these states into one `installed` or `verified` boolean.

## Required test matrix

Host CI fixtures must cover:

- Classic/Indev/Infdev metadata where present, alpha, beta and early release metadata.
- Pre-1.6 virtual assets and snapshots mapping assets to `resources/`.
- Pre-1.13 LWJGL2 and 1.13+ LWJGL3 library metadata.
- Legacy string arguments and modern argument objects.
- Java 8, 17, 21 and 25 runtime selection.
- Date-based snapshots across the LWJGL2/LWJGL3 boundary.
- Inheritance chains, duplicate libraries, native classifiers and OS/feature rules.
- Fabric, Quilt, Forge and NeoForge metadata/installer fixtures where supported.
- Corrupt files, missing natives, unavailable renderer and interrupted installation.

Physical ARM64 device tests must validate the actual mobile JRE, both relevant LWJGL paths, EGL window surface, renderer, input, audio and game startup. An x86_64 emulator or successful APK build is not a substitute for the target phone.

## Current implementation boundary

Implemented foundations include version manifest channels (`release`, `snapshot`, `old_beta`, `old_alpha`), metadata inheritance, legacy/modern argument parsing, asset materialization, native extraction, Java 8/17/21/25 ARM64 runtime artifacts, and metadata-aware LWJGL routing tests.

**Minecraft is not currently launchable from Flint.** The app still deliberately stops after Java preparation. The native module builds GL4ES and has EGL pbuffer diagnostics, but Flint does not yet connect GL4ES to a real window surface, supply the complete Android LWJGL2/LWJGL3 native paths, route input/audio, or run a managed game session in an isolated process.

Next gates:

1. Complete and fixture-test installer/argument/classpath behavior across every metadata generation.
2. Integrate and license-audit both Android LWJGL2 and LWJGL3 backends.
3. Implement the isolated game process and Android Surface/EGL/input/audio lifecycle.
4. Verify mobile JVM execution on the target ARM64 device.
5. Reach the main menu and validate input for representative versions from each compatibility family.
6. Expand the tested matrix before marking additional versions **Playable**.

No version family should be silently filtered out merely because it is old, snapshot, alpha or beta. Unsupported cases must remain visible as blocked until their exact compatibility path is implemented and tested.
