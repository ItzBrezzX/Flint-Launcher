#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <stdio.h>
#include <string.h>
#include <pthread.h>
#include <mutex>

#define LOG_TAG "FlintEGL"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static const char *safe_gl_string(GLenum name) {
    const GLubyte *value = glGetString(name);
    return value ? reinterpret_cast<const char *>(value) : "unknown";
}

static void copy_field(char *target, size_t capacity, const char *value) {
    if (capacity == 0) return;
    if (!value) value = "unknown";
    snprintf(target, capacity, "%s", value);
    for (char *p = target; *p; ++p) {
        if (*p == '|' || *p == '\n' || *p == '\r') *p = ' ';
    }
}

/*
 * Minimal EGL + GLES2 smoke test.
 * Creates a pbuffer surface (no window required), clears to a color, reads back.
 * This validates that EGL/GLES are usable on the device before attempting
 * a full LWJGL / GL4ES path.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeEglSmoke(
    JNIEnv *env, jclass
) {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return env->NewStringUTF("egl-no-display");
    }
    EGLint major = 0, minor = 0;
    if (!eglInitialize(display, &major, &minor)) {
        LOGE("eglInitialize failed");
        return env->NewStringUTF("egl-init-failed");
    }
    LOGI("EGL %d.%d", major, minor);

    const EGLint configAttribs[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
        EGL_NONE
    };
    EGLConfig config;
    EGLint num = 0;
    if (!eglChooseConfig(display, configAttribs, &config, 1, &num) || num == 0) {
        eglTerminate(display);
        return env->NewStringUTF("egl-no-config");
    }

    const EGLint pbufAttribs[] = { EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE };
    EGLSurface surface = eglCreatePbufferSurface(display, config, pbufAttribs);
    if (surface == EGL_NO_SURFACE) {
        eglTerminate(display);
        return env->NewStringUTF("egl-no-surface");
    }

    const EGLint ctxAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    EGLContext ctx = eglCreateContext(display, config, EGL_NO_CONTEXT, ctxAttribs);
    if (ctx == EGL_NO_CONTEXT) {
        eglDestroySurface(display, surface);
        eglTerminate(display);
        return env->NewStringUTF("egl-no-context");
    }

    if (!eglMakeCurrent(display, surface, surface, ctx)) {
        eglDestroyContext(display, ctx);
        eglDestroySurface(display, surface);
        eglTerminate(display);
        return env->NewStringUTF("egl-make-current-failed");
    }

    glClearColor(0.1f, 0.2f, 0.3f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glFinish();
    const GLenum glError = glGetError();

    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, ctx);
    eglDestroySurface(display, surface);
    eglTerminate(display);

    if (glError != GL_NO_ERROR) {
        LOGE("GLES smoke failed with GL error 0x%x", glError);
        return env->NewStringUTF("egl-gles-operation-failed");
    }
    LOGI("EGL smoke: clear + finish OK");
    return env->NewStringUTF("egl-smoke-ok");
}

/*
 * Reports the actual system EGL/GLES driver strings from a temporary context.
 * This is diagnostic only: it does not mean GL4ES, Zink, SDL or LWJGL is present.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeEglCapabilities(
    JNIEnv *env, jclass
) {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        char error[64];
        snprintf(error, sizeof(error), "egl-no-display:0x%x", eglGetError());
        return env->NewStringUTF(error);
    }

    EGLint major = 0, minor = 0;
    if (!eglInitialize(display, &major, &minor)) {
        char error[64];
        snprintf(error, sizeof(error), "egl-init-failed:0x%x", eglGetError());
        return env->NewStringUTF(error);
    }

    const EGLint configAttribs[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
        EGL_NONE
    };
    EGLConfig config;
    EGLint count = 0;
    if (!eglChooseConfig(display, configAttribs, &config, 1, &count) || count == 0) {
        eglTerminate(display);
        return env->NewStringUTF("egl-no-gles2-config");
    }

    const EGLint surfaceAttribs[] = { EGL_WIDTH, 8, EGL_HEIGHT, 8, EGL_NONE };
    EGLSurface surface = eglCreatePbufferSurface(display, config, surfaceAttribs);
    if (surface == EGL_NO_SURFACE) {
        eglTerminate(display);
        return env->NewStringUTF("egl-pbuffer-failed");
    }

    const EGLint contextAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs);
    if (context == EGL_NO_CONTEXT) {
        eglDestroySurface(display, surface);
        eglTerminate(display);
        return env->NewStringUTF("egl-context-failed");
    }

    if (!eglMakeCurrent(display, surface, surface, context)) {
        eglDestroyContext(display, context);
        eglDestroySurface(display, surface);
        eglTerminate(display);
        return env->NewStringUTF("egl-make-current-failed");
    }

    char eglVendor[160], eglVersion[160], glVendor[160], glRenderer[256], glVersion[160];
    copy_field(eglVendor, sizeof(eglVendor), eglQueryString(display, EGL_VENDOR));
    copy_field(eglVersion, sizeof(eglVersion), eglQueryString(display, EGL_VERSION));
    copy_field(glVendor, sizeof(glVendor), safe_gl_string(GL_VENDOR));
    copy_field(glRenderer, sizeof(glRenderer), safe_gl_string(GL_RENDERER));
    copy_field(glVersion, sizeof(glVersion), safe_gl_string(GL_VERSION));

    char report[1024];
    snprintf(report, sizeof(report),
        "EGL %d.%d | vendor=%s | egl=%s | GLES vendor=%s | GPU=%s | GLES=%s",
        major, minor, eglVendor, eglVersion, glVendor, glRenderer, glVersion);

    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, surface);
    eglTerminate(display);

    LOGI("GPU capability probe: %s", report);
    return env->NewStringUTF(report);
}


/*
 * Real Android ANativeWindow EGL surface used by the GL4ES render-thread test.
 * Create, resize, swap and destroy must all run on the same dedicated thread.
 */
