# Progress: native + runtime shard (dola/shard-native)

Shard owner: NATIVE + RUNTIME. Branch: `dola/shard-native`. Baseline: `7d1ffb0`.
All code/comments in English. Only `native/` and `runtime/` edited (plus this doc).
No gradle/assemble run (NDK not installed in this environment); C++ files passed
`g++ -std=c++17 -fsyntax-only` with stubbed Android/EGL/JNI headers. Kotlin not
compiled here — organizer compiles at merge.

## What was implemented

### 1. Clean-room GLFW stub (acceptance #1) — REAL implementation
Files: `native/src/main/cpp/glfw_stub.h`, `glfw_stub.cpp` (replaced placeholder).
- C API subset (ABI-compatible constants/function signatures, written from public
  GLFW 3.x docs — no GLFW/Pojav/Boardwalk source copied):
  - init/terminate/initHint, windowHint/windowHintString/defaultWindowHints
  - create/destroy window, shouldClose/setWindowShouldClose, setTitle/Size/Pos
  - getWindowSize/getFramebufferSize/getWindowPos, getWindowAttrib/setWindowAttrib
  - iconify/restore/maximize/show/hide/focus/requestAttention
  - makeContextCurrent/getCurrentContext, swapBuffers, swapInterval, getProcAddress
  - extensionSupported, pollEvents/waitEvents/waitEventsTimeout/postEmptyEvent
  - getKey/getMouseButton/getCursorPos/setCursorPos
  - setInputMode/getInputMode (cursor normal/hidden/disabled + FLINT extension CAPTURED,
    sticky keys/mouse, lock key mods, raw mouse motion)
  - getTime/setTime/getTimerValue/getTimerFrequency, getVersion/getVersionString
  - primary monitor / getMonitors / getVideoMode(s) / monitor name+size (synthetic single monitor)
  - cursor create/destroy/set (stub handles), getKeyName/getKeyScancode (partial)
  - clipboard set/get (in-memory), all 17 callback setters (return previous)
  - joystick/gamepad: stubbed — always report "not present" (TODO Spike 1)
- Backing: Android EGL + ANativeWindow (single window). EGL config chosen from
  window hints (RGBA/depth/stencil/samples). Context version GLES2/3 from
  CONTEXT_VERSION_MAJOR hint. Event queue mutex-protected, drained in pollEvents
  which dispatches to registered C callbacks. Time base via CLOCK_MONOTONIC.
- JNI: nativeGlfwInit/Terminate/AttachSurface/DetachSurface/PollEvents/
  WindowShouldClose/SwapBuffers/MakeContextCurrent/GetTime/PostResize/PostClose/
  VersionString.
- **Gap (honest):** C callbacks fire natively, but the Java upcall (C callback →
  LWJGL Java callback) is NOT wired in Spike 0. The org.lwjgl.glfw.GLFW shim
  stores callbacks but they are not invoked from native. This is the main Spike-0
  gap for end-to-end Minecraft input. Tracked below under Shared API requests.

### 2. GL symbol loader + renderer select/fallback/context-recovery (acceptance #2) — REAL
Files: `native/src/main/cpp/gl_symbol_loader.h`, `gl_symbol_loader.cpp` (replaced placeholder).
- Two backends: `Gl4esPlugin` (dlopen libGL.so, MIT, requires current EGL context →
  initialize_gl4es) and `SystemGles` (direct libGLESv2 / eglGetProcAddress).
- Fallback chain: requested → GL4ES (if present + inits) → SystemGLES → None.
- glGetProcAddress dispatch: GL4ES dlsym → eglGetProcAddress (with desktop→GLES
  name alias table, e.g. glDepthRange→glDepthRangef; fixed-function entries
  marked no-GLES-equivalent) → libGLESv2 dlsym → RTLD_DEFAULT → NULL.
- glQueryCompat: fills renderer/version/glsl/vendor/extensions/maxTexSize.
- glRecoverContext: re-runs GL4ES init after window layer re-creates EGL context
  (EGL context ownership is in glfw_stub; loader re-inits the GL state machine).
- addNativeLibraryPath (updates LD_LIBRARY_PATH + internal dir list), glSetPluginDir.
- JNI: nativeGlInit/nativeGlGetProcAddress/nativeGlQueryCompat/nativeGlRecoverContext/
  nativeGlCurrentBackend/nativeGlLastError/nativeAddNativeLibraryPath/nativeSetPluginDir.
