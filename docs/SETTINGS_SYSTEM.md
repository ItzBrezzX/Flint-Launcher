# Flint Settings System

## Purpose

This document records the settings taxonomy and the implementation boundary for Flint's Android launcher. The UI is informed by public design patterns in MojoLauncher, PojavLauncher and ZalithLauncher2. Flint keeps its own Kotlin/Compose model and does not copy their source files or native binaries.

## Settings areas

| Area | Flint setting surface | Required backend before it can affect a running game |
|---|---|---|
| Overview | Device ABI, Android version, memory profile, runtime/GPU diagnostics, launcher behavior | Device diagnostics are available; launch-session behavior is pending |
| Java | Installed mobile JREs, ABI compatibility, automatic Java selection, heap budget, additional JVM arguments | Runtime catalog/installation and probe exist; heap and custom JVM arguments must still be consumed by the final launch request |
| Graphics | Renderer choice, graphics API, Vulkan driver, resolution scale/custom resolution, fullscreen, cutout, VSync and sustained performance | Renderer registry, native renderer libraries, surface lifecycle and swapchain are pending |
| Controls | Touch overlay, button scale/opacity, gesture timing, mouse sensitivity, gamepad deadzone/remapping and gyroscope | Flint control editor/model exists; session overlay and native/game input bridge are pending |
| Game | Per-version defaults, session log behavior and game-specific display/input defaults | Per-version settings storage and a live game-session controller are pending |
| Advanced | GPU workarounds, CPU affinity, OpenAL backend, shader diagnostics and native compatibility layers | Only expose a switch when the target ABI/GPU/backend is detected and the implementation has a test |

## Reference implementation findings

### MojoLauncher

The inspected v3_openjdk tree groups preferences into Video, Control, Java, Miscellaneous and Experimental screens. Its Java page exposes runtime management, JVM arguments and RAM allocation. Its video page discovers renderer IDs from a renderer cache and conditionally reveals options such as alternate surfaces, VSync, ANGLE, driver management, resolution scaling and sustained performance. Its controls page covers touch gestures, button geometry/opacity, mouse behavior, gamepad deadzones and gyro tuning. The experimental page hides Adreno-specific workarounds on unsupported hardware.

### PojavLauncher

The archived PojavLauncher codebase uses the same broad preference split and device-aware visibility approach. Its published release notes also document profiles with different renderers, Java runtimes and game directories, plus alternate surface rendering, virtual mouse defaults and sustained performance options. Flint should adopt the separation and capability gating, not assume those native features exist in Flint.

### ZalithLauncher2

The inspected main branch uses typed setting registries and separate Game, Launcher, Renderer, Control and Misc settings screens. Renderer selection is discovered dynamically; renderers may expose their own config schema, and Vulkan drivers are managed separately. Its graphics surface includes graphics API selection, percentage or custom resolution, fullscreen, sustained performance, refresh-rate requests and shader diagnostics. Its input stack has a dedicated control layout module, event bus, mouse/gamepad mapping and gyroscope configuration.

## Persistence and effective behavior

- Flint stores global preferences in the app-private SharedPreferences file named flint_launcher_settings.
- Integer values are clamped at the ViewModel boundary. JVM argument text is length-limited and remains user-authored input.
- These preferences are global defaults, not per-instance overrides.
- Saving a preference is not proof that Minecraft consumes it. The current build does not yet provide a complete asynchronous game process, renderer, window/surface lifecycle or input bridge.
- The Java runtime inventory/install/probe is the only settings area with an existing native/runtime implementation. The renderer, display, controls and experimental categories must remain explicitly unavailable or marked as saved defaults until their consumers are implemented and verified.
- Do not expose toggles for GPU-specific workarounds, Vulkan drivers, shader dumping, CPU affinity or audio backends until capability checks and native implementations exist.

## Licensing and implementation boundary

The reviewed repositories identify different copyleft licenses. This research uses their public feature organization and architectural concepts only. Do not copy code, assets, renderer binaries, configuration defaults or transitive native components into Flint without a separate provenance, compatibility and license review. Any future dependency must be recorded with version, origin, license, ABI coverage and required notices.
