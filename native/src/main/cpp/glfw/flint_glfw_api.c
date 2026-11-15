/*
 * The exported GLFW C API. Android has exactly one window (the app's Surface), one monitor (the
 * display) and no cursor, so most window-management calls are deliberate no-ops. Every function
 * LWJGL 3.3 can bind is defined: calling a missing one through LWJGL would jump to NULL.
 */
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "flint_glfw_core.h"

#define GLFW_VERSION_STRING "3.4.0 Flint Android"

static int g_initialized = 0;
static GLFWerrorfun g_error_callback = NULL;
static int g_last_error = GLFW_NO_ERROR;
static char g_last_error_text[256];
static GLFWmonitorfun g_monitor_callback = NULL;
static GLFWjoystickfun g_joystick_callback = NULL;
static char *g_clipboard = NULL;
static struct timespec g_time_base;
static __thread GLFWwindow *t_current_context = NULL;

static FlintHint g_window_hints[FLINT_MAX_HINTS];
static int g_window_hint_count = 0;

typedef struct { int dummy; void *user; } MonitorObject;
static MonitorObject g_monitor;
static GLFWvidmode g_video_mode;

struct GLFWcursor { int shape; };

static void error(int code, const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vsnprintf(g_last_error_text, sizeof g_last_error_text, fmt, args);
    va_end(args);
    g_last_error = code;
    if (g_error_callback) g_error_callback(code, g_last_error_text);
}

#define REQUIRE_INIT(ret)                                  \
    do {                                                   \
        if (!g_initialized) {                              \
            error(GLFW_NOT_INITIALIZED, "GLFW is not initialized"); \
            return ret;                                    \
        }                                                  \
    } while (0)

static double now_seconds_raw(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec + (double)ts.tv_nsec / 1e9;
}

static void hint_set(FlintHint *list, int *count, int id, int value) {
    for (int i = 0; i < *count; i++) {
        if (list[i].id == id) { list[i].value = value; return; }
    }
    if (*count < FLINT_MAX_HINTS) {
        list[*count].id = id;
        list[*count].value = value;
        (*count)++;
    }
}

static int hint_get(const FlintHint *list, int count, int id, int fallback) {
    for (int i = 0; i < count; i++) if (list[i].id == id) return list[i].value;
    return fallback;
}

/* ============================================================ init / meta */

GLFWAPI int glfwInit(void) {
    if (g_initialized) return GLFW_TRUE;
    if (!flint_get_platform()) {
        error(GLFW_PLATFORM_ERROR, "Flint GLFW shim has no platform backend");
        return GLFW_FALSE;
    }
    clock_gettime(CLOCK_MONOTONIC, &g_time_base);
    memset(&g_monitor, 0, sizeof g_monitor);
    g_window_hint_count = 0;
    g_initialized = 1;
    return GLFW_TRUE;
}

GLFWAPI void glfwTerminate(void) {
    if (!g_initialized) return;
    GLFWwindow *window = flint_window();
    if (window) {
        const FlintPlatform *p = flint_get_platform();
        if (window->has_context && p && p->destroy_context) p->destroy_context();
        flint_free_window(window);
        flint_set_window(NULL);
    }
    t_current_context = NULL;
    free(g_clipboard);
    g_clipboard = NULL;
    flint_event_clear();
    g_initialized = 0;
}

GLFWAPI void glfwInitHint(int hint, int value) { (void)hint; (void)value; }
GLFWAPI void glfwInitAllocator(const void *allocator) { (void)allocator; }
GLFWAPI void glfwInitVulkanLoader(void *loader) { (void)loader; }

GLFWAPI void glfwGetVersion(int *major, int *minor, int *rev) {
    if (major) *major = GLFW_VERSION_MAJOR;
    if (minor) *minor = GLFW_VERSION_MINOR;
    if (rev) *rev = GLFW_VERSION_REVISION;
}
GLFWAPI const char *glfwGetVersionString(void) { return GLFW_VERSION_STRING; }

