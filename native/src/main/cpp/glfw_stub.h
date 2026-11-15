// glfw_stub.h
// Clean-room GLFW API subset for Flint Launcher.
//
// This header declares the subset of the GLFW 3.x C API that Minecraft's
// LWJGL binding exercises. Every constant and function below is written from
// the public GLFW API documentation; no GLFW/Pojav/Boardwalk source is copied.
// The implementation (glfw_stub.cpp) is backed by Android EGL + ANativeWindow
// and an internal event queue fed by flint's input bridge.
#pragma once

#include <cstdint>

// ---------------------------------------------------------------------------
// GLFW API constants (values match the public GLFW 3.x ABI so LWJGL's
// generated Java constants line up). These are numeric API identifiers.
// ---------------------------------------------------------------------------
#define FLINT_GLFW_FALSE 0
#define FLINT_GLFW_TRUE 1

// Key / input action
#define FLINT_GLFW_RELEASE 0
#define FLINT_GLFW_PRESS 1
#define FLINT_GLFW_REPEAT 2

// Modifier key bits
#define FLINT_GLFW_MOD_SHIFT 0x0001
#define FLINT_GLFW_MOD_CONTROL 0x0002
#define FLINT_GLFW_MOD_ALT 0x0004
#define FLINT_GLFW_MOD_SUPER 0x0008
#define FLINT_GLFW_MOD_CAPS_LOCK 0x0010
#define FLINT_GLFW_MOD_NUM_LOCK 0x0020

// Mouse buttons (first 8)
#define FLINT_GLFW_MOUSE_BUTTON_1 0
#define FLINT_GLFW_MOUSE_BUTTON_2 1
#define FLINT_GLFW_MOUSE_BUTTON_3 2
#define FLINT_GLFW_MOUSE_BUTTON_4 3
#define FLINT_GLFW_MOUSE_BUTTON_5 4
#define FLINT_GLFW_MOUSE_BUTTON_6 5
#define FLINT_GLFW_MOUSE_BUTTON_7 6
#define FLINT_GLFW_MOUSE_BUTTON_8 7
#define FLINT_GLFW_MOUSE_BUTTON_LEFT FLINT_GLFW_MOUSE_BUTTON_1
#define FLINT_GLFW_MOUSE_BUTTON_RIGHT FLINT_GLFW_MOUSE_BUTTON_2
#define FLINT_GLFW_MOUSE_BUTTON_MIDDLE FLINT_GLFW_MOUSE_BUTTON_3

// Cursor input modes (glfwSetInputMode target)
#define FLINT_GLFW_CURSOR 0x00033001
#define FLINT_GLFW_CURSOR_NORMAL 0x00034001
#define FLINT_GLFW_CURSOR_HIDDEN 0x00034002
#define FLINT_GLFW_CURSOR_DISABLED 0x00034003
// Flint extension: captured pointer (relative mouse mode, like Pojav)
#define FLINT_GLFW_CURSOR_CAPTURED 0x00034004

#define FLINT_GLFW_STICKY_KEYS 0x00033002
#define FLINT_GLFW_STICKY_MOUSE_BUTTONS 0x00033003
#define FLINT_GLFW_LOCK_KEY_MODS 0x00033004
#define FLINT_GLFW_RAW_MOUSE_MOTION 0x00033005

// Error codes (subset)
#define FLINT_GLFW_NOT_INITIALIZED 0x00010001
#define FLINT_GLFW_NO_CURRENT_CONTEXT 0x00010002
#define FLINT_GLFW_INVALID_ENUM 0x00010003
#define FLINT_GLFW_INVALID_VALUE 0x00010004
#define FLINT_GLFW_OUT_OF_MEMORY 0x00010005
#define FLINT_GLFW_API_UNAVAILABLE 0x00010006
#define FLINT_GLFW_VERSION_UNAVAILABLE 0x00010007
#define FLINT_GLFW_PLATFORM_ERROR 0x00010008
#define FLINT_GLFW_FORMAT_UNAVAILABLE 0x00010009
#define FLINT_GLFW_NO_WINDOW_CONTEXT 0x0001000A

