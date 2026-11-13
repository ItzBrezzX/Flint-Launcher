# :runtime

Java runtime management, renderer selection, game process lifecycle, launch readiness.

## Spike 0 status

| Component | State |
|-----------|--------|
| `LaunchReadinessResolver` | Implemented |
| `MobileJreManager` | Implemented (download + SHA-256 + probe). Artifact URLs are placeholders. |
| `PojavStyleGameLauncher` | Implemented; fails precisely until JRE + full JLI path exist |
| `RuntimeSmoke` | Implemented |
| Native `flint_native` | JNI + EGL smoke + JRE dlopen skeleton |
| Real Minecraft main menu | **NOT VERIFIED** — requires device + real JRE + completed JLI path |

See [docs/SPIKE0.md](../docs/SPIKE0.md).
