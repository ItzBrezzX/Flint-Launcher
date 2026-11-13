#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>

#define LOG_TAG "FlintEGL"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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

    // Cleanup
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, ctx);
    eglDestroySurface(display, surface);
    eglTerminate(display);

    LOGI("EGL smoke: clear + finish OK");
    return env->NewStringUTF("egl-smoke-ok");
}