// Window hints
#define FLINT_GLFW_RESIZABLE 0x00020003
#define FLINT_GLFW_VISIBLE 0x00020004
#define FLINT_GLFW_DECORATED 0x00020005
#define FLINT_GLFW_FOCUSED 0x00020001
#define FLINT_GLFW_AUTO_ICONIFY 0x00020006
#define FLINT_GLFW_FLOATING 0x00020007
#define FLINT_GLFW_MAXIMIZED 0x00020008
#define FLINT_GLFW_CENTER_CURSOR 0x00020009
#define FLINT_GLFW_TRANSPARENT_FRAMEBUFFER 0x0002000A
#define FLINT_GLFW_FOCUS_ON_SHOW 0x0002000C
#define FLINT_GLFW_SCALE_TO_MONITOR 0x0002200C
// Client API hints
#define FLINT_GLFW_CLIENT_API 0x00022001
#define FLINT_GLFW_CONTEXT_VERSION_MAJOR 0x00022002
#define FLINT_GLFW_CONTEXT_VERSION_MINOR 0x00022003
#define FLINT_GLFW_OPENGL_FORWARD_COMPAT 0x00022006
#define FLINT_GLFW_OPENGL_DEBUG_CONTEXT 0x00022007
#define FLINT_GLFW_OPENGL_PROFILE 0x00022008
#define FLINT_GLFW_CONTEXT_ROBUSTNESS 0x00022005
#define FLINT_GLFW_REFRESH_RATE 0x0002200F
#define FLINT_GLFW_SAMPLES 0x0002100D
#define FLINT_GLFW_RED_BITS 0x00021001
#define FLINT_GLFW_GREEN_BITS 0x00021002
#define FLINT_GLFW_BLUE_BITS 0x00021003
#define FLINT_GLFW_ALPHA_BITS 0x00021004
#define FLINT_GLFW_DEPTH_BITS 0x00021005
#define FLINT_GLFW_STENCIL_BITS 0x00021006
#define FLINT_GLFW_DOUBLEBUFFER 0x00021010
#define FLINT_GLFW_SRGB_CAPABLE 0x0002100E
#define FLINT_GLFW_STEREO 0x0002100C
#define FLINT_GLFW_CONTEXT_RELEASE_BEHAVIOR 0x00022009
#define FLINT_GLFW_NO_API 0
#define FLINT_GLFW_OPENGL_API 0x00030001
#define FLINT_GLFW_OPENGL_ES_API 0x00030002
#define FLINT_GLFW_OPENGL_ANY_PROFILE 0
#define FLINT_GLFW_OPENGL_CORE_PROFILE 0x00032001
#define FLINT_GLFW_OPENGL_COMPAT_PROFILE 0x00032002
#define FLINT_GLFW_NO_ROBUSTNESS 0
#define FLINT_GLFW_ANY_RELEASE_BEHAVIOR 0
#define FLINT_GLFW_RELEASE_BEHAVIOR_FLUSH 0x00035001
#define FLINT_GLFW_RELEASE_BEHAVIOR_NONE 0x00035002

// Window attributes for glfwGetWindowAttrib
#define FLINT_GLFW_ICONIFIED 0x00020002
#define FLINT_GLFW_HOVERED 0x0002000B
#define FLINT_GLFW_MAXIMIZED_ATTR 0x00020008

// Joystick / gamepad (stub)
#define FLINT_GLFW_JOYSTICK_1 0
#define FLINT_GLFW_JOYSTICK_LAST 15
#define FLINT_GLFW_GAMEPAD_BUTTON_A 0
#define FLINT_GLFW_GAMEPAD_AXIS_LEFT_X 0

