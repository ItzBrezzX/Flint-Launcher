# Mojo source comparison and Flint porting decisions

Audited against the supplied source archives:

- `mojoexec-master.zip`
- `NativesPlugin-master.zip`
- `glfw-glfw34.zip`
- `MojoLauncher-3_openjdk.zip`

## Reusable behavior ported

Mojo's Android GLFW implementation reconfigures the reported client API when it creates an EGL/GLES context. Flint's production EGL path currently creates an OpenGL ES 2 context for GL4ES, but its GLFW implementation previously reported `GLFW_OPENGL_API`. That metadata was inconsistent with the actual context.

Flint now reports:

- `GLFW_CLIENT_API = GLFW_OPENGL_ES_API`
- context version `2.0`

This is a targeted behavior port. It does not import Mojo's entire GLFW platform or renderer stack.

## Components not copied wholesale

### MojoExec

MojoExec provides EGL/Vulkan handle management, renderer configuration, linker-namespace helpers and CPU-affinity utilities. Mojo's Android GLFW fork calls these APIs directly. Importing it alone would not provide a working renderer: Flint would also need MojoExec's native target, JNI bridge, EGL lifecycle integration and matching GLFW hooks.

### Android GLFW fork

The supplied GLFW fork contains a more complete Android platform implementation, including Android-specific window, monitor, input and EGL paths. Its Android build is coupled to MojoExec and its Java JNI bridge. Flint already has its own Activity-owned Surface, input queue and EGL/JLI lifecycle. Replacing only selected GLFW C files would mix incompatible ownership and event models.

The current Flint GLFW header declares the 117 functions referenced by the LWJGL 3.3.3 Java binding. The existing APK verification script checks the exported ELF symbols. The remaining compatibility work is behavioral and lifecycle testing, not simply adding missing function names.

### NativesPlugin

The supplied archive is a plugin/app wrapper and does not provide a ready-to-integrate Android native renderer or LWJGL backend. No native library was imported from it.

### MojoLauncher renderer/runtime

The source includes renderer selection and a custom JNI/JVM launch architecture. Its OpenLTW path expects `libltw.so` and a GLES 3 context. The supplied archives do not contain `libltw.so`; therefore Flint cannot safely enable OpenLTW from these sources alone. Flint keeps its existing GL4ES path for now.

Mojo's Java runner and native JVM launcher also rely on a larger set of class-loader, native-loader, EGL and process hooks. Copying only its `JNI_CreateJavaVM` or Java runner fragments would leave those dependencies unresolved.

## LWJGL version compatibility

Flint currently builds its Android JNI core/OpenGL libraries from the LWJGL version pinned in `native/src/main/cpp/flint_lwjgl_android.cmake` (currently 3.3.3). Minecraft version metadata can declare a different LWJGL release. Java bindings and native JNI libraries must be kept on the same version.

The launcher now reads the exact native build version from `libflint_native.so` and compares it with the LWJGL version resolved from the installed Minecraft metadata. A mismatch is rejected before JLI starts, with an actionable error, instead of being allowed to fail later in native code.

Mojo's `unilwjgl3-builder` project and substitutions catalog are useful references for a future version-specific Android artifact pipeline. They are not a drop-in replacement for Flint's current native build. The supplied catalog does not establish a compatible artifact for every LWJGL version supported by Minecraft.

## License and integration notes

The supplied MojoExec source is MIT-licensed and the GLFW fork is Zlib-licensed. MojoLauncher source is LGPL-3.0 with project-specific exceptions. Any future source port must retain the corresponding license notices and attribution. This audit does not copy those larger implementations into Flint.

## Current boundary

The changes in this audit improve API correctness and detect a known native/Java version mismatch. They do not prove that Flint reaches the Minecraft title screen on a physical device. Device acceptance still requires an actual launch with a matching Android LWJGL build, followed by title-screen and world-entry tests.

## Archive-level verification (2026-11-18)

The three available source archives were inspected directly:

| Archive | Observed contents | Integration decision |
|---|---|---|
| `mojoexec-master.zip` | C/C++ native library, JNI Java binding, Vulkan/EGL loader, Android linker-namespace helpers, ARM64 function locator, and CPU-affinity helper | Do not link it into Flint as an isolated library. Its EGL/Vulkan handles and linker hooks assume the Mojo GLFW/runtime lifecycle. Reuse individual behavior only where Flint has an equivalent owner and test. |
| `glfw-glfw34.zip` | GLFW 3.4 Android platform implementation, Android window/input/joystick/monitor code, EGL hooks, and Java JNI bindings | Do not mix individual Android platform files into Flint's single-Surface backend. The Android implementation imports MojoExec APIs and owns a different window/input lifecycle. Keep Flint's GLFW-compatible C ABI until a complete replacement can be integrated and tested together. |
| `NativesPlugin-master.zip` | Android application/plugin wrapper; Gradle loads optional AAR files from `app/libs`; the supplied archive contains no native renderer or LWJGL implementation | No runtime code is reusable from this archive. It is an optional native-library distribution mechanism, not a renderer backend. |

