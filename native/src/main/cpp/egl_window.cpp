#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <stdio.h>
#include <mutex>

#define LOG_TAG "FlintEglWindow"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_window_mutex;
static EGLDisplay g_window_display = EGL_NO_DISPLAY;
static EGLSurface g_window_surface = EGL_NO_SURFACE;
static EGLContext g_window_context = EGL_NO_CONTEXT;
static ANativeWindow *g_native_window = nullptr;

static jstring egl_error(JNIEnv *env, const char *operation) {
    char message[160];
    const EGLint error = eglGetError();
    snprintf(message, sizeof(message), "egl-window-%s:0x%04x", operation, error);
    LOGE("%s", message);
    return env->NewStringUTF(message);
}

static void release_partial(
    EGLDisplay display,
    EGLSurface surface,
    EGLContext context,
    ANativeWindow *window,
    bool initialized
) {
    if (display != EGL_NO_DISPLAY) {
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
        if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
        if (initialized) eglTerminate(display);
    }
    if (window != nullptr) ANativeWindow_release(window);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeCreateEglWindow(
    JNIEnv *env, jobject, jobject java_surface
) {
    if (java_surface == nullptr) return env->NewStringUTF("egl-window-null-surface");

    std::lock_guard<std::mutex> guard(g_window_mutex);
    if (g_native_window != nullptr || g_window_surface != EGL_NO_SURFACE) {
        return env->NewStringUTF("egl-window-already-attached");
    }

    ANativeWindow *window = ANativeWindow_fromSurface(env, java_surface);
    if (window == nullptr) return env->NewStringUTF("egl-window-native-window-failed");

    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        const jstring result = egl_error(env, "get-display-failed");
        ANativeWindow_release(window);
        return result;
    }

    EGLint major = 0;
    EGLint minor = 0;
    if (!eglInitialize(display, &major, &minor)) {
        const jstring result = egl_error(env, "initialize-failed");
        release_partial(display, EGL_NO_SURFACE, EGL_NO_CONTEXT, window, false);
        return result;
    }

    if (!eglBindAPI(EGL_OPENGL_ES_API)) {
        const jstring result = egl_error(env, "bind-api-failed");
        release_partial(display, EGL_NO_SURFACE, EGL_NO_CONTEXT, window, true);
        return result;
    }

    const EGLint config_attributes[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };
    EGLConfig config = nullptr;
    EGLint config_count = 0;
    if (!eglChooseConfig(display, config_attributes, &config, 1, &config_count) || config_count < 1) {
        const jstring result = egl_error(env, "choose-window-config-failed");
        release_partial(display, EGL_NO_SURFACE, EGL_NO_CONTEXT, window, true);
        return result;
    }

    EGLint visual_format = 0;
    if (eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, &visual_format) && visual_format != 0) {
        if (ANativeWindow_setBuffersGeometry(window, 0, 0, visual_format) != 0) {
            char message[128];
            snprintf(message, sizeof(message), "egl-window-set-buffer-geometry-failed:%d",
                ANativeWindow_getFormat(window));
            LOGE("%s", message);
            release_partial(display, EGL_NO_SURFACE, EGL_NO_CONTEXT, window, true);
            return env->NewStringUTF(message);
        }
    }

    EGLSurface surface = eglCreateWindowSurface(display, config, window, nullptr);
    if (surface == EGL_NO_SURFACE) {
        const jstring result = egl_error(env, "create-window-surface-failed");
        release_partial(display, surface, EGL_NO_CONTEXT, window, true);
        return result;
    }

    const EGLint context_attributes[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attributes);
    if (context == EGL_NO_CONTEXT) {
        const jstring result = egl_error(env, "create-context-failed");
        release_partial(display, surface, context, window, true);
        return result;
    }

    if (!eglMakeCurrent(display, surface, surface, context)) {
        const jstring result = egl_error(env, "make-current-failed");
        release_partial(display, surface, context, window, true);
        return result;
    }

    const int width = ANativeWindow_getWidth(window);
    const int height = ANativeWindow_getHeight(window);
    glViewport(0, 0, width, height);
    glClearColor(0.04f, 0.13f, 0.08f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glFinish();
    const GLenum gl_error = glGetError();
    if (gl_error != GL_NO_ERROR) {
        char message[128];
        snprintf(message, sizeof(message), "egl-window-gles-clear-failed:0x%04x", gl_error);
        LOGE("%s", message);
        release_partial(display, surface, context, window, true);
        return env->NewStringUTF(message);
    }

    if (!eglSwapBuffers(display, surface)) {
        const jstring result = egl_error(env, "initial-swap-failed");
        release_partial(display, surface, context, window, true);
        return result;
    }

    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    g_window_display = display;
    g_window_surface = surface;
    g_window_context = context;
    g_native_window = window;

    char message[128];
    snprintf(message, sizeof(message), "egl-window-ok:%dx%d", width, height);
    LOGI("%s (EGL %d.%d)", message, major, minor);
    return env->NewStringUTF(message);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeRenderEglWindowFrame(
    JNIEnv *env, jobject
) {
    std::lock_guard<std::mutex> guard(g_window_mutex);
    if (g_native_window == nullptr || g_window_display == EGL_NO_DISPLAY ||
        g_window_surface == EGL_NO_SURFACE || g_window_context == EGL_NO_CONTEXT) {
        return env->NewStringUTF("egl-window-not-attached");
    }

    if (!eglMakeCurrent(g_window_display, g_window_surface, g_window_surface, g_window_context)) {
        return egl_error(env, "frame-make-current-failed");
    }

    const int width = ANativeWindow_getWidth(g_native_window);
    const int height = ANativeWindow_getHeight(g_native_window);
    glViewport(0, 0, width, height);
    glClearColor(0.04f, 0.13f, 0.08f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glFinish();

    const GLenum gl_error = glGetError();
    if (gl_error != GL_NO_ERROR) {
        eglMakeCurrent(g_window_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        char message[128];
        snprintf(message, sizeof(message), "egl-window-frame-gles-failed:0x%04x", gl_error);
        return env->NewStringUTF(message);
    }

    if (!eglSwapBuffers(g_window_display, g_window_surface)) {
        const jstring result = egl_error(env, "frame-swap-failed");
        eglMakeCurrent(g_window_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        return result;
    }

    eglMakeCurrent(g_window_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    return env->NewStringUTF("egl-window-frame-ok");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeDestroyEglWindow(
    JNIEnv *env, jobject
) {
    std::lock_guard<std::mutex> guard(g_window_mutex);
    if (g_window_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(g_window_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (g_window_context != EGL_NO_CONTEXT) {
            eglDestroyContext(g_window_display, g_window_context);
        }
        if (g_window_surface != EGL_NO_SURFACE) {
            eglDestroySurface(g_window_display, g_window_surface);
        }
        eglTerminate(g_window_display);
        eglReleaseThread();
    }
    if (g_native_window != nullptr) ANativeWindow_release(g_native_window);

    g_window_display = EGL_NO_DISPLAY;
    g_window_surface = EGL_NO_SURFACE;
    g_window_context = EGL_NO_CONTEXT;
    g_native_window = nullptr;

    LOGI("egl-window-destroyed");
    return env->NewStringUTF("egl-window-destroyed");
}
