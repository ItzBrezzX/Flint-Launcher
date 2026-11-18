#include "flint_glfw_backend.h"
#include "flint_input_queue.h"

#include <EGL/egl.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <dlfcn.h>
#include <string>
#include <array>

struct GLFWwindow {
    void *userPointer = nullptr;
    std::string title = "Flint";
    bool shouldClose = false;
    bool focused = true;
    bool iconified = false;
    bool maximized = false;
    bool visible = true;
    bool decorated = false;
    bool resizable = true;
    bool floating = false;
    int width = 0;
    int height = 0;
    int framebufferWidth = 0;
    int framebufferHeight = 0;
    int cursorMode = 0x00034001;
    int stickyKeys = 0;
    int stickyMouseButtons = 0;
    int lockKeyMods = 0;
    int rawMouseMotion = 0;
    // Flint creates an OpenGL ES 2 context for the GL4ES renderer.
    // Expose the actual client API/version to LWJGL, matching Mojo's
    // force_gles_context behavior instead of advertising desktop OpenGL.
    int contextClientApi = GLFW_OPENGL_ES_API;
    int contextVersionMajor = 2;
    int contextVersionMinor = 0;
    double cursorX = 0.0;
    double cursorY = 0.0;
    std::array<int, 512> keys{};
    std::array<int, 8> mouseButtons{};
    GLFWwindowposfun positionCallback = nullptr;
    GLFWwindowsizefun sizeCallback = nullptr;
    GLFWwindowclosefun closeCallback = nullptr;
    GLFWwindowrefreshfun refreshCallback = nullptr;
    GLFWwindowfocusfun focusCallback = nullptr;
    GLFWwindowiconifyfun iconifyCallback = nullptr;
    GLFWwindowmaximizefun maximizeCallback = nullptr;
    GLFWframebuffersizefun framebufferCallback = nullptr;
    GLFWwindowcontentscalefun contentScaleCallback = nullptr;
    GLFWkeyfun keyCallback = nullptr;
    GLFWcharfun charCallback = nullptr;
    GLFWcharmodsfun charModsCallback = nullptr;
    GLFWmousebuttonfun mouseButtonCallback = nullptr;
    GLFWcursorposfun cursorCallback = nullptr;
    GLFWcursorenterfun cursorEnterCallback = nullptr;
    GLFWscrollfun scrollCallback = nullptr;
    GLFWdropfun dropCallback = nullptr;
};

struct GLFWmonitor {
    void *userPointer = nullptr;
};

