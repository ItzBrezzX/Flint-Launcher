#include "flint_glfw_backend.h"
#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <signal.h>
#include <stdio.h>

#define LOG_TAG "FlintNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


/*
 * Promote the packaged GLFW C ABI dependency into the process-global symbol
 * scope. An Android-built LWJGL GLFW JNI adapter can then resolve glfw* from
 * this library instead of loading a desktop/Linux GLFW implementation.
 */
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *) {
    void *glfw = dlopen("libglfw.so", RTLD_NOW | RTLD_GLOBAL);
    if (!glfw) {
        LOGE("Could not promote Flint libglfw.so: %s", dlerror());
    } else {
        LOGI("Flint libglfw.so exported in process-global symbol scope");
    }
    return JNI_VERSION_1_6;
}

typedef int (*JLI_Launch_func)(
    int argc, char **argv,
    int jargc, const char **jargv,
    int appclassc, const char **appclassv,
    const char *fullversion,
    const char *dotversion,
    const char *pname,
    const char *lname,
    jboolean javaargs,
    jboolean cpwildcard,
    jboolean javaw,
    jint ergo
);

static void *g_libjli = nullptr;
static JLI_Launch_func g_jli_launch = nullptr;
static void *g_gl4es = nullptr;
static const GLubyte *(*g_gl4es_get_string)(GLenum) = nullptr;
static void (*g_gl4es_clear_color)(GLfloat, GLfloat, GLfloat, GLfloat) = nullptr;
static void (*g_gl4es_clear)(GLbitfield) = nullptr;
static GLenum (*g_gl4es_get_error)(void) = nullptr;