GLFWAPI int glfwGetError(const char **description) {
    int code = g_last_error;
    if (description) *description = code ? g_last_error_text : NULL;
    g_last_error = GLFW_NO_ERROR;
    return code;
}

GLFWAPI GLFWerrorfun glfwSetErrorCallback(GLFWerrorfun callback) {
    GLFWerrorfun previous = g_error_callback;
    g_error_callback = callback;
    return previous;
}

GLFWAPI int glfwGetPlatform(void) { return GLFW_PLATFORM_NULL; }
GLFWAPI int glfwPlatformSupported(int platform) { return platform == GLFW_PLATFORM_NULL ? GLFW_TRUE : GLFW_FALSE; }

/* ================================================================ monitors */

static void refresh_video_mode(void) {
    int w = 1280, h = 720;
    const FlintPlatform *p = flint_get_platform();
    int sw = 0, sh = 0;
    if (p && p->get_surface_size && p->get_surface_size(&sw, &sh) && sw > 0 && sh > 0) {
        w = sw;
        h = sh;
    }
    g_video_mode.width = w;
    g_video_mode.height = h;
    g_video_mode.redBits = 8;
    g_video_mode.greenBits = 8;
    g_video_mode.blueBits = 8;
    g_video_mode.refreshRate = 60;
}

GLFWAPI GLFWmonitor **glfwGetMonitors(int *count) {
    static GLFWmonitor *list[1];
    list[0] = (GLFWmonitor *)&g_monitor;
    if (count) *count = 1;
    return list;
}
GLFWAPI GLFWmonitor *glfwGetPrimaryMonitor(void) { return (GLFWmonitor *)&g_monitor; }
GLFWAPI void glfwGetMonitorPos(GLFWmonitor *m, int *x, int *y) { (void)m; if (x) *x = 0; if (y) *y = 0; }
GLFWAPI void glfwGetMonitorWorkarea(GLFWmonitor *m, int *x, int *y, int *w, int *h) {
    (void)m;
    refresh_video_mode();
    if (x) *x = 0;
    if (y) *y = 0;
    if (w) *w = g_video_mode.width;
    if (h) *h = g_video_mode.height;
}
GLFWAPI void glfwGetMonitorPhysicalSize(GLFWmonitor *m, int *w, int *h) { (void)m; if (w) *w = 150; if (h) *h = 70; }
GLFWAPI void glfwGetMonitorContentScale(GLFWmonitor *m, float *x, float *y) { (void)m; if (x) *x = 1.0f; if (y) *y = 1.0f; }
GLFWAPI const char *glfwGetMonitorName(GLFWmonitor *m) { (void)m; return "Android Display"; }
GLFWAPI void glfwSetMonitorUserPointer(GLFWmonitor *m, void *p) { if (m) ((MonitorObject *)m)->user = p; }
GLFWAPI void *glfwGetMonitorUserPointer(GLFWmonitor *m) { return m ? ((MonitorObject *)m)->user : NULL; }
GLFWAPI GLFWmonitorfun glfwSetMonitorCallback(GLFWmonitorfun cb) {
    GLFWmonitorfun previous = g_monitor_callback;
    g_monitor_callback = cb;
    return previous;
}
GLFWAPI const GLFWvidmode *glfwGetVideoModes(GLFWmonitor *m, int *count) {
    (void)m;
    refresh_video_mode();
    if (count) *count = 1;
    return &g_video_mode;
}
GLFWAPI const GLFWvidmode *glfwGetVideoMode(GLFWmonitor *m) {
    (void)m;
    refresh_video_mode();
    return &g_video_mode;
}
GLFWAPI void glfwSetGamma(GLFWmonitor *m, float gamma) { (void)m; (void)gamma; }
GLFWAPI const GLFWgammaramp *glfwGetGammaRamp(GLFWmonitor *m) { (void)m; return NULL; }
GLFWAPI void glfwSetGammaRamp(GLFWmonitor *m, const GLFWgammaramp *r) { (void)m; (void)r; }

/* ================================================================== hints */

