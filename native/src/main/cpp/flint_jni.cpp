#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <signal.h>

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

    strncpy(g_jre_home, home, sizeof(g_jre_home) - 1);

    const char *candidates[] = {
        "/lib/libjli.so",
        "/lib/aarch64/libjli.so",
        "/lib/arm64/libjli.so",
        "/lib/arm/libjli.so",
        "/lib/i386/libjli.so",
        "/lib/amd64/libjli.so",
        "/lib/x86_64/libjli.so",
        "/lib/jli/libjli.so",
        "/lib/server/libjli.so",
        "/lib/aarch64/server/libjli.so",
        "/lib/arm64/server/libjli.so",
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
    if (!g_jli_launch) {
        LOGE("nativeLaunchJvm: JRE not loaded");
        return -1;
    }
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
        if (!jstr) {
            argv[i] = strdup("");
            continue;
        }
        const char *utf = env->GetStringUTFChars(jstr, nullptr);
        argv[i] = strdup(utf ? utf : "");
        env->ReleaseStringUTFChars(jstr, utf);
        env->DeleteLocalRef(jstr);
    }

    LOGI("Calling JLI_Launch with argc=%d argv0=%s", (int)argc, argv[0] ? argv[0] : "(null)");
    reset_signal_handlers();

    int code = g_jli_launch(
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

    for (jsize i = 0; i < argc; ++i) free(argv[i]);
    free(argv);
    return code;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeShutdown(
    JNIEnv *, jclass
) {
    if (g_libjli) {
        dlclose(g_libjli);
        g_libjli = nullptr;
        g_jli_launch = nullptr;
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
