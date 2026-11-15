# Flint checklist: honest status (15 Nov 2026)

Legend: **Done*** = written, unit-tested at most, never compiled or run on a device. **Partial** = some pieces exist.
**None** = not started. **Blocked** = needs binaries or a device that this repository/sandbox does not have.

Nothing here was compiled: the environment had no Kotlin compiler, Gradle, Android SDK, NDK, network or device.

| Area | Item | Status |
|---|---|---|
| A Graphics | GL4ES built as libGL.so, loaded RTLD_GLOBAL, initialised on an EGL context (`GL4ESSurfaceView`) | Done* |
| A | Android Surface/EGL bridge, EGL smoke test | Done* |
| A | GLFW (window, event loop, input callbacks) on Android | **New**: own libglfw.so, host-tested (`native/src/test/c`); never run on a device |
| A | LWJGL 3 Android integration | **New**: desktop LWJGL swapped for an Android build made in CI (`LwjglAndroid`), unverified |
| A | LWJGL 2 (1.12.2 and older) | None: refused with a message |
| A | GPU selection, fallback, context recovery, compatibility tests | None |
| B Runtime | Java 8/17/21/25 arm64 catalogue, pinned SHA-256, safe extraction, libjli/libjvm probe | Done* (URLs/hashes unverified here) |
| B | In-process JVM via JLI_Launch on its own thread | Done* |
| B | JVM shutdown/restart, crash recovery, stop button | None (an in-process JVM cannot be restarted; needs a separate process) |
| C Pipeline | Metadata, libraries, asset index and objects, client jar, natives, legacy assets | Done* (tested against a fake Mojang server) |
| C | Classpath, JVM args, game args, main class | Done* |
| C | Environment variables (JAVA_HOME, LD_LIBRARY_PATH, HOME, TMPDIR, LIBGL_*) | **New**, Done* |
| C | Session management, exit-code handling, error reporting | Partial (exit code is logged, no UI) |
| D Versions | Release, snapshot, beta, alpha listed, installed and launch-argument built | Done* |
| D | Anything actually playable | Blocked on A (LWJGL/GLFW) |
| E Input | Touch control models, editor, overlay | Done* |
| E | Touch/keyboard bridge to Minecraft (fixed layout: stick, look, buttons, soft keyboard) | **New**, unverified. Mouse capture, gamepad: None |
| F Audio | OpenAL / Android audio backend | None |
| G Accounts | Offline accounts | Done* |
| G | Microsoft chain (Xbox, XSTS, Minecraft services), refresh, Keystore vault | Done* |
| G | **Device-code sign-in UI, token refresh and session handed to the game** | **New**, Done*. Needs an Azure client ID, see MICROSOFT_LOGIN.md |
| H Mods | Modrinth browser and mod installer | Done* |
| H | Fabric/Quilt metadata only; Forge, NeoForge, shaders, resource packs, modpacks, conflict checks | None / Partial |
| I Files | Log viewer, world backup, storage migration, per-version directories, import/export | Partial (profile import/export domain code only) |
| J UI | PLAY calls the launch coordinator and shows a message | Partial |
| J | Game session screen (`FlintGameActivity`, own process), landscape, immersive, error + Share log | **New**, unverified. Crash screen: None |
| K Build | Multi-ABI filters, workflows | Written, never run |
| K | Device install, launch, gameplay, stability, profiling, release AAB | None |

See [GAME_PIPELINE.md](GAME_PIPELINE.md) for the architecture, the version support table and the test loop.
