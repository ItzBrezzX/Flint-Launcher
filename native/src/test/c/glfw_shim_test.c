/*
 * Host-side tests for Flint's GLFW shim (core + exported API) with a fake platform.
 * Build/run: bash native/src/test/c/run.sh
 */
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include "../../main/cpp/glfw/flint_glfw_core.h"

#define CHECK(cond)                                                              \
    do {                                                                         \
        if (!(cond)) {                                                           \
            fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);     \
            failures++;                                                          \
        }                                                                        \
    } while (0)

static int failures = 0;

/* ---- fake platform ---- */
static int f_surface = 1, f_w = 1280, f_h = 720;
static int f_contexts = 0, f_current = 0, f_swaps = 0, f_interval = -99, f_destroyed = 0;
static int fp_wait(int ms) { (void)ms; return f_surface; }
static int fp_size(int *w, int *h) { if (!f_surface) return 0; *w = f_w; *h = f_h; return 1; }
static int fp_create(void) { f_contexts++; return 1; }
static int fp_current(int make) { f_current = make; return 1; }
static int fp_swap(void) { f_swaps++; return 1; }
static void fp_interval(int i) { f_interval = i; }
static void *fp_proc(const char *n) { return strcmp(n, "glClear") == 0 ? (void *)0x1234 : NULL; }
static void fp_destroy(void) { f_destroyed++; }
static const FlintPlatform fake = {fp_wait, fp_size, fp_create, fp_current, fp_swap, fp_interval, fp_proc, fp_destroy};

/* ---- the exported API under test ---- */
int glfwInit(void);
void glfwTerminate(void);
GLFWwindow *glfwCreateWindow(int, int, const char *, GLFWmonitor *, GLFWwindow *);
void glfwDestroyWindow(GLFWwindow *);
void glfwWindowHint(int, int);
void glfwDefaultWindowHints(void);
int glfwGetWindowAttrib(GLFWwindow *, int);
void glfwPollEvents(void);
void glfwWaitEvents(void);
void glfwWaitEventsTimeout(double);
void glfwPostEmptyEvent(void);
GLFWkeyfun glfwSetKeyCallback(GLFWwindow *, GLFWkeyfun);
GLFWcursorposfun glfwSetCursorPosCallback(GLFWwindow *, GLFWcursorposfun);
GLFWframebuffersizefun glfwSetFramebufferSizeCallback(GLFWwindow *, GLFWframebuffersizefun);
GLFWwindowsizefun glfwSetWindowSizeCallback(GLFWwindow *, GLFWwindowsizefun);
GLFWmousebuttonfun glfwSetMouseButtonCallback(GLFWwindow *, GLFWmousebuttonfun);
GLFWscrollfun glfwSetScrollCallback(GLFWwindow *, GLFWscrollfun);
GLFWcharfun glfwSetCharCallback(GLFWwindow *, GLFWcharfun);
GLFWerrorfun glfwSetErrorCallback(GLFWerrorfun);
int glfwGetKey(GLFWwindow *, int);
int glfwGetMouseButton(GLFWwindow *, int);
void glfwSetInputMode(GLFWwindow *, int, int);
int glfwGetInputMode(GLFWwindow *, int);
void glfwGetCursorPos(GLFWwindow *, double *, double *);
void glfwSetCursorPos(GLFWwindow *, double, double);
void glfwGetWindowSize(GLFWwindow *, int *, int *);
void glfwGetFramebufferSize(GLFWwindow *, int *, int *);
void glfwMakeContextCurrent(GLFWwindow *);
GLFWwindow *glfwGetCurrentContext(void);
void glfwSwapBuffers(GLFWwindow *);
void glfwSwapInterval(int);
GLFWglproc glfwGetProcAddress(const char *);
int glfwGetError(const char **);
const GLFWvidmode *glfwGetVideoMode(GLFWmonitor *);
GLFWmonitor *glfwGetPrimaryMonitor(void);
const char *glfwGetKeyName(int, int);
double glfwGetTime(void);
int glfwWindowShouldClose(GLFWwindow *);
void glfwSetClipboardString(GLFWwindow *, const char *);
const char *glfwGetClipboardString(GLFWwindow *);

/* ---- recorders ---- */
static int key_calls, last_key, last_scan, last_action, last_mods;
static void on_key(GLFWwindow *w, int k, int s, int a, int m) { (void)w; key_calls++; last_key = k; last_scan = s; last_action = a; last_mods = m; }
static int pos_calls; static double pos_x, pos_y;
static void on_pos(GLFWwindow *w, double x, double y) { (void)w; pos_calls++; pos_x = x; pos_y = y; }
static int fb_calls, win_calls, fb_w, fb_h;
static void on_fb(GLFWwindow *w, int x, int y) { (void)w; fb_calls++; fb_w = x; fb_h = y; }
static void on_win(GLFWwindow *w, int x, int y) { (void)w; win_calls++; (void)x; (void)y; }
static int mb_calls, mb_btn, mb_act;
static void on_mb(GLFWwindow *w, int b, int a, int m) { (void)w; (void)m; mb_calls++; mb_btn = b; mb_act = a; }
static double sc_x, sc_y; static int sc_calls;
static void on_scroll(GLFWwindow *w, double x, double y) { (void)w; sc_calls++; sc_x = x; sc_y = y; }
static unsigned last_char; static void on_char(GLFWwindow *w, unsigned c) { (void)w; last_char = c; }
static int err_code; static char err_text[128];
static void on_err(int c, const char *t) { err_code = c; snprintf(err_text, sizeof err_text, "%s", t); }