namespace {
using Clock = std::chrono::steady_clock;
GLFWwindow g_window;
GLFWmonitor g_monitor;
GLFWmonitor *g_monitorList[] = {&g_monitor};
GLFWvidmode g_videoMode{0, 0, 8, 8, 8, 60};
GLFWerrorfun g_errorCallback = nullptr;
GLFWmonitorfun g_monitorCallback = nullptr;
GLFWjoystickfun g_joystickCallback = nullptr;
std::array<void *, 16> g_joystickUserPointers{};
GLFWgammaramp g_gammaRamp{nullptr, nullptr, nullptr, 0};
bool g_surfaceAttached = false;
bool g_initialized = false;
bool g_windowCreated = false;
std::atomic_bool g_close_requested{false};
std::atomic<uint64_t> g_presentedFrameCount{0};
EGLDisplay g_display = EGL_NO_DISPLAY;
EGLSurface g_surface = EGL_NO_SURFACE;
EGLContext g_context = EGL_NO_CONTEXT;
int g_surfaceWidth = 0;
int g_surfaceHeight = 0;
int g_lastError = 0;
std::string g_lastErrorText;
std::string g_clipboard;
Clock::time_point g_timeOrigin = Clock::now();
double g_timeOffset = 0.0;
double g_lastPhysicalCursorX = 0.0;
double g_lastPhysicalCursorY = 0.0;
bool g_hasPhysicalCursor = false;

bool valid(GLFWwindow *window) {
    return window != nullptr && window == &g_window && g_windowCreated;
}

void set_error(int code, const char *message) {
    g_lastError = code;
    g_lastErrorText = message ? message : "Flint GLFW backend error";
    if (g_errorCallback) g_errorCallback(code, g_lastErrorText.c_str());
}

int current_egl_context_ready() {
    return g_surfaceAttached && g_display != EGL_NO_DISPLAY &&
        g_surface != EGL_NO_SURFACE && g_context != EGL_NO_CONTEXT &&
        eglGetCurrentContext() == g_context;
}

void release_pressed_inputs() {
    for (size_t i = 0; i < g_window.keys.size(); ++i) {
        if (g_window.keys[i] == GLFW_PRESS) {
            g_window.keys[i] = GLFW_RELEASE;
            if (g_window.keyCallback) {
                g_window.keyCallback(&g_window, static_cast<int>(i), 0, GLFW_RELEASE, 0);
            }
        }
    }
    for (size_t i = 0; i < g_window.mouseButtons.size(); ++i) {
        if (g_window.mouseButtons[i] == GLFW_PRESS) {
            g_window.mouseButtons[i] = GLFW_RELEASE;
            if (g_window.mouseButtonCallback) {
                g_window.mouseButtonCallback(&g_window, static_cast<int>(i), GLFW_RELEASE, 0);
            }
        }
    }
}

void dispatch_event(const flint::InputEvent &event) {
    if (event.type == static_cast<int32_t>(flint::InputEventType::Close)) {
        g_close_requested.store(true, std::memory_order_relaxed);
        if (g_windowCreated) {
            g_window.shouldClose = true;
            if (g_window.closeCallback) g_window.closeCallback(&g_window);
        }
        return;
    }
    if (!g_windowCreated) return;
    switch (event.type) {
        case static_cast<int32_t>(flint::InputEventType::Key): {
            if (event.code < 0 || event.code >= static_cast<int>(g_window.keys.size())) break;
            const int action = event.action == GLFW_REPEAT ? GLFW_REPEAT :
                (event.action == GLFW_RELEASE ? GLFW_RELEASE : GLFW_PRESS);
            g_window.keys[static_cast<size_t>(event.code)] = action == GLFW_RELEASE
                ? GLFW_RELEASE : GLFW_PRESS;
            if (g_window.keyCallback) {
                g_window.keyCallback(&g_window, event.code, 0, action, event.mods);
            }
            break;
        }
        case static_cast<int32_t>(flint::InputEventType::Character):
            if (event.code >= 0) {
                if (g_window.charCallback) g_window.charCallback(&g_window, static_cast<unsigned int>(event.code));
                if (g_window.charModsCallback) {
                    g_window.charModsCallback(&g_window, static_cast<unsigned int>(event.code), event.mods);
                }
            }
            break;
        case static_cast<int32_t>(flint::InputEventType::Cursor): {
            if (g_window.cursorMode == GLFW_CURSOR_DISABLED) {
                if (!g_hasPhysicalCursor) {
                    g_lastPhysicalCursorX = event.x;
                    g_lastPhysicalCursorY = event.y;
                    g_hasPhysicalCursor = true;
                    break;
                }
                const double deltaX = event.x - g_lastPhysicalCursorX;
                const double deltaY = event.y - g_lastPhysicalCursorY;
                g_lastPhysicalCursorX = event.x;
                g_lastPhysicalCursorY = event.y;
                g_window.cursorX += deltaX;
                g_window.cursorY += deltaY;
                if (g_window.cursorCallback) {
                    g_window.cursorCallback(&g_window, g_window.cursorX, g_window.cursorY);
                }
            } else {
                g_window.cursorX = event.x;
                g_window.cursorY = event.y;
                g_hasPhysicalCursor = false;
                if (g_window.cursorCallback) {
                    g_window.cursorCallback(&g_window, event.x, event.y);
                }
            }
            break;
        }
        case static_cast<int32_t>(flint::InputEventType::MouseButton): {
            if (event.code < 0 || event.code >= static_cast<int>(g_window.mouseButtons.size())) break;
            const int action = event.action == GLFW_RELEASE ? GLFW_RELEASE : GLFW_PRESS;
            g_window.mouseButtons[static_cast<size_t>(event.code)] = action;
            g_window.cursorX = event.x;
            g_window.cursorY = event.y;
            if (g_window.mouseButtonCallback) {
                g_window.mouseButtonCallback(&g_window, event.code, action, event.mods);
            }
            break;
        }
        case static_cast<int32_t>(flint::InputEventType::Scroll):
            if (g_window.scrollCallback) g_window.scrollCallback(&g_window, event.x, event.y);
            break;
        case static_cast<int32_t>(flint::InputEventType::Focus):
            g_window.focused = event.action != 0;
            if (!g_window.focused) {
                release_pressed_inputs();
                g_hasPhysicalCursor = false;
            }
            if (g_window.focusCallback) g_window.focusCallback(&g_window, g_window.focused ? GLFW_TRUE : GLFW_FALSE);
            break;
        case static_cast<int32_t>(flint::InputEventType::WindowSize): {
            const int width = std::max(0, static_cast<int>(event.x));
            const int height = std::max(0, static_cast<int>(event.y));
            const bool changed = width != g_window.width || height != g_window.height;
            g_window.width = width;
            g_window.height = height;
            if (changed && g_window.sizeCallback) g_window.sizeCallback(&g_window, width, height);
            break;
        }
        case static_cast<int32_t>(flint::InputEventType::FramebufferSize): {
            const int width = std::max(0, static_cast<int>(event.x));
            const int height = std::max(0, static_cast<int>(event.y));
            const bool changed = width != g_window.framebufferWidth || height != g_window.framebufferHeight;
            g_window.framebufferWidth = width;
            g_window.framebufferHeight = height;
            g_videoMode.width = width;
            g_videoMode.height = height;
            if (changed && g_window.framebufferCallback) g_window.framebufferCallback(&g_window, width, height);
            break;
        }
        default:
            // Wake events and unknown future event records are intentionally ignored.
            break;
    }
}
} // namespace

extern "C" FLINT_GLFW_API int flint_glfw_attach_android_surface(int width, int height) {
    if (width <= 0 || height <= 0 || eglGetCurrentContext() == EGL_NO_CONTEXT) {
        set_error(GLFW_PLATFORM_ERROR, "An active EGL context and non-zero Android surface size are required");
        return GLFW_FALSE;
    }
    g_display = eglGetCurrentDisplay();
    g_surface = eglGetCurrentSurface(EGL_DRAW);
    g_context = eglGetCurrentContext();
    if (g_display == EGL_NO_DISPLAY || g_surface == EGL_NO_SURFACE) {
        set_error(GLFW_PLATFORM_ERROR, "No current EGL display/draw surface is attached");
        g_display = EGL_NO_DISPLAY;
        g_surface = EGL_NO_SURFACE;
        g_context = EGL_NO_CONTEXT;
        return GLFW_FALSE;
    }
    g_surfaceWidth = width;
    g_surfaceHeight = height;
    g_videoMode.width = width;
    g_videoMode.height = height;
    g_surfaceAttached = true;
    return GLFW_TRUE;
}

