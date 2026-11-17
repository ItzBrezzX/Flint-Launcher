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

void flint_host_set_native_library_dir(const char *path);
const char *flint_host_native_library_dir(void);
void flint_host_set_display_metrics(int32_t width, int32_t height, float refresh_hz);
FlintDisplayMetrics flint_host_display_metrics(void);
int flint_host_prefer_performance_cores(int enable);

#ifdef __cplusplus
}
#endif
