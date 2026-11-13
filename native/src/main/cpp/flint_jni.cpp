#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <signal.h>
#include <stdio.h>
#include <sys/stat.h>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "FlintNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/*
 * Flint-owned mobile JRE loader.
 *
 * This deliberately implements only the runtime-loading boundary. It does
 * not copy launcher code from another project and it does not claim that
 * Minecraft graphics, LWJGL or the game window are ready.
 *
 * A HotSpot VM is process-global and is not safely unloadable/restartable.
 * Therefore successfully loaded JRE libraries are retained until process
 * exit. nativeShutdown() is intentionally not a dlclose operation.
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

static std::mutex g_jre_mutex;
static void *g_libjli = nullptr;
static void *g_libjvm = nullptr;
static JLI_Launch_func g_jli_launch = nullptr;
static std::string g_jre_home;
static std::string g_jre_error;

static bool is_file(const std::string &path) {
    struct stat info {};
    return stat(path.c_str(), &info) == 0 && S_ISREG(info.st_mode);
}

static std::string first_existing(const std::string &home, const char *const *suffixes) {
    for (size_t i = 0; suffixes[i] != nullptr; ++i) {
        std::string candidate = home + suffixes[i];
        if (is_file(candidate)) return candidate;
    }
    return {};
}

static std::vector<std::string> library_directories(const std::string &home) {
    return {
        home + "/lib",
        home + "/lib/server",
        home + "/lib/jli",
        home + "/lib/aarch64",
        home + "/lib/aarch64/server",
        home + "/lib/arm64",
        home + "/lib/arm64/server",
        home + "/lib/arm",
        home + "/lib/arm/server",
        home + "/lib/amd64",
        home + "/lib/amd64/server",
        home + "/lib/x86_64",
        home + "/lib/x86_64/server",
        home + "/lib/i386",
        home + "/lib/i386/server"
    };
}

static void configure_jre_environment(const std::string &home) {
    setenv("JAVA_HOME", home.c_str(), 1);
    setenv("JRE_HOME", home.c_str(), 1);

    std::string search_path;
    for (const auto &directory : library_directories(home)) {
        if (!search_path.empty()) search_path += ":";
        search_path += directory;
    }
    const char *existing = getenv("LD_LIBRARY_PATH");
    if (existing && existing[0] != '\0') {
        search_path += ":";
        search_path += existing;
    }
    setenv("LD_LIBRARY_PATH", search_path.c_str(), 1);
}

