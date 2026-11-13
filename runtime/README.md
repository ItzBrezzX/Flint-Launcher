# :runtime

Java runtime management, renderer selection, game process lifecycle, and launch readiness.

## Flint-owned runtime status

| Component | State |
|-----------|-------|
| `LaunchReadinessResolver` | Implemented |
| `MobileJreManager` | Download, archive verification, extraction, metadata probe, and native loader verification implemented |
| Native JRE loader | Loads `libjvm.so` and `libjli.so`, resolves `JLI_Launch`, and reports linker errors |
| `RuntimeSmoke` | JNI, EGL/GLES2, GPU strings, and JRE loader diagnostics |
| Java class execution probe | Not implemented |
| LWJGL2 / Minecraft 1.2.5 compatibility | Not implemented |
| Android game surface and input bridge | Not implemented |
| Minecraft main menu | **NOT VERIFIED** |

A successful native JRE loader check is not a Java execution test and is not a playable-game readiness signal. The HotSpot libraries are process-lifetime resources; changing runtime requires restarting Flint.

Flint's engine is being implemented independently. Other launchers are architectural references only; no Amethyst application or backend source is vendored here.
