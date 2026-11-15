// glfw_stub.cpp
// Clean-room GLFW 3.x API subset implementation for Flint Launcher.
// Backed by Android EGL + ANativeWindow and flint's input event queue.
// Written from the public GLFW API documentation; no GLFW/Pojav/Boardwalk source.
//
// Spike 0 model: exactly one window (Minecraft is single-window). Multi-window
// and monitor enumeration return synthetic single-monitor values.

#include "flint_common.h"
#include "glfw_stub.h"
#include "input_bridge.h"
#include "gl_symbol_loader.h"

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <string.h>
#include <time.h>
#include <mutex>
#include <atomic>
#include <vector>
#include <string>
#include <unordered_map>

#define FLINT_GLFW_DONT_CARE_VALUE (-1)

namespace flint {

// ---------------------------------------------------------------------------
// Internal window state. Spike 0: single window.
// ---------------------------------------------------------------------------
struct GlfwWindowState {
    bool initialized = false;
    bool shouldClose = false;
    bool visible = true;
    bool focused = true;
    bool iconified = false;
    bool resizable = true;
    bool decorated = true;
    bool floating = false;
    bool autoIconify = true;
    bool transparentFramebuffer = false;

    int width = 800;
    int height = 600;
    int framebufferWidth = 800;
    int framebufferHeight = 600;
    float contentScale = 1.0f;
    int posX = 0, posY = 0;

    double cursorX = 0.0, cursorY = 0.0;
    int cursorMode = FLINT_GLFW_CURSOR_NORMAL;
    bool stickyKeys = false;
    bool stickyMouseButtons = false;
    bool lockKeyMods = false;
    bool rawMouseMotion = false;

    int keyState[FLINT_GLFW_KEY_LAST + 1] = {0};
    int mouseButtonState[8] = {0};
    int keyMods = 0;

    std::string title;
    std::string clipboard;

    ANativeWindow* nativeWindow = nullptr;
    EGLDisplay eglDisplay = EGL_NO_DISPLAY;
    EGLSurface eglSurface = EGL_NO_SURFACE;
    EGLContext eglContext = EGL_NO_CONTEXT;
    EGLConfig  eglConfig = nullptr;
    int eglClientVersion = 2;
    bool eglCurrent = false;

    FLINT_GLFWerrorfun          cbError = nullptr;
    FLINT_GLFWwindowposfun      cbWindowPos = nullptr;
    FLINT_GLFWwindowsizefun     cbWindowSize = nullptr;
    FLINT_GLFWwindowclosefun    cbWindowClose = nullptr;
    FLINT_GLFWwindowrefreshfun  cbWindowRefresh = nullptr;
    FLINT_GLFWwindowfocusfun    cbWindowFocus = nullptr;
    FLINT_GLFWwindowiconifyfun  cbWindowIconify = nullptr;
    FLINT_GLFWwindowmaximizefun cbWindowMaximize = nullptr;
    FLINT_GLFWframebuffersizefun cbFramebufferSize = nullptr;
    FLINT_GLFWmousebuttonfun    cbMouseButton = nullptr;
    FLINT_GLFWcursorposfun      cbCursorPos = nullptr;
    FLINT_GLFWcursorenterfun    cbCursorEnter = nullptr;
    FLINT_GLFWscrollfun         cbScroll = nullptr;
    FLINT_GLFWkeyfun            cbKey = nullptr;
    FLINT_GLFWcharfun           cbChar = nullptr;
    FLINT_GLFWcharmodsfun       cbCharMods = nullptr;
    FLINT_GLFWdropfun           cbDrop = nullptr;
    FLINT_GLFWmonitorfun        cbMonitor = nullptr;
    FLINT_GLFWjoystickfun       cbJoystick = nullptr;
};

static std::mutex g_stateMutex;
static GlfwWindowState* g_window = nullptr;
static std::atomic<bool> g_initialized{false};
static std::atomic<void*> g_currentContext{nullptr};
static struct timespec g_timeBase;
static double g_timeOffset = 0.0;
static std::unordered_map<int, int> g_windowHints;
static std::unordered_map<int, std::string> g_windowHintStrings;

static const char* kVersionString = "3.4.0 Flint clean-room stub (Android EGL backend)";

double monotonicSeconds() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec * 1e-9;
}

void glfwReportError(int code, const char* message) {
    FLINT_LOGW("GLFW error 0x%x: %s", code, message ? message : "(null)");
    std::lock_guard<std::mutex> lock(g_stateMutex);
    if (g_window && g_window->cbError) {
        g_window->cbError(code, message ? message : "unknown error");
    }
}

