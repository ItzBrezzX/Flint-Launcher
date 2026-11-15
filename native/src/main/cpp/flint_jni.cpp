#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <signal.h>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>

#define LOG_TAG "FlintNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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

// The JVM writes to file descriptors 1 and 2 of this process, which Android points at /dev/null.
// Redirecting them to a file is the only way the person can ever see Minecraft's output or a
// Java crash message.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeRedirectStdio(
    JNIEnv *env, jclass, jstring jpath
) {
    if (!jpath) return JNI_FALSE;
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) return JNI_FALSE;
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    jboolean ok = JNI_FALSE;
    if (fd >= 0) {
        ok = (dup2(fd, STDOUT_FILENO) >= 0 && dup2(fd, STDERR_FILENO) >= 0) ? JNI_TRUE : JNI_FALSE;
        close(fd);
    }
    if (!ok) LOGE("Could not redirect stdout/stderr to %s", path);
    env->ReleaseStringUTFChars(jpath, path);
    return ok;
}

// A process environment variable can only be changed from native code: the JVM that
// JLI_Launch starts in this same process reads JAVA_HOME / LD_LIBRARY_PATH and friends
// from the C environment, not from anything Kotlin can set.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSetEnv(
    JNIEnv *env, jclass, jstring jkey, jstring jvalue
) {
    if (!jkey || !jvalue) return JNI_FALSE;
    const char *key = env->GetStringUTFChars(jkey, nullptr);
    const char *value = env->GetStringUTFChars(jvalue, nullptr);
    jboolean ok = JNI_FALSE;
    if (key && value && key[0] != '\0' && strchr(key, '=') == nullptr) {
        ok = setenv(key, value, 1) == 0 ? JNI_TRUE : JNI_FALSE;
        if (!ok) LOGE("setenv failed for %s", key);
    }
    if (key) env->ReleaseStringUTFChars(jkey, key);
    if (value) env->ReleaseStringUTFChars(jvalue, value);
    return ok;
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
