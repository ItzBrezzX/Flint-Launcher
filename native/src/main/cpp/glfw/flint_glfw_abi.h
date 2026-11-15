/*
 * Flint GLFW shim: public ABI subset.
 *
 * Flint's own libglfw.so for Android. LWJGL 3 binds to GLFW by C symbol name, so providing these
 * symbols is all the Java side needs (-Dorg.lwjgl.glfw.libname=<path>/libglfw.so). The type layouts
 * and numeric constants below follow the documented, stable GLFW 3.x C API.
 */
#ifndef FLINT_GLFW_ABI_H
#define FLINT_GLFW_ABI_H

#ifdef __cplusplus
extern "C" {
#endif

#define GLFWAPI __attribute__((visibility("default")))

#define GLFW_TRUE 1
#define GLFW_FALSE 0
#define GLFW_RELEASE 0
#define GLFW_PRESS 1
#define GLFW_REPEAT 2

#define GLFW_VERSION_MAJOR 3
#define GLFW_VERSION_MINOR 4
#define GLFW_VERSION_REVISION 0

#define GLFW_KEY_LAST 348

/* errors */
#define GLFW_NO_ERROR 0
#define GLFW_NOT_INITIALIZED 0x00010001
#define GLFW_NO_CURRENT_CONTEXT 0x00010002
#define GLFW_INVALID_ENUM 0x00010003
#define GLFW_INVALID_VALUE 0x00010004
#define GLFW_OUT_OF_MEMORY 0x00010005
#define GLFW_API_UNAVAILABLE 0x00010006
#define GLFW_VERSION_UNAVAILABLE 0x00010007
#define GLFW_PLATFORM_ERROR 0x00010008
#define GLFW_FORMAT_UNAVAILABLE 0x00010009
#define GLFW_NO_WINDOW_CONTEXT 0x0001000A

/* window attributes / hints */
#define GLFW_FOCUSED 0x00020001
#define GLFW_ICONIFIED 0x00020002
#define GLFW_RESIZABLE 0x00020003
#define GLFW_VISIBLE 0x00020004
#define GLFW_DECORATED 0x00020005
#define GLFW_AUTO_ICONIFY 0x00020006
#define GLFW_FLOATING 0x00020007
#define GLFW_MAXIMIZED 0x00020008
#define GLFW_CENTER_CURSOR 0x00020009
#define GLFW_TRANSPARENT_FRAMEBUFFER 0x0002000A
#define GLFW_HOVERED 0x0002000B
#define GLFW_FOCUS_ON_SHOW 0x0002000C
#define GLFW_CLIENT_API 0x00022001
#define GLFW_CONTEXT_VERSION_MAJOR 0x00022002
#define GLFW_CONTEXT_VERSION_MINOR 0x00022003
#define GLFW_CONTEXT_REVISION 0x00022004
#define GLFW_OPENGL_API 0x00030001
#define GLFW_NO_API 0

/* input modes */
#define GLFW_CURSOR 0x00033001
#define GLFW_STICKY_KEYS 0x00033002
#define GLFW_STICKY_MOUSE_BUTTONS 0x00033003
#define GLFW_LOCK_KEY_MODS 0x00033004
#define GLFW_RAW_MOUSE_MOTION 0x00033005
#define GLFW_CURSOR_NORMAL 0x00034001
#define GLFW_CURSOR_HIDDEN 0x00034002
#define GLFW_CURSOR_DISABLED 0x00034003

#define GLFW_PLATFORM_NULL 0x00060005

typedef struct GLFWmonitor GLFWmonitor;
typedef struct GLFWwindow GLFWwindow;
typedef struct GLFWcursor GLFWcursor;

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
typedef void (*GLFWmousebuttonfun)(GLFWwindow *, int, int, int);
typedef void (*GLFWcursorposfun)(GLFWwindow *, double, double);
typedef void (*GLFWcursorenterfun)(GLFWwindow *, int);
typedef void (*GLFWscrollfun)(GLFWwindow *, double, double);
typedef void (*GLFWkeyfun)(GLFWwindow *, int, int, int, int);
typedef void (*GLFWcharfun)(GLFWwindow *, unsigned int);
typedef void (*GLFWcharmodsfun)(GLFWwindow *, unsigned int, int);
typedef void (*GLFWdropfun)(GLFWwindow *, int, const char **);
typedef void (*GLFWmonitorfun)(GLFWmonitor *, int);
typedef void (*GLFWjoystickfun)(int, int);

typedef struct GLFWvidmode {
    int width, height, redBits, greenBits, blueBits, refreshRate;
} GLFWvidmode;

typedef struct GLFWgammaramp {
    unsigned short *red, *green, *blue;
    unsigned int size;
} GLFWgammaramp;

typedef struct GLFWimage {
    int width, height;
    unsigned char *pixels;
} GLFWimage;

typedef struct GLFWgamepadstate {
    unsigned char buttons[15];
    float axes[6];
} GLFWgamepadstate;

#ifdef __cplusplus
}
#endif
#endif
