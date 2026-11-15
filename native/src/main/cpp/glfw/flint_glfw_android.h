#ifndef FLINT_GLFW_ANDROID_H
#define FLINT_GLFW_ANDROID_H

#include <android/native_window.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Called from the Android UI thread when the SurfaceView's surface appears/changes/disappears. */
void flint_android_surface_set(ANativeWindow *window, int width, int height);
void flint_android_surface_resize(int width, int height);
void flint_android_surface_clear(void);

/* Number of frames the game has presented so far (0 until Minecraft draws its first frame). */
unsigned long long flint_android_frame_count(void);

/* Installs the EGL backend into the GLFW core. Safe to call more than once. */
void flint_android_install_platform(void);

#ifdef __cplusplus
}
#endif
#endif