GLFWAPI void glfwDefaultWindowHints(void) { g_window_hint_count = 0; }
GLFWAPI void glfwWindowHint(int hint, int value) { hint_set(g_window_hints, &g_window_hint_count, hint, value); }
GLFWAPI void glfwWindowHintString(int hint, const char *value) { (void)hint; (void)value; }

/* ================================================================= windows */

GLFWAPI GLFWwindow *glfwCreateWindow(int width, int height, const char *title, GLFWmonitor *monitor, GLFWwindow *share) {
    (void)monitor;
    (void)share;
    REQUIRE_INIT(NULL);
    if (flint_window()) {
        error(GLFW_PLATFORM_ERROR, "Flint supports a single window (the Android surface)");
        return NULL;
    }
    const FlintPlatform *p = flint_get_platform();
    /* The game thread starts long before Android finishes creating the Surface; wait for it. */
    if (!p->wait_for_surface || !p->wait_for_surface(30000)) {
        error(GLFW_PLATFORM_ERROR, "No Android surface became available");
        return NULL;
    }
    int sw = 0, sh = 0;
    if (!p->get_surface_size || !p->get_surface_size(&sw, &sh) || sw <= 0 || sh <= 0) {
        sw = width > 0 ? width : 1280;
        sh = height > 0 ? height : 720;
    }
    GLFWwindow *window = flint_new_window(sw, sh, title);
    if (!window) {
        error(GLFW_OUT_OF_MEMORY, "Out of memory creating window");
        return NULL;
    }
    window->context_hint_count = g_window_hint_count;
    memcpy(window->context_hints, g_window_hints, sizeof(FlintHint) * (size_t)g_window_hint_count);

    if (hint_get(g_window_hints, g_window_hint_count, GLFW_CLIENT_API, GLFW_OPENGL_API) != GLFW_NO_API) {
        if (!p->create_context || !p->create_context()) {
            error(GLFW_VERSION_UNAVAILABLE, "Could not create an OpenGL ES context on this device");
            flint_free_window(window);
            return NULL;
        }
        window->has_context = 1;
    }
    flint_set_window(window);
    return window;
}

GLFWAPI void glfwDestroyWindow(GLFWwindow *window) {
    if (!window || window != flint_window()) return;
    const FlintPlatform *p = flint_get_platform();
    if (t_current_context == window) {
        if (p && p->make_current) p->make_current(0);
        t_current_context = NULL;
    }
    if (window->has_context && p && p->destroy_context) p->destroy_context();
    flint_set_window(NULL);
    flint_free_window(window);
}

