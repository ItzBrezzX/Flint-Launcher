#include "flint_glfw_core.h"

#include <errno.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static const FlintPlatform *g_platform = NULL;
static GLFWwindow *g_window = NULL;
static volatile int g_cursor_grabbed = 0;

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_cond = PTHREAD_COND_INITIALIZER;
static FlintEvent g_queue[FLINT_QUEUE_CAPACITY];
static size_t g_head = 0;
static size_t g_count = 0;
static int g_wake = 0;

void flint_set_platform(const FlintPlatform *platform) { g_platform = platform; }
const FlintPlatform *flint_get_platform(void) { return g_platform; }

GLFWwindow *flint_window(void) { return g_window; }
void flint_set_window(GLFWwindow *window) { g_window = window; }

GLFWwindow *flint_new_window(int width, int height, const char *title) {
    GLFWwindow *window = (GLFWwindow *)calloc(1, sizeof(GLFWwindow));
    if (!window) return NULL;
    window->width = width;
    window->height = height;
    window->focused = GLFW_TRUE;
    window->cursor_mode = GLFW_CURSOR_NORMAL;
    window->cursor_x = window->saved_x = width / 2.0;
    window->cursor_y = window->saved_y = height / 2.0;
    if (title) {
        window->title = strdup(title);
    }
    return window;
}

void flint_free_window(GLFWwindow *window) {
    if (!window) return;
    free(window->title);
    free(window);
}

/* ------------------------------------------------------------------ queue */

void flint_event_push(const FlintEvent *event) {
    pthread_mutex_lock(&g_lock);
    if (event->type == FLINT_EV_CURSOR_DELTA && g_count > 0) {
        /* Coalesce a flood of finger-move deltas into one entry. */
        FlintEvent *last = &g_queue[(g_head + g_count - 1) % FLINT_QUEUE_CAPACITY];
        if (last->type == FLINT_EV_CURSOR_DELTA) {
            last->d0 += event->d0;
            last->d1 += event->d1;
            pthread_cond_broadcast(&g_cond);
            pthread_mutex_unlock(&g_lock);
            return;
        }
    }
    if (g_count == FLINT_QUEUE_CAPACITY) {
        /* Full: drop the oldest so that fresh input (and releases) are never the ones lost. */
        g_head = (g_head + 1) % FLINT_QUEUE_CAPACITY;
        g_count--;
    }
    g_queue[(g_head + g_count) % FLINT_QUEUE_CAPACITY] = *event;
    g_count++;
    pthread_cond_broadcast(&g_cond);
    pthread_mutex_unlock(&g_lock);
}

int flint_event_wait(double timeout_seconds) {
    pthread_mutex_lock(&g_lock);
    if (timeout_seconds < 0) {
        while (g_count == 0 && !g_wake) pthread_cond_wait(&g_cond, &g_lock);
    } else if (g_count == 0 && !g_wake) {
        struct timespec deadline;
        clock_gettime(CLOCK_REALTIME, &deadline);
        long long nanos = (long long)(timeout_seconds * 1e9);
        deadline.tv_sec += (time_t)(nanos / 1000000000LL);
        deadline.tv_nsec += (long)(nanos % 1000000000LL);
        if (deadline.tv_nsec >= 1000000000L) {
            deadline.tv_sec += 1;
            deadline.tv_nsec -= 1000000000L;
        }
        while (g_count == 0 && !g_wake) {
            if (pthread_cond_timedwait(&g_cond, &g_lock, &deadline) == ETIMEDOUT) break;
        }
    }
    g_wake = 0;
    int pending = g_count > 0;
    pthread_mutex_unlock(&g_lock);
    return pending;
}

void flint_event_wake(void) {
    pthread_mutex_lock(&g_lock);
    g_wake = 1;
    pthread_cond_broadcast(&g_cond);
    pthread_mutex_unlock(&g_lock);
}

size_t flint_event_pending(void) {
    pthread_mutex_lock(&g_lock);
    size_t count = g_count;
    pthread_mutex_unlock(&g_lock);
    return count;
}

void flint_event_clear(void) {
    pthread_mutex_lock(&g_lock);
    g_head = 0;
    g_count = 0;
    g_wake = 0;
    pthread_mutex_unlock(&g_lock);
}

/* --------------------------------------------------------------- dispatch */

