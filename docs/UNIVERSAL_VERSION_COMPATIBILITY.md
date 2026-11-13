# Universal Minecraft Java Version Compatibility

## Product requirement

Flint targets the full Minecraft Java version catalogue, not a single legacy release:

- Current and historical release versions.
- Snapshot versions, including older snapshot identifiers.
- Legacy beta and alpha versions.
- Modded installations when their loader and dependencies are supported.

A version appearing in the catalogue is not a claim that it can launch. Flint may mark a profile **Playable** only after its install plan, runtime, native dependencies, renderer path, and launch smoke test pass.

## Version metadata and runtime selection

1. Read the official version manifest and preserve each entry's exact ID, type, URL, and timestamps.
2. Fetch the selected version JSON and its parent metadata when inheritance is used.
3. Resolve the Java major from that version's `javaVersion.majorVersion`. For legacy metadata without this field, use the documented legacy fallback (Java 8) only where appropriate.
4. Select an Android runtime matching the app process ABI and required Java major. Never choose a runtime merely because it is installed.
5. Keep runtime verification separate from archive/layout detection. Verification must execute a Java probe on the selected runtime; native library presence alone is insufficient.

## Installer compatibility

The installer must resolve, validate, and install per-version:

- Inherited version metadata and launch arguments.
- OS/architecture library rules and artifact classifiers.
- Legacy and modern asset indexes, including legacy asset materialization where required.
- Client JAR, libraries, natives, logging configuration, and checksums.
- Version-specific main class and argument schemas.
- Loader-specific metadata and dependencies for supported mod loaders.

Unknown metadata fields and newer argument forms must not crash parsing. Unsupported required rules or missing artifacts must produce an actionable blocked state rather than a partially installed profile.

## Launch and rendering compatibility

The launch backend must construct the final classpath and JVM/game arguments from the resolved metadata, then execute the correct main class. Java success is not equivalent to game success.

The Android backend must provide a compatible window/surface lifecycle, input bridge, audio path, and LWJGL/OpenGL compatibility layer. Legacy LWJGL 2 and modern rendering paths may require different compatibility implementations. Renderer availability must be detected on the actual device; never advertise GL4ES, Zink, or another backend as usable without a real load and context test.

## Compatibility states

Each profile should expose one of these explicit states:

- **Available** — metadata is discoverable.
- **Installing** — artifacts are being resolved/downloaded/validated.
- **Installed** — required files are present and hashes/layout checks pass.
- **Runtime verified** — selected Java runtime successfully executes the probe.
- **Launch tested** — game process reaches a known successful startup milestone.
- **Playable** — the game reaches its main menu and basic input/rendering work.
- **Blocked** — a specific missing runtime, library, loader, renderer, device capability, or unsupported metadata requirement is shown.

Do not collapse these states into a single "installed" or "verified" boolean.

## Required test matrix

CI should cover representative fixtures from each metadata generation and channel:

- Legacy alpha and beta.
- Early release before modern argument metadata.
- Release with legacy asset handling.
- Snapshot using modern argument objects.
- Release requiring Java 8.
- Release requiring Java 17.
- Release requiring Java 21 or newer.
- A version with inherited metadata.
- Fabric/Quilt and Forge/NeoForge installer metadata where supported.

Host tests validate metadata resolution, rules, argument generation, and install plans. Android ARM64 device tests validate the real JRE, native loader, graphics context, input, and game startup. Emulator-only success is not a substitute for physical ARM64 validation.

## Current implementation boundary

The manifest model already represents `release`, `snapshot`, `old_beta`, and `old_alpha`, and version metadata can declare a Java major. The mobile runtime work is still a foundation: Java runtime probing and native loading do not yet prove that every Minecraft generation can launch.

The next engineering gates are:

1. Make version inheritance, modern/legacy argument parsing, library rules, and asset handling complete and fixture-tested.
2. Route every version through a Java-major/ABI-aware runtime selector.
3. Finish and verify the Android graphics/input/audio compatibility backend.
4. Run the version-channel test matrix on CI and representative real ARM64 devices.
5. Enable **Playable** only for versions that pass the main-menu and input smoke test.

No version family should be silently filtered out merely because it is old or marked snapshot/beta/alpha. Unsupported cases must be identified precisely and kept visible as blocked until implemented.