#include <android/native_window.h>
#include <android/native_window_jni.h>

static EGLDisplay g_window_display = EGL_NO_DISPLAY;
static EGLSurface g_window_surface = EGL_NO_SURFACE;
static EGLContext g_window_context = EGL_NO_CONTEXT;
static ANativeWindow *g_native_window = nullptr;
static pthread_t g_window_owner_thread{};
static bool g_window_owner_valid = false;
static std::mutex g_window_egl_mutex;

static bool is_window_egl_owner_thread() {
    return g_window_owner_valid && pthread_equal(g_window_owner_thread, pthread_self()) != 0;
}

/*
 * EGL contexts are thread-affine. Never try to migrate this context implicitly:
 * Android EGL drivers reject cross-thread use while the previous owner still
 * has it current, and destroying the ANativeWindow from another thread can
 * invalidate the renderer's surface lifetime.
 *
 * Returns false without changing global state when called by a non-owner.
 */
static bool destroy_window_egl(JNIEnv *env) {
    if (g_window_owner_valid && !is_window_egl_owner_thread()) {
        LOGE("Refusing to destroy EGL resources from a non-owner thread");
        return false;
    }

    if (g_window_display != EGL_NO_DISPLAY) {
        if (g_window_context != EGL_NO_CONTEXT &&
            eglGetCurrentContext() == g_window_context &&
            !eglMakeCurrent(g_window_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT)) {
            LOGE("Could not release the EGL context on its owner thread: 0x%x", eglGetError());
            return false;
        }
        if (g_window_context != EGL_NO_CONTEXT &&
            !eglDestroyContext(g_window_display, g_window_context)) {
            LOGE("eglDestroyContext failed: 0x%x", eglGetError());
            return false;
        }
        if (g_window_surface != EGL_NO_SURFACE &&
            !eglDestroySurface(g_window_display, g_window_surface)) {
            LOGW("eglDestroySurface failed during teardown: 0x%x", eglGetError());
        }
        if (!eglTerminate(g_window_display)) {
            LOGW("eglTerminate failed during teardown: 0x%x", eglGetError());
        }
    }

    g_window_display = EGL_NO_DISPLAY;
    g_window_surface = EGL_NO_SURFACE;
    g_window_context = EGL_NO_CONTEXT;
    if (g_native_window) {
        ANativeWindow_release(g_native_window);
        g_native_window = nullptr;
    }
    g_window_owner_valid = false;
    (void)env;
    return true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeCreateWindowSurface(
    JNIEnv *env, jclass, jobject surface
) {
    std::lock_guard<std::mutex> lock(g_window_egl_mutex);
    if (!destroy_window_egl(env)) {
        return env->NewStringUTF("egl-window-owned-by-another-thread");
    }
    if (!surface) return env->NewStringUTF("window-surface-null");

    g_native_window = ANativeWindow_fromSurface(env, surface);
    if (!g_native_window) return env->NewStringUTF("native-window-unavailable");

    g_window_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major = 0, minor = 0;
    if (g_window_display == EGL_NO_DISPLAY || !eglInitialize(g_window_display, &major, &minor)) {
        const EGLint error = eglGetError();
        destroy_window_egl(env);
        char message[64];
        snprintf(message, sizeof(message), "egl-window-init-failed:0x%x", error);
        return env->NewStringUTF(message);
    }

    const EGLint config_attrs[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };
    EGLConfig config = nullptr;
    EGLint count = 0;
    if (!eglChooseConfig(g_window_display, config_attrs, &config, 1, &count) || count < 1) {
        destroy_window_egl(env);
        return env->NewStringUTF("egl-window-config-unavailable");
    }

    EGLint native_visual_id = 0;
    if (!eglGetConfigAttrib(g_window_display, config, EGL_NATIVE_VISUAL_ID, &native_visual_id) ||
        native_visual_id == 0) {
        const EGLint error = eglGetError();
        destroy_window_egl(env);
        char message[64];
        snprintf(message, sizeof(message), "egl-window-visual-unavailable:0x%x", error);
        return env->NewStringUTF(message);
    }
    if (ANativeWindow_setBuffersGeometry(g_native_window, 0, 0, native_visual_id) != 0) {
        destroy_window_egl(env);
        return env->NewStringUTF("egl-window-buffer-format-rejected");
    }

    g_window_surface = eglCreateWindowSurface(g_window_display, config, g_native_window, nullptr);
    if (g_window_surface == EGL_NO_SURFACE) {
        const EGLint error = eglGetError();
        destroy_window_egl(env);
        char message[64];
        snprintf(message, sizeof(message), "egl-window-surface-failed:0x%x", error);
        return env->NewStringUTF(message);
    }

    const EGLint context_attrs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    g_window_context = eglCreateContext(g_window_display, config, EGL_NO_CONTEXT, context_attrs);
    if (g_window_context == EGL_NO_CONTEXT ||
        !eglMakeCurrent(g_window_display, g_window_surface, g_window_surface, g_window_context)) {
        const EGLint error = eglGetError();
        destroy_window_egl(env);
        char message[64];
        snprintf(message, sizeof(message), "egl-window-context-failed:0x%x", error);
        return env->NewStringUTF(message);
    }
    g_window_owner_thread = pthread_self();
    g_window_owner_valid = true;

    const EGLint width = ANativeWindow_getWidth(g_native_window);
    const EGLint height = ANativeWindow_getHeight(g_native_window);
    glViewport(0, 0, width, height);
    LOGI("Android EGL window ready: %dx%d, EGL %d.%d", width, height, major, minor);
    return env->NewStringUTF("egl-window-ready");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeResizeWindowSurface(
    JNIEnv *, jclass, jint width, jint height
) {
    std::lock_guard<std::mutex> lock(g_window_egl_mutex);
    if (!is_window_egl_owner_thread() ||
        eglGetCurrentContext() != g_window_context) {
        LOGE("EGL resize must run on the thread that owns the current context");
        return JNI_FALSE;
    }
    if (g_window_display == EGL_NO_DISPLAY || g_window_surface == EGL_NO_SURFACE ||
        g_window_context == EGL_NO_CONTEXT || width <= 0 || height <= 0) return JNI_FALSE;
    glViewport(0, 0, width, height);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSwapWindowBuffers(
    JNIEnv *, jclass
) {
    std::lock_guard<std::mutex> lock(g_window_egl_mutex);
    if (!is_window_egl_owner_thread() ||
        eglGetCurrentContext() != g_window_context) {
        LOGE("EGL swap must run on the thread that owns the current context");
        return JNI_FALSE;
    }
    if (g_window_display == EGL_NO_DISPLAY || g_window_surface == EGL_NO_SURFACE ||
        g_window_context == EGL_NO_CONTEXT) return JNI_FALSE;
    if (!eglSwapBuffers(g_window_display, g_window_surface)) {
        LOGE("eglSwapBuffers failed: 0x%x", eglGetError());
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeDestroyWindowSurface(
    JNIEnv *env, jclass
) {
    std::lock_guard<std::mutex> lock(g_window_egl_mutex);
    if (destroy_window_egl(env)) {
        LOGI("Android EGL window released");
    }
}
