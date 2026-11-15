#pragma once

#include <cstdint>

struct FlintGlfwEvent {
    int32_t type;
    int32_t a;
    int32_t b;
    int32_t c;
    int32_t d;
};

extern "C" {
void flint_glfw_post_event(int32_t type, int32_t a, int32_t b, int32_t c, int32_t d);
bool flint_glfw_poll_event(FlintGlfwEvent *event);
void flint_glfw_reset_window_state();
bool flint_glfw_window_should_close();
void flint_glfw_set_window_should_close(bool should_close);
void flint_glfw_get_window_state(int32_t *width, int32_t *height, bool *focused);
int32_t flint_glfw_get_key_state(int32_t key);
int32_t flint_glfw_get_mouse_button_state(int32_t button);
void flint_glfw_get_cursor_position(float *x, float *y);
uint64_t flint_glfw_dropped_event_count();

bool flint_egl_window_is_ready();
bool flint_egl_window_make_current();
bool flint_egl_window_swap_buffers();
void flint_egl_window_get_size(int32_t *width, int32_t *height);
void *flint_egl_get_proc_address(const char *name);
}