// Key codes (subset Minecraft uses; full table documented in progress doc).
// Values match GLFW 3.x ABI (public numeric identifiers).
#define FLINT_GLFW_KEY_UNKNOWN -1
#define FLINT_GLFW_KEY_SPACE 32
#define FLINT_GLFW_KEY_APOSTROPHE 39
#define FLINT_GLFW_KEY_COMMA 44
#define FLINT_GLFW_KEY_MINUS 45
#define FLINT_GLFW_KEY_PERIOD 46
#define FLINT_GLFW_KEY_SLASH 47
#define FLINT_GLFW_KEY_0 48
#define FLINT_GLFW_KEY_9 57
#define FLINT_GLFW_KEY_SEMICOLON 59
#define FLINT_GLFW_KEY_EQUAL 61
#define FLINT_GLFW_KEY_A 65
#define FLINT_GLFW_KEY_D 68
#define FLINT_GLFW_KEY_E 69
#define FLINT_GLFW_KEY_Q 81
#define FLINT_GLFW_KEY_S 83
#define FLINT_GLFW_KEY_W 87
#define FLINT_GLFW_KEY_LEFT_BRACKET 91
#define FLINT_GLFW_KEY_BACKSLASH 92
#define FLINT_GLFW_KEY_RIGHT_BRACKET 93
#define FLINT_GLFW_KEY_GRAVE_ACCENT 96
#define FLINT_GLFW_KEY_WORLD_1 161
#define FLINT_GLFW_KEY_ESCAPE 256
#define FLINT_GLFW_KEY_ENTER 257
#define FLINT_GLFW_KEY_TAB 258
#define FLINT_GLFW_KEY_BACKSPACE 259
#define FLINT_GLFW_KEY_INSERT 260
#define FLINT_GLFW_KEY_DELETE 261
#define FLINT_GLFW_KEY_RIGHT 262
#define FLINT_GLFW_KEY_LEFT 263
#define FLINT_GLFW_KEY_DOWN 264
#define FLINT_GLFW_KEY_UP 265
#define FLINT_GLFW_KEY_PAGE_UP 266
#define FLINT_GLFW_KEY_PAGE_DOWN 267
#define FLINT_GLFW_KEY_HOME 268
#define FLINT_GLFW_KEY_END 269
#define FLINT_GLFW_KEY_CAPS_LOCK 280
#define FLINT_GLFW_KEY_SCROLL_LOCK 281
#define FLINT_GLFW_KEY_NUM_LOCK 282
#define FLINT_GLFW_KEY_PRINT_SCREEN 283
#define FLINT_GLFW_KEY_PAUSE 284
#define FLINT_GLFW_KEY_F1 290
#define FLINT_GLFW_KEY_F25 314
#define FLINT_GLFW_KEY_LEFT_SHIFT 340
#define FLINT_GLFW_KEY_LEFT_CONTROL 341
#define FLINT_GLFW_KEY_LEFT_ALT 342
#define FLINT_GLFW_KEY_LEFT_SUPER 343
#define FLINT_GLFW_KEY_RIGHT_SHIFT 344
#define FLINT_GLFW_KEY_RIGHT_CONTROL 345
#define FLINT_GLFW_KEY_RIGHT_ALT 346
#define FLINT_GLFW_KEY_RIGHT_SUPER 347
#define FLINT_GLFW_KEY_MENU 348
#define FLINT_GLFW_KEY_LAST FLINT_GLFW_KEY_MENU

// ---------------------------------------------------------------------------
// Opaque handle types (ABI-compatible pointer sizes with real GLFW).
// ---------------------------------------------------------------------------
typedef struct FLINT_GLFWwindow* FLINT_GLFWwindow_ptr;
typedef struct FLINT_GLFWmonitor* FLINT_GLFWmonitor_ptr;
typedef struct FLINT_GLFWcursor* FLINT_GLFWcursor_ptr;

