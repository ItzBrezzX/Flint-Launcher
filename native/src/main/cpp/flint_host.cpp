#include "flint_host.h"
#include "flint_cpu_affinity.h"

#include <android/log.h>
#include <errno.h>
#include <sched.h>
#include <string.h>
#include <unistd.h>

#define LOG_TAG "FlintHost"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static char g_native_lib_dir[512] = {0};
static FlintDisplayMetrics g_metrics = {1280, 720, 60.0f};

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
    if (!enable) return 0;
#if defined(__linux__)
    long configured_cpus = sysconf(_SC_NPROCESSORS_CONF);
    if (configured_cpus <= 0) {
        LOGW("CPU count unavailable");
        return -1;
    }

    cpu_set_t allowed;
    CPU_ZERO(&allowed);
    if (sched_getaffinity(0, sizeof(allowed), &allowed) != 0) {
        LOGW("sched_getaffinity failed: %s", strerror(errno));
        return -1;
    }

    cpu_set_t performance_cpus;
    unsigned long max_frequency = 0;
    if (!flint_select_performance_cpus(
            static_cast<int>(configured_cpus), &allowed,
            "/sys/devices/system/cpu", &performance_cpus, &max_frequency)) {
        LOGW("CPU frequency topology unavailable; keeping the current affinity");
        return -1;
    }

    if (sched_setaffinity(0, sizeof(performance_cpus), &performance_cpus) != 0) {
        LOGW("sched_setaffinity for performance CPUs failed: %s", strerror(errno));
        return -1;
    }

    LOGI("selected %d performance CPU(s) at max frequency %lu kHz",
         CPU_COUNT(&performance_cpus), max_frequency);
    return 0;
#else
    (void)enable;
    return -1;
#endif
}
