# Open-source launcher engineering research

This document records architectural lessons used to design Flint's independent implementation. It is not a source-code import plan.

## Public references reviewed

- [PojavLauncher (TeamPojavLauncher)](https://github.com/TeamPojavLauncher/PojavLauncher) — Android Java runtime packaging, native loading, LWJGL 2 compatibility for older Minecraft, GLFW stubs for newer versions, input and audio bridges.
- [Amethyst Android](https://github.com/AngelAuraMC/Amethyst-Android) — continued support for historical versions through modern snapshots, multi-major Android Java runtimes, and separate rendering/runtime concerns.
- [ZalithLauncher](https://github.com/ZalithLauncher/ZalithLauncher2) — a maintained Android launcher implementation and a useful reference for current launch/install workflows.
- [FlameLauncher Android](https://github.com/FlameLaunchers/FlameLauncher-Android) — explicit dependency/license accounting and separation of launch flow, native bridges, and renderer integrations.

These projects are useful technical references, not a guarantee that their code or binary dependencies can be copied into Flint.

## Findings applied to Flint

### Version metadata is a graph, not a single JSON file

Loader profiles can inherit from another profile which itself inherits from vanilla. Flint now resolves the full inheritance chain recursively, merges from the vanilla root toward the selected child, detects cycles, and enforces a maximum depth. The vanilla root remains the source of the client JAR.

### Runtime selection is per version

The Java major is derived from the selected version metadata, with a legacy fallback only when metadata omits the field. Runtime architecture must match the running Android process ABI. A runtime is not considered verified because its directory or shared libraries exist; the selected JVM must execute a probe class.

### Rendering is a compatibility subsystem

Older Minecraft versions may use LWJGL 2-era APIs, while newer versions use LWJGL 3 / GLFW-era APIs and newer graphics requirements. Flint must own a backend interface and choose a tested compatibility path by version and device capabilities. A successful JVM launch must not be confused with a successful graphics context or playable game.

### Input and audio are separate native boundaries

Touch, keyboard, mouse, controller, audio, Android surface lifecycle, and native event delivery need explicit interfaces and device-level tests. They must not be hidden inside the version installer or reported as ready merely because the JVM loaded.

## Flint implementation policy

- Implement Flint-owned Kotlin/JNI interfaces and orchestration.
- Study upstream behavior and public documentation; do not paste upstream source into Flint.
- Before considering any code or binary dependency, record its exact origin, version, license, required notices, and compatibility impact.
- Keep components with incompatible licenses isolated or replace them with independently implemented alternatives.
- Test legacy, modern, snapshot, and loader metadata using fixtures; test ARM64 JVM and renderer behavior on real Android hardware.
- Show a precise blocked reason for unsupported combinations instead of claiming universal support prematurely.

## Current applied code

- `MinecraftInstaller.resolveVersion` recursively resolves multi-level `inheritsFrom` metadata.
- The installer regression suite now includes a three-level vanilla → loader → loader-overlay chain.
- The universal version compatibility contract is documented in `docs/UNIVERSAL_VERSION_COMPATIBILITY.md`.

## References and license caution

PojavLauncher and Amethyst identify GPL/LGPL components and third-party native dependencies. FlameLauncher documents that combining copyleft components can change the distribution obligations of the resulting work. Flint must review the exact license of every candidate component and its transitive dependencies before reuse. Public availability is not permission to copy code without satisfying the license.