// Callback typedefs mirror the GLFW 3.x C signatures.
typedef void (*FLINT_GLFWerrorfun)(int error_code, const char* description);
typedef void (*FLINT_GLFWwindowposfun)(FLINT_GLFWwindow_ptr window, int xpos, int ypos);
typedef void (*FLINT_GLFWwindowsizefun)(FLINT_GLFWwindow_ptr window, int width, int height);
typedef void (*FLINT_GLFWwindowclosefun)(FLINT_GLFWwindow_ptr window);
typedef void (*FLINT_GLFWwindowrefreshfun)(FLINT_GLFWwindow_ptr window);
typedef void (*FLINT_GLFWwindowfocusfun)(FLINT_GLFWwindow_ptr window, int focused);
typedef void (*FLINT_GLFWwindowiconifyfun)(FLINT_GLFWwindow_ptr window, int iconified);
typedef void (*FLINT_GLFWwindowmaximizefun)(FLINT_GLFWwindow_ptr window, int maximized);
typedef void (*FLINT_GLFWframebuffersizefun)(FLINT_GLFWwindow_ptr window, int width, int height);
typedef void (*FLINT_GLFWmousebuttonfun)(FLINT_GLFWwindow_ptr window, int button, int action, int mods);
typedef void (*FLINT_GLFWcursorposfun)(FLINT_GLFWwindow_ptr window, double xpos, double ypos);
typedef void (*FLINT_GLFWcursorenterfun)(FLINT_GLFWwindow_ptr window, int entered);
typedef void (*FLINT_GLFWscrollfun)(FLINT_GLFWwindow_ptr window, double xoffset, double yoffset);
typedef void (*FLINT_GLFWkeyfun)(FLINT_GLFWwindow_ptr window, int key, int scancode, int action, int mods);
typedef void (*FLINT_GLFWcharfun)(FLINT_GLFWwindow_ptr window, unsigned int codepoint);
typedef void (*FLINT_GLFWcharmodsfun)(FLINT_GLFWwindow_ptr window, unsigned int codepoint, int mods);
typedef void (*FLINT_GLFWdropfun)(FLINT_GLFWwindow_ptr window, int count, const char** paths);
typedef void (*FLINT_GLFWmonitorfun)(FLINT_GLFWmonitor_ptr monitor, int event);
typedef void (*FLINT_GLFWjoystickfun)(int jid, int event);

typedef struct FLINT_GLFWvidmode {
    int width;
    int height;
    int redBits;
    int greenBits;
    int blueBits;
    int refreshRate;
} FLINT_GLFWvidmode;