extern "C" FLINT_GLFW_API int flint_glfw_make_android_context_current(void) {
    if (!g_surfaceAttached || g_display == EGL_NO_DISPLAY ||
        g_surface == EGL_NO_SURFACE || g_context == EGL_NO_CONTEXT) {
        set_error(GLFW_PLATFORM_ERROR, "No attached Android EGL context is available to bind");
        return GLFW_FALSE;
    }
    if (eglGetCurrentContext() == g_context) return GLFW_TRUE;
    if (!eglMakeCurrent(g_display, g_surface, g_surface, g_context)) {
        set_error(GLFW_PLATFORM_ERROR, "Could not bind the attached Android EGL context to this thread");
        return GLFW_FALSE;
    }
    return GLFW_TRUE;
}

extern "C" FLINT_GLFW_API int flint_glfw_release_android_context_current(void) {
    if (g_display == EGL_NO_DISPLAY || g_context == EGL_NO_CONTEXT) {
        set_error(GLFW_PLATFORM_ERROR, "No attached Android EGL context is available to release");
        return GLFW_FALSE;
    }
    if (eglGetCurrentContext() != g_context) return GLFW_TRUE;
    if (!eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT)) {
        set_error(GLFW_PLATFORM_ERROR, "Could not release the attached Android EGL context from this thread");
        return GLFW_FALSE;
    }
    return GLFW_TRUE;
}

extern "C" FLINT_GLFW_API void flint_glfw_detach_android_surface(void) {
    glfwTerminate();
    g_surfaceAttached = false;
    g_display = EGL_NO_DISPLAY;
    g_surface = EGL_NO_SURFACE;
    g_context = EGL_NO_CONTEXT;
    g_surfaceWidth = 0;
    g_surfaceHeight = 0;
}

extern "C" FLINT_GLFW_API GLFWwindow *flint_glfw_android_window(void) {
    return g_windowCreated ? &g_window : nullptr;
}

extern "C" FLINT_GLFW_API uint64_t flint_glfw_presented_frame_count(void) {
    return g_presentedFrameCount.load(std::memory_order_relaxed);
}

extern "C" FLINT_GLFW_API int flint_glfw_poll_events(void) {
    if (!g_initialized || !g_windowCreated) return 0;
    const auto events = flint::poll_input_events(1024);
    for (const auto &event : events) dispatch_event(event);
    return static_cast<int>(events.size());
}

extern "C" FLINT_GLFW_API int glfwInit(void) {
    if (!g_surfaceAttached) {
        set_error(GLFW_PLATFORM_ERROR, "Flint GLFW requires an attached Android EGL surface");
        return GLFW_FALSE;
    }
    if (!current_egl_context_ready()) {
        set_error(GLFW_PLATFORM_ERROR, "The attached EGL context is not current on this thread");
        return GLFW_FALSE;
    }
    g_initialized = true;
    return GLFW_TRUE;
}

extern "C" FLINT_GLFW_API void glfwTerminate(void) {
    g_initialized = false;
    g_windowCreated = false;
    g_close_requested.store(false, std::memory_order_relaxed);
    g_hasPhysicalCursor = false;
    g_window = GLFWwindow{};
}

extern "C" FLINT_GLFW_API void glfwGetVersion(int *major, int *minor, int *rev) {
    if (major) *major = 3;
    if (minor) *minor = 3;
    if (rev) *rev = 8;
}

extern "C" FLINT_GLFW_API const char *glfwGetVersionString(void) {
    return "3.3.8 Flint Android GLFW-compatible backend";
}

extern "C" FLINT_GLFW_API GLFWerrorfun glfwSetErrorCallback(GLFWerrorfun callback) {
    GLFWerrorfun previous = g_errorCallback;
    g_errorCallback = callback;
    return previous;
}

extern "C" FLINT_GLFW_API int glfwGetError(const char **description) {
    const int error = g_lastError;
    if (description) *description = error == 0 ? nullptr : g_lastErrorText.c_str();
    g_lastError = 0;
    g_lastErrorText.clear();
    return error;
}

extern "C" FLINT_GLFW_API void glfwDefaultWindowHints(void) {}
extern "C" FLINT_GLFW_API void glfwWindowHint(int, int) {}

extern "C" FLINT_GLFW_API GLFWwindow *glfwCreateWindow(
    int width, int height, const char *title, GLFWmonitor *monitor, GLFWwindow *share
) {
    (void)monitor;
    (void)share;
    if (!g_initialized) {
        set_error(GLFW_NOT_INITIALIZED, "glfwInit must succeed before creating a window");
        return nullptr;
    }
    if (!current_egl_context_ready()) {
        set_error(GLFW_PLATFORM_ERROR, "Window creation must run on the current Android EGL owner thread");
        return nullptr;
    }
    if (width <= 0 || height <= 0) {
        set_error(GLFW_INVALID_VALUE, "Window dimensions must be positive");
        return nullptr;
    }
    if (g_windowCreated) return &g_window;

    g_window = GLFWwindow{};
    g_presentedFrameCount.store(0, std::memory_order_relaxed);
    g_window.title = title ? title : "Flint";
    g_window.shouldClose = g_close_requested.load(std::memory_order_relaxed);
    g_window.width = g_surfaceWidth;
    g_window.height = g_surfaceHeight;
    g_window.framebufferWidth = g_surfaceWidth;
    g_window.framebufferHeight = g_surfaceHeight;
    g_windowCreated = true;
    return &g_window;
}

