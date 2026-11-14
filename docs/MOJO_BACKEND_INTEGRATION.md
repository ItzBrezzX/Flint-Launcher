# MojoLauncher engine integration plan

## Selected upstream snapshot

- Repository: https://github.com/MojoLauncher/MojoLauncher
- Branch: `v3_openjdk`
- Commit: `1b8d90424c1cb791321d45f60ac62c54f4b013fa`
- Root licence: LGPL-3.0
- Snapshot inspected: 14 Nov 2026

This is the strongest inspected reference for Flint's complete Java Edition target: classic/alpha/beta through current releases and 26.x snapshots, plus Forge and Fabric. Current upstream release notes describe 26.2+ support, an LWJGL2-on-GLFW compatibility path, and a GLFW backend for LWJGL3.

## Why this engine

The selected upstream has both rendering/input API generations and an Android game lifecycle:

- `GameActivity` and `GameService` run in Android process `:game`, separate from the launcher process.
- `GameView` and its Surface providers own the Android window/surface lifecycle.
- `GameRunner` selects Java from version metadata, constructs classpath/game arguments and performs renderer compatibility checks.
- `JavaRunner` and native `jre_launcher` use `JNI_CreateJavaVM`, load-stage diagnostics, class-loader hooks and signal/exit handling. This is not the same as Flint's current experimental `JLI_Launch` call in the launcher process.
- `GameRenderer` selects renderer implementations and checks device capabilities.
- The native build includes GLFW, SDL, mojoexec, JVM runtime glue and AWT support.

Upstream compatibility claims do not automatically transfer to Flint or the itel A665L. Flint must validate the port and device separately.

## Pinned native submodules

These gitlink commits are recorded in the selected MojoLauncher snapshot:

| Component | Upstream | Pinned commit | Role |
|---|---|---|---|
| GLFW | https://github.com/MojoLauncher/glfw | `7a6c6ceb51f20cd0db84dec2914ac1a0237051f6` | Android GLFW/window/input |
| mojoexec | https://github.com/MojoLauncher/mojoexec | `9ec4b836e93900135bad823eaac16ebc81cf6b98` | Android linker namespace and native loading helpers |
| SDL | https://github.com/MojoLauncher/MojoSDL | `c63845bd49c5a7667d1de0f6134f85ed1ccc8889` | Alternate window/input/audio backend |
| NoBlocklist | https://github.com/MojoLauncher/NoBlocklist | `f4209d4755d6af6a19fedbe578313e29ebfc45ff` | Upstream runtime helper |

Keep these pins fixed during the first port. Do not silently follow upstream branch heads.

## Android architecture rule tokens

The Android architecture token must match the launcher's metadata rule convention, not blindly copy Java's `os.arch` property. MojoLauncher’s `Architecture.archAsString()` returns `arm64`, `arm`, `x86_64` or `x86`, and its metadata OS rule compares against that token exactly. Flint’s `Environment.android()` therefore normalizes `arm64-v8a` to `arm64` (not `aarch64`) before evaluating library rules. Reference: https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/java/git/artdeell/mojo/Architecture.java and https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/java/git/artdeell/mojo/value/MoJsonRule.java.

## Flint integration boundary

Retain Flint's Compose launcher, version catalogue, installer, profiles, accounts and Modrinth marketplace. Extract the engine behind a Flint-owned `:game-engine` or equivalent runtime module:

1. **Runtime selection:** use final merged version metadata and exact app-process ABI. Select mobile Java 8, 17, 21 or 25 where an Android runtime exists for that ABI.
2. **LWJGL selection:** inspect resolved library coordinates. Route `org.lwjgl.lwjgl:lwjgl` to LWJGL2 compatibility and `org.lwjgl:lwjgl` to LWJGL3. Do not infer the backend only from a version-ID string.
3. **Launch adapter:** translate Flint's installed profile, account, classpath, natives and arguments into the engine's launch request. Remove incompatible desktop LWJGL binaries and supply the matching Android-compatible Java/native implementation.
4. **Isolated game process:** run the game Activity, JVM and game service in `:game`. Never load the process-global HotSpot VM from Flint's launcher UI process.
5. **Native build:** import the pinned GLFW, mojoexec and SDL submodules in an isolated native-engine directory. Adapt CMake/NDK versions for Flint; verify ELF machine, SONAME, JNI exports and transitive dependencies for every packaged ABI.
6. **Surface and input:** port SurfaceView/TextureView lifecycle, EGL window surface/context, touch, keyboard, mouse, controller, resize, pause/resume, context loss and audio.
7. **Renderer capability:** keep renderer checks device-aware. GL4ES is not a universal fallback for every modern version. Activate a renderer only after its ABI library loads and creates a working context against a real Android window.
8. **Session and diagnostics:** expose truthful Preparing/Starting/Running/Exited/Crashed states, capture JVM/game logs and exit reasons, implement stop/recovery, and wire those logs into Flint's existing share flow.

Do not copy the entire `:app_mojo` application module. It has its own application ID, Application class, Activities, services, accounts, instances, preferences, resources, installation logic and app-specific dependencies. Reuse engine portions only through a reviewed boundary.

## Licence and redistribution gate

The upstream launcher identifies LGPL-3.0. Its README separately lists components with different licences, including GLFW (zlib), SDL (zlib), mojoexec (MIT), LWJGL2-GLFW (BSD-3-Clause), LWJGL3 (BSD-3-Clause), and OpenJDK (GPLv2 with Classpath Exception). Renderer and optional components have additional terms.

Flint currently has no root `LICENSE` file. Before copying or shipping any source/binary:

- agree and record Flint's licence strategy with the project owner;
- preserve exact notices and licence texts for every selected component;
- meet LGPL corresponding-source and relinking requirements where applicable;
- exclude optional components with unresolved or incompatible terms;
- pin every component and keep a reproducible source/notice inventory.

The upstream root licence does not automatically apply to all dependencies.

## Acceptance and test matrix

### Host/CI

- Build every native component for the ABIs Flint actually packages.
- Verify JNI exports, ELF machine, SONAME and transitive dependencies.
- Test Java-major and LWJGL-line resolution using real version JSON fixtures.
- Cover classic/Indev/Infdev metadata where available, alpha, beta, early releases, pre-1.13, 1.13+, date-based snapshots, 26.x and inherited loader profiles.
- Test native replacement, legacy assets, old string arguments, modern argument objects, and blocked/failure states.

### Physical target

Target device: itel A665L, Android 13, ARM64.

A version family becomes **Playable** only after a real-device test reaches the Minecraft main menu, renders frames, accepts input and exits cleanly. Record Java major, LWJGL line, renderer, GPU/EGL, APK SHA-256, version and game log.

Representative first matrix:

- Legacy alpha/beta and 1.2.5 (LWJGL2).
- A late pre-1.13 release (LWJGL2).
- A 1.13+ release (LWJGL3).
- A 1.21.x release (modern LWJGL3).
- A 26.x release or snapshot (Java 25 / LWJGL3).
- Fabric and Forge profiles once their install paths are integrated.

Passing one version does not prove all versions. Expand evidence across metadata generations and loader families.

## Current Flint status

Flint is **not yet launching Minecraft**. The Play path still deliberately stops after Java preparation. Flint currently builds GL4ES and has EGL pbuffer diagnostics, but does not yet contain the Mojo game process, complete Android GLFW/LWJGL2/LWJGL3 paths, renderer-window integration, or persistent input/audio session lifecycle.

The next implementation milestone must port a coherent all-version runtime foundation, not add another isolated 1.2.5 special case.
