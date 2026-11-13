#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <stdio.h>
#include <string.h>

#define LOG_TAG "FlintEGL"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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
