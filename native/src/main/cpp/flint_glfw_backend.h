#pragma once

#include <stddef.h>
#include <stdint.h>

#if defined(__GNUC__)
#define FLINT_GLFW_API __attribute__((visibility("default")))
#else
#define FLINT_GLFW_API
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct GLFWwindow GLFWwindow;
typedef struct GLFWmonitor GLFWmonitor;
typedef struct GLFWcursor GLFWcursor;
typedef struct GLFWimage {
    int width;
    int height;
    unsigned char *pixels;
} GLFWimage;
typedef struct GLFWgammaramp {
    unsigned short *red;
    unsigned short *green;
    unsigned short *blue;
    unsigned int size;
} GLFWgammaramp;
typedef struct GLFWgamepadstate {
    unsigned char buttons[15];
    float axes[6];
} GLFWgamepadstate;
typedef struct GLFWallocator {
    void *(*allocate)(size_t size, void *user);
    void *(*reallocate)(void *block, size_t size, void *user);
    void (*deallocate)(void *block, void *user);
    void *user;
} GLFWallocator;
typedef struct GLFWvidmode {
    int width;
    int height;
    int redBits;
    int greenBits;
    int blueBits;
    int refreshRate;
} GLFWvidmode;

typedef void (*GLFWglproc)(void);
typedef void (*GLFWerrorfun)(int, const char *);
typedef void (*GLFWwindowposfun)(GLFWwindow *, int, int);
typedef void (*GLFWwindowsizefun)(GLFWwindow *, int, int);
typedef void (*GLFWwindowclosefun)(GLFWwindow *);
typedef void (*GLFWwindowrefreshfun)(GLFWwindow *);
typedef void (*GLFWwindowfocusfun)(GLFWwindow *, int);
typedef void (*GLFWwindowiconifyfun)(GLFWwindow *, int);
typedef void (*GLFWwindowmaximizefun)(GLFWwindow *, int);
typedef void (*GLFWframebuffersizefun)(GLFWwindow *, int, int);
typedef void (*GLFWwindowcontentscalefun)(GLFWwindow *, float, float);
typedef void (*GLFWkeyfun)(GLFWwindow *, int, int, int, int);
typedef void (*GLFWcharfun)(GLFWwindow *, unsigned int);
typedef void (*GLFWmousebuttonfun)(GLFWwindow *, int, int, int);
typedef void (*GLFWcursorposfun)(GLFWwindow *, double, double);
typedef void (*GLFWcursorenterfun)(GLFWwindow *, int);
typedef void (*GLFWscrollfun)(GLFWwindow *, double, double);
typedef void (*GLFWdropfun)(GLFWwindow *, int, const char **);
typedef void (*GLFWcharmodsfun)(GLFWwindow *, unsigned int, int);
typedef void (*GLFWmonitorfun)(GLFWmonitor *, int);
typedef void (*GLFWjoystickfun)(int, int);

enum {
    GLFW_RELEASE = 0,
    GLFW_PRESS = 1,
    GLFW_REPEAT = 2,
    GLFW_FALSE = 0,
    GLFW_TRUE = 1,
    GLFW_NO_API = 0,
    GLFW_OPENGL_API = 0x00030001,
    GLFW_FOCUSED = 0x00020001,
    GLFW_ICONIFIED = 0x00020002,
    GLFW_RESIZABLE = 0x00020003,
    GLFW_VISIBLE = 0x00020004,
    GLFW_DECORATED = 0x00020005,
    GLFW_AUTO_ICONIFY = 0x00020006,
    GLFW_FLOATING = 0x00020007,
    GLFW_MAXIMIZED = 0x00020008,
    GLFW_HOVERED = 0x0002000B,
    GLFW_CENTER_CURSOR = 0x00020009,
    GLFW_TRANSPARENT_FRAMEBUFFER = 0x0002000A,
    GLFW_CLIENT_API = 0x00022001,
    GLFW_CONTEXT_VERSION_MAJOR = 0x00022002,
    GLFW_CONTEXT_VERSION_MINOR = 0x00022003,
    GLFW_CONTEXT_REVISION = 0x00022004,
    GLFW_OPENGL_FORWARD_COMPAT = 0x00022006,
    GLFW_OPENGL_DEBUG_CONTEXT = 0x00022007,
    GLFW_OPENGL_PROFILE = 0x00022008,
    GLFW_CONTEXT_ROBUSTNESS = 0x00022005,
    GLFW_DOUBLEBUFFER = 0x00021010,
    GLFW_SAMPLES = 0x0002100D,
    GLFW_CURSOR = 0x00033001,
    GLFW_STICKY_KEYS = 0x00033002,
    GLFW_STICKY_MOUSE_BUTTONS = 0x00033003,
    GLFW_LOCK_KEY_MODS = 0x00033004,
    GLFW_RAW_MOUSE_MOTION = 0x00033005,
    GLFW_CURSOR_NORMAL = 0x00034001,
    GLFW_CURSOR_HIDDEN = 0x00034002,
    GLFW_CURSOR_DISABLED = 0x00034003,
    GLFW_NOT_INITIALIZED = 0x00010001,
    GLFW_INVALID_VALUE = 0x00010004,
    GLFW_API_UNAVAILABLE = 0x00010006,
    GLFW_PLATFORM_ERROR = 0x00010008,
    GLFW_PLATFORM_NULL = 0x00060005
};