### Concrete MojoExec contract

The public native header exposes a global render specification (`force_gles_context`, `override_major_version`, display width/height/refresh rate), a native-library directory, Vulkan/EGL handle acquisition, and a big-core affinity helper. The Java binding additionally exposes EGL preparation, custom Vulkan driver configuration, QGL config selection, Vulkan preload, native-library directory, and affinity configuration.

Flint already owns the relevant Android Surface, EGL context, native-library lookup, display metrics, and thread-affinity entry points. Importing MojoExec would duplicate those owners and could make EGL handles or linker state disagree. The current targeted GLES client-API metadata correction is the safe behavior-level port; it does not require importing MojoExec.

### GLFW coupling confirmed

The supplied Android GLFW platform registers a complete Android platform vtable and includes Android-specific window, input-queue, joystick, monitor, and EGL code. Its Android initialization includes MojoExec headers and its Android EGL/window hooks are coupled to that platform lifecycle. This confirms that copying only `android_window.c`, `android_input_queue.c`, or the EGL hook into Flint would not be a self-contained integration.

### Verification boundary

Archive inspection confirms source structure and coupling only. It does not establish runtime compatibility or performance. No renderer or windowing code from these archives should be treated as integrated merely because the archive builds independently. Flint acceptance still requires a matching Android ABI build and physical-device launch, title-screen, world-entry, input, resize, pause/resume, and teardown tests.

## PojavLauncher comparison (v3_openjdk)

Reference inspected: `TeamPojavLauncher/PojavLauncher`, branch `v3_openjdk` (source tree SHA `5431df433002c460ca322867d1954f3e62d16287`). This is an architecture comparison, not a source-code import.

| Area | Pojav v3_openjdk pattern | Flint decision |
|---|---|---|
| Android Surface lifecycle | `GameView` uses a `SurfaceProvider` abstraction and forwards available/resized/destroyed callbacks to the platform backend | Keep Flint's Activity-owned `Surface` and explicit EGL owner thread. Ensure resize/destroy events are handled without destroying EGL from the UI/coroutine thread. |
| GLFW boundary | `GLFWBackend` forwards surface lifecycle and input through its JNI GLFW binding | Keep Flint's existing GLFW-compatible ABI and bounded native input queue. Do not load Pojav's `mojort`/GLFW JNI layer beside Flint's backend. |
| Input | Separate in-game and GUI touch processors; supports pointer capture, mouse/stylus, gamepad, text and modifier state | Keep Flint's input mapping and queue. Use Pojav behavior as a checklist for missing cases, not as a replacement input stack. |
| Renderer selection | `GameRenderer` selects a `RenderSpec`, configures environment variables and optional library search paths, then attempts a renderer fallback | Flint currently has a GL4ES path. Do not expose LTW/Zink/Mesa/MobileGlues choices until their matching Android artifacts, EGL contract and version compatibility are present. |
| Runtime and launch | Pojav has its own JRE utilities, game runner, service and native bridge | Keep Flint's own JRE manager, metadata/classpath resolver, authentication, launch coordinator and native lifecycle. Do not copy Pojav's launcher/account/install layers. |
| Device tuning | Renderer setup can configure custom Vulkan driver paths, QGL config and big-core affinity through MojoExec | Flint already owns display metrics and a performance-core preference hook. Vulkan-driver override is not enabled by this source comparison alone. |

### Concrete lifecycle and input lessons

Pojav's `GameView` forwards surface availability to `Platform.updateSurface`, forwards size changes to `surfaceUpdated`, and reports destruction to `surfaceDestroyed`. Its GLFW backend maps Android key, mouse, scroll and text events to native GLFW calls. The separation between the Android view, platform backend, and game renderer is useful; the exact Pojav native bridge is not interchangeable with Flint's bridge.

Pojav also switches between in-game and GUI touch processors when mouse-grab state changes. Its event handling covers hardware mouse/stylus, gamepad, pointer capture, touch controls and text input. Flint should verify these behaviors individually against its own input queue and callbacks.

### Porting decision

No Pojav Java, JNI, C/C++, or renderer implementation is copied by this audit. Pojav is LGPL-3.0; its GLFW and MojoExec submodules carry separate licenses. Any future source-level port must preserve the relevant license files and notices, isolate the imported component, and pass Flint's native ABI, lifecycle, and device tests. Architecture and behavior references do not by themselves grant proof of binary compatibility.

The next implementation gate is to compare Flint's actual resize, pause/resume, Surface-destroy, pointer-grab, text-input, and gamepad paths against these Pojav behaviors, then add focused tests to Flint's existing interfaces. Do not replace the whole backend as a shortcut.