- Kotlin: `Renderer.kt` — RendererBackend enum, RendererProbeResult, RendererSelector
  (probeAll/selectBest/recoverContext/currentBackend + parseCompat).
- **Note:** GL4ES built from source via CMake FetchContent stays OFF by default
  (FLINT_BUILD_GL4ES=OFF). Expected as download-on-demand libGL.so plugin.

### 3. Input bridge (acceptance #3) — REAL implementation
Files: `native/src/main/cpp/input_bridge.h`, `input_bridge.cpp` (replaced placeholder).
- Thread-safe InputEventQueue (bounded 4096, drops oldest on flood).
- Injection: key/char/charMods/mouseButton/cursorPos/cursorDelta/scroll.
- Virtual joystick: left stick → synthetic WASD key press/release (edge-detected,
  deadzone 0.25); right stick → cursor delta (camera look) with sensitivity.
- Touch-action → key mapping (jump=SPACE, sneak=LEFT_SHIFT, sprint=LEFT_CONTROL,
  inventory=E, drop=Q, chat=T, attack/use/pick = mouse buttons 0/1/2), overridable.
- Mouse capture state (normal/hidden/disabled/captured), resetInputState.
- Gamepad: stub (gamepadIsPresent=false). TODO Spike 1: Android InputDevice.
- JNI: nativeInjectKey/Char/MouseButton/CursorPos/CursorDelta/Scroll/JoystickAxis,
  nativeSet/GetTouchKeyMapping, nativeInjectTouchAction, nativeSet/GetMouseCapture,
  nativeResetInput.
- Kotlin: `InputBridge.kt` — Android KeyEvent/MotionEvent → GLFW injection,
  touch→mouse mapping (tap=left click, 2nd finger=right click, 2-finger drag=scroll,
  captured mode=relative look), joystick axis passthrough, touch-button dispatch,
  androidKeyToGlfw mapping table (subset), androidModsToGlfw.

### 4. LWJGL2/3 compat layer (acceptance #4) — PARTIAL (high-traffic subset)
Files: `native/src/main/java/org/lwjgl/glfw/GLFW.kt`, `org/lwjgl/opengl/GL.kt`,
`org/lwjgl/openal/AL.kt`, plus `GlfwStubShim.kt` (Flint-owned facade).
- `org.lwjgl.glfw.GLFW` shadow object: ~70 methods. IMPLEMENTED (delegate to
  NativeBridge): init/terminate, window hints, create/destroy window, shouldClose,
  makeContextCurrent, swapBuffers, getProcAddress, poll/wait events, setInputMode
  (cursor), getTime, getPrimaryMonitor, getVersionString, setWindowShouldClose,
  setWindowSize (posts resize). API-ONLY/STUB: all set*Callback (stored, no Java
  upcall), getKey/getMouseButton/getCursorPos (return 0 — needs native query wiring),
  getVideoMode (synthetic handle), joystick/gamepad (not present). Unlisted methods
  throw UnsupportedOperationException with a clear message.
- `org.lwjgl.opengl.GL`: function provider delegating to nativeGlGetProcAddress;
  per-function GL11..GL30 binding classes NOT shimmed — expected from upstream
  LWJGL opengl jar (pure Java) wired through our provider.
- `org.lwjgl.openal.AL`: function provider delegating to nativeAudioGetProcAddress;
  per-function AL bindings from upstream LWJGL openal jar.