/* Flint-owned Android window attachment hooks. Call on the EGL owner thread. */
FLINT_GLFW_API int flint_glfw_attach_android_surface(int width, int height);
FLINT_GLFW_API void flint_glfw_detach_android_surface(void);
FLINT_GLFW_API int flint_glfw_poll_events(void);
FLINT_GLFW_API GLFWwindow *flint_glfw_android_window(void);
FLINT_GLFW_API uint64_t flint_glfw_presented_frame_count(void);

/* GLFW 3.x C ABI subset implemented by Flint. */
FLINT_GLFW_API int glfwInit(void);
FLINT_GLFW_API void glfwTerminate(void);
FLINT_GLFW_API void glfwGetVersion(int *major, int *minor, int *rev);
FLINT_GLFW_API const char *glfwGetVersionString(void);
FLINT_GLFW_API GLFWerrorfun glfwSetErrorCallback(GLFWerrorfun callback);
FLINT_GLFW_API int glfwGetError(const char **description);

FLINT_GLFW_API void glfwDefaultWindowHints(void);
FLINT_GLFW_API void glfwWindowHint(int hint, int value);
FLINT_GLFW_API GLFWwindow *glfwCreateWindow(int width, int height, const char *title, GLFWmonitor *monitor, GLFWwindow *share);
FLINT_GLFW_API void glfwDestroyWindow(GLFWwindow *window);
FLINT_GLFW_API int glfwWindowShouldClose(GLFWwindow *window);
FLINT_GLFW_API void glfwSetWindowShouldClose(GLFWwindow *window, int value);
FLINT_GLFW_API void glfwSetWindowTitle(GLFWwindow *window, const char *title);
FLINT_GLFW_API void glfwGetWindowPos(GLFWwindow *window, int *xpos, int *ypos);
FLINT_GLFW_API void glfwSetWindowPos(GLFWwindow *window, int xpos, int ypos);
FLINT_GLFW_API void glfwGetWindowSize(GLFWwindow *window, int *width, int *height);
FLINT_GLFW_API void glfwSetWindowSize(GLFWwindow *window, int width, int height);
FLINT_GLFW_API void glfwGetFramebufferSize(GLFWwindow *window, int *width, int *height);
FLINT_GLFW_API void glfwGetWindowContentScale(GLFWwindow *window, float *xscale, float *yscale);
FLINT_GLFW_API int glfwGetWindowAttrib(GLFWwindow *window, int attrib);
FLINT_GLFW_API void glfwSetWindowAttrib(GLFWwindow *window, int attrib, int value);
FLINT_GLFW_API void glfwIconifyWindow(GLFWwindow *window);
FLINT_GLFW_API void glfwRestoreWindow(GLFWwindow *window);
FLINT_GLFW_API void glfwMaximizeWindow(GLFWwindow *window);
FLINT_GLFW_API void glfwFocusWindow(GLFWwindow *window);
FLINT_GLFW_API void glfwRequestWindowAttention(GLFWwindow *window);
FLINT_GLFW_API GLFWmonitor *glfwGetWindowMonitor(GLFWwindow *window);
FLINT_GLFW_API void glfwSetWindowMonitor(GLFWwindow *window, GLFWmonitor *monitor, int xpos, int ypos, int width, int height, int refreshRate);

FLINT_GLFW_API int glfwGetKey(GLFWwindow *window, int key);
FLINT_GLFW_API int glfwGetMouseButton(GLFWwindow *window, int button);
FLINT_GLFW_API void glfwGetCursorPos(GLFWwindow *window, double *xpos, double *ypos);
FLINT_GLFW_API void glfwSetCursorPos(GLFWwindow *window, double xpos, double ypos);
FLINT_GLFW_API void glfwSetInputMode(GLFWwindow *window, int mode, int value);
FLINT_GLFW_API int glfwGetInputMode(GLFWwindow *window, int mode);
FLINT_GLFW_API const char *glfwGetKeyName(int key, int scancode);
FLINT_GLFW_API int glfwGetKeyScancode(int key);