void glfwResetWindowHints() {
    g_windowHints.clear();
    g_windowHintStrings.clear();
    g_windowHints[FLINT_GLFW_RESIZABLE] = FLINT_GLFW_TRUE;
    g_windowHints[FLINT_GLFW_VISIBLE] = FLINT_GLFW_TRUE;
    g_windowHints[FLINT_GLFW_DECORATED] = FLINT_GLFW_TRUE;
    g_windowHints[FLINT_GLFW_FOCUSED] = FLINT_GLFW_TRUE;
    g_windowHints[FLINT_GLFW_AUTO_ICONIFY] = FLINT_GLFW_TRUE;
    g_windowHints[FLINT_GLFW_FLOATING] = FLINT_GLFW_FALSE;
    g_windowHints[FLINT_GLFW_MAXIMIZED] = FLINT_GLFW_FALSE;
    g_windowHints[FLINT_GLFW_CENTER_CURSOR] = FLINT_GLFW_TRUE;
    g_windowHints[FLINT_GLFW_TRANSPARENT_FRAMEBUFFER] = FLINT_GLFW_FALSE;
    g_windowHints[FLINT_GLFW_FOCUS_ON_SHOW] = FLINT_GLFW_TRUE;
    g_windowHints[FLINT_GLFW_CLIENT_API] = FLINT_GLFW_OPENGL_API;
    g_windowHints[FLINT_GLFW_CONTEXT_VERSION_MAJOR] = 1;
    g_windowHints[FLINT_GLFW_CONTEXT_VERSION_MINOR] = 0;
    g_windowHints[FLINT_GLFW_OPENGL_FORWARD_COMPAT] = FLINT_GLFW_FALSE;
    g_windowHints[FLINT_GLFW_OPENGL_DEBUG_CONTEXT] = FLINT_GLFW_FALSE;
    g_windowHints[FLINT_GLFW_OPENGL_PROFILE] = FLINT_GLFW_OPENGL_ANY_PROFILE;
    g_windowHints[FLINT_GLFW_CONTEXT_ROBUSTNESS] = FLINT_GLFW_NO_ROBUSTNESS;
    g_windowHints[FLINT_GLFW_REFRESH_RATE] = FLINT_GLFW_DONT_CARE_VALUE;
    g_windowHints[FLINT_GLFW_SAMPLES] = 0;
    g_windowHints[FLINT_GLFW_RED_BITS] = 8;
    g_windowHints[FLINT_GLFW_GREEN_BITS] = 8;
    g_windowHints[FLINT_GLFW_BLUE_BITS] = 8;
    g_windowHints[FLINT_GLFW_ALPHA_BITS] = 8;
    g_windowHints[FLINT_GLFW_DEPTH_BITS] = 24;
    g_windowHints[FLINT_GLFW_STENCIL_BITS] = 8;
    g_windowHints[FLINT_GLFW_DOUBLEBUFFER] = FLINT_GLFW_TRUE;
    g_windowHints[FLINT_GLFW_SRGB_CAPABLE] = FLINT_GLFW_FALSE;
    g_windowHints[FLINT_GLFW_STEREO] = FLINT_GLFW_FALSE;
    g_windowHints[FLINT_GLFW_CONTEXT_RELEASE_BEHAVIOR] = FLINT_GLFW_ANY_RELEASE_BEHAVIOR;
}

// ---------------------------------------------------------------------------
// EGL lifecycle for the window. Render-thread only.
// ---------------------------------------------------------------------------
static bool eglInitForWindow(GlfwWindowState* w, ANativeWindow* anw) {
    w->nativeWindow = anw;
    w->eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (w->eglDisplay == EGL_NO_DISPLAY) {
        glfwReportError(FLINT_GLFW_PLATFORM_ERROR, "eglGetDisplay failed");
        return false;
    }
    EGLint major = 0, minor = 0;
    if (!eglInitialize(w->eglDisplay, &major, &minor)) {
        glfwReportError(FLINT_GLFW_PLATFORM_ERROR, "eglInitialize failed");
        return false;
    }
    auto hint = [](int key, int def) -> int {
        auto it = g_windowHints.find(key);
        return it != g_windowHints.end() ? it->second : def;
    };
    const EGLint configAttrs[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, hint(FLINT_GLFW_RED_BITS, 8),
        EGL_GREEN_SIZE, hint(FLINT_GLFW_GREEN_BITS, 8),
        EGL_BLUE_SIZE, hint(FLINT_GLFW_BLUE_BITS, 8),
        EGL_ALPHA_SIZE, hint(FLINT_GLFW_ALPHA_BITS, 8),
        EGL_DEPTH_SIZE, hint(FLINT_GLFW_DEPTH_BITS, 16),
        EGL_STENCIL_SIZE, hint(FLINT_GLFW_STENCIL_BITS, 0),
        EGL_SAMPLES, hint(FLINT_GLFW_SAMPLES, 0),
        EGL_NONE
    };
    EGLint count = 0;
    if (!eglChooseConfig(w->eglDisplay, configAttrs, &w->eglConfig, 1, &count) || count < 1) {
        glfwReportError(FLINT_GLFW_FORMAT_UNAVAILABLE, "eglChooseConfig failed");
        return false;
    }
    EGLint nativeVisualId = 0;
    eglGetConfigAttrib(w->eglDisplay, w->eglConfig, EGL_NATIVE_VISUAL_ID, &nativeVisualId);
    if (nativeVisualId != 0) {
        ANativeWindow_setBuffersGeometry(anw, 0, 0, nativeVisualId);
    }
    w->eglSurface = eglCreateWindowSurface(w->eglDisplay, w->eglConfig, anw, nullptr);
    if (w->eglSurface == EGL_NO_SURFACE) {
        char msg[128];
        snprintf(msg, sizeof(msg), "eglCreateWindowSurface failed: 0x%x", eglGetError());
        glfwReportError(FLINT_GLFW_PLATFORM_ERROR, msg);
        return false;
    }
    const EGLint ctxAttrs[] = { EGL_CONTEXT_CLIENT_VERSION, w->eglClientVersion, EGL_NONE };
    w->eglContext = eglCreateContext(w->eglDisplay, w->eglConfig, EGL_NO_CONTEXT, ctxAttrs);
    if (w->eglContext == EGL_NO_CONTEXT) {
        char msg[128];
        snprintf(msg, sizeof(msg), "eglCreateContext(GLES%d) failed: 0x%x", w->eglClientVersion, eglGetError());
        glfwReportError(FLINT_GLFW_VERSION_UNAVAILABLE, msg);
        return false;
    }
    w->width = ANativeWindow_getWidth(anw);
    w->height = ANativeWindow_getHeight(anw);
    w->framebufferWidth = w->width;
    w->framebufferHeight = w->height;
    FLINT_LOGI("GLFW window EGL ready: %dx%d GLES%d", w->width, w->height, w->eglClientVersion);
    return true;
}