static void dispatch_one(GLFWwindow *w, const FlintEvent *ev) {
    switch (ev->type) {
    case FLINT_EV_KEY: {
        int key = ev->i0;
        int action = ev->i2;
        if (key >= 0 && key < FLINT_MAX_KEYS) {
            if (action == GLFW_RELEASE && w->keys[key] == GLFW_RELEASE) break; /* stray release */
            if (action == GLFW_PRESS && w->keys[key] == GLFW_PRESS) action = GLFW_REPEAT;
            w->keys[key] = (action == GLFW_RELEASE) ? GLFW_RELEASE : GLFW_PRESS;
        }
        if (w->cb.key) w->cb.key(w, key, ev->i1, action, ev->i3);
        break;
    }
    case FLINT_EV_CHAR:
        if (w->cb.character) w->cb.character(w, (unsigned int)ev->i0);
        if (w->cb.charmods) w->cb.charmods(w, (unsigned int)ev->i0, ev->i1);
        break;
    case FLINT_EV_MOUSE_BUTTON: {
        int button = ev->i0;
        if (button >= 0 && button < FLINT_MAX_MOUSE_BUTTONS) {
            if (ev->i1 == GLFW_RELEASE && w->mouse_buttons[button] == GLFW_RELEASE) break;
            w->mouse_buttons[button] = (unsigned char)(ev->i1 == GLFW_RELEASE ? GLFW_RELEASE : GLFW_PRESS);
        }
        if (w->cb.mouse_button) w->cb.mouse_button(w, button, ev->i1, ev->i2);
        break;
    }
    case FLINT_EV_CURSOR_POS:
        if (w->cursor_mode == GLFW_CURSOR_DISABLED) break; /* absolute positions mean nothing while grabbed */
        w->cursor_x = ev->d0;
        w->cursor_y = ev->d1;
        if (w->cb.cursor_pos) w->cb.cursor_pos(w, w->cursor_x, w->cursor_y);
        break;
    case FLINT_EV_CURSOR_DELTA:
        if (w->cursor_mode != GLFW_CURSOR_DISABLED) break;
        w->virtual_x += ev->d0;
        w->virtual_y += ev->d1;
        if (w->cb.cursor_pos) w->cb.cursor_pos(w, w->virtual_x, w->virtual_y);
        break;
    case FLINT_EV_SCROLL:
        if (w->cb.scroll) w->cb.scroll(w, ev->d0, ev->d1);
        break;
    case FLINT_EV_SIZE:
        if (ev->i0 <= 0 || ev->i1 <= 0) break;
        if (ev->i0 == w->width && ev->i1 == w->height) break;
        w->width = ev->i0;
        w->height = ev->i1;
        if (w->cb.size) w->cb.size(w, w->width, w->height);
        if (w->cb.framebuffer) w->cb.framebuffer(w, w->width, w->height);
        if (w->cb.refresh) w->cb.refresh(w);
        break;
    case FLINT_EV_FOCUS:
        if (w->focused == ev->i0) break;
        w->focused = ev->i0;
        if (w->cb.focus) w->cb.focus(w, ev->i0);
        break;
    case FLINT_EV_CLOSE:
        w->should_close = GLFW_TRUE;
        if (w->cb.close) w->cb.close(w);
        break;
    default:
        break;
    }
}

void flint_dispatch_events(void) {
    FlintEvent batch[FLINT_QUEUE_CAPACITY];
    size_t n;

    pthread_mutex_lock(&g_lock);
    n = g_count;
    for (size_t i = 0; i < n; i++) batch[i] = g_queue[(g_head + i) % FLINT_QUEUE_CAPACITY];
    g_head = 0;
    g_count = 0;
    pthread_mutex_unlock(&g_lock);

    GLFWwindow *window = g_window;
    if (!window) return; /* input that arrives before a window exists is dropped */
    /* Callbacks run outside the lock: they re-enter the JVM and may call glfw* functions. */
    for (size_t i = 0; i < n; i++) dispatch_one(window, &batch[i]);
}

/* -------------------------------------------------------------- producers */

static void push(FlintEvent e) { flint_event_push(&e); }

void flint_push_key(int key, int scancode, int action, int mods) {
    FlintEvent e = {FLINT_EV_KEY, key, scancode, action, mods, 0, 0};
    push(e);
}
void flint_push_char(unsigned int codepoint, int mods) {
    FlintEvent e = {FLINT_EV_CHAR, (int)codepoint, mods, 0, 0, 0, 0};
    push(e);
}
void flint_push_mouse_button(int button, int action, int mods) {
    FlintEvent e = {FLINT_EV_MOUSE_BUTTON, button, action, mods, 0, 0, 0};
    push(e);
}
void flint_push_cursor_pos(double x, double y) {
    FlintEvent e = {FLINT_EV_CURSOR_POS, 0, 0, 0, 0, x, y};
    push(e);
}
void flint_push_cursor_delta(double dx, double dy) {
    FlintEvent e = {FLINT_EV_CURSOR_DELTA, 0, 0, 0, 0, dx, dy};
    push(e);
}
void flint_push_scroll(double dx, double dy) {
    FlintEvent e = {FLINT_EV_SCROLL, 0, 0, 0, 0, dx, dy};
    push(e);
}
void flint_push_size(int width, int height) {
    FlintEvent e = {FLINT_EV_SIZE, width, height, 0, 0, 0, 0};
    push(e);
}
void flint_push_focus(int focused) {
    FlintEvent e = {FLINT_EV_FOCUS, focused ? 1 : 0, 0, 0, 0, 0, 0};
    push(e);
}
void flint_push_close(void) {
    FlintEvent e = {FLINT_EV_CLOSE, 0, 0, 0, 0, 0, 0};
    push(e);
}

/* ------------------------------------------------------------ cursor mode */

void flint_set_cursor_mode(GLFWwindow *window, int mode) {
    if (!window || window->cursor_mode == mode) return;
    int was_disabled = window->cursor_mode == GLFW_CURSOR_DISABLED;
    int now_disabled = mode == GLFW_CURSOR_DISABLED;
    if (now_disabled && !was_disabled) {
        window->saved_x = window->cursor_x;
        window->saved_y = window->cursor_y;
        window->virtual_x = window->cursor_x;
        window->virtual_y = window->cursor_y;
    } else if (was_disabled && !now_disabled) {
        window->cursor_x = window->saved_x;
        window->cursor_y = window->saved_y;
    }
    window->cursor_mode = mode;
    g_cursor_grabbed = now_disabled ? 1 : 0;
}

int flint_cursor_grabbed(void) { return g_cursor_grabbed; }
