# How Minecraft gets onto the screen (and what is not proven yet)

Status date: 15 Nov 2026. **Nothing below has been compiled or run on a device by the author.**
What *was* run: the GLFW shim's host tests (`bash native/src/test/c/run.sh`, passing, with ASan/UBSan).

## Pieces

```
PLAY (launcher process)
  -> FlintGameActivity            separate process ":game"; owns the SurfaceView + touch controls
       |  surface appears
       v
  GlfwBridge.nativeSurfaceCreated -> libglfw.so (Flint's own GLFW, native/src/main/cpp/glfw)
       |  then
       v
  PlayLaunchCoordinator.launch    versions, libs, assets, natives, account/Microsoft token
       |  swaps Mojang's desktop LWJGL for LWJGL-for-Android (LwjglAndroid), sets JAVA_HOME / LD_LIBRARY_PATH
       v
  JLI_Launch (in-process JVM)  ->  Minecraft  ->  LWJGL  ->  glfw* calls land in libglfw.so
                                                    GL calls -> GL4ES (libGL.so) -> OpenGL ES
```

* **libglfw.so** is Flint's own clean-room implementation of the GLFW C API (about 120 functions). It owns the
  EGL context/surface, turns Android input into GLFW callbacks, and makes the cursor "grabbed" when Minecraft asks.
  No code from PojavLauncher is used. LWJGL finds it through `-Dorg.lwjgl.glfw.libname`.
* **LWJGL for Android** is built in CI (`build-lwjgl-android.yml`) from `PojavLauncherTeam/lwjgl3`
  (LWJGL 3.3.3, BSD-3-Clause, archived). Only its Java jars and `liblwjgl*.so` are used, plus an OpenAL Soft
  `libopenal.so` that its build script downloads (OpenAL Soft is LGPL: dynamically linked, but keep the notice).
* **GL4ES** (pinned commit in `native/src/main/cpp/CMakeLists.txt`) provides desktop OpenGL on top of OpenGL ES.
* The game runs in the `:game` process because Minecraft ends with `System.exit()`.

## Fixed along the way (verified by reading, not by running)

1. Native libraries were not extracted (`minSdk 26` default), so `nativeLibraryDir/libGL.so` never existed and PLAY
   always reported a missing renderer. Now `useLegacyPackaging = true` + `extractNativeLibs="true"`.
2. The JVM had no `JAVA_HOME` / `LD_LIBRARY_PATH` / `HOME` / `TMPDIR` / `LIBGL_*` (`JvmEnvironment`).
3. JVM stdout/stderr went to `/dev/null`; now written to `Android/data/<app>/files/logs/last-game.log`.
4. Microsoft accounts could not launch at all; see `MICROSOFT_LOGIN.md`.

## What to try first, and what it can and cannot do

**Target: Minecraft 1.13 to 1.16.5** (Java 8 runtime, LWJGL 3, OpenGL 2.1 class).

| Version range | Expected | Why |
|---|---|---|
| 1.13 - 1.16.5 | The path this build is aimed at | LWJGL 3 + GL 2.1 |
| 1.17 and newer | Will most likely fail at context creation | They require OpenGL 3.2 core; GL4ES reports 2.1. Needs a different renderer (not in this repo) |
| 1.12.2 and older, Beta, Alpha, Classic | Refused with a clear message | They use LWJGL 2; the compatibility layer is not built yet (`lwjglx` jar is picked up automatically *if* the CI build produces one, unverified) |
| Snapshots | Same as the release they belong to | |

So "every version playable" is **not** true yet and this build does not claim it.

## The test loop (you run it, you send me the result)

1. Repository variable `FLINT_MS_CLIENT_ID` (only for Microsoft accounts; offline works without it).
2. Run **Build APK**. Order: GLFW host tests, then LWJGL (4 ABIs), then the APK. If the LWJGL job fails, the
   launcher still builds but cannot start Minecraft; send me that job's log.
3. Install the APK, add an offline account, install **1.16.5**, press PLAY.
4. On failure the game screen shows the reason and a **Share log** button. Send that text. Also useful:
   `adb logcat -s FlintGLFW FlintNative` if you have a PC.

## Milestones

| # | Milestone | Proof |
|---|---|---|
| M1 | LWJGL job green, APK contains `liblwjgl*.so`, `libglfw.so`, `libGL.so` | Actions run |
| M2 | JVM starts and Minecraft prints its log | `last-game.log` has "Setting user:" |
| M3 | `glfwCreateWindow` returns, GL4ES initialises | log shows `FlintGLFW` lines, frame counter > 0 |
| M4 | Title screen visible | screenshot |
| M5 | Touch: tap buttons, create world, walk, look, break, place | video |
| M6 | Sound (OpenAL), 10-minute session stable | log |

## Known gaps

Controller/gamepad, external-mouse pointer capture, key-binding editor integration with the existing Touch Controls
Editor (this screen uses a fixed layout), JVM stop/restart without killing the process, crash screen, audio device
selection, Forge/NeoForge, GL 3.2 renderer for 1.17+, LWJGL 2 layer for old versions.