static void eglDestroyForWindow(GlfwWindowState* w) {
    if (w->eglDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(w->eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (w->eglContext != EGL_NO_CONTEXT) eglDestroyContext(w->eglDisplay, w->eglContext);
        if (w->eglSurface != EGL_NO_SURFACE) eglDestroySurface(w->eglDisplay, w->eglSurface);
        eglTerminate(w->eglDisplay);
    }
    w->eglDisplay = EGL_NO_DISPLAY;
    w->eglSurface = EGL_NO_SURFACE;
    w->eglContext = EGL_NO_CONTEXT;
    w->eglConfig = nullptr;
    w->eglCurrent = false;
    if (w->nativeWindow) {
        ANativeWindow_release(w->nativeWindow);
        w->nativeWindow = nullptr;
    }
}

// ---------------------------------------------------------------------------
// Event dispatch (called from glfwPollEvents on the consumer thread).
// ---------------------------------------------------------------------------
static void dispatchEvent(GlfwWindowState* w, const InputEvent& ev, FLINT_GLFWwindow_ptr handle) {
    switch (ev.type) {
        case InputEventType::Key: {
            const int key = ev.ival1, scancode = ev.ival2, action = ev.ival3, mods = ev.ival4;
            if (key >= 0 && key <= FLINT_GLFW_KEY_LAST) {
                w->keyState[key] = (action == FLINT_GLFW_RELEASE) ? FLINT_GLFW_RELEASE : FLINT_GLFW_PRESS;
            }
            if (w->cbKey) w->cbKey(handle, key, scancode, action, mods);
            break;
        }
        case InputEventType::Char:
            if (w->cbChar) w->cbChar(handle, ev.codepoint);
            break;
        case InputEventType::CharMods:
            if (w->cbCharMods) w->cbCharMods(handle, ev.codepoint, ev.ival4);
            if (w->cbChar) w->cbChar(handle, ev.codepoint);
            break;
        case InputEventType::MouseButton: {
            const int button = ev.ival1, action = ev.ival2, mods = ev.ival3;
            if (button >= 0 && button < 8) w->mouseButtonState[button] = (action == FLINT_GLFW_PRESS) ? 1 : 0;
            if (w->cbMouseButton) w->cbMouseButton(handle, button, action, mods);
            break;
        }
        case InputEventType::CursorPos:
            w->cursorX = ev.dval1;
            w->cursorY = ev.dval2;
            if (w->cbCursorPos) w->cbCursorPos(handle, ev.dval1, ev.dval2);
            break;
        case InputEventType::CursorPosDelta: {
            w->cursorX += ev.dval1;
            w->cursorY += ev.dval2;
            if (w->cursorX < 0) w->cursorX = 0;
            if (w->cursorY < 0) w->cursorY = 0;
            if (w->cbCursorPos) w->cbCursorPos(handle, w->cursorX, w->cursorY);
            break;
        }
        case InputEventType::Scroll:
            if (w->cbScroll) w->cbScroll(handle, ev.dval1, ev.dval2);
            break;
        case InputEventType::CursorEnter:
            if (w->cbCursorEnter) w->cbCursorEnter(handle, ev.ival1);
            break;
        case InputEventType::WindowSize:
            w->width = ev.ival1;
            w->height = ev.ival2;
            if (w->cbWindowSize) w->cbWindowSize(handle, ev.ival1, ev.ival2);
            break;
        case InputEventType::FramebufferSize:
            w->framebufferWidth = ev.ival1;
            w->framebufferHeight = ev.ival2;
            if (w->eglDisplay != EGL_NO_DISPLAY && w->eglCurrent) {
                glViewport(0, 0, ev.ival1, ev.ival2);
            }
            if (w->cbFramebufferSize) w->cbFramebufferSize(handle, ev.ival1, ev.ival2);
            break;
        case InputEventType::WindowClose:
            w->shouldClose = true;
            if (w->cbWindowClose) w->cbWindowClose(handle);
            break;
        case InputEventType::WindowFocus:
            w->focused = ev.ival1 != 0;
            if (w->cbWindowFocus) w->cbWindowFocus(handle, ev.ival1);
            break;
        case InputEventType::WindowIconify:
            w->iconified = ev.ival1 != 0;
            if (w->cbWindowIconify) w->cbWindowIconify(handle, ev.ival1);
            break;
        case InputEventType::WindowRefresh:
            if (w->cbWindowRefresh) w->cbWindowRefresh(handle);
            break;
        case InputEventType::WindowPos:
            w->posX = ev.ival1;
            w->posY = ev.ival2;
            if (w->cbWindowPos) w->cbWindowPos(handle, ev.ival1, ev.ival2);
            break;
        case InputEventType::DropFiles:
            if (w->cbDrop) {
                const char* paths[1] = { "" };
                w->cbDrop(handle, ev.ival1, paths);
            }
            break;
        case InputEventType::MonitorEvent:
            if (w->cbMonitor) w->cbMonitor(nullptr, ev.ival3);
            break;
        case InputEventType::None:
            break;
    }
}

// ---------------------------------------------------------------------------
// Flint-internal surface attach (called from JNI on render thread).
// ---------------------------------------------------------------------------
bool glfwAttachAndroidSurface(void* anw, int width, int height) {
    std::lock_guard<std::mutex> lock(g_stateMutex);
    if (!g_window) {
        g_window = new GlfwWindowState();
        g_window->width = width > 0 ? width : 800;
        g_window->height = height > 0 ? height : 600;
        g_window->framebufferWidth = g_window->width;
        g_window->framebufferHeight = g_window->height;
    }
    if (g_window->eglDisplay != EGL_NO_DISPLAY) {
        eglDestroyForWindow(g_window);
    }
    if (!anw) {
        glfwReportError(FLINT_GLFW_PLATFORM_ERROR, "null ANativeWindow");
        return false;
    }
    auto it = g_windowHints.find(FLINT_GLFW_CONTEXT_VERSION_MAJOR);
    int major = (it != g_windowHints.end()) ? it->second : 2;
    if (major < 2) major = 2;
    if (major > 3) major = 3;
    g_window->eglClientVersion = major;
    if (!eglInitForWindow(g_window, static_cast<ANativeWindow*>(anw))) {
        return false;
    }
    g_window->initialized = true;
    g_initialized = true;
    return true;
}

void glfwDetachAndroidSurface() {
    std::lock_guard<std::mutex> lock(g_stateMutex);
    if (g_window) {
        eglDestroyForWindow(g_window);
        g_window->initialized = false;
    }
}

bool glfwHasCurrentContext() {
    return g_currentContext.load() != nullptr;
}

void glfwPostSurfaceResize(int width, int height) {
    InputEvent ev;
    ev.type = InputEventType::FramebufferSize;
    ev.ival1 = width;
    ev.ival2 = height;
    globalInputQueue().push(ev);
    InputEvent ev2;
    ev2.type = InputEventType::WindowSize;
    ev2.ival1 = width;
    ev2.ival2 = height;
    globalInputQueue().push(ev2);
}

void glfwPostCloseRequest() {
    InputEvent ev;
    ev.type = InputEventType::WindowClose;
    globalInputQueue().push(ev);
}

GlfwWindowState* glfwWindowInstance() { return g_window; }

} // namespace flint