extern "C" FLINT_GLFW_API void glfwDestroyWindow(GLFWwindow *window) {
    if (!valid(window)) return;
    g_windowCreated = false;
    g_window = GLFWwindow{};
}

extern "C" FLINT_GLFW_API int glfwWindowShouldClose(GLFWwindow *window) {
    return valid(window) && (g_window.shouldClose || g_close_requested.load(std::memory_order_relaxed)) ? GLFW_TRUE : GLFW_FALSE;
}

extern "C" FLINT_GLFW_API void glfwSetWindowShouldClose(GLFWwindow *window, int value) {
    if (valid(window)) {
        g_window.shouldClose = value != 0;
        g_close_requested.store(value != 0, std::memory_order_relaxed);
    }
}

extern "C" FLINT_GLFW_API void glfwSetWindowTitle(GLFWwindow *window, const char *title) {
    if (valid(window)) g_window.title = title ? title : "";
}

extern "C" FLINT_GLFW_API void glfwGetWindowPos(GLFWwindow *window, int *xpos, int *ypos) {
    if (!valid(window)) return;
    if (xpos) *xpos = 0;
    if (ypos) *ypos = 0;
}

extern "C" FLINT_GLFW_API void glfwSetWindowPos(GLFWwindow *window, int, int) {
    if (valid(window)) set_error(GLFW_PLATFORM_ERROR, "Android controls the physical window position");
}

extern "C" FLINT_GLFW_API void glfwGetWindowSize(GLFWwindow *window, int *width, int *height) {
    if (!valid(window)) return;
    if (width) *width = g_window.width;
    if (height) *height = g_window.height;
}

extern "C" FLINT_GLFW_API void glfwSetWindowSize(GLFWwindow *window, int width, int height) {
    if (!valid(window)) return;
    if (width <= 0 || height <= 0) {
        set_error(GLFW_INVALID_VALUE, "Window dimensions must be positive");
        return;
    }
    // Android owns the actual Surface geometry. Retain the requested logical
    // size only as backend state; framebuffer dimensions remain surface pixels.
    const bool changed = width != g_window.width || height != g_window.height;
    g_window.width = width;
    g_window.height = height;
    if (changed && g_window.sizeCallback) g_window.sizeCallback(&g_window, width, height);
}

extern "C" FLINT_GLFW_API void glfwGetFramebufferSize(GLFWwindow *window, int *width, int *height) {
    if (!valid(window)) return;
    if (width) *width = g_window.framebufferWidth;
    if (height) *height = g_window.framebufferHeight;
}

extern "C" FLINT_GLFW_API void glfwGetWindowContentScale(GLFWwindow *window, float *xscale, float *yscale) {
    if (!valid(window)) return;
    if (xscale) *xscale = 1.0f;
    if (yscale) *yscale = 1.0f;
}

extern "C" FLINT_GLFW_API int glfwGetWindowAttrib(GLFWwindow *window, int attrib) {
    if (!valid(window)) return GLFW_FALSE;
    switch (attrib) {
        case GLFW_FOCUSED: return g_window.focused ? GLFW_TRUE : GLFW_FALSE;
        case GLFW_ICONIFIED: return g_window.iconified ? GLFW_TRUE : GLFW_FALSE;
        case GLFW_RESIZABLE: return g_window.resizable ? GLFW_TRUE : GLFW_FALSE;
        case GLFW_VISIBLE: return g_window.visible ? GLFW_TRUE : GLFW_FALSE;
        case GLFW_DECORATED: return g_window.decorated ? GLFW_TRUE : GLFW_FALSE;
        case GLFW_FLOATING: return g_window.floating ? GLFW_TRUE : GLFW_FALSE;
        case GLFW_MAXIMIZED: return g_window.maximized ? GLFW_TRUE : GLFW_FALSE;
        case GLFW_CLIENT_API: return g_window.contextClientApi;
        case GLFW_CONTEXT_VERSION_MAJOR: return g_window.contextVersionMajor;
        case GLFW_CONTEXT_VERSION_MINOR: return g_window.contextVersionMinor;
        case GLFW_DOUBLEBUFFER: return GLFW_TRUE;
        default: return GLFW_FALSE;
    }
}

extern "C" FLINT_GLFW_API void glfwSetWindowAttrib(GLFWwindow *window, int attrib, int value) {
    if (!valid(window)) return;
    switch (attrib) {
        case GLFW_RESIZABLE: g_window.resizable = value != 0; break;
        case GLFW_VISIBLE: g_window.visible = value != 0; break;
        case GLFW_DECORATED: g_window.decorated = value != 0; break;
        case GLFW_FLOATING: g_window.floating = value != 0; break;
        default: break;
    }
}

