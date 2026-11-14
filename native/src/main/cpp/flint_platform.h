#ifndef FLINT_PLATFORM_H
#define FLINT_PLATFORM_H

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Flint-owned Android platform bridge used by the GLFW Android backend.
 * This exposes only public Android system-library loading and display hints.
 * It intentionally does not bypass Android linker namespaces or load private
 * platform libraries.
 */
typedef struct {
    int force_gles_context;
    int override_major_version;
    int disp_width;
    int disp_height;
    float disp_hz;
} flint_render_spec_t;

extern flint_render_spec_t flint_render_spec;

/* Handles remain open for the lifetime of the process. */
void *flint_acquire_egl_handle(void);
void *flint_acquire_vulkan_handle(void);

/* Optional display metadata supplied by the Android host. */
void flint_set_display_params(int width, int height, float refresh_rate);

#ifdef __cplusplus
}
#endif

#endif /* FLINT_PLATFORM_H */
