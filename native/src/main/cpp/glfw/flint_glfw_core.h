/*
 * Platform-independent core of Flint's GLFW shim: event queue, window state, dispatch.
 * No EGL, no JNI and no Android headers here, so it builds and is unit-tested on a host machine.
 */
#ifndef FLINT_GLFW_CORE_H
#define FLINT_GLFW_CORE_H

#include <stddef.h>
#include "flint_glfw_abi.h"

#ifdef __cplusplus
extern "C" {
#endif

#define FLINT_QUEUE_CAPACITY 1024
#define FLINT_MAX_KEYS 512
#define FLINT_MAX_MOUSE_BUTTONS 8
#define FLINT_MAX_HINTS 64

typedef enum {
    FLINT_EV_KEY = 1,        /* i0 key, i1 scancode, i2 action, i3 mods */
    FLINT_EV_CHAR,           /* i0 codepoint, i1 mods */
    FLINT_EV_MOUSE_BUTTON,   /* i0 button, i1 action, i2 mods */
    FLINT_EV_CURSOR_POS,     /* d0 x, d1 y (absolute; used while the cursor is visible) */
    FLINT_EV_CURSOR_DELTA,   /* d0 dx, d1 dy (relative; used while the cursor is disabled/grabbed) */
    FLINT_EV_SCROLL,         /* d0 dx, d1 dy */
    FLINT_EV_SIZE,           /* i0 width, i1 height */
    FLINT_EV_FOCUS,          /* i0 focused */
    FLINT_EV_CLOSE
} FlintEventType;

typedef struct {
    int type;
    int i0, i1, i2, i3;
    double d0, d1;
} FlintEvent;

/* What the shim needs from the host platform (EGL + Android surface on a device). */
typedef struct FlintPlatform {
    int (*wait_for_surface)(int timeout_ms);        /* 1 once a drawable surface exists */
    int (*get_surface_size)(int *width, int *height);
    int (*create_context)(void);
    int (*make_current)(int make);                  /* 1 = bind to calling thread, 0 = unbind */
    int (*swap_buffers)(void);
    void (*swap_interval)(int interval);
    void *(*get_proc_address)(const char *name);
    void (*destroy_context)(void);
} FlintPlatform;

typedef struct { int id; int value; } FlintHint;

struct GLFWwindow {
    int width, height;
    int should_close;
    int focused;
    int cursor_mode;
    int sticky_keys, sticky_mouse, lock_key_mods, raw_motion;
    double cursor_x, cursor_y;      /* position reported while the cursor is visible */
    double virtual_x, virtual_y;    /* unbounded position reported while it is disabled */
    double saved_x, saved_y;        /* where the cursor returns to when it is shown again */
    unsigned char keys[FLINT_MAX_KEYS];
    unsigned char mouse_buttons[FLINT_MAX_MOUSE_BUTTONS];
    void *user_pointer;
    char *title;
    int has_context;
    FlintHint context_hints[FLINT_MAX_HINTS];
    int context_hint_count;
    struct {
        GLFWwindowposfun pos;
        GLFWwindowsizefun size;
        GLFWwindowclosefun close;
        GLFWwindowrefreshfun refresh;
        GLFWwindowfocusfun focus;
        GLFWwindowiconifyfun iconify;
        GLFWwindowmaximizefun maximize;
        GLFWframebuffersizefun framebuffer;
        GLFWwindowcontentscalefun content_scale;
        GLFWkeyfun key;
        GLFWcharfun character;
        GLFWcharmodsfun charmods;
        GLFWmousebuttonfun mouse_button;
        GLFWcursorposfun cursor_pos;
        GLFWcursorenterfun cursor_enter;
        GLFWscrollfun scroll;
        GLFWdropfun drop;
    } cb;
};

void flint_set_platform(const FlintPlatform *platform);
const FlintPlatform *flint_get_platform(void);

/* single-window state */
GLFWwindow *flint_window(void);
void flint_set_window(GLFWwindow *window);
GLFWwindow *flint_new_window(int width, int height, const char *title);
void flint_free_window(GLFWwindow *window);

/* event queue (thread-safe; producers are Android UI threads, the consumer is the game thread) */
void flint_event_push(const FlintEvent *event);
int flint_event_wait(double timeout_seconds); /* < 0 waits forever; returns 1 if events are pending */
void flint_event_wake(void);
void flint_dispatch_events(void);
size_t flint_event_pending(void);
void flint_event_clear(void);

/* helpers for the producers */
void flint_push_key(int key, int scancode, int action, int mods);
void flint_push_char(unsigned int codepoint, int mods);
void flint_push_mouse_button(int button, int action, int mods);
void flint_push_cursor_pos(double x, double y);
void flint_push_cursor_delta(double dx, double dy);
void flint_push_scroll(double dx, double dy);
void flint_push_size(int width, int height);
void flint_push_focus(int focused);
void flint_push_close(void);

/* cursor mode, shared with the Java layer so it knows whether a drag means "look" or "pointer" */
void flint_set_cursor_mode(GLFWwindow *window, int mode);
int flint_cursor_grabbed(void);

#ifdef __cplusplus
}
#endif
#endif