using namespace flint;

// ---------------------------------------------------------------------------
// C API implementation.
// ---------------------------------------------------------------------------
extern "C" {

int flint_glfwInit(void) {
    std::lock_guard<std::mutex> lock(g_stateMutex);
    clock_gettime(CLOCK_MONOTONIC, &g_timeBase);
    g_timeOffset = 0.0;
    glfwResetWindowHints();
    resetInputState();
    g_initialized = true;
    FLINT_LOGI("flint_glfwInit ok (clean-room stub, single-window Android backend)");
    return FLINT_GLFW_TRUE;
}

void flint_glfwTerminate(void) {
    std::lock_guard<std::mutex> lock(g_stateMutex);
    if (g_window) {
        eglDestroyForWindow(g_window);
        delete g_window;
        g_window = nullptr;
    }
    g_currentContext = nullptr;
    g_initialized = false;
    globalInputQueue().clear();
    glShutdown();
}

void flint_glfwInitHint(int hint, int value) {
    FLINT_LOGD("glfwInitHint 0x%x=%d (ignored in stub)", hint, value);
}

void flint_glfwWindowHint(int hint, int value) {
    g_windowHints[hint] = value;
}

void flint_glfwWindowHintString(int hint, const char* value) {
    if (value) g_windowHintStrings[hint] = value;
}

void flint_glfwDefaultWindowHints(void) {
    glfwResetWindowHints();
}

FLINT_GLFWwindow_ptr flint_glfwCreateWindow(int width, int height, const char* title,
                                            FLINT_GLFWmonitor_ptr /*monitor*/,
                                            FLINT_GLFWwindow_ptr /*share*/) {
    std::lock_guard<std::mutex> lock(g_stateMutex);
    if (!g_initialized) {
        glfwReportError(FLINT_GLFW_NOT_INITIALIZED, "glfwInit not called");
        return nullptr;
    }
    if (!g_window) {
        g_window = new GlfwWindowState();
    }
    if (width > 0) g_window->width = width;
    if (height > 0) g_window->height = height;
    if (title) g_window->title = title;
    auto bhint = [](int key, bool def) -> bool {
        auto it = g_windowHints.find(key);
        return it != g_windowHints.end() ? (it->second != 0) : def;
    };
    g_window->resizable = bhint(FLINT_GLFW_RESIZABLE, true);
    g_window->visible = bhint(FLINT_GLFW_VISIBLE, true);
    g_window->decorated = bhint(FLINT_GLFW_DECORATED, true);
    g_window->focused = bhint(FLINT_GLFW_FOCUSED, true);
    g_window->autoIconify = bhint(FLINT_GLFW_AUTO_ICONIFY, true);
    g_window->floating = bhint(FLINT_GLFW_FLOATING, false);
    g_window->transparentFramebuffer = bhint(FLINT_GLFW_TRANSPARENT_FRAMEBUFFER, false);
    return reinterpret_cast<FLINT_GLFWwindow_ptr>(g_window);
}

void flint_glfwDestroyWindow(FLINT_GLFWwindow_ptr window) {
    std::lock_guard<std::mutex> lock(g_stateMutex);
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (!w || w != g_window) return;
    eglDestroyForWindow(w);
    w->initialized = false;
    w->shouldClose = true;
}

int flint_glfwWindowShouldClose(FLINT_GLFWwindow_ptr window) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    return w ? (w->shouldClose ? 1 : 0) : 1;
}

void flint_glfwSetWindowShouldClose(FLINT_GLFWwindow_ptr window, int value) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (w) w->shouldClose = (value != 0);
}

void flint_glfwSetWindowTitle(FLINT_GLFWwindow_ptr window, const char* title) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (w && title) w->title = title;
}

