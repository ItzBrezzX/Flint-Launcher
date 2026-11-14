#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <gl4esinit.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <mutex>

#define LOG_TAG "FlintEglWindow"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using Gl4esClearColor = void (*)(GLfloat, GLfloat, GLfloat, GLfloat);
using Gl4esClear = void (*)(GLbitfield);
using Gl4esFinish = void (*)();
using Gl4esGetError = GLenum (*)();
using Gl4esGetString = const GLubyte *(*)(GLenum);

static std::mutex g_window_mutex;
static EGLDisplay g_window_display = EGL_NO_DISPLAY;
static EGLSurface g_window_surface = EGL_NO_SURFACE;
static EGLContext g_window_context = EGL_NO_CONTEXT;
static ANativeWindow *g_native_window = nullptr;
static bool g_gl4es_initialized = false;
static int g_framebuffer_width = 0;
static int g_framebuffer_height = 0;
static Gl4esClearColor g_gl4es_clear_color = nullptr;
static Gl4esClear g_gl4es_clear = nullptr;
static Gl4esFinish g_gl4es_finish = nullptr;
static Gl4esGetError g_gl4es_get_error = nullptr;
static Gl4esGetString g_gl4es_get_string = nullptr;

static void *flint_gl4es_get_proc_address(const char *name) {
    if (name == nullptr) return nullptr;
    void *symbol = dlsym(RTLD_DEFAULT, name);
    if (symbol != nullptr) return symbol;
    const auto proc = eglGetProcAddress(name);
    return reinterpret_cast<void *>(proc);
}

static void flint_gl4es_get_main_fb_size(int *width, int *height) {
    if (width != nullptr) *width = g_framebuffer_width;
    if (height != nullptr) *height = g_framebuffer_height;
}

static bool initialize_gl4es_for_current_context() {
    if (g_gl4es_initialized) return true;

    set_getprocaddress(flint_gl4es_get_proc_address);
    set_getmainfbsize(flint_gl4es_get_main_fb_size);
    setenv("LIBGL_NOBANNER", "1", 0);
    initialize_gl4es();
    g_gl4es_initialized = true;

    g_gl4es_clear_color = reinterpret_cast<Gl4esClearColor>(gl4es_GetProcAddress("glClearColor"));
    g_gl4es_clear = reinterpret_cast<Gl4esClear>(gl4es_GetProcAddress("glClear"));
    g_gl4es_finish = reinterpret_cast<Gl4esFinish>(gl4es_GetProcAddress("glFinish"));
    g_gl4es_get_error = reinterpret_cast<Gl4esGetError>(gl4es_GetProcAddress("glGetError"));
    g_gl4es_get_string = reinterpret_cast<Gl4esGetString>(gl4es_GetProcAddress("glGetString"));

    if (!g_gl4es_clear_color || !g_gl4es_clear || !g_gl4es_finish ||
        !g_gl4es_get_error || !g_gl4es_get_string) {
        LOGE("GL4ES wrapper resolution failed: clearColor=%d clear=%d finish=%d getError=%d getString=%d",
            g_gl4es_clear_color != nullptr, g_gl4es_clear != nullptr,
            g_gl4es_finish != nullptr, g_gl4es_get_error != nullptr,
            g_gl4es_get_string != nullptr);
        close_gl4es();
        g_gl4es_initialized = false;
        g_gl4es_clear_color = nullptr;
        g_gl4es_clear = nullptr;
        g_gl4es_finish = nullptr;
        g_gl4es_get_error = nullptr;
        g_gl4es_get_string = nullptr;
        return false;
    }
    return true;
}

static void shutdown_gl4es_for_current_context() {
    if (!g_gl4es_initialized) return;
    close_gl4es();
    g_gl4es_initialized = false;
    g_gl4es_clear_color = nullptr;
    g_gl4es_clear = nullptr;
    g_gl4es_finish = nullptr;
    g_gl4es_get_error = nullptr;
    g_gl4es_get_string = nullptr;
}

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
        if (eglGetCurrentContext() == context && context != EGL_NO_CONTEXT) {
            shutdown_gl4es_for_current_context();
        }
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
        if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
        if (initialized) eglTerminate(display);
    }
    if (window != nullptr) ANativeWindow_release(window);
}

static bool render_gl4es_frame(int width, int height, GLenum *error_out) {
    if (!g_gl4es_initialized || !g_gl4es_clear_color || !g_gl4es_clear ||
        !g_gl4es_finish || !g_gl4es_get_error) {
        return false;
    }
    g_framebuffer_width = width;
    g_framebuffer_height = height;
    g_gl4es_clear_color(0.04f, 0.13f, 0.08f, 1.0f);
    g_gl4es_clear(GL_COLOR_BUFFER_BIT);
    g_gl4es_finish();
    const GLenum error = g_gl4es_get_error();
    if (error_out != nullptr) *error_out = error;
    return error == GL_NO_ERROR;
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
    g_framebuffer_width = width;
    g_framebuffer_height = height;
    if (!initialize_gl4es_for_current_context()) {
        release_partial(display, surface, context, window, true);
        return env->NewStringUTF("gl4es-window-init-failed");
    }

    GLenum gl_error = GL_NO_ERROR;
    if (!render_gl4es_frame(width, height, &gl_error)) {
        char message[128];
        snprintf(message, sizeof(message), "gl4es-window-initial-frame-failed:0x%04x", gl_error);
        LOGE("%s", message);
        release_partial(display, surface, context, window, true);
        return env->NewStringUTF(message);
    }

    if (!eglSwapBuffers(display, surface)) {
        const jstring result = egl_error(env, "initial-swap-failed");
        release_partial(display, surface, context, window, true);
        return result;
    }

    const char *version = reinterpret_cast<const char *>(g_gl4es_get_string(GL_VERSION));
    const char *renderer = reinterpret_cast<const char *>(g_gl4es_get_string(GL_RENDERER));
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    g_window_display = display;
    g_window_surface = surface;
    g_window_context = context;
    g_native_window = window;

    char message[256];
    snprintf(message, sizeof(message), "egl-window-ok:%dx%d|gl4es-window-ok:%s|renderer:%s",
        width, height, version ? version : "unknown", renderer ? renderer : "unknown");
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
    GLenum gl_error = GL_NO_ERROR;
    if (!render_gl4es_frame(width, height, &gl_error)) {
        eglMakeCurrent(g_window_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        char message[128];
        snprintf(message, sizeof(message), "egl-window-frame-gl4es-failed:0x%04x", gl_error);
        return env->NewStringUTF(message);
    }

    if (!eglSwapBuffers(g_window_display, g_window_surface)) {
        const jstring result = egl_error(env, "frame-swap-failed");
        eglMakeCurrent(g_window_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        return result;
    }

    eglMakeCurrent(g_window_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    return env->NewStringUTF("egl-window-frame-ok|gl4es-window-frame-ok");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeDestroyEglWindow(
    JNIEnv *env, jobject
) {
    std::lock_guard<std::mutex> guard(g_window_mutex);
    if (g_window_display != EGL_NO_DISPLAY) {
        if (g_window_context != EGL_NO_CONTEXT && g_window_surface != EGL_NO_SURFACE) {
            eglMakeCurrent(g_window_display, g_window_surface, g_window_surface, g_window_context);
            shutdown_gl4es_for_current_context();
        }
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
    g_framebuffer_width = 0;
    g_framebuffer_height = 0;

    LOGI("egl-window-destroyed");
    return env->NewStringUTF("egl-window-destroyed");
}