extern "C" FLINT_GLFW_API void glfwIconifyWindow(GLFWwindow *window) {
    if (valid(window)) g_window.iconified = true;
}
extern "C" FLINT_GLFW_API void glfwRestoreWindow(GLFWwindow *window) {
    if (valid(window)) { g_window.iconified = false; g_window.maximized = false; }
}
extern "C" FLINT_GLFW_API void glfwMaximizeWindow(GLFWwindow *window) {
    if (valid(window)) { g_window.iconified = false; g_window.maximized = true; }
}
extern "C" FLINT_GLFW_API void glfwFocusWindow(GLFWwindow *window) {
    if (valid(window)) {
        g_window.focused = true;
        if (g_window.focusCallback) g_window.focusCallback(&g_window, GLFW_TRUE);
    }
}
extern "C" FLINT_GLFW_API void glfwRequestWindowAttention(GLFWwindow *) {}

extern "C" FLINT_GLFW_API GLFWmonitor *glfwGetWindowMonitor(GLFWwindow *window) {
    return valid(window) ? &g_monitor : nullptr;
}

extern "C" FLINT_GLFW_API void glfwSetWindowMonitor(
    GLFWwindow *window, GLFWmonitor *, int, int, int width, int height, int
) {
    if (valid(window) && width > 0 && height > 0) glfwSetWindowSize(window, width, height);
}

extern "C" FLINT_GLFW_API int glfwGetKey(GLFWwindow *window, int key) {
    if (!valid(window) || key < 0 || key >= static_cast<int>(g_window.keys.size())) return GLFW_RELEASE;
    return g_window.keys[static_cast<size_t>(key)];
}

extern "C" FLINT_GLFW_API int glfwGetMouseButton(GLFWwindow *window, int button) {
    if (!valid(window) || button < 0 || button >= static_cast<int>(g_window.mouseButtons.size())) return GLFW_RELEASE;
    return g_window.mouseButtons[static_cast<size_t>(button)];
}

extern "C" FLINT_GLFW_API void glfwGetCursorPos(GLFWwindow *window, double *xpos, double *ypos) {
    if (!valid(window)) return;
    if (xpos) *xpos = g_window.cursorX;
    if (ypos) *ypos = g_window.cursorY;
}

extern "C" FLINT_GLFW_API void glfwSetCursorPos(GLFWwindow *window, double xpos, double ypos) {
    if (!valid(window)) return;
    g_window.cursorX = xpos;
    g_window.cursorY = ypos;
    g_hasPhysicalCursor = false;
    if (g_window.cursorCallback) g_window.cursorCallback(&g_window, xpos, ypos);
}

extern "C" FLINT_GLFW_API void glfwSetInputMode(GLFWwindow *window, int mode, int value) {
    if (!valid(window)) return;
    switch (mode) {
        case GLFW_CURSOR:
            if (g_window.cursorMode != value) g_hasPhysicalCursor = false;
            g_window.cursorMode = value;
            break;
        case GLFW_STICKY_KEYS: g_window.stickyKeys = value != 0; break;
        case GLFW_STICKY_MOUSE_BUTTONS: g_window.stickyMouseButtons = value != 0; break;
        case GLFW_LOCK_KEY_MODS: g_window.lockKeyMods = value != 0; break;
        case GLFW_RAW_MOUSE_MOTION: g_window.rawMouseMotion = value != 0; break;
        default: set_error(GLFW_INVALID_VALUE, "Unknown GLFW input mode"); break;
    }
}

extern "C" FLINT_GLFW_API int glfwGetInputMode(GLFWwindow *window, int mode) {
    if (!valid(window)) return 0;
    switch (mode) {
        case GLFW_CURSOR: return g_window.cursorMode;
        case GLFW_STICKY_KEYS: return g_window.stickyKeys;
        case GLFW_STICKY_MOUSE_BUTTONS: return g_window.stickyMouseButtons;
        case GLFW_LOCK_KEY_MODS: return g_window.lockKeyMods;
        case GLFW_RAW_MOUSE_MOTION: return g_window.rawMouseMotion;
        default: set_error(GLFW_INVALID_VALUE, "Unknown GLFW input mode"); return 0;
    }
}

extern "C" FLINT_GLFW_API const char *glfwGetKeyName(int, int) { return nullptr; }
extern "C" FLINT_GLFW_API int glfwGetKeyScancode(int) { return -1; }

extern "C" FLINT_GLFW_API int flint_glfw_poll_events(void);

extern "C" FLINT_GLFW_API void glfwPollEvents(void) {
    (void)flint_glfw_poll_events();
}

extern "C" FLINT_GLFW_API void glfwWaitEvents(void) {
    if (!g_initialized) return;
    (void)flint::wait_for_input_event(-1.0);
    (void)flint_glfw_poll_events();
}

extern "C" FLINT_GLFW_API void glfwWaitEventsTimeout(double timeout) {
    if (!g_initialized) return;
    const double bounded = std::max(0.0, std::min(timeout, 86400.0));
    (void)flint::wait_for_input_event(bounded);
    (void)flint_glfw_poll_events();
}

extern "C" FLINT_GLFW_API void glfwPostEmptyEvent(void) {
    flint::push_input_event({static_cast<int32_t>(flint::InputEventType::Wake), 0, 0, 0, 0.0, 0.0});
}