GLFWAPI int glfwWindowShouldClose(GLFWwindow *w) { return w ? w->should_close : GLFW_TRUE; }
GLFWAPI void glfwSetWindowShouldClose(GLFWwindow *w, int value) { if (w) w->should_close = value ? GLFW_TRUE : GLFW_FALSE; }
GLFWAPI const char *glfwGetWindowTitle(GLFWwindow *w) { return w ? w->title : NULL; }
GLFWAPI void glfwSetWindowTitle(GLFWwindow *w, const char *title) {
    if (!w) return;
    free(w->title);
    w->title = title ? strdup(title) : NULL;
}
GLFWAPI void glfwSetWindowIcon(GLFWwindow *w, int count, const GLFWimage *images) { (void)w; (void)count; (void)images; }
GLFWAPI void glfwGetWindowPos(GLFWwindow *w, int *x, int *y) { (void)w; if (x) *x = 0; if (y) *y = 0; }
GLFWAPI void glfwSetWindowPos(GLFWwindow *w, int x, int y) { (void)w; (void)x; (void)y; }
GLFWAPI void glfwGetWindowSize(GLFWwindow *w, int *width, int *height) {
    if (width) *width = w ? w->width : 0;
    if (height) *height = w ? w->height : 0;
}
GLFWAPI void glfwSetWindowSizeLimits(GLFWwindow *w, int a, int b, int c, int d) { (void)w; (void)a; (void)b; (void)c; (void)d; }
GLFWAPI void glfwSetWindowAspectRatio(GLFWwindow *w, int n, int d) { (void)w; (void)n; (void)d; }
GLFWAPI void glfwSetWindowSize(GLFWwindow *w, int width, int height) { (void)w; (void)width; (void)height; }
GLFWAPI void glfwGetFramebufferSize(GLFWwindow *w, int *width, int *height) { glfwGetWindowSize(w, width, height); }
GLFWAPI void glfwGetWindowFrameSize(GLFWwindow *w, int *l, int *t, int *r, int *b) {
    (void)w;
    if (l) *l = 0;
    if (t) *t = 0;
    if (r) *r = 0;
    if (b) *b = 0;
}
GLFWAPI void glfwGetWindowContentScale(GLFWwindow *w, float *x, float *y) { (void)w; if (x) *x = 1.0f; if (y) *y = 1.0f; }
GLFWAPI float glfwGetWindowOpacity(GLFWwindow *w) { (void)w; return 1.0f; }
GLFWAPI void glfwSetWindowOpacity(GLFWwindow *w, float o) { (void)w; (void)o; }
GLFWAPI void glfwIconifyWindow(GLFWwindow *w) { (void)w; }
GLFWAPI void glfwRestoreWindow(GLFWwindow *w) { (void)w; }
GLFWAPI void glfwMaximizeWindow(GLFWwindow *w) { (void)w; }
GLFWAPI void glfwShowWindow(GLFWwindow *w) { (void)w; }
GLFWAPI void glfwHideWindow(GLFWwindow *w) { (void)w; }
GLFWAPI void glfwFocusWindow(GLFWwindow *w) { (void)w; }
GLFWAPI void glfwRequestWindowAttention(GLFWwindow *w) { (void)w; }
GLFWAPI GLFWmonitor *glfwGetWindowMonitor(GLFWwindow *w) { (void)w; return NULL; }
GLFWAPI void glfwSetWindowMonitor(GLFWwindow *w, GLFWmonitor *m, int x, int y, int width, int height, int rate) {
    (void)w; (void)m; (void)x; (void)y; (void)width; (void)height; (void)rate;
}
GLFWAPI void glfwSetWindowAttrib(GLFWwindow *w, int attrib, int value) { (void)w; (void)attrib; (void)value; }

GLFWAPI int glfwGetWindowAttrib(GLFWwindow *w, int attrib) {
    if (!w) return 0;
    switch (attrib) {
    case GLFW_FOCUSED: return w->focused;
    case GLFW_ICONIFIED: return 0;
    case GLFW_RESIZABLE: return 1;
    case GLFW_VISIBLE: return 1;
    case GLFW_DECORATED: return 0;
    case GLFW_AUTO_ICONIFY: return 0;
    case GLFW_FLOATING: return 0;
    case GLFW_MAXIMIZED: return 0;
    case GLFW_TRANSPARENT_FRAMEBUFFER: return 0;
    case GLFW_HOVERED: return 1;
    case GLFW_FOCUS_ON_SHOW: return 1;
    case GLFW_CLIENT_API:
        return hint_get(w->context_hints, w->context_hint_count, attrib, GLFW_OPENGL_API);
    default:
        /* Context version/profile/etc.: report whatever the game asked for. */
        return hint_get(w->context_hints, w->context_hint_count, attrib, 0);
    }
}

GLFWAPI void glfwSetWindowUserPointer(GLFWwindow *w, void *p) { if (w) w->user_pointer = p; }
GLFWAPI void *glfwGetWindowUserPointer(GLFWwindow *w) { return w ? w->user_pointer : NULL; }

#define FLINT_SETTER(Name, Type, Field)                                  \
    GLFWAPI Type glfwSet##Name##Callback(GLFWwindow *w, Type callback) { \
        if (!w) return NULL;                                             \
        Type previous = w->cb.Field;                                     \
        w->cb.Field = callback;                                          \
        return previous;                                                 \
    }