void flint_glfwSetWindowSize(FLINT_GLFWwindow_ptr window, int width, int height) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (!w) return;
    w->width = width;
    w->height = height;
    InputEvent ev;
    ev.type = InputEventType::WindowSize;
    ev.ival1 = width; ev.ival2 = height;
    globalInputQueue().push(ev);
}

void flint_glfwGetWindowSize(FLINT_GLFWwindow_ptr window, int* width, int* height) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (width) *width = w ? w->width : 0;
    if (height) *height = w ? w->height : 0;
}

void flint_glfwSetWindowPos(FLINT_GLFWwindow_ptr /*window*/, int /*xpos*/, int /*ypos*/) {}

void flint_glfwGetWindowPos(FLINT_GLFWwindow_ptr window, int* xpos, int* ypos) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (xpos) *xpos = w ? w->posX : 0;
    if (ypos) *ypos = w ? w->posY : 0;
}

void flint_glfwGetFramebufferSize(FLINT_GLFWwindow_ptr window, int* width, int* height) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (width) *width = w ? w->framebufferWidth : 0;
    if (height) *height = w ? w->framebufferHeight : 0;
}

float flint_glfwGetWindowContentScale(FLINT_GLFWwindow_ptr window) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    return w ? w->contentScale : 1.0f;
}

int flint_glfwGetWindowAttrib(FLINT_GLFWwindow_ptr window, int attrib) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (!w) return 0;
    switch (attrib) {
        case FLINT_GLFW_RESIZABLE: return w->resizable ? 1 : 0;
        case FLINT_GLFW_VISIBLE: return w->visible ? 1 : 0;
        case FLINT_GLFW_DECORATED: return w->decorated ? 1 : 0;
        case FLINT_GLFW_FOCUSED: return w->focused ? 1 : 0;
        case FLINT_GLFW_ICONIFIED: return w->iconified ? 1 : 0;
        case FLINT_GLFW_FLOATING: return w->floating ? 1 : 0;
        case FLINT_GLFW_AUTO_ICONIFY: return w->autoIconify ? 1 : 0;
        case FLINT_GLFW_MAXIMIZED_ATTR: return 0;
        case FLINT_GLFW_HOVERED: return 1;
        case FLINT_GLFW_TRANSPARENT_FRAMEBUFFER: return w->transparentFramebuffer ? 1 : 0;
        case FLINT_GLFW_CLIENT_API: return FLINT_GLFW_OPENGL_ES_API;
        case FLINT_GLFW_CONTEXT_VERSION_MAJOR: return w->eglClientVersion;
        case FLINT_GLFW_CONTEXT_VERSION_MINOR: return 0;
        case FLINT_GLFW_OPENGL_FORWARD_COMPAT: return 0;
        case FLINT_GLFW_OPENGL_DEBUG_CONTEXT: return 0;
        case FLINT_GLFW_OPENGL_PROFILE: return FLINT_GLFW_OPENGL_COMPAT_PROFILE;
        case FLINT_GLFW_CONTEXT_ROBUSTNESS: return FLINT_GLFW_NO_ROBUSTNESS;
        case FLINT_GLFW_SAMPLES: return 0;
        case FLINT_GLFW_RED_BITS: return 8;
        case FLINT_GLFW_GREEN_BITS: return 8;
        case FLINT_GLFW_BLUE_BITS: return 8;
        case FLINT_GLFW_ALPHA_BITS: return 8;
        case FLINT_GLFW_DEPTH_BITS: return 16;
        case FLINT_GLFW_STENCIL_BITS: return 0;
        case FLINT_GLFW_DOUBLEBUFFER: return 1;
        case FLINT_GLFW_SRGB_CAPABLE: return 0;
        case FLINT_GLFW_STEREO: return 0;
        default:
            glfwReportError(FLINT_GLFW_INVALID_ENUM, "unknown window attrib");
            return 0;
    }
}

void flint_glfwSetWindowAttrib(FLINT_GLFWwindow_ptr window, int attrib, int value) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (!w) return;
    if (attrib == FLINT_GLFW_RESIZABLE) w->resizable = value != 0;
    else if (attrib == FLINT_GLFW_DECORATED) w->decorated = value != 0;
    else if (attrib == FLINT_GLFW_FLOATING) w->floating = value != 0;
    else if (attrib == FLINT_GLFW_AUTO_ICONIFY) w->autoIconify = value != 0;
    else if (attrib == FLINT_GLFW_FOCUS_ON_SHOW) {}
    else glfwReportError(FLINT_GLFW_INVALID_ENUM, "setWindowAttrib: unsupported attrib");
}

void flint_glfwIconifyWindow(FLINT_GLFWwindow_ptr window) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (w) { w->iconified = true; InputEvent ev; ev.type=InputEventType::WindowIconify; ev.ival1=1; globalInputQueue().push(ev); }
}
void flint_glfwRestoreWindow(FLINT_GLFWwindow_ptr window) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (w) { w->iconified = false; InputEvent ev; ev.type=InputEventType::WindowIconify; ev.ival1=0; globalInputQueue().push(ev); }
}
void flint_glfwMaximizeWindow(FLINT_GLFWwindow_ptr /*window*/) {}
void flint_glfwShowWindow(FLINT_GLFWwindow_ptr window) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(window); if(w) w->visible=true; }
void flint_glfwHideWindow(FLINT_GLFWwindow_ptr window) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(window); if(w) w->visible=false; }
void flint_glfwFocusWindow(FLINT_GLFWwindow_ptr window) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(window); if(w) w->focused=true; }
void flint_glfwRequestWindowAttention(FLINT_GLFWwindow_ptr /*window*/) {}

