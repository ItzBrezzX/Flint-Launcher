#include "flint_glfw_internal.h"

#include <time.h>
#include <stdint.h>
#include <string.h>
#include <mutex>
#include <new>

#if defined(__GNUC__)
#define GLFW_EXPORT __attribute__((visibility("default")))
#else
#define GLFW_EXPORT
#endif

struct GLFWwindow;
using GLFWerrorfun = void (*)(int, const char *);
using GLFWwindowposfun = void (*)(GLFWwindow *, int, int);
using GLFWwindowsizefun = void (*)(GLFWwindow *, int, int);
using GLFWwindowclosefun = void (*)(GLFWwindow *);
using GLFWwindowrefreshfun = void (*)(GLFWwindow *);
using GLFWwindowfocusfun = void (*)(GLFWwindow *, int);
using GLFWwindowiconifyfun = void (*)(GLFWwindow *, int);
using GLFWwindowmaximizefun = void (*)(GLFWwindow *, int);
using GLFWframebuffersizefun = void (*)(GLFWwindow *, int, int);
using GLFWwindowcontentscalefun = void (*)(GLFWwindow *, float, float);
using GLFWkeyfun = void (*)(GLFWwindow *, int, int, int, int);
using GLFWcharfun = void (*)(GLFWwindow *, unsigned int);
using GLFWcursorposfun = void (*)(GLFWwindow *, double, double);
using GLFWcursorenterfun = void (*)(GLFWwindow *, int);
using GLFWmousebuttonfun = void (*)(GLFWwindow *, int, int, int);
using GLFWscrollfun = void (*)(GLFWwindow *, double, double);
using GLFWdropfun = void (*)(GLFWwindow *, int, const char **);

struct GLFWwindow {
    int width = 0;
    int height = 0;
    bool shouldClose = false;
    bool focused = false;
    void *userPointer = nullptr;
    GLFWwindowposfun positionCallback = nullptr;
    GLFWwindowsizefun sizeCallback = nullptr;
    GLFWwindowclosefun closeCallback = nullptr;
    GLFWwindowrefreshfun refreshCallback = nullptr;
    GLFWwindowfocusfun focusCallback = nullptr;
    GLFWwindowiconifyfun iconifyCallback = nullptr;
    GLFWwindowmaximizefun maximizeCallback = nullptr;
    GLFWframebuffersizefun framebufferSizeCallback = nullptr;
    GLFWwindowcontentscalefun contentScaleCallback = nullptr;
    GLFWkeyfun keyCallback = nullptr;
    GLFWcharfun charCallback = nullptr;
    GLFWcursorposfun cursorPositionCallback = nullptr;
    GLFWcursorenterfun cursorEnterCallback = nullptr;
    GLFWmousebuttonfun mouseButtonCallback = nullptr;
    GLFWscrollfun scrollCallback = nullptr;
    GLFWdropfun dropCallback = nullptr;
};

namespace {
std::mutex g_glfw_mutex;
GLFWwindow *g_window = nullptr;
GLFWwindow *g_current_context = nullptr;
bool g_initialized = false;
GLFWerrorfun g_error_callback = nullptr;
int g_last_error = 0;
const char *g_last_error_description = nullptr;
timespec g_time_origin{};
double g_time_offset = 0.0;

void set_error(int code, const char *description) {
    g_last_error = code;
    g_last_error_description = description;
    if (g_error_callback) g_error_callback(code, description);
}

double monotonic_seconds() {
    timespec now{};
    clock_gettime(CLOCK_MONOTONIC, &now);
    return static_cast<double>(now.tv_sec) + static_cast<double>(now.tv_nsec) / 1.0e9;
}

float bits_to_float(int32_t bits) {
    float value = 0.0f;
    memcpy(&value, &bits, sizeof(value));
    return value;
}

int32_t float_to_bits(float value) {
    int32_t bits = 0;
    memcpy(&bits, &value, sizeof(bits));
    return bits;
}

GLFWwindow *active_window() {
    std::lock_guard<std::mutex> lock(g_glfw_mutex);
    return g_window;
}

void update_dimensions(GLFWwindow *window, int width, int height) {
    if (!window) return;
    window->width = width > 0 ? width : 0;
    window->height = height > 0 ? height : 0;
}
} // namespace