extern "C" FLINT_GLFW_API void glfwMakeContextCurrent(GLFWwindow *window) {
    if (window == nullptr) {
        if (g_display != EGL_NO_DISPLAY) eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        return;
    }
    if (!valid(window) || !g_surfaceAttached || g_display == EGL_NO_DISPLAY) {
        set_error(GLFW_PLATFORM_ERROR, "No attached Android EGL window is available");
        return;
    }
    if (eglGetCurrentContext() != g_context &&
        !eglMakeCurrent(g_display, g_surface, g_surface, g_context)) {
        set_error(GLFW_PLATFORM_ERROR, "Could not make the Flint EGL context current on this thread");
    }
}

extern "C" FLINT_GLFW_API GLFWwindow *glfwGetCurrentContext(void) {
    return valid(&g_window) && eglGetCurrentContext() == g_context ? &g_window : nullptr;
}

extern "C" FLINT_GLFW_API void glfwSwapBuffers(GLFWwindow *window) {
    if (!valid(window) || g_display == EGL_NO_DISPLAY || g_surface == EGL_NO_SURFACE) return;
    if (eglGetCurrentContext() != g_context &&
        !eglMakeCurrent(g_display, g_surface, g_surface, g_context)) {
        set_error(GLFW_PLATFORM_ERROR, "Could not make EGL context current before buffer swap");
        return;
    }
    if (!eglSwapBuffers(g_display, g_surface)) {
        set_error(GLFW_PLATFORM_ERROR, "eglSwapBuffers failed in Flint GLFW backend");
    } else {
        g_presentedFrameCount.fetch_add(1, std::memory_order_relaxed);
    }
}

extern "C" FLINT_GLFW_API void glfwSwapInterval(int) {}

extern "C" FLINT_GLFW_API GLFWglproc glfwGetProcAddress(const char *procname) {
    if (!procname || !*procname) return nullptr;
    auto eglProc = eglGetProcAddress(procname);
    if (eglProc) return reinterpret_cast<GLFWglproc>(eglProc);
    return reinterpret_cast<GLFWglproc>(dlsym(RTLD_DEFAULT, procname));
}

extern "C" FLINT_GLFW_API double glfwGetTime(void) {
    const double elapsed = std::chrono::duration<double>(Clock::now() - g_timeOrigin).count();
    return elapsed + g_timeOffset;
}
extern "C" FLINT_GLFW_API void glfwSetTime(double time) {
    if (!std::isfinite(time) || time < 0.0) {
        set_error(GLFW_INVALID_VALUE, "GLFW time must be a finite non-negative value");
        return;
    }
    const double elapsed = std::chrono::duration<double>(Clock::now() - g_timeOrigin).count();
    g_timeOffset = time - elapsed;
}
extern "C" FLINT_GLFW_API uint64_t glfwGetTimerValue(void) {
    return static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::nanoseconds>(
        Clock::now().time_since_epoch()).count());
}
extern "C" FLINT_GLFW_API uint64_t glfwGetTimerFrequency(void) { return 1000000000ULL; }

extern "C" FLINT_GLFW_API GLFWmonitor **glfwGetMonitors(int *count) {
    if (count) *count = g_surfaceAttached ? 1 : 0;
    return g_surfaceAttached ? g_monitorList : nullptr;
}
extern "C" FLINT_GLFW_API GLFWmonitor *glfwGetPrimaryMonitor(void) {
    return g_surfaceAttached ? &g_monitor : nullptr;
}
extern "C" FLINT_GLFW_API void glfwGetMonitorPos(GLFWmonitor *monitor, int *xpos, int *ypos) {
    if (monitor != &g_monitor) return;
    if (xpos) *xpos = 0;
    if (ypos) *ypos = 0;
}
extern "C" FLINT_GLFW_API void glfwGetMonitorPhysicalSize(GLFWmonitor *monitor, int *widthMM, int *heightMM) {
    if (monitor != &g_monitor) return;
    if (widthMM) *widthMM = 0;
    if (heightMM) *heightMM = 0;
}
extern "C" FLINT_GLFW_API const char *glfwGetMonitorName(GLFWmonitor *monitor) {
    return monitor == &g_monitor ? "Flint Android Display" : nullptr;
}
extern "C" FLINT_GLFW_API const GLFWvidmode *glfwGetVideoMode(GLFWmonitor *monitor) {
    return monitor == &g_monitor ? &g_videoMode : nullptr;
}
extern "C" FLINT_GLFW_API const GLFWvidmode *glfwGetVideoModes(GLFWmonitor *monitor, int *count) {
    if (count) *count = monitor == &g_monitor ? 1 : 0;
    return monitor == &g_monitor ? &g_videoMode : nullptr;
}
extern "C" FLINT_GLFW_API void glfwGetMonitorContentScale(GLFWmonitor *monitor, float *xscale, float *yscale) {
    if (monitor != &g_monitor) return;
    if (xscale) *xscale = 1.0f;
    if (yscale) *yscale = 1.0f;
}
extern "C" FLINT_GLFW_API void glfwGetMonitorWorkarea(GLFWmonitor *monitor, int *xpos, int *ypos, int *width, int *height) {
    if (monitor != &g_monitor) return;
    if (xpos) *xpos = 0;
    if (ypos) *ypos = 0;
    if (width) *width = g_surfaceWidth;
    if (height) *height = g_surfaceHeight;
}

