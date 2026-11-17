#include "flint_host.h"

#include <android/log.h>
#include <pthread.h>
#include <sched.h>
#include <string.h>
#include <unistd.h>

#define LOG_TAG "FlintHost"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static char g_native_lib_dir[512] = {0};
static FlintDisplayMetrics g_metrics = {1280, 720, 60.0f};
static int g_prefer_perf = 0;

void flint_host_set_native_library_dir(const char *path) {
    if (!path) {
        g_native_lib_dir[0] = '\0';
        return;
    }
    strncpy(g_native_lib_dir, path, sizeof(g_native_lib_dir) - 1);
    g_native_lib_dir[sizeof(g_native_lib_dir) - 1] = '\0';
    LOGI("native library dir = %s", g_native_lib_dir);
}

const char *flint_host_native_library_dir(void) {
    return g_native_lib_dir;
}

void flint_host_set_display_metrics(int32_t width, int32_t height, float refresh_hz) {
    if (width > 0) g_metrics.width = width;
    if (height > 0) g_metrics.height = height;
    if (refresh_hz > 1.0f && refresh_hz < 1000.0f) g_metrics.refresh_hz = refresh_hz;
    LOGI("display metrics %dx%d @ %.2f Hz", g_metrics.width, g_metrics.height, g_metrics.refresh_hz);
}

FlintDisplayMetrics flint_host_display_metrics(void) {
    return g_metrics;
}

int flint_host_prefer_performance_cores(int enable) {
    g_prefer_perf = enable ? 1 : 0;
    if (!enable) {
        return 0;
    }
#if defined(__linux__)
    long ncpu = sysconf(_SC_NPROCESSORS_CONF);
    if (ncpu <= 0) {
        LOGW("CPU count unavailable");
        return -1;
    }
    cpu_set_t set;
    CPU_ZERO(&set);
    // Prefer the upper half of the logical CPU set when more than two cores exist.
    int start = ncpu > 2 ? (int)(ncpu / 2) : 0;
    for (int i = start; i < (int)ncpu; ++i) {
        CPU_SET(i, &set);
    }
    if (sched_setaffinity(0, sizeof(set), &set) != 0) {
        LOGW("sched_setaffinity failed");
        return -1;
    }
    LOGI("preferred performance CPU range [%d,%ld)", start, ncpu);
    return 0;
#else
    (void)enable;
    return -1;
#endif
}
