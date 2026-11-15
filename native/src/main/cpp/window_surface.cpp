#include <jni.h>
#include "flint_glfw_internal.h"
#include <dlfcn.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <mutex>

#define LOG_TAG "FlintWindow"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex g_surface_mutex;
ANativeWindow *g_window = nullptr;
EGLDisplay g_display = EGL_NO_DISPLAY;
EGLConfig g_config = nullptr;
EGLContext g_context = EGL_NO_CONTEXT;
EGLSurface g_surface = EGL_NO_SURFACE;
EGLint g_width = 0;
EGLint g_height = 0;

void destroy_egl_locked() {
    if (g_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (g_surface != EGL_NO_SURFACE) eglDestroySurface(g_display, g_surface);
        if (g_context != EGL_NO_CONTEXT) eglDestroyContext(g_display, g_context);
        eglTerminate(g_display);
    }
    g_surface = EGL_NO_SURFACE;
    g_context = EGL_NO_CONTEXT;
    g_display = EGL_NO_DISPLAY;
    g_config = nullptr;
    g_width = 0;
    g_height = 0;
}

bool create_egl_locked() {
    g_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g_display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed: 0x%x", eglGetError());
        return false;
    }
    EGLint major = 0, minor = 0;
    if (!eglInitialize(g_display, &major, &minor)) {
        LOGE("eglInitialize failed: 0x%x", eglGetError());
        g_display = EGL_NO_DISPLAY;
        return false;
    }

    const EGLint config_attributes[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 16,
        EGL_NONE
    };
    EGLint count = 0;
    if (!eglChooseConfig(g_display, config_attributes, &g_config, 1, &count) || count == 0) {
        LOGE("No EGL window config: 0x%x", eglGetError());
        destroy_egl_locked();
        return false;
    }

    EGLint native_visual_id = 0;
    if (!eglGetConfigAttrib(g_display, g_config, EGL_NATIVE_VISUAL_ID, &native_visual_id)) {
        LOGE("Could not read EGL_NATIVE_VISUAL_ID: 0x%x", eglGetError());
        destroy_egl_locked();
        return false;
    }
    if (ANativeWindow_setBuffersGeometry(g_window, 0, 0, native_visual_id) != 0) {
        LOGE("ANativeWindow_setBuffersGeometry failed");
        destroy_egl_locked();
        return false;
    }

    const EGLint context_attributes[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    g_context = eglCreateContext(g_display, g_config, EGL_NO_CONTEXT, context_attributes);
    if (g_context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed: 0x%x", eglGetError());
        destroy_egl_locked();
        return false;
    }

    g_surface = eglCreateWindowSurface(g_display, g_config, g_window, nullptr);
    if (g_surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed: 0x%x", eglGetError());
        destroy_egl_locked();
        return false;
    }
    if (!eglMakeCurrent(g_display, g_surface, g_surface, g_context)) {
        LOGE("eglMakeCurrent failed: 0x%x", eglGetError());
        destroy_egl_locked();
        return false;
    }

    eglQuerySurface(g_display, g_surface, EGL_WIDTH, &g_width);
    eglQuerySurface(g_display, g_surface, EGL_HEIGHT, &g_height);
    glViewport(0, 0, g_width, g_height);
    glClearColor(0.035f, 0.045f, 0.065f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    if (!eglSwapBuffers(g_display, g_surface)) {
        LOGE("Initial eglSwapBuffers failed: 0x%x", eglGetError());
        destroy_egl_locked();
        return false;
    }
    // Do not leave the context owned by the Android UI thread. The eventual
    // game render thread must explicitly acquire it with nativeSurfaceMakeCurrent.
    eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    LOGI("EGL window surface ready: %dx%d (EGL %d.%d)", g_width, g_height, major, minor);
    return true;
}
} // namespace

extern "C" bool flint_egl_window_is_ready() {
    std::lock_guard<std::mutex> lock(g_surface_mutex);
    return g_display != EGL_NO_DISPLAY && g_surface != EGL_NO_SURFACE &&
        g_context != EGL_NO_CONTEXT && g_window != nullptr;
}