extern "C" FLINT_GLFW_API const char *glfwGetClipboardString(GLFWwindow *window) {
    return valid(window) && !g_clipboard.empty() ? g_clipboard.c_str() : nullptr;
}
extern "C" FLINT_GLFW_API void glfwSetClipboardString(GLFWwindow *window, const char *string) {
    if (valid(window)) g_clipboard = string ? string : "";
}
extern "C" FLINT_GLFW_API void *glfwGetWindowUserPointer(GLFWwindow *window) {
    return valid(window) ? g_window.userPointer : nullptr;
}
extern "C" FLINT_GLFW_API void glfwSetWindowUserPointer(GLFWwindow *window, void *pointer) {
    if (valid(window)) g_window.userPointer = pointer;
}

#define FLINT_CALLBACK_SETTER(name, field, type) \
extern "C" FLINT_GLFW_API type name(GLFWwindow *window, type callback) { \
    if (!valid(window)) return nullptr; \
    type previous = g_window.field; \
    g_window.field = callback; \
    return previous; \
}

FLINT_CALLBACK_SETTER(glfwSetWindowPosCallback, positionCallback, GLFWwindowposfun)
FLINT_CALLBACK_SETTER(glfwSetWindowSizeCallback, sizeCallback, GLFWwindowsizefun)
FLINT_CALLBACK_SETTER(glfwSetWindowCloseCallback, closeCallback, GLFWwindowclosefun)
FLINT_CALLBACK_SETTER(glfwSetWindowRefreshCallback, refreshCallback, GLFWwindowrefreshfun)
FLINT_CALLBACK_SETTER(glfwSetWindowFocusCallback, focusCallback, GLFWwindowfocusfun)
FLINT_CALLBACK_SETTER(glfwSetWindowIconifyCallback, iconifyCallback, GLFWwindowiconifyfun)
FLINT_CALLBACK_SETTER(glfwSetWindowMaximizeCallback, maximizeCallback, GLFWwindowmaximizefun)
FLINT_CALLBACK_SETTER(glfwSetFramebufferSizeCallback, framebufferCallback, GLFWframebuffersizefun)
FLINT_CALLBACK_SETTER(glfwSetWindowContentScaleCallback, contentScaleCallback, GLFWwindowcontentscalefun)
FLINT_CALLBACK_SETTER(glfwSetKeyCallback, keyCallback, GLFWkeyfun)
FLINT_CALLBACK_SETTER(glfwSetCharCallback, charCallback, GLFWcharfun)
FLINT_CALLBACK_SETTER(glfwSetMouseButtonCallback, mouseButtonCallback, GLFWmousebuttonfun)
FLINT_CALLBACK_SETTER(glfwSetCursorPosCallback, cursorCallback, GLFWcursorposfun)
FLINT_CALLBACK_SETTER(glfwSetCursorEnterCallback, cursorEnterCallback, GLFWcursorenterfun)
FLINT_CALLBACK_SETTER(glfwSetScrollCallback, scrollCallback, GLFWscrollfun)
FLINT_CALLBACK_SETTER(glfwSetDropCallback, dropCallback, GLFWdropfun)


/* GLFW symbols required by the LWJGL 3.3.x generated binding but whose
 * desktop-only behavior is not provided by Android. Keep every ABI entry
 * point present and return explicit unsupported/empty values. */
extern "C" FLINT_GLFW_API void glfwInitHint(int, int) {}
extern "C" FLINT_GLFW_API void glfwInitAllocator(const GLFWallocator *) {}

extern "C" FLINT_GLFW_API int glfwGetPlatform(void) {
    return GLFW_PLATFORM_NULL;
}
extern "C" FLINT_GLFW_API int glfwPlatformSupported(int platform) {
    return platform == GLFW_PLATFORM_NULL ? GLFW_TRUE : GLFW_FALSE;
}

extern "C" FLINT_GLFW_API void glfwSetMonitorUserPointer(GLFWmonitor *monitor, void *pointer) {
    if (monitor == &g_monitor) g_monitor.userPointer = pointer;
}
extern "C" FLINT_GLFW_API void *glfwGetMonitorUserPointer(GLFWmonitor *monitor) {
    return monitor == &g_monitor ? g_monitor.userPointer : nullptr;
}
extern "C" FLINT_GLFW_API GLFWmonitorfun glfwSetMonitorCallback(GLFWmonitorfun callback) {
    GLFWmonitorfun previous = g_monitorCallback;
    g_monitorCallback = callback;
    return previous;
}

extern "C" FLINT_GLFW_API void glfwSetGamma(GLFWmonitor *, float) {}
extern "C" FLINT_GLFW_API const GLFWgammaramp *glfwGetGammaRamp(GLFWmonitor *monitor) {
    return monitor == &g_monitor ? &g_gammaRamp : nullptr;
}
extern "C" FLINT_GLFW_API void glfwSetGammaRamp(GLFWmonitor *, const GLFWgammaramp *) {}

