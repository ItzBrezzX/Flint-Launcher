#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <mutex>

#define LOG_TAG "FlintNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/*
 * Spike 0.2 — JNI bridge modeled on the public PojavLauncher architecture
 * (dlopen libjli → JLI_Launch). Does not copy GPL source.
 *
 * nativeLaunchJvm builds argv and calls JLI_Launch when the symbol is present.
 * On devices without a loaded mobile JRE it returns a clear error code.
 */

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
static char g_jre_home[4096] = {0};
static std::mutex g_jvm_mutex;
static bool g_jvm_launching = false;
static bool g_shutdown_requested = false;

static void free_argv(char **argv, jsize argc) {
    if (!argv) return;
    for (jsize i = 0; i < argc; ++i) {
        free(argv[i]);
    }
    free(argv);
}

static void reset_signal_handlers(void) {
    /* Give the JVM a clean slate for its own signal detection (Pojav pattern). */
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

    std::lock_guard<std::mutex> lock(g_jvm_mutex);
    if (g_jvm_launching) {
        env->ReleaseStringUTFChars(jreHomePath, home);
        LOGE("nativeLoadJre: refusing to replace a runtime while the JVM is launching");
        return JNI_FALSE;
    }
    if (g_libjli) {
        dlclose(g_libjli);
        g_libjli = nullptr;
        g_jli_launch = nullptr;
    }
    g_shutdown_requested = false;
    strncpy(g_jre_home, home, sizeof(g_jre_home) - 1);
    g_jre_home[sizeof(g_jre_home) - 1] = '\0';

    const char *candidates[] = {
        "/lib/libjli.so",
        "/lib/aarch64/libjli.so",
        "/lib/arm64/libjli.so",
        "/lib/aarch32/libjli.so",
        "/lib/arm/libjli.so",
        "/lib/i386/libjli.so",
        "/lib/amd64/libjli.so",
        "/lib/x86_64/libjli.so",
        "/lib/jli/libjli.so",
        "/lib/server/libjli.so",
        "/lib/aarch64/server/libjli.so",
        "/lib/arm64/server/libjli.so",
        "/lib/aarch32/server/libjli.so",
        "/lib/arm/server/libjli.so",
        "/lib/i386/server/libjli.so",
        "/lib/amd64/server/libjli.so",
        "/lib/x86_64/server/libjli.so",
        nullptr
    };

    char path[4096];
    void *handle = nullptr;
    for (int i = 0; candidates[i]; ++i) {
        snprintf(path, sizeof(path), "%s%s", home, candidates[i]);
        handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
        if (handle) {
            LOGI("dlopen ok: %s", path);
            break;
        }
        LOGI("dlopen miss: %s (%s)", path, dlerror());
    }

    if (!handle) {
        LOGE("Failed to dlopen libjli.so under %s", home);
        env->ReleaseStringUTFChars(jreHomePath, home);
        return JNI_FALSE;
    }

    g_libjli = handle;
    g_jli_launch = (JLI_Launch_func) dlsym(handle, "JLI_Launch");
    if (!g_jli_launch) {
        LOGE("JLI_Launch not found: %s", dlerror());
        dlclose(g_libjli);
        g_libjli = nullptr;
        env->ReleaseStringUTFChars(jreHomePath, home);
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(jreHomePath, home);
    LOGI("JRE native symbols resolved (JLI_Launch ready)");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeLaunchJvm(
    JNIEnv *env, jclass, jobjectArray argsArray
) {
    if (argsArray == nullptr) {
        LOGE("nativeLaunchJvm: null args");
        return -3;
    }

    jsize argc = env->GetArrayLength(argsArray);
    if (argc <= 0 || argc > 512) {
        LOGE("nativeLaunchJvm: bad argc %d", (int)argc);
        return -4;
    }

    char **argv = (char **) calloc((size_t)argc + 1, sizeof(char *));
    if (!argv) return -5;

    for (jsize i = 0; i < argc; ++i) {
        auto jstr = (jstring) env->GetObjectArrayElement(argsArray, i);
        if (env->ExceptionCheck()) {
            free_argv(argv, argc);
            return -7;
        }
        if (!jstr) {
            LOGE("nativeLaunchJvm: null argument at index %d", (int)i);
            free_argv(argv, argc);
            return -7;
        }

        const char *utf = env->GetStringUTFChars(jstr, nullptr);
        if (!utf) {
            // Keep any pending JNI exception (typically OutOfMemoryError) intact.
            env->DeleteLocalRef(jstr);
            free_argv(argv, argc);
            return -7;
        }

        argv[i] = strdup(utf);
        env->ReleaseStringUTFChars(jstr, utf);
        env->DeleteLocalRef(jstr);

        if (!argv[i]) {
            LOGE("nativeLaunchJvm: could not allocate argument %d", (int)i);
            free_argv(argv, argc);
            return -5;
        }
    }

    JLI_Launch_func launch_func = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_jvm_mutex);
        if (!g_jli_launch || !g_libjli) {
            LOGE("nativeLaunchJvm: JRE not loaded");
            free_argv(argv, argc);
            return -1;
        }
        if (g_jvm_launching) {
            LOGE("nativeLaunchJvm: another JVM launch is already active");
            free_argv(argv, argc);
            return -6;
        }
        g_jvm_launching = true;
        g_shutdown_requested = false;
        launch_func = g_jli_launch;
    }

    LOGI("Calling JLI_Launch with argc=%d argv0=%s", (int)argc, argv[0] ? argv[0] : "(null)");
    reset_signal_handlers();

    int code = launch_func(
        (int)argc, argv,
        0, nullptr,
        0, nullptr,
        "1.8.0-internal",
        "1.8",
        argv[0],
        argv[0],
        JNI_FALSE,
        JNI_TRUE,
        JNI_FALSE,
        0
    );

    LOGI("JLI_Launch returned %d", code);

    {
        std::lock_guard<std::mutex> lock(g_jvm_mutex);
        g_jvm_launching = false;
        if (g_shutdown_requested && g_libjli) {
            dlclose(g_libjli);
            g_libjli = nullptr;
            g_jli_launch = nullptr;
            g_shutdown_requested = false;
            LOGI("Deferred JRE unload completed after JLI_Launch returned");
        }
    }

    free_argv(argv, argc);
    return code;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeShutdown(
    JNIEnv *, jclass
) {
    std::lock_guard<std::mutex> lock(g_jvm_mutex);
    if (g_jvm_launching) {
        g_shutdown_requested = true;
        LOGI("JRE unload deferred: JLI_Launch is still active");
        return;
    }
    if (g_libjli) {
        dlclose(g_libjli);
        g_libjli = nullptr;
        g_jli_launch = nullptr;
        g_shutdown_requested = false;
        LOGI("JRE native handle closed");
    }
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSmokeTest(
    JNIEnv *env, jclass
) {
    LOGI("Flint native smoke test OK");
    return env->NewStringUTF("flint-native-smoke-ok");
}