extern "C" bool flint_egl_window_make_current() {
    std::lock_guard<std::mutex> lock(g_surface_mutex);
    if (g_display == EGL_NO_DISPLAY || g_surface == EGL_NO_SURFACE ||
        g_context == EGL_NO_CONTEXT) return false;
    return eglMakeCurrent(g_display, g_surface, g_surface, g_context) == EGL_TRUE;
}

extern "C" bool flint_egl_window_swap_buffers() {
    std::lock_guard<std::mutex> lock(g_surface_mutex);
    if (g_display == EGL_NO_DISPLAY || g_surface == EGL_NO_SURFACE) return false;
    return eglSwapBuffers(g_display, g_surface) == EGL_TRUE;
}

extern "C" void flint_egl_window_release_current() {
    std::lock_guard<std::mutex> lock(g_surface_mutex);
    if (g_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    }
}

extern "C" void flint_egl_window_get_size(int32_t *width, int32_t *height) {
    std::lock_guard<std::mutex> lock(g_surface_mutex);
    if (width) *width = g_width;
    if (height) *height = g_height;
}

extern "C" void *flint_egl_get_proc_address(const char *name) {
    if (name == nullptr) return nullptr;
    auto proc = eglGetProcAddress(name);
    if (proc != nullptr) return reinterpret_cast<void *>(proc);
    return dlsym(RTLD_DEFAULT, name);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSurfaceCreated(
    JNIEnv *env, jclass, jobject surface
) {
    if (surface == nullptr) return JNI_FALSE;
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) {
        LOGE("ANativeWindow_fromSurface returned null");
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_surface_mutex);
    destroy_egl_locked();
    if (g_window != nullptr) ANativeWindow_release(g_window);
    g_window = window;
    if (!create_egl_locked()) {
        ANativeWindow_release(g_window);
        g_window = nullptr;
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSurfaceChanged(
    JNIEnv *, jclass, jint width, jint height
) {
    std::lock_guard<std::mutex> lock(g_surface_mutex);
    if (g_display == EGL_NO_DISPLAY || g_surface == EGL_NO_SURFACE ||
        g_context == EGL_NO_CONTEXT || width <= 0 || height <= 0) {
        return JNI_FALSE;
    }
    if (!eglMakeCurrent(g_display, g_surface, g_surface, g_context)) {
        LOGE("eglMakeCurrent on resize failed: 0x%x", eglGetError());
        return JNI_FALSE;
    }
    g_width = width;
    g_height = height;
    glViewport(0, 0, width, height);
    glClearColor(0.035f, 0.045f, 0.065f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    const bool swapped = eglSwapBuffers(g_display, g_surface) == EGL_TRUE;
    eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    return swapped ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSurfaceMakeCurrent(
    JNIEnv *, jclass
) {
    std::lock_guard<std::mutex> lock(g_surface_mutex);
    if (g_display == EGL_NO_DISPLAY || g_surface == EGL_NO_SURFACE || g_context == EGL_NO_CONTEXT) {
        return JNI_FALSE;
    }
    return eglMakeCurrent(g_display, g_surface, g_surface, g_context) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSurfaceSwapBuffers(
    JNIEnv *, jclass
) {
    std::lock_guard<std::mutex> lock(g_surface_mutex);
    if (g_display == EGL_NO_DISPLAY || g_surface == EGL_NO_SURFACE) return JNI_FALSE;
    return eglSwapBuffers(g_display, g_surface) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSurfaceReleaseCurrent(
    JNIEnv *, jclass
) {
    flint_egl_window_release_current();
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSurfaceDestroyed(
    JNIEnv *, jclass
) {
    std::lock_guard<std::mutex> lock(g_surface_mutex);
    destroy_egl_locked();
    if (g_window != nullptr) {
        ANativeWindow_release(g_window);
        g_window = nullptr;
    }
    LOGI("Android game window surface detached");
}