extern "C" FLINT_GLFW_API void glfwWindowHintString(int, const char *) {}
extern "C" FLINT_GLFW_API void glfwSetWindowIcon(GLFWwindow *window, int, const GLFWimage *) {
    if (!valid(window)) return;
}
extern "C" FLINT_GLFW_API void glfwSetWindowSizeLimits(GLFWwindow *window, int, int, int, int) {
    if (!valid(window)) return;
}
extern "C" FLINT_GLFW_API void glfwSetWindowAspectRatio(GLFWwindow *window, int numer, int denom) {
    if (!valid(window)) return;
    if ((numer != -1 && numer <= 0) || (denom != -1 && denom <= 0)) {
        set_error(GLFW_INVALID_VALUE, "Window aspect ratio must be positive or -1");
    }
}
extern "C" FLINT_GLFW_API void glfwGetWindowFrameSize(GLFWwindow *window, int *left, int *top, int *right, int *bottom) {
    if (!valid(window)) return;
    if (left) *left = 0;
    if (top) *top = 0;
    if (right) *right = 0;
    if (bottom) *bottom = 0;
}
extern "C" FLINT_GLFW_API float glfwGetWindowOpacity(GLFWwindow *window) {
    return valid(window) ? 1.0f : 0.0f;
}
extern "C" FLINT_GLFW_API void glfwSetWindowOpacity(GLFWwindow *window, float opacity) {
    if (!valid(window)) return;
    if (!std::isfinite(opacity) || opacity < 0.0f || opacity > 1.0f) {
        set_error(GLFW_INVALID_VALUE, "Window opacity must be between 0.0 and 1.0");
    }
}
extern "C" FLINT_GLFW_API void glfwShowWindow(GLFWwindow *window) {
    if (valid(window)) g_window.visible = true;
}
extern "C" FLINT_GLFW_API void glfwHideWindow(GLFWwindow *window) {
    if (valid(window)) g_window.visible = false;
}

extern "C" FLINT_GLFW_API int glfwRawMouseMotionSupported(void) {
    return GLFW_FALSE;
}
extern "C" FLINT_GLFW_API GLFWcursor *glfwCreateCursor(const GLFWimage *, int, int) {
    return nullptr;
}
extern "C" FLINT_GLFW_API GLFWcursor *glfwCreateStandardCursor(int) {
    return nullptr;
}
extern "C" FLINT_GLFW_API void glfwDestroyCursor(GLFWcursor *) {}
extern "C" FLINT_GLFW_API void glfwSetCursor(GLFWwindow *window, GLFWcursor *) {
    if (!valid(window)) return;
}
extern "C" FLINT_GLFW_API GLFWcharmodsfun glfwSetCharModsCallback(GLFWwindow *window, GLFWcharmodsfun callback) {
    if (!valid(window)) return nullptr;
    GLFWcharmodsfun previous = g_window.charModsCallback;
    g_window.charModsCallback = callback;
    return previous;
}

extern "C" FLINT_GLFW_API int glfwJoystickPresent(int) { return GLFW_FALSE; }
extern "C" FLINT_GLFW_API const float *glfwGetJoystickAxes(int, int *count) {
    if (count) *count = 0;
    return nullptr;
}
extern "C" FLINT_GLFW_API const unsigned char *glfwGetJoystickButtons(int, int *count) {
    if (count) *count = 0;
    return nullptr;
}
extern "C" FLINT_GLFW_API const unsigned char *glfwGetJoystickHats(int, int *count) {
    if (count) *count = 0;
    return nullptr;
}
extern "C" FLINT_GLFW_API const char *glfwGetJoystickName(int) { return nullptr; }
extern "C" FLINT_GLFW_API const char *glfwGetJoystickGUID(int) { return nullptr; }
extern "C" FLINT_GLFW_API void glfwSetJoystickUserPointer(int jid, void *pointer) {
    if (jid >= 0 && jid < static_cast<int>(g_joystickUserPointers.size())) {
        g_joystickUserPointers[static_cast<size_t>(jid)] = pointer;
    }
}
extern "C" FLINT_GLFW_API void *glfwGetJoystickUserPointer(int jid) {
    if (jid < 0 || jid >= static_cast<int>(g_joystickUserPointers.size())) return nullptr;
    return g_joystickUserPointers[static_cast<size_t>(jid)];
}
extern "C" FLINT_GLFW_API int glfwJoystickIsGamepad(int) { return GLFW_FALSE; }
extern "C" FLINT_GLFW_API GLFWjoystickfun glfwSetJoystickCallback(GLFWjoystickfun callback) {
    GLFWjoystickfun previous = g_joystickCallback;
    g_joystickCallback = callback;
    return previous;
}
extern "C" FLINT_GLFW_API int glfwUpdateGamepadMappings(const char *) { return GLFW_FALSE; }
extern "C" FLINT_GLFW_API const char *glfwGetGamepadName(int) { return nullptr; }
extern "C" FLINT_GLFW_API int glfwGetGamepadState(int, GLFWgamepadstate *) { return GLFW_FALSE; }

extern "C" FLINT_GLFW_API int glfwExtensionSupported(const char *extension) {
    if (!extension || !*extension) return GLFW_FALSE;
    using GetStringFn = const unsigned char *(*)(unsigned int);
    auto getString = reinterpret_cast<GetStringFn>(glfwGetProcAddress("glGetString"));
    if (!getString) return GLFW_FALSE;
    const char *extensions = reinterpret_cast<const char *>(getString(0x1F03)); // GL_EXTENSIONS
    if (!extensions) return GLFW_FALSE;
    const size_t length = std::strlen(extension);
    const char *cursor = extensions;
    while ((cursor = std::strstr(cursor, extension)) != nullptr) {
        const bool leftBoundary = cursor == extensions || cursor[-1] == ' ';
        const bool rightBoundary = cursor[length] == '\0' || cursor[length] == ' ';
        if (leftBoundary && rightBoundary) return GLFW_TRUE;
        cursor += length;
    }
    return GLFW_FALSE;
}
