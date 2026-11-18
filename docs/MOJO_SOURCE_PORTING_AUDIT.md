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