// ---------------------------------------------------------------------------
// C API (exported with default visibility so LWJGL's dlsym can resolve them).
// Naming: flint_glfw* internally; we also expose a glfw* alias table via the
// symbol loader so LWJGL's JNI native layer finds the expected names.
// ---------------------------------------------------------------------------
extern "C" {

int  flint_glfwInit(void);
void flint_glfwTerminate(void);
void flint_glfwInitHint(int hint, int value);
void flint_glfwWindowHint(int hint, int value);
void flint_glfwWindowHintString(int hint, const char* value);
void flint_glfwDefaultWindowHints(void);

FLINT_GLFWwindow_ptr flint_glfwCreateWindow(int width, int height, const char* title,
                                            FLINT_GLFWmonitor_ptr monitor,
                                            FLINT_GLFWwindow_ptr share);
void flint_glfwDestroyWindow(FLINT_GLFWwindow_ptr window);
int  flint_glfwWindowShouldClose(FLINT_GLFWwindow_ptr window);
void flint_glfwSetWindowShouldClose(FLINT_GLFWwindow_ptr window, int value);
void flint_glfwSetWindowTitle(FLINT_GLFWwindow_ptr window, const char* title);
void flint_glfwSetWindowSize(FLINT_GLFWwindow_ptr window, int width, int height);
void flint_glfwGetWindowSize(FLINT_GLFWwindow_ptr window, int* width, int* height);
void flint_glfwSetWindowPos(FLINT_GLFWwindow_ptr window, int xpos, int ypos);
void flint_glfwGetWindowPos(FLINT_GLFWwindow_ptr window, int* xpos, int* ypos);
void flint_glfwGetFramebufferSize(FLINT_GLFWwindow_ptr window, int* width, int* height);
float flint_glfwGetWindowContentScale(FLINT_GLFWwindow_ptr window);
int  flint_glfwGetWindowAttrib(FLINT_GLFWwindow_ptr window, int attrib);
void flint_glfwSetWindowAttrib(FLINT_GLFWwindow_ptr window, int attrib, int value);
void flint_glfwIconifyWindow(FLINT_GLFWwindow_ptr window);
void flint_glfwRestoreWindow(FLINT_GLFWwindow_ptr window);
void flint_glfwMaximizeWindow(FLINT_GLFWwindow_ptr window);
void flint_glfwShowWindow(FLINT_GLFWwindow_ptr window);
void flint_glfwHideWindow(FLINT_GLFWwindow_ptr window);
void flint_glfwFocusWindow(FLINT_GLFWwindow_ptr window);
void flint_glfwRequestWindowAttention(FLINT_GLFWwindow_ptr window);

FLINT_GLFWmonitor_ptr flint_glfwGetPrimaryMonitor(void);
FLINT_GLFWmonitor_ptr* flint_glfwGetMonitors(int* count);
const FLINT_GLFWvidmode* flint_glfwGetVideoMode(FLINT_GLFWmonitor_ptr monitor);
const FLINT_GLFWvidmode* flint_glfwGetVideoModes(FLINT_GLFWmonitor_ptr monitor, int* count);
void flint_glfwGetMonitorPhysicalSize(FLINT_GLFWmonitor_ptr monitor, int* widthMM, int* heightMM);
void flint_glfwGetMonitorPos(FLINT_GLFWmonitor_ptr monitor, int* xpos, int* ypos);
const char* flint_glfwGetMonitorName(FLINT_GLFWmonitor_ptr monitor);

void flint_glfwMakeContextCurrent(FLINT_GLFWwindow_ptr window);
FLINT_GLFWwindow_ptr flint_glfwGetCurrentContext(void);
void flint_glfwSwapBuffers(FLINT_GLFWwindow_ptr window);
void flint_glfwSwapInterval(int interval);
int  flint_glfwExtensionSupported(const char* extension);
void* flint_glfwGetProcAddress(const char* procname);
int  flint_glfwExtensionSupportedNative(const char* extension);

void flint_glfwPollEvents(void);
void flint_glfwWaitEvents(void);
void flint_glfwWaitEventsTimeout(double timeout);
void flint_glfwPostEmptyEvent(void);

int  flint_glfwGetKey(FLINT_GLFWwindow_ptr window, int key);
int  flint_glfwGetMouseButton(FLINT_GLFWwindow_ptr window, int button);
void flint_glfwGetCursorPos(FLINT_GLFWwindow_ptr window, double* xpos, double* ypos);
void flint_glfwSetCursorPos(FLINT_GLFWwindow_ptr window, double xpos, double ypos);
int  flint_glfwGetInputMode(FLINT_GLFWwindow_ptr window, int mode);
void flint_glfwSetInputMode(FLINT_GLFWwindow_ptr window, int mode, int value);
int  flint_glfwRawMouseMotionSupported(void);

FLINT_GLFWcursor_ptr flint_glfwCreateCursor(const void* image, int xhot, int yhot);
FLINT_GLFWcursor_ptr flint_glfwCreateStandardCursor(int shape);
void flint_glfwDestroyCursor(FLINT_GLFWcursor_ptr cursor);
void flint_glfwSetCursor(FLINT_GLFWwindow_ptr window, FLINT_GLFWcursor_ptr cursor);

const char* flint_glfwGetKeyName(int key, int scancode);
int  flint_glfwGetKeyScancode(int key);

void flint_glfwSetClipboardString(FLINT_GLFWwindow_ptr window, const char* text);
const char* flint_glfwGetClipboardString(FLINT_GLFWwindow_ptr window);

double flint_glfwGetTime(void);
void   flint_glfwSetTime(double time);
uint64_t flint_glfwGetTimerValue(void);
uint64_t flint_glfwGetTimerFrequency(void);

void flint_glfwGetVersion(int* major, int* minor, int* rev);
const char* flint_glfwGetVersionString(void);

// Callback setters (return previous callback).
FLINT_GLFWerrorfun          flint_glfwSetErrorCallback(FLINT_GLFWerrorfun cb);
FLINT_GLFWwindowposfun      flint_glfwSetWindowPosCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWwindowposfun cb);
FLINT_GLFWwindowsizefun     flint_glfwSetWindowSizeCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWwindowsizefun cb);
FLINT_GLFWwindowclosefun    flint_glfwSetWindowCloseCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWwindowclosefun cb);
FLINT_GLFWwindowrefreshfun  flint_glfwSetWindowRefreshCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWwindowrefreshfun cb);
FLINT_GLFWwindowfocusfun    flint_glfwSetWindowFocusCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWwindowfocusfun cb);
FLINT_GLFWwindowiconifyfun  flint_glfwSetWindowIconifyCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWwindowiconifyfun cb);
FLINT_GLFWwindowmaximizefun flint_glfwSetWindowMaximizeCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWwindowmaximizefun cb);
FLINT_GLFWframebuffersizefun flint_glfwSetFramebufferSizeCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWframebuffersizefun cb);
FLINT_GLFWmousebuttonfun    flint_glfwSetMouseButtonCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWmousebuttonfun cb);
FLINT_GLFWcursorposfun      flint_glfwSetCursorPosCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWcursorposfun cb);
FLINT_GLFWcursorenterfun    flint_glfwSetCursorEnterCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWcursorenterfun cb);
FLINT_GLFWscrollfun         flint_glfwSetScrollCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWscrollfun cb);
FLINT_GLFWkeyfun            flint_glfwSetKeyCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWkeyfun cb);
FLINT_GLFWcharfun           flint_glfwSetCharCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWcharfun cb);
FLINT_GLFWcharmodsfun       flint_glfwSetCharModsCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWcharmodsfun cb);
FLINT_GLFWdropfun           flint_glfwSetDropCallback(FLINT_GLFWwindow_ptr w, FLINT_GLFWdropfun cb);
FLINT_GLFWmonitorfun        flint_glfwSetMonitorCallback(FLINT_GLFWmonitorfun cb);
FLINT_GLFWjoystickfun       flint_glfwSetJoystickCallback(FLINT_GLFWjoystickfun cb);

