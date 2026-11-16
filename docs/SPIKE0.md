# Spike 0 / 0.2 — Real Android Minecraft Java Runtime

## Goal

Load a compatible mobile OpenJDK on ARM64, start a JVM via `JLI_Launch`, attach a real Android Surface/EGL context, initialize GL4ES and Flint's GLFW C ABI, then boot Minecraft with an offline account into a single-player world.

## Artifact policy (important)

The historical source of mobile JREs used by PojavLauncher is:

- https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch  
  **Archived 19 May 2025.** Builds were published as **GitHub Actions artifacts** (`jre17-pojav`, `jre21-pojav`, `jre8-pojav`), not permanent release files with stable public SHA-256.

Therefore Flint does not invent JRE URLs or checksums.

### How to install a JRE into Flint

1. Obtain a trusted aarch64 mobile OpenJDK archive (`.tar.gz`) from a build you control or a maintained successor project.
2. Import it through `MobileJreManager.importLocalArchive(file, id, major, expectedSha256)`.
3. `SafeTarGzExtractor` unpacks with path-traversal rejection, symlink handling and size caps.
4. The probe requires the expected Java runtime libraries before marking the runtime verified.

License: OpenJDK GPLv2 + Classpath Exception. Only redistribute binaries you are allowed to redistribute.

## Current source status

| Piece | Status |
|-------|--------|
| Safe tar.gz extraction | Implemented |
| Local JRE import + SHA-256 verification + probe | Implemented |
| Remote install when no trusted artifact URL/checksum exists | Explicitly unavailable |
| JNI smoke and EGL pbuffer smoke | Implemented |
| ANativeWindow-backed EGL window on game thread | Implemented in source |
| GL4ES initialization on the current game EGL context | Implemented in source |
| Flint GLFW C ABI attach/init + input FIFO | Implemented in source |
| Android LWJGL 3.3.3 core/OpenGL native build | Integrated in native CMake |
| Full argv → `JLI_Launch` call on the EGL owner thread | Implemented in source |
| PLAY → Surface-backed `GameActivity` | Implemented in source |
| Touch joystick, click/use/jump/sneak/inventory/pause bridge | Implemented in source |
| Graceful GLFW close request | Implemented in source; no force-kill guarantee |
| Minecraft title screen / single-player world | **NOT VERIFIED on device** |

## Device success criteria

- [ ] App opens to PLAY screen without a startup GPU probe.
- [ ] Install/import a trusted ARM64 JRE and confirm it verifies.
- [ ] Native JRE load succeeds.
- [ ] Production Android Surface creates EGL and initializes GL4ES.
- [ ] GLFW and LWJGL resolve the Android backend and create the game window.
- [ ] Minecraft reaches title screen.
- [ ] Offline account enters a single-player world.
- [ ] Joystick, camera drag, attack/use, jump, inventory and pause work.
- [ ] Audio works or fails safely without crashing.
- [ ] STOP returns to Flint and releases EGL on the owner thread.
- [ ] Background/foreground, surface recreation and repeated-launch behavior are stable.

Until title-screen and world-entry tests pass on hardware, do not claim READY TO PLAY.