FLINT_SETTER(WindowPos, GLFWwindowposfun, pos)
FLINT_SETTER(WindowSize, GLFWwindowsizefun, size)
FLINT_SETTER(WindowClose, GLFWwindowclosefun, close)
FLINT_SETTER(WindowRefresh, GLFWwindowrefreshfun, refresh)
FLINT_SETTER(WindowFocus, GLFWwindowfocusfun, focus)
FLINT_SETTER(WindowIconify, GLFWwindowiconifyfun, iconify)
FLINT_SETTER(WindowMaximize, GLFWwindowmaximizefun, maximize)
FLINT_SETTER(FramebufferSize, GLFWframebuffersizefun, framebuffer)
FLINT_SETTER(WindowContentScale, GLFWwindowcontentscalefun, content_scale)
FLINT_SETTER(Key, GLFWkeyfun, key)
FLINT_SETTER(Char, GLFWcharfun, character)
FLINT_SETTER(CharMods, GLFWcharmodsfun, charmods)
FLINT_SETTER(MouseButton, GLFWmousebuttonfun, mouse_button)
FLINT_SETTER(CursorPos, GLFWcursorposfun, cursor_pos)
FLINT_SETTER(CursorEnter, GLFWcursorenterfun, cursor_enter)
FLINT_SETTER(Scroll, GLFWscrollfun, scroll)
FLINT_SETTER(Drop, GLFWdropfun, drop)

/* ================================================================== events */

GLFWAPI void glfwPollEvents(void) { REQUIRE_INIT(); flint_dispatch_events(); }
GLFWAPI void glfwWaitEvents(void) { REQUIRE_INIT(); flint_event_wait(-1.0); flint_dispatch_events(); }
GLFWAPI void glfwWaitEventsTimeout(double timeout) {
    REQUIRE_INIT();
    flint_event_wait(timeout < 0 ? 0 : timeout);
    flint_dispatch_events();
}
GLFWAPI void glfwPostEmptyEvent(void) { flint_event_wake(); }

/* =================================================================== input */

GLFWAPI int glfwGetInputMode(GLFWwindow *w, int mode) {
    if (!w) return 0;
    switch (mode) {
    case GLFW_CURSOR: return w->cursor_mode;
    case GLFW_STICKY_KEYS: return w->sticky_keys;
    case GLFW_STICKY_MOUSE_BUTTONS: return w->sticky_mouse;
    case GLFW_LOCK_KEY_MODS: return w->lock_key_mods;
    case GLFW_RAW_MOUSE_MOTION: return w->raw_motion;
    default:
        error(GLFW_INVALID_ENUM, "Invalid input mode 0x%08X", mode);
        return 0;
    }
}

GLFWAPI void glfwSetInputMode(GLFWwindow *w, int mode, int value) {
    if (!w) return;
    switch (mode) {
    case GLFW_CURSOR:
        if (value != GLFW_CURSOR_NORMAL && value != GLFW_CURSOR_HIDDEN && value != GLFW_CURSOR_DISABLED) {
            error(GLFW_INVALID_ENUM, "Invalid cursor mode 0x%08X", value);
            return;
        }
        flint_set_cursor_mode(w, value);
        break;
    case GLFW_STICKY_KEYS: w->sticky_keys = value ? 1 : 0; break;
    case GLFW_STICKY_MOUSE_BUTTONS: w->sticky_mouse = value ? 1 : 0; break;
    case GLFW_LOCK_KEY_MODS: w->lock_key_mods = value ? 1 : 0; break;
    case GLFW_RAW_MOUSE_MOTION: w->raw_motion = value ? 1 : 0; break;
    default: error(GLFW_INVALID_ENUM, "Invalid input mode 0x%08X", mode); break;
    }
}

GLFWAPI int glfwRawMouseMotionSupported(void) { return GLFW_TRUE; }

static const char *printable_key_name(int key) {
    static const char *letters[26] = {"a","b","c","d","e","f","g","h","i","j","k","l","m",
                                      "n","o","p","q","r","s","t","u","v","w","x","y","z"};
    static const char *digits[10] = {"0","1","2","3","4","5","6","7","8","9"};
    if (key >= 65 && key <= 90) return letters[key - 65];
    if (key >= 48 && key <= 57) return digits[key - 48];
    switch (key) {
    case 39: return "'";
    case 44: return ",";
    case 45: return "-";
    case 46: return ".";
    case 47: return "/";
    case 59: return ";";
    case 61: return "=";
    case 91: return "[";
    case 92: return "\\";
    case 93: return "]";
    case 96: return "`";
    default: return NULL;
    }
}