static FLINT_GLFWvidmode g_videoMode = { 1920, 1080, 8, 8, 8, 60 };
FLINT_GLFWmonitor_ptr flint_glfwGetPrimaryMonitor(void) {
    return reinterpret_cast<FLINT_GLFWmonitor_ptr>(1);
}
FLINT_GLFWmonitor_ptr* flint_glfwGetMonitors(int* count) {
    static FLINT_GLFWmonitor_ptr one[1] = { reinterpret_cast<FLINT_GLFWmonitor_ptr>(1) };
    if (count) *count = 1;
    return one;
}
const FLINT_GLFWvidmode* flint_glfwGetVideoMode(FLINT_GLFWmonitor_ptr /*monitor*/) {
    if (g_window && g_window->framebufferWidth > 0) {
        g_videoMode.width = g_window->framebufferWidth;
        g_videoMode.height = g_window->framebufferHeight;
    }
    return &g_videoMode;
}
const FLINT_GLFWvidmode* flint_glfwGetVideoModes(FLINT_GLFWmonitor_ptr /*monitor*/, int* count) {
    if (count) *count = 1;
    return &g_videoMode;
}
void flint_glfwGetMonitorPhysicalSize(FLINT_GLFWmonitor_ptr /*monitor*/, int* widthMM, int* heightMM) {
    if (widthMM) *widthMM = 120;
    if (heightMM) *heightMM = 70;
}
void flint_glfwGetMonitorPos(FLINT_GLFWmonitor_ptr /*monitor*/, int* xpos, int* ypos) {
    if (xpos) *xpos = 0;
    if (ypos) *ypos = 0;
}
const char* flint_glfwGetMonitorName(FLINT_GLFWmonitor_ptr /*monitor*/) {
    return "Flint Android Display";
}

void flint_glfwMakeContextCurrent(FLINT_GLFWwindow_ptr window) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (!w) {
        if (g_window && g_window->eglDisplay != EGL_NO_DISPLAY) {
            eglMakeCurrent(g_window->eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            g_window->eglCurrent = false;
        }
        g_currentContext = nullptr;
        return;
    }
    if (w->eglDisplay == EGL_NO_DISPLAY || w->eglSurface == EGL_NO_SURFACE || w->eglContext == EGL_NO_CONTEXT) {
        glfwReportError(FLINT_GLFW_NO_CURRENT_CONTEXT, "makeContextCurrent: no EGL surface attached");
        return;
    }
    if (!eglMakeCurrent(w->eglDisplay, w->eglSurface, w->eglSurface, w->eglContext)) {
        char msg[128];
        snprintf(msg, sizeof(msg), "eglMakeCurrent failed: 0x%x", eglGetError());
        glfwReportError(FLINT_GLFW_PLATFORM_ERROR, msg);
        return;
    }
    w->eglCurrent = true;
    g_currentContext = w;
    glViewport(0, 0, w->framebufferWidth, w->framebufferHeight);
}

FLINT_GLFWwindow_ptr flint_glfwGetCurrentContext(void) {
    return reinterpret_cast<FLINT_GLFWwindow_ptr>(g_currentContext.load());
}

void flint_glfwSwapBuffers(FLINT_GLFWwindow_ptr window) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (!w || w->eglDisplay == EGL_NO_DISPLAY || w->eglSurface == EGL_NO_SURFACE) {
        glfwReportError(FLINT_GLFW_PLATFORM_ERROR, "swapBuffers: no surface");
        return;
    }
    if (!eglSwapBuffers(w->eglDisplay, w->eglSurface)) {
        FLINT_LOGW("eglSwapBuffers failed: 0x%x", eglGetError());
    }
}

void flint_glfwSwapInterval(int interval) {
    GlfwWindowState* w = g_window;
    if (!w || w->eglDisplay == EGL_NO_DISPLAY) return;
    eglSwapInterval(w->eglDisplay, interval);
}

int flint_glfwExtensionSupported(const char* extension) {
    return flint_glfwExtensionSupportedNative(extension);
}
int flint_glfwExtensionSupportedNative(const char* extension) {
    if (!extension) return 0;
    GlfwWindowState* w = g_window;
    if (!w || w->eglDisplay == EGL_NO_DISPLAY) return 0;
    const char* exts = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
    if (!exts) return 0;
    std::string hay = exts;
    std::string needle = extension;
    return hay.find(needle) != std::string::npos ? 1 : 0;
}

void* flint_glfwGetProcAddress(const char* procname) {
    return glGetProcAddress(procname);
}

void flint_glfwPollEvents(void) {
    if (!g_window) return;
    static std::vector<InputEvent> drained;
    globalInputQueue().drainAll(drained);
    auto handle = reinterpret_cast<FLINT_GLFWwindow_ptr>(g_window);
    for (const auto& ev : drained) {
        dispatchEvent(g_window, ev, handle);
    }
}

void flint_glfwWaitEvents(void) {
    flint_glfwWaitEventsTimeout(0.1);
}

void flint_glfwWaitEventsTimeout(double timeout) {
    if (globalInputQueue().empty()) {
        struct timespec ts;
        ts.tv_sec = (time_t)timeout;
        ts.tv_nsec = (long)((timeout - ts.tv_sec) * 1e9);
        nanosleep(&ts, nullptr);
    }
    flint_glfwPollEvents();
}

void flint_glfwPostEmptyEvent(void) {
    InputEvent ev;
    ev.type = InputEventType::None;
    globalInputQueue().push(ev);
}