static void reset_recorders(void) {
    key_calls = pos_calls = fb_calls = win_calls = mb_calls = sc_calls = 0;
    last_char = 0;
}

static void *producer_thread(void *arg) {
    (void)arg;
    usleep(30 * 1000);
    flint_push_key(65, 30, GLFW_PRESS, 2);
    return NULL;
}

int main(void) {
    /* init without a platform must fail cleanly */
    flint_set_platform(NULL);
    glfwSetErrorCallback(on_err);
    CHECK(glfwInit() == GLFW_FALSE);
    CHECK(err_code == GLFW_PLATFORM_ERROR);

    flint_set_platform(&fake);
    CHECK(glfwInit() == GLFW_TRUE);

    /* window creation takes its size from the surface, not from the request */
    glfwDefaultWindowHints();
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
    GLFWwindow *w = glfwCreateWindow(854, 480, "Minecraft", NULL, NULL);
    CHECK(w != NULL);
    int ww = 0, wh = 0;
    glfwGetWindowSize(w, &ww, &wh);
    CHECK(ww == 1280 && wh == 720);
    glfwGetFramebufferSize(w, &ww, &wh);
    CHECK(ww == 1280 && wh == 720);
    CHECK(f_contexts == 1);
    CHECK(glfwGetWindowAttrib(w, GLFW_CONTEXT_VERSION_MAJOR) == 3);
    CHECK(glfwGetWindowAttrib(w, GLFW_CLIENT_API) == GLFW_OPENGL_API);

    /* only one window */
    CHECK(glfwCreateWindow(1, 1, "x", NULL, NULL) == NULL);
    CHECK(err_code == GLFW_PLATFORM_ERROR);
    const char *desc = NULL;
    CHECK(glfwGetError(&desc) == GLFW_PLATFORM_ERROR && desc != NULL);
    CHECK(glfwGetError(NULL) == GLFW_NO_ERROR);

    /* context / swap / proc address */
    CHECK(glfwGetCurrentContext() == NULL);
    glfwMakeContextCurrent(w);
    CHECK(glfwGetCurrentContext() == w && f_current == 1);
    glfwSwapInterval(1);
    CHECK(f_interval == 1);
    glfwSwapBuffers(w);
    CHECK(f_swaps == 1);
    CHECK(glfwGetProcAddress("glClear") == (GLFWglproc)0x1234);
    CHECK(glfwGetProcAddress("nope") == NULL);

    /* keyboard: press, auto-repeat conversion, release, polled state */
    CHECK(glfwSetKeyCallback(w, on_key) == NULL);
    CHECK(glfwSetKeyCallback(w, on_key) == on_key); /* returns previous */
    flint_push_key(87, 17, GLFW_PRESS, 0);
    glfwPollEvents();
    CHECK(key_calls == 1 && last_key == 87 && last_scan == 17 && last_action == GLFW_PRESS);
    CHECK(glfwGetKey(w, 87) == GLFW_PRESS);
    flint_push_key(87, 17, GLFW_PRESS, 0);
    glfwPollEvents();
    CHECK(key_calls == 2 && last_action == GLFW_REPEAT);
    flint_push_key(87, 17, GLFW_RELEASE, 0);
    flint_push_key(87, 17, GLFW_RELEASE, 0); /* stray duplicate release is swallowed */
    glfwPollEvents();
    CHECK(key_calls == 3 && last_action == GLFW_RELEASE && glfwGetKey(w, 87) == GLFW_RELEASE);

    /* mouse buttons, scroll, chars */
    reset_recorders();
    glfwSetMouseButtonCallback(w, on_mb);
    glfwSetScrollCallback(w, on_scroll);
    glfwSetCharCallback(w, on_char);
    flint_push_mouse_button(0, GLFW_PRESS, 0);
    flint_push_scroll(0.0, -1.0);
    flint_push_char(0x1F600, 0);
    glfwPollEvents();
    CHECK(mb_calls == 1 && mb_btn == 0 && mb_act == GLFW_PRESS && glfwGetMouseButton(w, 0) == GLFW_PRESS);
    CHECK(sc_calls == 1 && sc_y == -1.0);
    CHECK(last_char == 0x1F600);

    /* visible cursor: absolute positions pass through, deltas are ignored */
    reset_recorders();
    glfwSetCursorPosCallback(w, on_pos);
    flint_push_cursor_pos(100.0, 50.0);
    flint_push_cursor_delta(7.0, 7.0);
    glfwPollEvents();
    CHECK(pos_calls == 1 && pos_x == 100.0 && pos_y == 50.0);
    double cx, cy;
    glfwGetCursorPos(w, &cx, &cy);
    CHECK(cx == 100.0 && cy == 50.0);

    /* grabbed cursor: deltas accumulate into a virtual position, absolute events are ignored */
    reset_recorders();
    CHECK(flint_cursor_grabbed() == 0);
    glfwSetInputMode(w, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
    CHECK(flint_cursor_grabbed() == 1);
    CHECK(glfwGetInputMode(w, GLFW_CURSOR) == GLFW_CURSOR_DISABLED);
    flint_push_cursor_pos(999.0, 999.0);
    flint_push_cursor_delta(3.0, -2.0);
    flint_push_cursor_delta(2.0, -1.0); /* coalesced with the previous delta */
    glfwPollEvents();
    CHECK(pos_calls == 1);
    CHECK(pos_x == 105.0 && pos_y == 47.0);
    flint_push_cursor_delta(1.0, 1.0);
    glfwPollEvents();
    CHECK(pos_calls == 2 && pos_x == 106.0 && pos_y == 48.0);
    glfwSetInputMode(w, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    CHECK(flint_cursor_grabbed() == 0);
    glfwGetCursorPos(w, &cx, &cy);
    CHECK(cx == 100.0 && cy == 50.0); /* returns to where it was before the grab */

    /* surface resize fires both size callbacks once; an identical size is ignored */
    reset_recorders();
    glfwSetFramebufferSizeCallback(w, on_fb);
    glfwSetWindowSizeCallback(w, on_win);
    flint_push_size(1600, 720);
    flint_push_size(1600, 720);
    glfwPollEvents();
    CHECK(fb_calls == 1 && win_calls == 1 && fb_w == 1600 && fb_h == 720);
    glfwGetWindowSize(w, &ww, &wh);
    CHECK(ww == 1600 && wh == 720);

    /* wait/wake: a producer on another thread wakes glfwWaitEvents */
    reset_recorders();
    pthread_t t;
    pthread_create(&t, NULL, producer_thread, NULL);
    glfwWaitEvents();
    pthread_join(t, NULL);
    CHECK(key_calls == 1 && last_key == 65 && last_mods == 2);
    flint_push_key(65, 30, GLFW_RELEASE, 0);
    glfwPollEvents();

    /* timeout variant returns without events */
    double t0 = glfwGetTime();
    glfwWaitEventsTimeout(0.05);
    double dt = glfwGetTime() - t0;
    CHECK(dt >= 0.04 && dt < 1.0);
    glfwPostEmptyEvent();
    t0 = glfwGetTime();
    glfwWaitEvents(); /* returns immediately because of the posted wake-up */
    CHECK(glfwGetTime() - t0 < 0.5);

    /* queue overflow drops the oldest, never crashes, keeps newest */
    reset_recorders();
    for (int i = 0; i < FLINT_QUEUE_CAPACITY + 50; i++) flint_push_mouse_button(1, (i % 2) ? GLFW_PRESS : GLFW_RELEASE, 0);
    CHECK(flint_event_pending() == FLINT_QUEUE_CAPACITY);
    glfwPollEvents();
    CHECK(flint_event_pending() == 0);

    /* focus/close events */
    flint_push_close();
    glfwPollEvents();
    CHECK(glfwWindowShouldClose(w) == GLFW_TRUE);

    /* video mode and monitor follow the surface; key names; clipboard */
    const GLFWvidmode *vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
    CHECK(vm && vm->width == f_w && vm->height == f_h && vm->refreshRate == 60);
    CHECK(strcmp(glfwGetKeyName(65, 0), "a") == 0);
    CHECK(glfwGetKeyName(256, 0) == NULL);
    glfwSetClipboardString(w, "hello");
    CHECK(strcmp(glfwGetClipboardString(w), "hello") == 0);

    /* input arriving without a window is dropped, not queued forever */
    glfwDestroyWindow(w);
    CHECK(f_destroyed == 1 && glfwGetCurrentContext() == NULL);
    flint_push_key(1, 1, GLFW_PRESS, 0);
    glfwPollEvents();
    CHECK(flint_event_pending() == 0);

    /* terminate then init again works */
    glfwTerminate();
    CHECK(glfwInit() == GLFW_TRUE);
    f_surface = 0;
    err_code = 0;
    CHECK(glfwCreateWindow(10, 10, "x", NULL, NULL) == NULL); /* no surface in time */
    CHECK(err_code == GLFW_PLATFORM_ERROR);
    glfwTerminate();

    if (failures) {
        fprintf(stderr, "%d check(s) failed\n", failures);
        return 1;
    }
    printf("glfw shim: all checks passed\n");
    return 0;
}