extern "C" {

GLFW_EXPORT int glfwInit(void) {
    std::lock_guard<std::mutex> lock(g_glfw_mutex);
    g_initialized = true;
    clock_gettime(CLOCK_MONOTONIC, &g_time_origin);
    g_time_offset = 0.0;
    return 1;
}

GLFW_EXPORT void glfwTerminate(void) {
    std::lock_guard<std::mutex> lock(g_glfw_mutex);
    delete g_window;
    g_window = nullptr;
    g_current_context = nullptr;
    g_initialized = false;
    flint_glfw_reset_window_state();
}

GLFW_EXPORT void glfwInitHint(int, int) {}
GLFW_EXPORT int glfwGetInitHint(int) { return 0; }

GLFW_EXPORT void glfwGetVersion(int *major, int *minor, int *revision) {
    if (major) *major = 3;
    if (minor) *minor = 3;
    if (revision) *revision = 8;
}

GLFW_EXPORT const char *glfwGetVersionString(void) {
    return "3.3.8 Flint Android EGL backend";
}

GLFW_EXPORT GLFWerrorfun glfwSetErrorCallback(GLFWerrorfun callback) {
    std::lock_guard<std::mutex> lock(g_glfw_mutex);
    GLFWerrorfun previous = g_error_callback;
    g_error_callback = callback;
    return previous;
}

GLFW_EXPORT void glfwDefaultWindowHints(void) {}
GLFW_EXPORT void glfwWindowHint(int, int) {}

GLFW_EXPORT GLFWwindow *glfwCreateWindow(
    int width, int height, const char *, void *monitor, GLFWwindow *share
) {
    if (width <= 0 || height <= 0) {
        set_error(0x00010008, "Window dimensions must be positive");
        return nullptr;
    }
    if (monitor != nullptr || share != nullptr) {
        set_error(0x00010006, "Fullscreen monitors and shared contexts are not supported by the Android surface backend");
        return nullptr;
    }
    if (!flint_egl_window_is_ready()) {
        set_error(0x00010008, "No Android game Surface is attached to the EGL backend");
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(g_glfw_mutex);
    if (!g_initialized) {
        set_error(0x00010001, "glfwInit must be called before glfwCreateWindow");
        return nullptr;
    }
    if (g_window != nullptr) {
        set_error(0x00010008, "Flint Android backend supports one active game window");
        return nullptr;
    }
    auto *window = new (std::nothrow) GLFWwindow();
    if (!window) {
        set_error(0x00010008, "Could not allocate GLFWwindow");
        return nullptr;
    }
    int32_t surfaceWidth = 0, surfaceHeight = 0;
    flint_egl_window_get_size(&surfaceWidth, &surfaceHeight);
    window->width = surfaceWidth > 0 ? surfaceWidth : width;
    window->height = surfaceHeight > 0 ? surfaceHeight : height;
    int32_t stateWidth = 0, stateHeight = 0;
    bool focused = false;
    flint_glfw_get_window_state(&stateWidth, &stateHeight, &focused);
    window->focused = focused;
    if (stateWidth > 0 && stateHeight > 0) update_dimensions(window, stateWidth, stateHeight);
    g_window = window;
    return window;
}

GLFW_EXPORT void glfwDestroyWindow(GLFWwindow *window) {
    std::lock_guard<std::mutex> lock(g_glfw_mutex);
    if (window == nullptr || window != g_window) return;
    if (g_current_context == window) {
        g_current_context = nullptr;
    }
    delete g_window;
    g_window = nullptr;
}

GLFW_EXPORT void glfwMakeContextCurrent(GLFWwindow *window) {
    if (window == nullptr) {
        g_current_context = nullptr;
        return;
    }
    if (window != active_window() || !flint_egl_window_make_current()) {
        set_error(0x00010008, "Could not make the Android EGL context current");
        return;
    }
    g_current_context = window;
}

GLFW_EXPORT GLFWwindow *glfwGetCurrentContext(void) {
    return g_current_context;
}

GLFW_EXPORT void glfwSwapBuffers(GLFWwindow *window) {
    if (window == nullptr || window != active_window()) {
        set_error(0x00010008, "glfwSwapBuffers called for an inactive window");
        return;
    }
    if (!flint_egl_window_swap_buffers()) {
        set_error(0x00010008, "eglSwapBuffers failed for the Android game Surface");
    }
}

GLFW_EXPORT void glfwSwapInterval(int) {}

GLFW_EXPORT void glfwPollEvents(void) {
    GLFWwindow *window = active_window();
    if (!window) return;
    FlintGlfwEvent event{};
    while (flint_glfw_poll_event(&event)) {
        switch (event.type) {
            case 1:
                if (window->keyCallback) window->keyCallback(window, event.a, event.b, event.c, event.d);
                break;
            case 2:
                if (window->charCallback && event.a >= 0) {
                    window->charCallback(window, static_cast<unsigned int>(event.a));
                }
                break;
            case 3:
                if (window->cursorPositionCallback) {
                    window->cursorPositionCallback(window, bits_to_float(event.a), bits_to_float(event.b));
                }
                break;
            case 4:
                if (window->mouseButtonCallback) {
                    window->mouseButtonCallback(window, event.a, event.b, event.c);
                }
                break;
            case 5:
                if (window->scrollCallback) {
                    window->scrollCallback(window, bits_to_float(event.a), bits_to_float(event.b));
                }
                break;
            case 6:
                window->focused = event.a != 0;
                if (window->focusCallback) window->focusCallback(window, window->focused ? 1 : 0);
                break;
            case 7:
                update_dimensions(window, event.a, event.b);
                if (window->sizeCallback) window->sizeCallback(window, window->width, window->height);
                if (window->framebufferSizeCallback) {
                    window->framebufferSizeCallback(window, window->width, window->height);
                }
                break;
            case 8:
                window->shouldClose = true;
                if (window->closeCallback) window->closeCallback(window);
                break;
            default:
                break;
        }
    }
    if (flint_glfw_window_should_close()) window->shouldClose = true;
}

GLFW_EXPORT void glfwWaitEvents(void) { glfwPollEvents(); }
GLFW_EXPORT void glfwWaitEventsTimeout(double) { glfwPollEvents(); }
GLFW_EXPORT void glfwPostEmptyEvent(void) {}

GLFW_EXPORT int glfwWindowShouldClose(GLFWwindow *window) {
    return window == nullptr || window->shouldClose || flint_glfw_window_should_close();
}

GLFW_EXPORT void glfwSetWindowShouldClose(GLFWwindow *window, int value) {
    if (!window) return;
    window->shouldClose = value != 0;
    flint_glfw_set_window_should_close(value != 0);
}

GLFW_EXPORT void glfwGetWindowSize(GLFWwindow *window, int *width, int *height) {
    int32_t w = 0, h = 0;
    flint_egl_window_get_size(&w, &h);
    if (w <= 0 && window) w = window->width;
    if (h <= 0 && window) h = window->height;
    if (width) *width = w;
    if (height) *height = h;
}

GLFW_EXPORT void glfwSetWindowSize(GLFWwindow *, int, int) {
    set_error(0x00010008, "Android game Surface dimensions are controlled by the host view");
}

GLFW_EXPORT void glfwGetFramebufferSize(GLFWwindow *window, int *width, int *height) {
    glfwGetWindowSize(window, width, height);
}

GLFW_EXPORT void glfwGetWindowContentScale(GLFWwindow *, float *xscale, float *yscale) {
    if (xscale) *xscale = 1.0f;
    if (yscale) *yscale = 1.0f;
}

GLFW_EXPORT void glfwGetWindowPos(GLFWwindow *, int *xpos, int *ypos) {
    if (xpos) *xpos = 0;
    if (ypos) *ypos = 0;
}
GLFW_EXPORT void glfwSetWindowPos(GLFWwindow *, int, int) {}
GLFW_EXPORT void glfwSetWindowTitle(GLFWwindow *, const char *) {}
GLFW_EXPORT void glfwSetWindowIcon(GLFWwindow *, int, const void *) {}
GLFW_EXPORT void glfwGetWindowFrameSize(GLFWwindow *, int *left, int *top, int *right, int *bottom) {
    if (left) *left = 0;
    if (top) *top = 0;
    if (right) *right = 0;
    if (bottom) *bottom = 0;
}

GLFW_EXPORT int glfwGetWindowAttrib(GLFWwindow *window, int attrib) {
    if (!window) return 0;
    if (attrib == 0x00020001) return window->focused ? 1 : 0; // GLFW_FOCUSED
    if (attrib == 0x00020004) return 1; // GLFW_VISIBLE
    if (attrib == 0x00020003) return 1; // GLFW_RESIZABLE (host surface can resize)
    return 0;
}

GLFW_EXPORT void glfwSetWindowAttrib(GLFWwindow *, int, int) {}
GLFW_EXPORT void glfwSetWindowUserPointer(GLFWwindow *window, void *pointer) {
    if (window) window->userPointer = pointer;
}
GLFW_EXPORT void *glfwGetWindowUserPointer(GLFWwindow *window) {
    return window ? window->userPointer : nullptr;
}

GLFW_EXPORT int glfwGetInputMode(GLFWwindow *, int) { return 0; }
GLFW_EXPORT void glfwSetInputMode(GLFWwindow *, int, int) {}
GLFW_EXPORT int glfwRawMouseMotionSupported(void) { return 0; }
GLFW_EXPORT int glfwGetKey(GLFWwindow *, int key) { return flint_glfw_get_key_state(key); }
GLFW_EXPORT int glfwGetMouseButton(GLFWwindow *, int button) { return flint_glfw_get_mouse_button_state(button); }
GLFW_EXPORT void glfwGetCursorPos(GLFWwindow *, double *xpos, double *ypos) {
    float x = 0.0f, y = 0.0f;
    flint_glfw_get_cursor_position(&x, &y);
    if (xpos) *xpos = x;
    if (ypos) *ypos = y;
}
GLFW_EXPORT void glfwSetCursorPos(GLFWwindow *, double xpos, double ypos) {
    flint_glfw_post_event(3, float_to_bits(static_cast<float>(xpos)), float_to_bits(static_cast<float>(ypos)), 0, 0);
}
GLFW_EXPORT int glfwGetKeyScancode(int) { return -1; }
GLFW_EXPORT const char *glfwGetKeyName(int, int) { return nullptr; }
GLFW_EXPORT void glfwSetCursor(GLFWwindow *, void *) {}
GLFW_EXPORT void *glfwCreateStandardCursor(int) { return nullptr; }
GLFW_EXPORT void glfwDestroyCursor(void *) {}

GLFW_EXPORT void *glfwGetPrimaryMonitor(void) { return nullptr; }
GLFW_EXPORT void **glfwGetMonitors(int *count) {
    if (count) *count = 0;
    return nullptr;
}
GLFW_EXPORT void *glfwGetWindowMonitor(GLFWwindow *) { return nullptr; }
GLFW_EXPORT void glfwSetWindowMonitor(GLFWwindow *, void *, int, int, int, int, int) {
    set_error(0x00010006, "Android game Surface does not support GLFW monitor switching");
}
GLFW_EXPORT const void *glfwGetVideoMode(void *) { return nullptr; }
GLFW_EXPORT const void *glfwGetVideoModes(void *, int *count) {
    if (count) *count = 0;
    return nullptr;
}

GLFW_EXPORT void *glfwGetProcAddress(const char *name) {
    return flint_egl_get_proc_address(name);
}
GLFW_EXPORT int glfwExtensionSupported(const char *) { return 0; }

GLFW_EXPORT double glfwGetTime(void) {
    return monotonic_seconds() - (static_cast<double>(g_time_origin.tv_sec) +
        static_cast<double>(g_time_origin.tv_nsec) / 1.0e9) + g_time_offset;
}
GLFW_EXPORT void glfwSetTime(double time) {
    g_time_offset = time - (monotonic_seconds() - (static_cast<double>(g_time_origin.tv_sec) +
        static_cast<double>(g_time_origin.tv_nsec) / 1.0e9));
}
GLFW_EXPORT uint64_t glfwGetTimerValue(void) {
    timespec now{};
    clock_gettime(CLOCK_MONOTONIC, &now);
    return static_cast<uint64_t>(now.tv_sec) * 1000000000ULL + static_cast<uint64_t>(now.tv_nsec);
}
GLFW_EXPORT uint64_t glfwGetTimerFrequency(void) { return 1000000000ULL; }
GLFW_EXPORT int glfwGetError(const char **description) {
    if (description) *description = g_last_error_description;
    int code = g_last_error;
    g_last_error = 0;
    g_last_error_description = nullptr;
    return code;
}

#define GLFW_SET_CALLBACK(name, field, type) \
GLFW_EXPORT type name(GLFWwindow *window, type callback) { \
    if (!window) return nullptr; \
    type previous = window->field; \
    window->field = callback; \
    return previous; \
}
GLFW_SET_CALLBACK(glfwSetWindowPosCallback, positionCallback, GLFWwindowposfun)
GLFW_SET_CALLBACK(glfwSetWindowSizeCallback, sizeCallback, GLFWwindowsizefun)
GLFW_SET_CALLBACK(glfwSetWindowCloseCallback, closeCallback, GLFWwindowclosefun)
GLFW_SET_CALLBACK(glfwSetWindowRefreshCallback, refreshCallback, GLFWwindowrefreshfun)
GLFW_SET_CALLBACK(glfwSetWindowFocusCallback, focusCallback, GLFWwindowfocusfun)
GLFW_SET_CALLBACK(glfwSetWindowIconifyCallback, iconifyCallback, GLFWwindowiconifyfun)
GLFW_SET_CALLBACK(glfwSetWindowMaximizeCallback, maximizeCallback, GLFWwindowmaximizefun)
GLFW_SET_CALLBACK(glfwSetFramebufferSizeCallback, framebufferSizeCallback, GLFWframebuffersizefun)
GLFW_SET_CALLBACK(glfwSetWindowContentScaleCallback, contentScaleCallback, GLFWwindowcontentscalefun)
GLFW_SET_CALLBACK(glfwSetKeyCallback, keyCallback, GLFWkeyfun)
GLFW_SET_CALLBACK(glfwSetCharCallback, charCallback, GLFWcharfun)
GLFW_SET_CALLBACK(glfwSetCursorPosCallback, cursorPositionCallback, GLFWcursorposfun)
GLFW_SET_CALLBACK(glfwSetCursorEnterCallback, cursorEnterCallback, GLFWcursorenterfun)
GLFW_SET_CALLBACK(glfwSetMouseButtonCallback, mouseButtonCallback, GLFWmousebuttonfun)
GLFW_SET_CALLBACK(glfwSetScrollCallback, scrollCallback, GLFWscrollfun)
GLFW_SET_CALLBACK(glfwSetDropCallback, dropCallback, GLFWdropfun)
#undef GLFW_SET_CALLBACK

GLFW_EXPORT void glfwSetClipboardString(GLFWwindow *, const char *) {}
GLFW_EXPORT const char *glfwGetClipboardString(GLFWwindow *) { return ""; }
GLFW_EXPORT int glfwVulkanSupported(void) { return 0; }
GLFW_EXPORT const char **glfwGetRequiredInstanceExtensions(uint32_t *count) {
    if (count) *count = 0;
    return nullptr;
}
GLFW_EXPORT int glfwPlatformSupported(int) { return 0; }
GLFW_EXPORT int glfwGetPlatform(void) { return 0; }

} // extern "C"
