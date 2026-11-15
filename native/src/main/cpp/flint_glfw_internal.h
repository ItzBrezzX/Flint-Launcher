#pragma once

#include <cstdint>

#if defined(__GNUC__)
#define FLINT_GLFW_INTERNAL_API __attribute__((visibility("default")))
#else
#define FLINT_GLFW_INTERNAL_API
#endif

struct FlintGlfwEvent {
    int32_t type;
    int32_t a;
    int32_t b;
    int32_t c;
    int32_t d;
};

extern "C" {
FLINT_GLFW_INTERNAL_API void flint_glfw_post_event(int32_t type, int32_t a, int32_t b, int32_t c, int32_t d);
FLINT_GLFW_INTERNAL_API bool flint_glfw_poll_event(FlintGlfwEvent *event);
void flint_glfw_reset_window_state();
bool flint_glfw_window_should_close();
void flint_glfw_set_window_should_close(bool should_close);
void flint_glfw_get_window_state(int32_t *width, int32_t *height, bool *focused);
FLINT_GLFW_INTERNAL_API int32_t flint_glfw_get_key_state(int32_t key);
int32_t flint_glfw_get_mouse_button_state(int32_t button);
void flint_glfw_get_cursor_position(float *x, float *y);
FLINT_GLFW_INTERNAL_API uint64_t flint_glfw_dropped_event_count();

FLINT_GLFW_INTERNAL_API bool flint_egl_window_is_ready();
bool flint_egl_window_make_current();
bool flint_egl_window_swap_buffers();
FLINT_GLFW_INTERNAL_API void flint_egl_window_get_size(int32_t *width, int32_t *height);
void *flint_egl_get_proc_address(const char *name);
}
