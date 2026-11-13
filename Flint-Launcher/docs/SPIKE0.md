# Spike 0 / 0.2 — Real Android Minecraft Java Runtime

## Goal

Load a mobile OpenJDK on ARM64, start a JVM via `JLI_Launch`, pass EGL smoke, then boot Minecraft with an offline account into a singleplayer world.

## Artifact policy (important)

The historical source of mobile JREs used by PojavLauncher is:

- https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch  
  **Archived 19 May 2025.** Builds were published as **GitHub Actions artifacts** (`jre17-pojav`, `jre21-pojav`, `jre8-pojav`), not permanent release files with stable public SHA-256.

Therefore Flint **does not** ship invented URLs or checksums.

### How to install a JRE into Flint

1. Obtain a trusted **aarch64** mobile OpenJDK tarball (`.tar.gz`) from a build you control or a successor project.
2. Call `MobileJreManager.importLocalArchive(file, id="jre17-arm64", major=17, expectedSha256=optional)`.
3. `SafeTarGzExtractor` unpacks with path-traversal rejection, symlink skip, size caps.
4. `probe()` requires `libjli.so` + `libjvm.so` before marking `verified=true`.

License: OpenJDK **GPLv2 + Classpath Exception**. Only redistribute binaries you are allowed to redistribute.

## What the code does now

| Piece | Status |
|-------|--------|
| Safe tar.gz extraction | Implemented (`SafeTarGzExtractor`) |
| Local JRE import + probe | Implemented |
| Remote install with fake URL | **Removed** — returns explicit error |
| JNI smoke | Implemented |
| EGL pbuffer smoke | Implemented |
| `dlopen` libjli + resolve `JLI_Launch` | Implemented |
| `nativeLaunchJvm` full argv → `JLI_Launch` | Implemented in source |
| GL4ES / MobileGlues | **Not integrated** |
| LWJGL Android natives | **Not integrated** |
| Minecraft title screen / world | **NOT VERIFIED** (no device, no JRE binary in CI) |

## Success criteria (device)

- [ ] `RuntimeSmoke`: jni + egl OK  
- [ ] `importLocalArchive` → `verified=true`  
- [ ] `nativeLoadJre` true  
- [ ] Minimal Java main exits 0 via `JLI_Launch`  
- [ ] Minecraft main class → title screen  
- [ ] Offline account → singleplayer world enter  

Until the last two boxes are checked on hardware (target: itel A665L, Android 13, ARM64), **do not claim READY_TO_PLAY for gameplay**.