int flint_glfwGetKey(FLINT_GLFWwindow_ptr window, int key) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (!w || key < 0 || key > FLINT_GLFW_KEY_LAST) return FLINT_GLFW_RELEASE;
    return w->keyState[key];
}
int flint_glfwGetMouseButton(FLINT_GLFWwindow_ptr window, int button) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (!w || button < 0 || button >= 8) return FLINT_GLFW_RELEASE;
    return w->mouseButtonState[button];
}
void flint_glfwGetCursorPos(FLINT_GLFWwindow_ptr window, double* xpos, double* ypos) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (xpos) *xpos = w ? w->cursorX : 0.0;
    if (ypos) *ypos = w ? w->cursorY : 0.0;
}
void flint_glfwSetCursorPos(FLINT_GLFWwindow_ptr window, double xpos, double ypos) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (!w) return;
    w->cursorX = xpos;
    w->cursorY = ypos;
    InputEvent ev;
    ev.type = InputEventType::CursorPos;
    ev.dval1 = xpos; ev.dval2 = ypos;
    globalInputQueue().push(ev);
}
int flint_glfwGetInputMode(FLINT_GLFWwindow_ptr window, int mode) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (!w) return 0;
    if (mode == FLINT_GLFW_CURSOR) return w->cursorMode;
    if (mode == FLINT_GLFW_STICKY_KEYS) return w->stickyKeys ? 1 : 0;
    if (mode == FLINT_GLFW_STICKY_MOUSE_BUTTONS) return w->stickyMouseButtons ? 1 : 0;
    if (mode == FLINT_GLFW_LOCK_KEY_MODS) return w->lockKeyMods ? 1 : 0;
    if (mode == FLINT_GLFW_RAW_MOUSE_MOTION) return w->rawMouseMotion ? 1 : 0;
    return 0;
}
void flint_glfwSetInputMode(FLINT_GLFWwindow_ptr window, int mode, int value) {
    GlfwWindowState* w = reinterpret_cast<GlfwWindowState*>(window);
    if (!w) return;
    if (mode == FLINT_GLFW_CURSOR) {
        w->cursorMode = value;
        setMouseCaptureState(value);
    } else if (mode == FLINT_GLFW_STICKY_KEYS) w->stickyKeys = value != 0;
    else if (mode == FLINT_GLFW_STICKY_MOUSE_BUTTONS) w->stickyMouseButtons = value != 0;
    else if (mode == FLINT_GLFW_LOCK_KEY_MODS) w->lockKeyMods = value != 0;
    else if (mode == FLINT_GLFW_RAW_MOUSE_MOTION) w->rawMouseMotion = value != 0;
}
int flint_glfwRawMouseMotionSupported(void) { return 0; }

FLINT_GLFWcursor_ptr flint_glfwCreateCursor(const void* /*image*/, int /*xhot*/, int /*yhot*/) {
    return reinterpret_cast<FLINT_GLFWcursor_ptr>(1);
}
FLINT_GLFWcursor_ptr flint_glfwCreateStandardCursor(int /*shape*/) {
    return reinterpret_cast<FLINT_GLFWcursor_ptr>(1);
}
void flint_glfwDestroyCursor(FLINT_GLFWcursor_ptr /*cursor*/) {}
void flint_glfwSetCursor(FLINT_GLFWwindow_ptr /*window*/, FLINT_GLFWcursor_ptr /*cursor*/) {}

const char* flint_glfwGetKeyName(int key, int /*scancode*/) {
    static char buf[2] = {0,0};
    if (key >= 32 && key < 127) { buf[0] = (char)key; return buf; }
    return nullptr;
}
int flint_glfwGetKeyScancode(int /*key*/) { return -1; }

void flint_glfwSetClipboardString(FLINT_GLFWwindow_ptr /*window*/, const char* text) {
    if (g_window && text) g_window->clipboard = text;
}
const char* flint_glfwGetClipboardString(FLINT_GLFWwindow_ptr /*window*/) {
    return g_window ? g_window->clipboard.c_str() : "";
}

double flint_glfwGetTime(void) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    double elapsed = (now.tv_sec - g_timeBase.tv_sec) + (now.tv_nsec - g_timeBase.tv_nsec) * 1e-9;
    return elapsed + g_timeOffset;
}
void flint_glfwSetTime(double time) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    double elapsed = (now.tv_sec - g_timeBase.tv_sec) + (now.tv_nsec - g_timeBase.tv_nsec) * 1e-9;
    g_timeOffset = time - elapsed;
}
uint64_t flint_glfwGetTimerValue(void) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (uint64_t)now.tv_sec * 1000000000ULL + (uint64_t)now.tv_nsec;
}
uint64_t flint_glfwGetTimerFrequency(void) { return 1000000000ULL; }

void flint_glfwGetVersion(int* major, int* minor, int* rev) {
    if (major) *major = 3;
    if (minor) *minor = 4;
    if (rev) *rev = 0;
}
const char* flint_glfwGetVersionString(void) { return kVersionString; }

FLINT_GLFWerrorfun flint_glfwSetErrorCallback(FLINT_GLFWerrorfun cb) {
    FLINT_GLFWerrorfun prev = g_window ? g_window->cbError : nullptr;
    if (g_window) g_window->cbError = cb;
    return prev;
}

#define SET_CB(field, cbtype, cbval) \
    { cbtype prev = w ? w->field : nullptr; if (w) w->field = cbval; return prev; }