FLINT_GLFW_API void glfwPollEvents(void);
FLINT_GLFW_API void glfwWaitEvents(void);
FLINT_GLFW_API void glfwWaitEventsTimeout(double timeout);
FLINT_GLFW_API void glfwPostEmptyEvent(void);

FLINT_GLFW_API void glfwMakeContextCurrent(GLFWwindow *window);
FLINT_GLFW_API GLFWwindow *glfwGetCurrentContext(void);
FLINT_GLFW_API void glfwSwapBuffers(GLFWwindow *window);
FLINT_GLFW_API void glfwSwapInterval(int interval);
FLINT_GLFW_API GLFWglproc glfwGetProcAddress(const char *procname);

FLINT_GLFW_API double glfwGetTime(void);
FLINT_GLFW_API void glfwSetTime(double time);
FLINT_GLFW_API uint64_t glfwGetTimerValue(void);
FLINT_GLFW_API uint64_t glfwGetTimerFrequency(void);

FLINT_GLFW_API GLFWmonitor **glfwGetMonitors(int *count);
FLINT_GLFW_API GLFWmonitor *glfwGetPrimaryMonitor(void);
FLINT_GLFW_API void glfwGetMonitorPos(GLFWmonitor *monitor, int *xpos, int *ypos);
FLINT_GLFW_API void glfwGetMonitorPhysicalSize(GLFWmonitor *monitor, int *widthMM, int *heightMM);
FLINT_GLFW_API const char *glfwGetMonitorName(GLFWmonitor *monitor);
FLINT_GLFW_API const GLFWvidmode *glfwGetVideoMode(GLFWmonitor *monitor);
FLINT_GLFW_API const GLFWvidmode *glfwGetVideoModes(GLFWmonitor *monitor, int *count);
FLINT_GLFW_API void glfwGetMonitorContentScale(GLFWmonitor *monitor, float *xscale, float *yscale);
FLINT_GLFW_API void glfwGetMonitorWorkarea(GLFWmonitor *monitor, int *xpos, int *ypos, int *width, int *height);

FLINT_GLFW_API const char *glfwGetClipboardString(GLFWwindow *window);
FLINT_GLFW_API void glfwSetClipboardString(GLFWwindow *window, const char *string);
FLINT_GLFW_API void *glfwGetWindowUserPointer(GLFWwindow *window);
FLINT_GLFW_API void glfwSetWindowUserPointer(GLFWwindow *window, void *pointer);

FLINT_GLFW_API GLFWwindowposfun glfwSetWindowPosCallback(GLFWwindow *window, GLFWwindowposfun cbfun);
FLINT_GLFW_API GLFWwindowsizefun glfwSetWindowSizeCallback(GLFWwindow *window, GLFWwindowsizefun cbfun);
FLINT_GLFW_API GLFWwindowclosefun glfwSetWindowCloseCallback(GLFWwindow *window, GLFWwindowclosefun cbfun);
FLINT_GLFW_API GLFWwindowrefreshfun glfwSetWindowRefreshCallback(GLFWwindow *window, GLFWwindowrefreshfun cbfun);
FLINT_GLFW_API GLFWwindowfocusfun glfwSetWindowFocusCallback(GLFWwindow *window, GLFWwindowfocusfun cbfun);
FLINT_GLFW_API GLFWwindowiconifyfun glfwSetWindowIconifyCallback(GLFWwindow *window, GLFWwindowiconifyfun cbfun);
FLINT_GLFW_API GLFWwindowmaximizefun glfwSetWindowMaximizeCallback(GLFWwindow *window, GLFWwindowmaximizefun cbfun);
FLINT_GLFW_API GLFWframebuffersizefun glfwSetFramebufferSizeCallback(GLFWwindow *window, GLFWframebuffersizefun cbfun);
FLINT_GLFW_API GLFWwindowcontentscalefun glfwSetWindowContentScaleCallback(GLFWwindow *window, GLFWwindowcontentscalefun cbfun);
FLINT_GLFW_API GLFWkeyfun glfwSetKeyCallback(GLFWwindow *window, GLFWkeyfun cbfun);
FLINT_GLFW_API GLFWcharfun glfwSetCharCallback(GLFWwindow *window, GLFWcharfun cbfun);
FLINT_GLFW_API GLFWmousebuttonfun glfwSetMouseButtonCallback(GLFWwindow *window, GLFWmousebuttonfun cbfun);
FLINT_GLFW_API GLFWcursorposfun glfwSetCursorPosCallback(GLFWwindow *window, GLFWcursorposfun cbfun);
FLINT_GLFW_API GLFWcursorenterfun glfwSetCursorEnterCallback(GLFWwindow *window, GLFWcursorenterfun cbfun);
FLINT_GLFW_API GLFWscrollfun glfwSetScrollCallback(GLFWwindow *window, GLFWscrollfun cbfun);
FLINT_GLFW_API GLFWdropfun glfwSetDropCallback(GLFWwindow *window, GLFWdropfun cbfun);


