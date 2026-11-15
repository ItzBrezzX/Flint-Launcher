#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <signal.h>
#include <stdio.h>
#include <atomic>
#include <mutex>
#include <thread>
#include <exception>

#define LOG_TAG "FlintNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/*
 * Spike 0.2 — JNI bridge modeled on the public PojavLauncher architecture
 * (dlopen libjli → JLI_Launch). Does not copy GPL source.
 *
 * nativeStartJvm copies argv and schedules JLI_Launch on a dedicated native worker.
 * State and exit code are queried independently; JNI never waits for the game main class.
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
static std::thread g_launch_thread;
static std::atomic<int> g_jvm_state{0}; // 0=IDLE, 1=STARTING, 2=RUNNING, 3=EXITED, 4=FAILED
static std::atomic<int> g_jvm_exit_code{-1};
static bool g_jvm_ever_started = false;

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
    if (!jreHomePath) return JNI_FALSE;
    const char *home = env->GetStringUTFChars(jreHomePath, nullptr);
    if (!home) return JNI_FALSE;

    std::lock_guard<std::mutex> lock(g_jvm_mutex);
    if (g_jli_launch) {
        const bool same_runtime = strncmp(g_jre_home, home, sizeof(g_jre_home)) == 0;
        env->ReleaseStringUTFChars(jreHomePath, home);
        return same_runtime ? JNI_TRUE : JNI_FALSE;
    }
    if (g_jvm_ever_started) {
        env->ReleaseStringUTFChars(jreHomePath, home);
        return JNI_FALSE;
    }

    snprintf(g_jre_home, sizeof(g_jre_home), "%s", home);

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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeStartJvm(
    JNIEnv *env, jclass, jobjectArray argsArray
) {
    if (argsArray == nullptr) {
        LOGE("nativeStartJvm: null args");
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_jvm_mutex);
    if (!g_jli_launch) {
        LOGE("nativeStartJvm: JRE not loaded");
        return JNI_FALSE;
    }
    if (g_jvm_ever_started) {
        LOGE("nativeStartJvm: embedded JVM can only be started once per Android process");
        return JNI_FALSE;
    }

    const jsize argc = env->GetArrayLength(argsArray);
    if (argc <= 0 || argc > 512) {
        LOGE("nativeStartJvm: invalid argc %d", (int)argc);
        return JNI_FALSE;
    }

    char **argv = (char **) calloc((size_t)argc + 1, sizeof(char *));
    if (!argv) return JNI_FALSE;

    for (jsize i = 0; i < argc; ++i) {
        auto jstr = (jstring) env->GetObjectArrayElement(argsArray, i);
        if (!jstr) {
            argv[i] = strdup("");
        } else {
            const char *utf = env->GetStringUTFChars(jstr, nullptr);
            if (!utf) {
                env->DeleteLocalRef(jstr);
                for (jsize j = 0; j < i; ++j) free(argv[j]);
                free(argv);
                return JNI_FALSE;
            }
            argv[i] = strdup(utf);
            env->ReleaseStringUTFChars(jstr, utf);
            env->DeleteLocalRef(jstr);
        }
        if (!argv[i]) {
            for (jsize j = 0; j < i; ++j) free(argv[j]);
            free(argv);
            return JNI_FALSE;
        }
    }

    const JLI_Launch_func launch = g_jli_launch;
    g_jvm_exit_code.store(-1);
    g_jvm_state.store(1); // STARTING
    try {
        g_launch_thread = std::thread([argc, argv, launch]() {
            g_jvm_state.store(2); // RUNNING: JLI_Launch is executing on this worker.
            LOGI("Starting embedded JVM on native worker thread (argc=%d)", (int)argc);
            reset_signal_handlers();

            const int code = launch(
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
            g_jvm_exit_code.store(code);
            g_jvm_state.store(3); // EXITED
        });
    } catch (const std::exception &error) {
        LOGE("Failed to create JVM worker: %s", error.what());
        for (jsize i = 0; i < argc; ++i) free(argv[i]);
        free(argv);
        g_jvm_state.store(4); // FAILED
        return JNI_FALSE;
    }

    g_jvm_ever_started = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeJvmState(
    JNIEnv *, jclass
) {
    return g_jvm_state.load();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeJvmExitCode(
    JNIEnv *, jclass
) {
    return g_jvm_exit_code.load();
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeShutdown(
    JNIEnv *, jclass
) {
    std::lock_guard<std::mutex> lock(g_jvm_mutex);
    const int state = g_jvm_state.load();
    if (state == 1 || state == 2) {
        // JLI_Launch owns the embedded JVM while active. Never dlclose libjli
        // or join its worker from the caller while the game is still running.
        LOGI("nativeShutdown deferred: embedded JVM is still active");
        return;
    }
    if (g_launch_thread.joinable()) {
        g_launch_thread.join();
    }
    // Keep libjli loaded for the lifetime of this Android process. An embedded
    // JVM cannot be safely unloaded/restarted after JLI_Launch has been called.
    LOGI("JVM worker reaped; libjli retained for process lifetime (state=%d)", state);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSmokeTest(
    JNIEnv *env, jclass
) {
    LOGI("Flint native smoke test OK");
    return env->NewStringUTF("flint-native-smoke-ok");
}