GLFWAPI const char *glfwGetKeyName(int key, int scancode) { return printable_key_name(key >= 0 ? key : scancode); }
GLFWAPI int glfwGetKeyScancode(int key) { return key; }

GLFWAPI int glfwGetKey(GLFWwindow *w, int key) {
    if (!w || key < 0 || key >= FLINT_MAX_KEYS) return GLFW_RELEASE;
    return w->keys[key];
}
GLFWAPI int glfwGetMouseButton(GLFWwindow *w, int button) {
    if (!w || button < 0 || button >= FLINT_MAX_MOUSE_BUTTONS) return GLFW_RELEASE;
    return w->mouse_buttons[button];
}
GLFWAPI void glfwGetCursorPos(GLFWwindow *w, double *x, double *y) {
    if (!w) { if (x) *x = 0; if (y) *y = 0; return; }
    int disabled = w->cursor_mode == GLFW_CURSOR_DISABLED;
    if (x) *x = disabled ? w->virtual_x : w->cursor_x;
    if (y) *y = disabled ? w->virtual_y : w->cursor_y;
}
GLFWAPI void glfwSetCursorPos(GLFWwindow *w, double x, double y) {
    if (!w) return;
    if (w->cursor_mode == GLFW_CURSOR_DISABLED) {
        w->virtual_x = x;
        w->virtual_y = y;
    } else {
        w->cursor_x = x;
        w->cursor_y = y;
    }
}

GLFWAPI GLFWcursor *glfwCreateCursor(const GLFWimage *image, int xhot, int yhot) {
    (void)image; (void)xhot; (void)yhot;
    GLFWcursor *c = (GLFWcursor *)calloc(1, sizeof(GLFWcursor));
    return c;
}
GLFWAPI GLFWcursor *glfwCreateStandardCursor(int shape) {
    GLFWcursor *c = (GLFWcursor *)calloc(1, sizeof(GLFWcursor));
    if (c) c->shape = shape;
    return c;
}
GLFWAPI void glfwDestroyCursor(GLFWcursor *c) { free(c); }
GLFWAPI void glfwSetCursor(GLFWwindow *w, GLFWcursor *c) { (void)w; (void)c; }

GLFWAPI const char *glfwGetClipboardString(GLFWwindow *w) { (void)w; return g_clipboard; }
GLFWAPI void glfwSetClipboardString(GLFWwindow *w, const char *s) {
    (void)w;
    char *copy = s ? strdup(s) : NULL;
    free(g_clipboard);
    g_clipboard = copy;
}

/* Joysticks and gamepads: none present for now (the controller bridge is a later step). */
GLFWAPI int glfwJoystickPresent(int jid) { (void)jid; return GLFW_FALSE; }
GLFWAPI const float *glfwGetJoystickAxes(int jid, int *count) { (void)jid; if (count) *count = 0; return NULL; }
GLFWAPI const unsigned char *glfwGetJoystickButtons(int jid, int *count) { (void)jid; if (count) *count = 0; return NULL; }
GLFWAPI const unsigned char *glfwGetJoystickHats(int jid, int *count) { (void)jid; if (count) *count = 0; return NULL; }
GLFWAPI const char *glfwGetJoystickName(int jid) { (void)jid; return NULL; }
GLFWAPI const char *glfwGetJoystickGUID(int jid) { (void)jid; return NULL; }
GLFWAPI void glfwSetJoystickUserPointer(int jid, void *p) { (void)jid; (void)p; }
GLFWAPI void *glfwGetJoystickUserPointer(int jid) { (void)jid; return NULL; }
GLFWAPI int glfwJoystickIsGamepad(int jid) { (void)jid; return GLFW_FALSE; }
GLFWAPI GLFWjoystickfun glfwSetJoystickCallback(GLFWjoystickfun cb) {
    GLFWjoystickfun previous = g_joystick_callback;
    g_joystick_callback = cb;
    return previous;
}
GLFWAPI int glfwUpdateGamepadMappings(const char *mappings) { (void)mappings; return GLFW_TRUE; }
GLFWAPI const char *glfwGetGamepadName(int jid) { (void)jid; return NULL; }
GLFWAPI int glfwGetGamepadState(int jid, GLFWgamepadstate *state) { (void)jid; (void)state; return GLFW_FALSE; }