static void reset_signal_handlers(void) {
    struct sigaction sa {};
    sa.sa_handler = SIG_DFL;
    sigemptyset(&sa.sa_mask);
    for (int s = 1; s < 32; ++s) {
        if (s == SIGKILL || s == SIGSTOP) continue;
        sigaction(s, &sa, nullptr);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeLoadJre(
    JNIEnv *env, jclass, jstring jreHomePath
) {
    if (jreHomePath == nullptr) return JNI_FALSE;
    const char *raw_home = env->GetStringUTFChars(jreHomePath, nullptr);
    if (!raw_home) return JNI_FALSE;
    const std::string home(raw_home);
    env->ReleaseStringUTFChars(jreHomePath, raw_home);

    std::lock_guard<std::mutex> guard(g_jre_mutex);

    if (g_jli_launch != nullptr) {
        if (home == g_jre_home) return JNI_TRUE;
        g_jre_error = "A different JVM is already loaded in this app process; restart Flint before changing Java runtime.";
        LOGE("%s", g_jre_error.c_str());
        return JNI_FALSE;
    }

    const char *jvm_suffixes[] = {
        "/lib/server/libjvm.so",
        "/lib/aarch64/server/libjvm.so",
        "/lib/arm64/server/libjvm.so",
        "/lib/amd64/server/libjvm.so",
        "/lib/x86_64/server/libjvm.so",
        "/lib/arm/server/libjvm.so",
        "/lib/libjvm.so",
        "/lib/aarch64/libjvm.so",
        "/lib/arm64/libjvm.so",
        "/lib/amd64/libjvm.so",
        "/lib/x86_64/libjvm.so",
        nullptr
    };
    const char *jli_suffixes[] = {
        "/lib/libjli.so",
        "/lib/aarch64/libjli.so",
        "/lib/arm64/libjli.so",
        "/lib/arm/libjli.so",
        "/lib/amd64/libjli.so",
        "/lib/x86_64/libjli.so",
        "/lib/i386/libjli.so",
        "/lib/jli/libjli.so",
        nullptr
    };

    const std::string jvm_path = first_existing(home, jvm_suffixes);
    const std::string jli_path = first_existing(home, jli_suffixes);
    if (jvm_path.empty() || jli_path.empty()) {
        g_jre_error = std::string("JRE layout incomplete: ") +
            (jvm_path.empty() ? "libjvm.so not found; " : "") +
            (jli_path.empty() ? "libjli.so not found" : "");
        LOGE("%s under %s", g_jre_error.c_str(), home.c_str());
        return JNI_FALSE;
    }

    configure_jre_environment(home);

    /* Load the VM first so its dependent ELF libraries resolve in the JRE path. */
    g_libjvm = dlopen(jvm_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!g_libjvm) {
        const char *error = dlerror();
        g_jre_error = std::string("dlopen libjvm.so failed: ") + (error ? error : "unknown linker error");
        LOGE("%s", g_jre_error.c_str());
        return JNI_FALSE;
    }

    g_libjli = dlopen(jli_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!g_libjli) {
        const char *error = dlerror();
        g_jre_error = std::string("dlopen libjli.so failed: ") + (error ? error : "unknown linker error");
        LOGE("%s", g_jre_error.c_str());
        return JNI_FALSE;
    }

    g_jli_launch = reinterpret_cast<JLI_Launch_func>(dlsym(g_libjli, "JLI_Launch"));
    if (!g_jli_launch) {
        const char *error = dlerror();
        g_jre_error = std::string("JLI_Launch symbol missing: ") + (error ? error : "unknown symbol error");
        LOGE("%s", g_jre_error.c_str());
        return JNI_FALSE;
    }

    g_jre_home = home;
    g_jre_error.clear();
    LOGI("JRE libraries loaded: libjvm=%s libjli=%s", jvm_path.c_str(), jli_path.c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeLaunchJvm(
    JNIEnv *env, jclass, jobjectArray argsArray
) {
    std::lock_guard<std::mutex> guard(g_jre_mutex);
    if (!g_jli_launch) {
        LOGE("nativeLaunchJvm: JRE not loaded (%s)", g_jre_error.c_str());
        return -1;
    }
    if (argsArray == nullptr) {
        LOGE("nativeLaunchJvm: null args");
        return -3;
    }

    const jsize argc = env->GetArrayLength(argsArray);
    if (argc <= 0 || argc > 512) {
        LOGE("nativeLaunchJvm: bad argc %d", static_cast<int>(argc));
        return -4;
    }

    char **argv = static_cast<char **>(calloc(static_cast<size_t>(argc) + 1, sizeof(char *)));
    if (!argv) return -5;

    bool allocation_failed = false;
    for (jsize i = 0; i < argc; ++i) {
        auto jstr = static_cast<jstring>(env->GetObjectArrayElement(argsArray, i));
        if (jstr == nullptr) {
            argv[i] = strdup("");
        } else {
            const char *utf = env->GetStringUTFChars(jstr, nullptr);
            if (utf == nullptr) {
                env->DeleteLocalRef(jstr);
                allocation_failed = true;
                break;
            }
            argv[i] = strdup(utf);
            env->ReleaseStringUTFChars(jstr, utf);
            env->DeleteLocalRef(jstr);
        }
        if (argv[i] == nullptr) {
            allocation_failed = true;
            break;
        }
    }

    if (allocation_failed) {
        for (jsize i = 0; i < argc; ++i) free(argv[i]);
        free(argv);
        LOGE("nativeLaunchJvm: argument allocation failed");
        return -5;
    }

    LOGI("Calling JLI_Launch with argc=%d argv0=%s", static_cast<int>(argc), argv[0]);
    reset_signal_handlers();

    const int code = g_jli_launch(
        static_cast<int>(argc), argv,
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
    /*
     * HotSpot is not a safely unloadable Android plugin. Keep the VM/JLI
     * handles for process lifetime; the OS reclaims them when Flint exits.
     */
    LOGI("nativeShutdown: retaining loaded JVM libraries until process exit");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSmokeTest(
    JNIEnv *env, jclass
) {
    LOGI("Flint native smoke test OK");
    return env->NewStringUTF("flint-native-smoke-ok");
}