/* Additional GLFW symbols used by LWJGL 3.3.x. Unsupported Android-only
 * desktop operations are safe no-ops or report unavailable device features. */
FLINT_GLFW_API void glfwInitHint(int hint, int value);
FLINT_GLFW_API void glfwInitAllocator(const GLFWallocator *allocator);
FLINT_GLFW_API int glfwGetPlatform(void);
FLINT_GLFW_API int glfwPlatformSupported(int platform);
FLINT_GLFW_API void glfwSetMonitorUserPointer(GLFWmonitor *monitor, void *pointer);
FLINT_GLFW_API void *glfwGetMonitorUserPointer(GLFWmonitor *monitor);
FLINT_GLFW_API GLFWmonitorfun glfwSetMonitorCallback(GLFWmonitorfun callback);
FLINT_GLFW_API void glfwSetGamma(GLFWmonitor *monitor, float gamma);
FLINT_GLFW_API const GLFWgammaramp *glfwGetGammaRamp(GLFWmonitor *monitor);
FLINT_GLFW_API void glfwSetGammaRamp(GLFWmonitor *monitor, const GLFWgammaramp *ramp);
FLINT_GLFW_API void glfwWindowHintString(int hint, const char *value);
FLINT_GLFW_API void glfwSetWindowIcon(GLFWwindow *window, int count, const GLFWimage *images);
FLINT_GLFW_API void glfwSetWindowSizeLimits(GLFWwindow *window, int minWidth, int minHeight, int maxWidth, int maxHeight);
FLINT_GLFW_API void glfwSetWindowAspectRatio(GLFWwindow *window, int numer, int denom);
FLINT_GLFW_API void glfwGetWindowFrameSize(GLFWwindow *window, int *left, int *top, int *right, int *bottom);
FLINT_GLFW_API float glfwGetWindowOpacity(GLFWwindow *window);
FLINT_GLFW_API void glfwSetWindowOpacity(GLFWwindow *window, float opacity);
FLINT_GLFW_API void glfwShowWindow(GLFWwindow *window);
FLINT_GLFW_API void glfwHideWindow(GLFWwindow *window);
FLINT_GLFW_API int glfwRawMouseMotionSupported(void);
FLINT_GLFW_API GLFWcursor *glfwCreateCursor(const GLFWimage *image, int xhot, int yhot);
FLINT_GLFW_API GLFWcursor *glfwCreateStandardCursor(int shape);
FLINT_GLFW_API void glfwDestroyCursor(GLFWcursor *cursor);
FLINT_GLFW_API void glfwSetCursor(GLFWwindow *window, GLFWcursor *cursor);
FLINT_GLFW_API GLFWcharmodsfun glfwSetCharModsCallback(GLFWwindow *window, GLFWcharmodsfun callback);
FLINT_GLFW_API int glfwJoystickPresent(int jid);
FLINT_GLFW_API const float *glfwGetJoystickAxes(int jid, int *count);
FLINT_GLFW_API const unsigned char *glfwGetJoystickButtons(int jid, int *count);
FLINT_GLFW_API const unsigned char *glfwGetJoystickHats(int jid, int *count);
FLINT_GLFW_API const char *glfwGetJoystickName(int jid);
FLINT_GLFW_API const char *glfwGetJoystickGUID(int jid);
FLINT_GLFW_API void glfwSetJoystickUserPointer(int jid, void *pointer);
FLINT_GLFW_API void *glfwGetJoystickUserPointer(int jid);
FLINT_GLFW_API int glfwJoystickIsGamepad(int jid);
FLINT_GLFW_API GLFWjoystickfun glfwSetJoystickCallback(GLFWjoystickfun callback);
FLINT_GLFW_API int glfwUpdateGamepadMappings(const char *string);
FLINT_GLFW_API const char *glfwGetGamepadName(int jid);
FLINT_GLFW_API int glfwGetGamepadState(int jid, GLFWgamepadstate *state);
FLINT_GLFW_API int glfwExtensionSupported(const char *extension);

#ifdef __cplusplus
}
#endif