/* ================================================================== timing */

GLFWAPI double glfwGetTime(void) { return now_seconds_raw() - ((double)g_time_base.tv_sec + (double)g_time_base.tv_nsec / 1e9); }
GLFWAPI void glfwSetTime(double t) {
    if (t != t || t < 0.0) { error(GLFW_INVALID_VALUE, "Invalid time"); return; }
    double base = now_seconds_raw() - t;
    g_time_base.tv_sec = (time_t)base;
    g_time_base.tv_nsec = (long)((base - (double)g_time_base.tv_sec) * 1e9);
}
GLFWAPI unsigned long long glfwGetTimerValue(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (unsigned long long)ts.tv_sec * 1000000000ULL + (unsigned long long)ts.tv_nsec;
}
GLFWAPI unsigned long long glfwGetTimerFrequency(void) { return 1000000000ULL; }

/* ================================================================== context */

GLFWAPI void glfwMakeContextCurrent(GLFWwindow *w) {
    REQUIRE_INIT();
    const FlintPlatform *p = flint_get_platform();
    if (!w) {
        if (p->make_current) p->make_current(0);
        t_current_context = NULL;
        return;
    }
    if (!w->has_context) {
        error(GLFW_NO_WINDOW_CONTEXT, "Window has no OpenGL context");
        return;
    }
    if (p->make_current && !p->make_current(1)) {
        error(GLFW_PLATFORM_ERROR, "Failed to make the EGL context current");
        return;
    }
    t_current_context = w;
}
GLFWAPI GLFWwindow *glfwGetCurrentContext(void) { return t_current_context; }

GLFWAPI void glfwSwapBuffers(GLFWwindow *w) {
    REQUIRE_INIT();
    if (!w || !w->has_context) {
        error(GLFW_NO_WINDOW_CONTEXT, "Window has no OpenGL context");
        return;
    }
    const FlintPlatform *p = flint_get_platform();
    if (p->swap_buffers) p->swap_buffers();
}
GLFWAPI void glfwSwapInterval(int interval) {
    REQUIRE_INIT();
    if (!t_current_context) {
        error(GLFW_NO_CURRENT_CONTEXT, "No current context");
        return;
    }
    const FlintPlatform *p = flint_get_platform();
    if (p->swap_interval) p->swap_interval(interval);
}
GLFWAPI int glfwExtensionSupported(const char *name) { (void)name; return GLFW_FALSE; }
GLFWAPI GLFWglproc glfwGetProcAddress(const char *name) {
    const FlintPlatform *p = flint_get_platform();
    if (!p || !p->get_proc_address || !name) return NULL;
    return (GLFWglproc)p->get_proc_address(name);
}

/* ================================================================== Vulkan */

GLFWAPI int glfwVulkanSupported(void) { return GLFW_FALSE; }
GLFWAPI const char **glfwGetRequiredInstanceExtensions(unsigned int *count) { if (count) *count = 0; return NULL; }
GLFWAPI void *glfwGetInstanceProcAddress(void *instance, const char *name) { (void)instance; (void)name; return NULL; }
GLFWAPI int glfwGetPhysicalDevicePresentationSupport(void *i, void *d, unsigned int q) { (void)i; (void)d; (void)q; return GLFW_FALSE; }
GLFWAPI int glfwCreateWindowSurface(void *i, GLFWwindow *w, const void *a, void *s) {
    (void)i; (void)w; (void)a; (void)s;
    return -7; /* VK_ERROR_EXTENSION_NOT_PRESENT */
}
