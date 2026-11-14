# Flint renderer backend: research and implementation plan

Status: compatibility policy added; native renderer and Android game surface are not implemented yet.

## Goal

Build a Flint-owned Android graphics/runtime integration that can run Minecraft Java Edition, starting with vanilla 1.2.5 on ARM64. Existing launchers are technical references, not codebases to copy wholesale.

## Findings from established launchers

- PojavLauncher and its Amethyst successor use an OpenGL compatibility layer because Minecraft's desktop OpenGL calls do not map directly to Android GLES.
- GL4ES is a practical first renderer target for vanilla clients. Pojav's renderer documentation describes it as the broad-compatibility option, including vanilla versions.
- Legacy clients through Minecraft 1.12.2 use the LWJGL 2 API compatibility line. Current Pojav release notes describe moving from the older lwjglx path to lwjgl2-glfw.
- Newer clients use the LWJGL 3 / GLFW path.
- The renderer needs a real Android window surface, not only an EGL pbuffer. A pbuffer smoke test proves basic EGL/GLES availability but cannot prove a game can render to the device display.

Reference material:
- PojavLauncher: https://github.com/TeamPojavLauncher/PojavLauncher
- Amethyst-Android: https://github.com/AngelAuraMC/Amethyst-Android
- Renderer concepts: https://github.com/TeamPojavLauncher/teampojavlauncher.github.io/blob/main/markdown/wiki/faq/android/RENDERERS.md
- Pojav renderer setup example: https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/app_pojavlauncher/src/main/jni/egl_bridge.c
- Pojav JRE/render environment example: https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java

## Licensing boundary

Flint will implement its own Kotlin orchestration, Android surface lifecycle, JNI contracts, session state, and input routing.

Potential third-party components must be reviewed individually before inclusion:
- GL4ES: MIT; retain copyright and license notices.
- LWJGL2-GLFW: BSD-3-Clause; retain notices and license text.
- GLFW: zlib license; retain notices.
- PojavLauncher / Amethyst code: copyleft terms apply to the relevant project revisions. Do not transplant their source files into Flint without an explicit license-compatibility decision and all required source/notice obligations.
- MobileGlues and Mesa-based components have separate licenses and are not part of the first target.

No renderer binary is considered integrated merely because its name appears in a setting or compatibility plan.

## Flint-owned implementation stages

1. **Version compatibility policy (implemented):** select the legacy LWJGL2 line for releases before 1.13 and LWJGL3 for 1.13+. Select GL4ES as the first vanilla renderer target. This is a plan only, not a device availability claim.
2. **Renderer artifact management:** package or fetch ABI-specific renderer libraries from a pinned, auditable source; verify SHA-256, ELF ABI, and required symbols before activation.
3. **Android window bridge:** own a Surface lifecycle using Android NDK `ANativeWindow`; create and destroy EGL display/context/window surface in response to lifecycle events; handle resize and context loss.
4. **LWJGL bridge:** implement the legacy LWJGL2 entry points required by Minecraft 1.2.5 and connect them to the renderer/window/input layer. Keep the modern LWJGL3 path separate.
5. **Game session lifecycle:** move game execution off the UI thread, report Started only after a real session is active, capture stdout/stderr and exit status, and implement safe stop/recovery.
6. **Input:** translate touch, keyboard, mouse, and controller events to the selected LWJGL API.
7. **Device proof:** run on physical ARM64 hardware, record GPU/EGL/renderer versions, and reach the Minecraft main menu before claiming support.

## Current blockers

- `:native` currently provides JNI smoke tests, EGL pbuffer diagnostics, and JRE loading. It does not expose a window-surface renderer bridge.
- `PojavStyleGameLauncher` invokes `JLI_Launch` synchronously and reports an exit after the Java main method returns. It does not own a persistent game window/session.
- No GL4ES shared library or LWJGL2 compatibility natives are currently packaged by Flint.
- `HomeViewModel` intentionally stops after Java verification.

## Acceptance criteria for Minecraft 1.2.5

- ARM64 GL4ES library is present, hash-verified, loaded, and initialized against an Android window surface.
- LWJGL2 compatibility implementation and required natives are available to the game classloader.
- Launch arguments and assets are complete; offline and Microsoft account modes are labelled correctly.
- Minecraft reaches the main menu, renders frames, accepts touch/keyboard input, and exits without leaving a stuck JVM.
- CI build success is not substituted for physical-device runtime proof.