static void reset_signal_handlers(void) {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = SIG_DFL;
    for (int s = 1; s < 32; ++s) {
        if (s == SIGKILL || s == SIGSTOP) continue;
        sigaction(s, &sa, nullptr);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeLoadJre(
    JNIEnv *env, jclass, jstring jreHomePath
) {
    const char *home = env->GetStringUTFChars(jreHomePath, nullptr);
    if (!home) return JNI_FALSE;
    const char *candidates[] = {
        "/lib/libjli.so", "/lib/aarch64/libjli.so", "/lib/arm64/libjli.so",
        "/lib/aarch32/libjli.so", "/lib/arm/libjli.so", "/lib/i386/libjli.so",
        "/lib/amd64/libjli.so", "/lib/x86_64/libjli.so", "/lib/jli/libjli.so",
        "/lib/server/libjli.so", "/lib/aarch64/server/libjli.so",
        "/lib/arm64/server/libjli.so", "/lib/aarch32/server/libjli.so",
        "/lib/arm/server/libjli.so", "/lib/i386/server/libjli.so",
        "/lib/amd64/server/libjli.so", "/lib/x86_64/server/libjli.so", nullptr
    };
    char path[4096];
    void *handle = nullptr;
    for (int i = 0; candidates[i]; ++i) {
        snprintf(path, sizeof(path), "%s%s", home, candidates[i]);
        handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
        if (handle) {
            LOGI("dlopen JLI ok: %s", path);
            break;
        }
        LOGI("dlopen JLI miss: %s (%s)", path, dlerror());
    }
    if (!handle) {
        LOGE("Failed to dlopen libjli.so under %s", home);
        env->ReleaseStringUTFChars(jreHomePath, home);
        return JNI_FALSE;
    }
    g_libjli = handle;
    g_jli_launch = reinterpret_cast<JLI_Launch_func>(dlsym(handle, "JLI_Launch"));
    env->ReleaseStringUTFChars(jreHomePath, home);
    if (!g_jli_launch) {
        LOGE("JLI_Launch not found: %s", dlerror());
        return JNI_FALSE;
    }
    LOGI("JRE native symbols resolved (JLI_Launch ready)");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeLaunchJvm(
    JNIEnv *env, jclass, jobjectArray argsArray
) {
    if (!g_jli_launch) return -1;
    if (!argsArray) return -3;
    const jsize argc = env->GetArrayLength(argsArray);
    if (argc <= 0 || argc > 512) return -4;

    char **argv = static_cast<char **>(calloc(static_cast<size_t>(argc) + 1, sizeof(char *)));
    if (!argv) return -5;
    for (jsize i = 0; i < argc; ++i) {
        auto jstr = static_cast<jstring>(env->GetObjectArrayElement(argsArray, i));
        if (!jstr) {
            argv[i] = strdup("");
            continue;
        }
        const char *utf = env->GetStringUTFChars(jstr, nullptr);
        argv[i] = strdup(utf ? utf : "");
        if (utf) env->ReleaseStringUTFChars(jstr, utf);
        env->DeleteLocalRef(jstr);
        if (!argv[i]) {
            for (jsize j = 0; j < i; ++j) free(argv[j]);
            free(argv);
            return -5;
        }
    }

    LOGI("Calling JLI_Launch with argc=%d", static_cast<int>(argc));
    reset_signal_handlers();
    const int code = g_jli_launch(
        static_cast<int>(argc), argv, 0, nullptr, 0, nullptr,
        "1.8.0-internal", "1.8", argv[0], argv[0],
        JNI_FALSE, JNI_TRUE, JNI_FALSE, 0
    );
    LOGI("JLI_Launch returned %d", code);
    for (jsize i = 0; i < argc; ++i) free(argv[i]);
    free(argv);
    return code;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeLoadGL4ES(
    JNIEnv *env, jclass, jstring libraryPath
) {
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) {
        LOGE("GL4ES initialization requires a current EGL context");
        return JNI_FALSE;
    }

    if (!g_gl4es) {
        const char *path = env->GetStringUTFChars(libraryPath, nullptr);
        if (!path) return JNI_FALSE;
        g_gl4es = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
        if (!g_gl4es) {
            LOGE("Could not load GL4ES %s: %s", path, dlerror());
            env->ReleaseStringUTFChars(libraryPath, path);
            return JNI_FALSE;
        }
        env->ReleaseStringUTFChars(libraryPath, path);

        g_gl4es_get_string = reinterpret_cast<const GLubyte *(*)(GLenum)>(dlsym(g_gl4es, "glGetString"));
        g_gl4es_clear_color = reinterpret_cast<void (*)(GLfloat, GLfloat, GLfloat, GLfloat)>(dlsym(g_gl4es, "glClearColor"));
        g_gl4es_clear = reinterpret_cast<void (*)(GLbitfield)>(dlsym(g_gl4es, "glClear"));
        g_gl4es_get_error = reinterpret_cast<GLenum (*)(void)>(dlsym(g_gl4es, "glGetError"));
    }

    auto initialize = reinterpret_cast<void (*)(void)>(dlsym(g_gl4es, "initialize_gl4es"));
    if (!initialize || !g_gl4es_get_string || !g_gl4es_clear_color || !g_gl4es_clear || !g_gl4es_get_error) {
        LOGE("GL4ES exports missing: init=%d getString=%d clearColor=%d clear=%d getError=%d",
             initialize != nullptr, g_gl4es_get_string != nullptr, g_gl4es_clear_color != nullptr,
             g_gl4es_clear != nullptr, g_gl4es_get_error != nullptr);
        return JNI_FALSE;
    }

    // Also reinitialize after EGL context recreation; the GL4ES library itself
    // remains loaded globally for LWJGL's later dlsym(RTLD_DEFAULT, ...) calls.
    initialize();
    const GLubyte *version = g_gl4es_get_string(GL_VERSION);
    LOGI("GL4ES ready; GL_VERSION=%s",
         version ? reinterpret_cast<const char *>(version) : "unknown");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGL4ESVersion(
    JNIEnv *env, jclass
) {
    if (!g_gl4es_get_string || eglGetCurrentContext() == EGL_NO_CONTEXT) {
        return env->NewStringUTF("gl4es-not-initialized-or-no-current-context");
    }
    const GLubyte *version = g_gl4es_get_string(GL_VERSION);
    return env->NewStringUTF(version ? reinterpret_cast<const char *>(version) : "gl4es-version-unavailable");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGL4ESClear(
    JNIEnv *, jclass
) {
    if (!g_gl4es_clear_color || !g_gl4es_clear || !g_gl4es_get_error ||
        eglGetCurrentContext() == EGL_NO_CONTEXT) return JNI_FALSE;
    g_gl4es_clear_color(0.08f, 0.18f, 0.12f, 1.0f);
    g_gl4es_clear(GL_COLOR_BUFFER_BIT);
    return g_gl4es_get_error() == GL_NO_ERROR ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeShutdown(JNIEnv *, jclass) {
    // Keep both libraries loaded: JLI owns process-wide VM state and GL4ES
    // exports must remain visible to LWJGL native libraries.
    LOGI("Native shutdown requested; process-global libraries remain loaded");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSmokeTest(JNIEnv *env, jclass) {
    LOGI("Flint native smoke test OK");
    return env->NewStringUTF("flint-native-smoke-ok");
}


/*
 * JNI control surface for the Flint GLFW preview and diagnostics. Minecraft's
 * eventual LWJGL JNI library will call the exported GLFW C ABI in libglfw.so;
 * these methods let the Android Surface owner validate that same backend now.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwAttachAndroidSurface(
    JNIEnv *, jclass, jint width, jint height
) {
    return flint_glfw_attach_android_surface(width, height) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwInitialize(
    JNIEnv *, jclass
) {
    return glfwInit() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwCreateWindow(
    JNIEnv *env, jclass, jint width, jint height, jstring title
) {
    const char *nativeTitle = title ? env->GetStringUTFChars(title, nullptr) : nullptr;
    GLFWwindow *window = glfwCreateWindow(width, height, nativeTitle ? nativeTitle : "Flint", nullptr, nullptr);
    if (title && nativeTitle) env->ReleaseStringUTFChars(title, nativeTitle);
    return window ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwPollEvents(
    JNIEnv *, jclass
) {
    return static_cast<jint>(flint_glfw_poll_events());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwSwapBuffers(
    JNIEnv *, jclass
) {
    GLFWwindow *window = flint_glfw_android_window();
    if (!window) return JNI_FALSE;
    glfwSwapBuffers(window);
    const char *description = nullptr;
    return glfwGetError(&description) == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwWindowShouldClose(
    JNIEnv *, jclass
) {
    return glfwWindowShouldClose(flint_glfw_android_window()) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwSetWindowShouldClose(
    JNIEnv *, jclass, jboolean shouldClose
) {
    glfwSetWindowShouldClose(flint_glfw_android_window(), shouldClose ? GLFW_TRUE : GLFW_FALSE);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwVersion(
    JNIEnv *env, jclass
) {
    return env->NewStringUTF(glfwGetVersionString());
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwDestroyWindow(
    JNIEnv *, jclass
) {
    GLFWwindow *window = flint_glfw_android_window();
    if (window) glfwDestroyWindow(window);
    flint_glfw_detach_android_surface();
}
