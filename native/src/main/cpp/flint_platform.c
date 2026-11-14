#include "flint_platform.h"

#include <dlfcn.h>
#include <pthread.h>

flint_render_config_t flint_render_config = {0, 0, 0, 0, 0.0f};

static pthread_mutex_t g_loader_mutex = PTHREAD_MUTEX_INITIALIZER;
static void *g_egl_handle = NULL;
static void *g_vulkan_handle = NULL;

static void *open_public_system_library(const char *soname, const char *fallback) {
    void *handle = dlopen(soname, RTLD_NOW | RTLD_LOCAL);
    if (handle == NULL && fallback != NULL) {
        handle = dlopen(fallback, RTLD_NOW | RTLD_LOCAL);
    }
    return handle;
}

void *flint_acquire_egl_handle(void) {
    pthread_mutex_lock(&g_loader_mutex);
    if (g_egl_handle == NULL) {
        g_egl_handle = open_public_system_library("libEGL.so", "libEGL.so.1");
    }
    void *result = g_egl_handle;
    pthread_mutex_unlock(&g_loader_mutex);
    return result;
}

void *flint_acquire_vulkan_handle(void) {
    pthread_mutex_lock(&g_loader_mutex);
    if (g_vulkan_handle == NULL) {
        g_vulkan_handle = open_public_system_library("libvulkan.so", "libvulkan.so.1");
    }
    void *result = g_vulkan_handle;
    pthread_mutex_unlock(&g_loader_mutex);
    return result;
}

void flint_set_display_params(int width, int height, float refresh_rate) {
    pthread_mutex_lock(&g_loader_mutex);
    flint_render_config.display_width_px = width > 0 ? width : 0;
    flint_render_config.display_height_px = height > 0 ? height : 0;
    flint_render_config.display_refresh_hz = refresh_rate > 0.0f ? refresh_rate : 0.0f;
    pthread_mutex_unlock(&g_loader_mutex);
}