FLINT_GLFWwindowposfun flint_glfwSetWindowPosCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWwindowposfun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbWindowPos, FLINT_GLFWwindowposfun, cb); }
FLINT_GLFWwindowsizefun flint_glfwSetWindowSizeCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWwindowsizefun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbWindowSize, FLINT_GLFWwindowsizefun, cb); }
FLINT_GLFWwindowclosefun flint_glfwSetWindowCloseCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWwindowclosefun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbWindowClose, FLINT_GLFWwindowclosefun, cb); }
FLINT_GLFWwindowrefreshfun flint_glfwSetWindowRefreshCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWwindowrefreshfun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbWindowRefresh, FLINT_GLFWwindowrefreshfun, cb); }
FLINT_GLFWwindowfocusfun flint_glfwSetWindowFocusCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWwindowfocusfun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbWindowFocus, FLINT_GLFWwindowfocusfun, cb); }
FLINT_GLFWwindowiconifyfun flint_glfwSetWindowIconifyCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWwindowiconifyfun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbWindowIconify, FLINT_GLFWwindowiconifyfun, cb); }
FLINT_GLFWwindowmaximizefun flint_glfwSetWindowMaximizeCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWwindowmaximizefun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbWindowMaximize, FLINT_GLFWwindowmaximizefun, cb); }
FLINT_GLFWframebuffersizefun flint_glfwSetFramebufferSizeCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWframebuffersizefun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbFramebufferSize, FLINT_GLFWframebuffersizefun, cb); }
FLINT_GLFWmousebuttonfun flint_glfwSetMouseButtonCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWmousebuttonfun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbMouseButton, FLINT_GLFWmousebuttonfun, cb); }
FLINT_GLFWcursorposfun flint_glfwSetCursorPosCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWcursorposfun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbCursorPos, FLINT_GLFWcursorposfun, cb); }
FLINT_GLFWcursorenterfun flint_glfwSetCursorEnterCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWcursorenterfun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbCursorEnter, FLINT_GLFWcursorenterfun, cb); }
FLINT_GLFWscrollfun flint_glfwSetScrollCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWscrollfun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbScroll, FLINT_GLFWscrollfun, cb); }
FLINT_GLFWkeyfun flint_glfwSetKeyCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWkeyfun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbKey, FLINT_GLFWkeyfun, cb); }
FLINT_GLFWcharfun flint_glfwSetCharCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWcharfun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbChar, FLINT_GLFWcharfun, cb); }
FLINT_GLFWcharmodsfun flint_glfwSetCharModsCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWcharmodsfun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbCharMods, FLINT_GLFWcharmodsfun, cb); }
FLINT_GLFWdropfun flint_glfwSetDropCallback(FLINT_GLFWwindow_ptr win, FLINT_GLFWdropfun cb) { GlfwWindowState* w=reinterpret_cast<GlfwWindowState*>(win); SET_CB(cbDrop, FLINT_GLFWdropfun, cb); }
FLINT_GLFWmonitorfun flint_glfwSetMonitorCallback(FLINT_GLFWmonitorfun cb) { FLINT_GLFWmonitorfun prev = g_window ? g_window->cbMonitor : nullptr; if (g_window) g_window->cbMonitor = cb; return prev; }
FLINT_GLFWjoystickfun flint_glfwSetJoystickCallback(FLINT_GLFWjoystickfun cb) { FLINT_GLFWjoystickfun prev = g_window ? g_window->cbJoystick : nullptr; if (g_window) g_window->cbJoystick = cb; return prev; }

int flint_glfwJoystickPresent(int /*jid*/) { return 0; }
const float* flint_glfwGetJoystickAxes(int /*jid*/, int* count) { if (count) *count=0; return nullptr; }
const unsigned char* flint_glfwGetJoystickButtons(int /*jid*/, int* count) { if (count) *count=0; return nullptr; }
const char* flint_glfwGetJoystickName(int /*jid*/) { return nullptr; }
int flint_glfwJoystickIsGamepad(int /*jid*/) { return 0; }
const char* flint_glfwGetGamepadName(int /*jid*/) { return nullptr; }

} // extern "C"

// ---------------------------------------------------------------------------
// JNI entry points for GLFW lifecycle (Kotlin: NativeBridge.nativeGlfw*).
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwInit(JNIEnv*, jclass) {
    return flint_glfwInit() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwTerminate(JNIEnv*, jclass) {
    flint_glfwTerminate();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwAttachSurface(
    JNIEnv* env, jclass, jobject surface, jint width, jint height) {
    if (!surface) return env->NewStringUTF("surface-null");
    ANativeWindow* anw = ANativeWindow_fromSurface(env, surface);
    if (!anw) return env->NewStringUTF("native-window-unavailable");
    bool ok = flint::glfwAttachAndroidSurface(anw, width, height);
    if (!ok) {
        ANativeWindow_release(anw);
        return env->NewStringUTF("glfw-attach-failed");
    }
    return env->NewStringUTF("glfw-window-ready");
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwDetachSurface(JNIEnv*, jclass) {
    flint::glfwDetachAndroidSurface();
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwPollEvents(JNIEnv*, jclass) {
    flint_glfwPollEvents();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwWindowShouldClose(JNIEnv*, jclass) {
    return flint_glfwWindowShouldClose(reinterpret_cast<FLINT_GLFWwindow_ptr>(flint::glfwWindowInstance())) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwSwapBuffers(JNIEnv*, jclass) {
    flint_glfwSwapBuffers(reinterpret_cast<FLINT_GLFWwindow_ptr>(flint::glfwWindowInstance()));
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwMakeContextCurrent(JNIEnv*, jclass) {
    flint_glfwMakeContextCurrent(reinterpret_cast<FLINT_GLFWwindow_ptr>(flint::glfwWindowInstance()));
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwGetTime(JNIEnv*, jclass) {
    return flint_glfwGetTime();
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwPostResize(JNIEnv*, jclass, jint w, jint h) {
    flint::glfwPostSurfaceResize(w, h);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwPostClose(JNIEnv*, jclass) {
    flint::glfwPostCloseRequest();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwVersionString(JNIEnv* env, jclass) {
    return env->NewStringUTF(flint_glfwGetVersionString());
}
