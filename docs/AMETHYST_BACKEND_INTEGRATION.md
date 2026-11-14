# Amethyst backend integration audit

Status: architecture and licensing audit only. No Amethyst source, binaries, submodules, or assets are vendored by this change.

## Pinned upstream snapshot

- Project: [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android)
- Branch inspected: `v3_openjdk`
- Snapshot inspected: `166af615dbdca1a507b8c2d379822cca02795132`
- Upstream project license: GNU LGPL-3.0 (see upstream `LICENSE`).

The snapshot is a complete Android launcher, not a standalone runtime AAR. Its documented build is `:app_pojavlauncher:assembleDebug` and it expects a recursive clone for submodules.

## Relevant backend pieces

| Area | Upstream location | Flint integration concern |
| --- | --- | --- |
| JVM entry point | `app_pojavlauncher/src/main/jni/jre_launcher.c` | Native JLI launch path, signal handling, and JNI lifecycle need a dedicated adapter. The inspected entry point calls `JLI_Launch`; it is not evidence of a `JNI_CreateJavaVM` integration. Do not reuse Flint's current experimental implementation as if it were equivalent. |
| Runtime setup | `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java` | Loads the JRE's dependent shared libraries and configures runtime environment/library paths. |
| Android game surface | `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MinecraftGLSurface.java` | Surface lifecycle, SDL setup, input dispatch, and GLFW bridge are coupled to upstream app classes. |
| Native build | `app_pojavlauncher/src/main/jni/Android.mk` | Uses ndk-build and several native components; Flint currently uses a smaller CMake module. |
| LWJGL/GLFW | `jre_lwjgl3glfw/` | Separate custom build outputs; not provided as a normal Maven dependency. |

## Why the upstream application module cannot be dropped into Flint

- Upstream `:app_pojavlauncher` is an Android application module with its own application ID, manifest, activities, providers, services, resources, and process declarations.
- It uses AGP 9.3.1 and compileSdk 37 in the inspected snapshot. Flint currently uses AGP 8.7.3 and compileSdk 35.
- Upstream native compilation uses ndk-build; Flint's `:native` module uses CMake.
- The upstream project has required Git submodules: MobileGlues, SDL, sdl2-compat, MioLibPatcher, and androidnsbypass.
- The upstream activity and surface classes are coupled to many Pojav/Amethyst application classes. Copying only `jre_launcher.c` or `MinecraftGLSurface.java` would not provide a working backend.

## Licensing and redistribution gate

The upstream root project declares LGPL-3.0. That does not automatically clear every bundled or referenced component for redistribution in Flint.

The inspected upstream README identifies components with materially different or unresolved terms, including:
- OpenJDK: GPLv2 (runtime distribution and corresponding-source obligations must be handled separately).
- OpenAL-Soft: GPLv2 in the inspected dependency list.
- ANGLE: described upstream as “All Rights Reserved”.
- LWJGLX: upstream lists its license as unknown.
- Boardwalk: upstream lists its license as unknown / Apache-2.0 or GPLv2.
- Other submodules and native libraries each require their own license and notice review.

Do not bundle or vendor a component whose redistribution terms have not been verified. Before shipping a combined APK, add the required notices and license texts, preserve source attribution, and provide the applicable corresponding source/relinking path for LGPL-covered components. This document is an engineering audit, not legal advice.

## Flint integration boundary

Keep Flint's Compose UI, accounts, profiles, version metadata, download/install pipeline, and Modrinth marketplace independent from the game runtime.

Introduce a backend contract behind `:runtime` rather than importing upstream launcher UI. The inspected upstream snapshot uses `JLI_Launch` plus runtime library/environment setup; confirm the exact bootstrap sequence against the pinned native and Java sources before implementation:
1. Runtime preparation: validate/extract a supported mobile JRE and its architecture.
2. Native bootstrap: load the required JRE and backend libraries in a controlled order.
3. Game launch: pass the resolved classpath, JVM arguments, main class, and game arguments.
4. Surface/input: attach an Android game surface and route lifecycle, touch, keyboard, and controller events.
5. Shutdown/diagnostics: collect process exit, native crash, JVM log, and cleanup state.

## Acceptance gates

- [ ] Confirm exact license and redistribution terms for every source, submodule, native library, and runtime artifact selected.
- [ ] Build the isolated backend for `arm64-v8a` without replacing Flint's app module or application ID.
- [ ] Verify Java 8 ARM64 runtime loading on an Android ARM64 target.
- [ ] Verify a minimal JVM entry-point test before invoking Minecraft.
- [ ] Verify the Android surface, OpenGL context, and input bridge.
- [ ] Launch Minecraft 1.2.5 through the real game pipeline and capture logs through the main menu.
- [ ] Run the existing Flint Android CI and install/launch smoke tests after integration.

A successful APK build or Activity startup is not evidence that Minecraft itself has launched. The Minecraft 1.2.5 main-menu gate remains unverified until it is observed on a device.
