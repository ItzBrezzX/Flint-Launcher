#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct FlintDisplayMetrics {
    int32_t width;
    int32_t height;
    float refresh_hz;
} FlintDisplayMetrics;

/** Register the APK nativeLibraryDir used when resolving process-local .so files. */
void flint_host_set_native_library_dir(const char *path);

/** Last registered native library directory, or empty string. */
const char *flint_host_native_library_dir(void);

/** Publish the active Android window size and preferred refresh rate. */
void flint_host_set_display_metrics(int32_t width, int32_t height, float refresh_hz);

FlintDisplayMetrics flint_host_display_metrics(void);

/**
 * Best-effort preference for performance cores. Returns 0 on success, -1 if
 * unavailable on this kernel/device. Never crashes the process on failure.
 */
int flint_host_prefer_performance_cores(int enable);

#ifdef __cplusplus
}
#endif