- **Gap:** native→Java callback upcall (item #1 gap) is required for Minecraft to
  receive input via LWJGL. Without it, the shim is structurally complete but not
  end-to-end functional for game input.

### 5. JNI lifecycle in runtime (acceptance #5) — REAL
Files: `runtime/src/main/kotlin/.../JvmController.kt`, `GameProcess.kt`,
`FlintGameLauncher.kt`, `NativeLibraryLoader.kt`.
- `JvmController`: non-blocking start (returns GameProcess immediately; JVM on
  dedicated thread), render-thread/main-thread separation documented, shutdown
  (cancel + join), restart, crash recovery via native signal handler →
  last_crash.txt → hasPendingCrash/lastCrashReport/clearCrashReport (app offers
  restart). Crash handler JNI added to flint_jni.cpp (nativeInstallCrashHandler/
  nativeGetLastCrashReport/nativeClearCrashReport; catches SIGSEGV/SIGABRT/SIGBUS/
  SIGFPE/SIGILL/SIGSYS, writes report, re-raises with default handler).
- `GameProcess`: state machine (STARTING/RUNNING/EXITED/CRASHED/CANCELLED),
  exitCode, cancel(), waitFor().
- `FlintGameLauncher` implements the **primitive-param contract**:
  `launch(classpath: List<File>, jvmArgs: List<String>, gameArgs: List<String>,
  mainClass: String, nativesDir: File, env: Map<String,String>, workingDir: File): GameProcess`.
  Validates inputs, loads libjli, prepares native libs (NativeLibraryLoader),
  builds argv (adds -cp, -Djava.library.path, -Duser.dir if absent), starts JVM
  via JvmController. Existing PojavStyleGameLauncher (LaunchRequest-based) untouched.
- `NativeLibraryLoader`: registers dirs with native bridge (addNativeLibraryPath),
  sets LD_LIBRARY_PATH (env passed to launch), -Djava.library.path JVM arg,
  preloads libopenal.so; GL4ES path registered (init deferred to render thread).
- ARM64 JRE verification: BLOCKED as instructed. MobileJreManager unchanged
  (importLocalArchive+checksum+probe retained). Needed artifact: AngelAuraMC
  OpenJDK mobile JRE tar.xz (URLs/SHA-256 pinned in KnownJreArtifacts).

### 6. OpenAL dynamic-load contract (acceptance #6) — REAL
Files: `native/src/main/cpp/audio_openal.h`, `audio_openal.cpp` (new).
- dlopen libopenal.so (plugin dir → system search). LGPL-2.1+ — dynamic only,
  never statically linked into flint_native (CMake links only system libs).
- Resolves core symbols (alcOpenDevice/alcCreateContext/alcMakeContextCurrent/
  alGenSources/alSourcePlay/alGetError etc.). Stub backend: 21 no-op trampolines
  returned via audioGetProcAddress when OpenAL absent → Minecraft audio init
  succeeds silently.
- JNI: nativeAudioInit/nativeAudioIsAvailable/nativeAudioQuery/nativeAudioGetProcAddress.
- Kotlin: `org.lwjgl.openal.AL` shim (function provider).

### 7. JNI smoke + instrumentation test skeleton (acceptance #7) — SKELETON
- `native/src/androidTest/.../NativeLoadingInstrumentedTest.kt`: 9 tests covering
  native load, JNI smoke, EGL smoke, EGL capabilities, GLFW init/version, GL
  loader fallback, audio stub, input injection no-crash, crash handler install,
  surface attach (TODO marked where a real Surface is needed).
- `runtime/src/test/.../GameProcessTest.kt`: pure unit tests (GameProcess state
  machine, NativeLibraryLoader path arg) — host-JVM runnable.
- Unit test for NativeBridge contract (host JVM) not added: NativeBridge references
  android.view.Surface in signatures; would need Robolectric. Left as TODO.

## Status per original scope checklist

| Item | Status | Notes |
|---|---|---|
| A. GLFW window impl/event loop/input callbacks | REAL (C side) | Java upcall gap — see above |
| A. GL4ES optional runtime-loaded backend | REAL | FLINT_BUILD_GL4ES default OFF; dlopen libGL.so |
| A. GL symbol loading | REAL | 4-stage dispatch + alias table |
| A. GL context init | REAL | EGL window surface, GLES2/3 |
| A. LWJGL2/3 compat layer | PARTIAL | high-traffic subset; callbacks API-only |
| A. GPU renderer selection + fallback | REAL | RendererSelector.probeAll/selectBest |
| A. OpenGL compat testing | REAL | glQueryCompat + parseCompat |
| A. Renderer context recovery | PARTIAL | GL-side re-init done; EGL context re-create owned by GLFW window (detach+attach) |
| B. JNI lifecycle non-blocking | REAL | JvmController.start returns immediately |
| B. JVM thread management | REAL | dedicated Flint-Minecraft-JVM thread |
| B. JVM shutdown/restart | REAL | shutdown()/restart(); hard nativeDestroyJavaVM hook TODO |
| B. JVM crash recovery | REAL | signal handler → last_crash.txt → restart offer |
| B. Native library loading (plugin dir + LD_LIBRARY_PATH) | REAL | NativeLibraryLoader |
| B. ARM64 JRE verification | BLOCKED | MobileJreManager retained; artifact needed |
| E. Touch/keyboard/mouse bridge → GLFW queue | REAL | InputBridge + native queue |
| E. Virtual joystick → mouse/key events | REAL | left=WASD, right=camera look |
| E. Camera movement | REAL | cursor delta in captured mode |
| E. Jump/sneak/sprint key mapping | REAL | touch-action map, overridable |
| E. Mouse capture/release | REAL | setInputMode(CURSOR) + native state |
| E. Gamepad | STUB | always not-present; TODO Spike 1 |
| F. OpenAL Soft dynamic-load (LGPL, dynamic only) | REAL | dlopen + no-op stub fallback |
| F. Stub backend | REAL | 21 trampolines |
| K. JNI smoke test | SKELETON | instrumentation test written, not run |
| K. Instrumentation test skeleton | SKELETON | NativeLoadingInstrumentedTest |

## Shared-dependency / shared-API requests (for organizer)

1. **Native→Java callback upcall** (cross-cutting): To fire LWJGL Java callbacks
   from the native C callbacks, we need a JNIEnv* on the render thread and a
   global-ref registry of Java callback objects. This is the single biggest
   Spike-0 gap and requires agreement on the callback registry API between
   native shard (this one) and the app/logic shards that set callbacks. Proposed:
   `NativeBridge.nativeRegisterCallback(kind: Int, callback: Long)` where the
   long is a LWJGL callback memory address; native stores it and invokes via
   JNI `CallVoidMethod` on the render thread. Needs organizer sign-off.
2. **Surface ownership**: The app shard owns the SurfaceView that provides the
   Android Surface. `GlfwStubShim.attachSurface(surface, w, h)` is the API.
   App shard should call it on the render thread in surfaceCreated.
3. **Crash report UI**: `JvmController.hasPendingCrash()/lastCrashReport()` is
   the API; app shard should show "Game crashed — restart?" dialog on next launch.
4. **Renderer plugin download**: GL4ES (libGL.so) and OpenAL Soft (libopenal.so)
   are download-on-demand. URLs/SHA-256 belong in docs/RUNTIME_ARTIFACTS.md
   (organizer-owned). This shard only provides the loader.

## Third-party licence attribution (in this doc, not RUNTIME_ARTIFACTS)
- GL4ES: MIT — optional libGL.so plugin, runtime-loaded (not linked).
- OpenAL Soft: LGPL-2.1+ — dynamic dlopen only, never statically linked into
  flint_native (CMake links only log/android/EGL/GLESv2/dl/m).
- GLFW API: constants/signatures written from public documentation; no source
  copied. Flint's implementation is permissive-licensed original code.

## Files changed/added
- Modified: `native/src/main/cpp/glfw_stub.cpp`, `gl_symbol_loader.cpp`,
  `input_bridge.cpp` (placeholders → real), `flint_jni.cpp` (appended crash
  handler JNI), `CMakeLists.txt` (added audio_openal.cpp),
  `native/.../NativeBridge.kt` (appended 40+ external funs + safe wrappers).
- Added (C++): `flint_common.h`, `glfw_stub.h`, `input_bridge.h`,
  `gl_symbol_loader.h`, `audio_openal.h`, `audio_openal.cpp`.
- Added (Kotlin native): `Renderer.kt`, `InputBridge.kt`, `GlfwStubShim.kt`,
  `org/lwjgl/glfw/GLFW.kt`, `org/lwjgl/opengl/GL.kt`, `org/lwjgl/openal/AL.kt`.
- Added (Kotlin runtime): `JvmController.kt`, `GameProcess.kt`,
  `FlintGameLauncher.kt`, `NativeLibraryLoader.kt`.
- Added (tests): `native/src/androidTest/.../NativeLoadingInstrumentedTest.kt`,
  `runtime/src/test/.../GameProcessTest.kt`.
- Added: `docs/progress-shard-native.md` (this file).