// Joystick / gamepad — stubbed (always "not present"). TODO: wire to Android
// InputDevice gamepad events via the input bridge.
int flint_glfwJoystickPresent(int jid);
const float* flint_glfwGetJoystickAxes(int jid, int* count);
const unsigned char* flint_glfwGetJoystickButtons(int jid, int* count);
const char* flint_glfwGetJoystickName(int jid);
int flint_glfwJoystickIsGamepad(int jid);
const char* flint_glfwGetGamepadName(int jid);

} // extern "C"

// ---------------------------------------------------------------------------
// Flint-internal helpers (not part of the GLFW API): used by the JNI bridge
// and input bridge to attach the Android Surface and pump events.
// ---------------------------------------------------------------------------
namespace flint {

struct EglWindowState; // defined in glfw_stub.cpp

// Attach an Android ANativeWindow (from a Surface) as the backing surface for
// the primary GLFW window. Returns true on success. Must be called on the
// render thread (the thread that will own the EGL context).
bool glfwAttachAndroidSurface(void* anative_window, int width, int height);

// Detach and destroy EGL resources for the primary window. Render thread only.
void glfwDetachAndroidSurface();

// Returns true if a window exists and has a current context.
bool glfwHasCurrentContext();

// Notify the window of a resize (from SurfaceHolder.Callback). Thread-safe;
// posts a framebuffer-size event into the queue.
void glfwPostSurfaceResize(int width, int height);

// Post a close request (e.g. user pressed back in the game activity).
void glfwPostCloseRequest();

// Error reporting helper: invokes the registered error callback (if any) and logs.
void glfwReportError(int code, const char* message);

// Reset window hints to defaults (called at init).
void glfwResetWindowHints();

} // namespace flint
