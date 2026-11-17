#include "flint_glfw_backend.h"
#include "flint_host.h"
#include "flint_input_queue.h"
#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <dirent.h>
#include <sys/stat.h>
#include <errno.h>
#include <vector>
#include <string>
#include <algorithm>
#include <mutex>
#include <unordered_map>
#include <fstream>
#include <pthread.h>
#include <signal.h>
#include <new>

#define LOG_TAG "FlintNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)


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

typedef jint (*JLI_Launch_func)(
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

// JRE/JVM handles and symbols remain valid for the process lifetime.
static void *g_libjli = nullptr;
static JLI_Launch_func g_jli_launch = nullptr;
static std::vector<void *> g_jre_handles;
static std::unordered_map<std::string, void *> g_jre_library_handles;
static std::string g_loaded_jre_home;
static std::mutex g_jre_load_mutex;

static void *g_gl4es = nullptr;
static const GLubyte *(*g_gl4es_get_string)(GLenum) = nullptr;
static void (*g_gl4es_clear_color)(GLfloat, GLfloat, GLfloat, GLfloat) = nullptr;
static void (*g_gl4es_clear)(GLbitfield) = nullptr;
static GLenum (*g_gl4es_get_error)(void) = nullptr;

static std::string join_path(const std::string &a, const std::string &b) {
    return a.empty() ? b : a + "/" + b;
}

static bool find_library_recursive(const std::string &directory, const std::string &name,
                                   std::string &result, int depth = 0) {
    if (depth > 8) return false;
    DIR *dir = opendir(directory.c_str());
    if (!dir) return false;
    std::vector<std::string> subdirs;
    struct dirent *entry = nullptr;
    while ((entry = readdir(dir)) != nullptr) {
        if (!strcmp(entry->d_name, ".") || !strcmp(entry->d_name, "..")) continue;
        const std::string path = join_path(directory, entry->d_name);
        struct stat st {};
        if (stat(path.c_str(), &st) != 0) continue;
        if (S_ISREG(st.st_mode) && name == entry->d_name) {
            result = path;
            closedir(dir);
            return true;
        }
        if (S_ISDIR(st.st_mode)) subdirs.push_back(path);
    }
    closedir(dir);
    std::sort(subdirs.begin(), subdirs.end());
    for (const auto &subdir : subdirs) {
        if (find_library_recursive(subdir, name, result, depth + 1)) return true;
    }
    return false;
}

static bool resolve_jre_library(const std::string &home, const std::string &name,
                                const std::vector<std::string> &preferred, std::string &result) {
    struct stat st {};
    for (const auto &relative : preferred) {
        const std::string candidate = join_path(home, relative);
        if (stat(candidate.c_str(), &st) == 0 && S_ISREG(st.st_mode)) {
            result = candidate;
            return true;
        }
    }
    return find_library_recursive(join_path(home, "lib"), name, result);
}

static bool load_jre_library(const std::string &path, bool required, void **loadedHandle = nullptr) {
    const auto existing = g_jre_library_handles.find(path);
    if (existing != g_jre_library_handles.end()) {
        if (loadedHandle) *loadedHandle = existing->second;
        return true;
    }
    dlerror();
    void *handle = dlopen(path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        const char *error = dlerror();
        if (required) LOGE("Required JRE library failed: %s (%s)", path.c_str(), error ? error : "unknown");
        else LOGW("Optional JRE library failed: %s (%s)", path.c_str(), error ? error : "unknown");
        return !required;
    }
    g_jre_handles.push_back(handle);
    g_jre_library_handles[path] = handle;
    if (loadedHandle) *loadedHandle = handle;
    LOGI("Loaded JRE library: %s", path.c_str());
    return true;
}

static void collect_jre_libraries(const std::string &directory, std::vector<std::string> &result,
                                  int depth = 0) {
    if (depth > 8) return;
    DIR *dir = opendir(directory.c_str());
    if (!dir) return;
    std::vector<std::string> subdirs;
    struct dirent *entry = nullptr;
    while ((entry = readdir(dir)) != nullptr) {
        if (!strcmp(entry->d_name, ".") || !strcmp(entry->d_name, "..")) continue;
        const std::string path = join_path(directory, entry->d_name);
        struct stat st {};
        if (stat(path.c_str(), &st) != 0) continue;
        if (S_ISREG(st.st_mode)) {
            const std::string name(entry->d_name);
            if (name.size() > 3 && name.compare(name.size() - 3, 3, ".so") == 0) result.push_back(path);
        } else if (S_ISDIR(st.st_mode)) {
            subdirs.push_back(path);
        }
    }
    closedir(dir);
    std::sort(subdirs.begin(), subdirs.end());
    for (const auto &subdir : subdirs) collect_jre_libraries(subdir, result, depth + 1);
}

static std::string read_jre_version(const std::string &home) {
    std::ifstream release(join_path(home, "release"));
    std::string line;
    while (std::getline(release, line)) {
        const std::string key = "JAVA_VERSION=";
        if (line.compare(0, key.size(), key) != 0) continue;
        std::string version = line.substr(key.size());
        if (!version.empty() && version.front() == '"') version.erase(0, 1);
        if (!version.empty() && version.back() == '"') version.pop_back();
        if (!version.empty()) return version;
    }
    return "1.8.0";
}

static std::string jre_dot_version(const std::string &version) {
    const size_t first = version.find('.');
    if (first == std::string::npos) return version;
    const size_t second = version.find('.', first + 1);
    return second == std::string::npos ? version : version.substr(0, second);
}

/*
 * Android ART installs process-wide signal handlers before loading Flint.
 * HotSpot/JLI needs to install its own handlers during VM startup. PojavLauncher
 * uses the same reset sequence; SIGSEGV is temporarily ignored because Android
 * rejects setting it to SIG_DFL while preparing the JVM handler replacement.
 *
 * These dispositions intentionally remain owned by the embedded JVM after
 * launch; restoring ART's handlers while HotSpot is alive would break VM signal
 * handling.
 */
static void reset_signal_handlers_for_jli() {
    struct sigaction action {};
    sigemptyset(&action.sa_mask);
    action.sa_flags = 0;

    for (int signalNumber = SIGHUP; signalNumber < NSIG; ++signalNumber) {
        if (signalNumber == SIGKILL || signalNumber == SIGSTOP) continue;

        action.sa_handler = signalNumber == SIGSEGV ? SIG_IGN : SIG_DFL;
        if (sigaction(signalNumber, &action, nullptr) != 0) {
            const int error = errno;
            LOGW("Could not reset signal handler %d before JLI: %s",
                 signalNumber, strerror(error));
        }
    }
    LOGI("Reset inherited Android/ART signal handlers before JLI_Launch");
}


extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSetEnv(
    JNIEnv *env, jclass, jstring jkey, jstring jvalue
) {
    if (!jkey || !jvalue) return JNI_FALSE;

    const char *key = env->GetStringUTFChars(jkey, nullptr);
    if (!key) return JNI_FALSE;

    const char *value = env->GetStringUTFChars(jvalue, nullptr);
    if (!value) {
        env->ReleaseStringUTFChars(jkey, key);
        return JNI_FALSE;
    }

    const bool validKey = key[0] != '\0' && strchr(key, '=') == nullptr;
    const bool success = validKey && setenv(key, value, 1) == 0;
    if (!success) LOGE("Could not set process environment variable: %s", key);

    env->ReleaseStringUTFChars(jvalue, value);
    env->ReleaseStringUTFChars(jkey, key);
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeLoadJre(
    JNIEnv *env, jclass, jstring jreHomePath
) {
    if (!jreHomePath) {
        LOGE("nativeLoadJre received null JRE home");
        return JNI_FALSE;
    }
    const char *homeChars = env->GetStringUTFChars(jreHomePath, nullptr);
    if (!homeChars) return JNI_FALSE;
    const std::string home(homeChars);
    env->ReleaseStringUTFChars(jreHomePath, homeChars);

    std::lock_guard<std::mutex> lock(g_jre_load_mutex);
    if (g_jli_launch && g_loaded_jre_home == home) {
        LOGI("JRE already initialized: %s", home.c_str());
        return JNI_TRUE;
    }
    if (!g_loaded_jre_home.empty() && g_loaded_jre_home != home) {
        LOGE("Refusing to mix JRE runtimes in one process: already initialized for %s", g_loaded_jre_home.c_str());
        return JNI_FALSE;
    }
    // Pin the process to one runtime before the first dlopen. A partial failure
    // cannot be safely rolled back because dependencies are process-global.
    if (g_loaded_jre_home.empty()) g_loaded_jre_home = home;

    // Configure the JRE search paths before the first dlopen. Absolute paths
    // are still used below; this also helps Bionic resolve transitive NEEDED
    // dependencies by SONAME where the process linker namespace permits it.
    std::string jreLdPath =
        join_path(home, "lib/jli") + ":" +
        join_path(home, "lib") + ":" +
        join_path(home, "lib/server") + ":" +
        join_path(home, "lib/aarch64") + ":" +
        join_path(home, "lib/aarch64/server") + ":" +
        join_path(home, "lib/arm64") + ":" +
        join_path(home, "lib/arm64/server");
    const char *oldLdPath = getenv("LD_LIBRARY_PATH");
    if (oldLdPath && *oldLdPath) jreLdPath += ":" + std::string(oldLdPath);
    if (setenv("JAVA_HOME", home.c_str(), 1) != 0) {
        LOGW("Could not update JAVA_HOME for JRE loading: %s", strerror(errno));
    }
    if (setenv("LD_LIBRARY_PATH", jreLdPath.c_str(), 1) != 0) {
        LOGW("Could not update LD_LIBRARY_PATH for JRE loading: %s", strerror(errno));
    } else {
        LOGI("Configured JRE native search path before dlopen");
    }

    std::string jliPath;
    const std::vector<std::string> jliCandidates = {
        "lib/jli/libjli.so", "lib/libjli.so", "lib/aarch64/libjli.so",
        "lib/arm64/libjli.so", "lib/aarch32/libjli.so", "lib/arm/libjli.so",
        "lib/i386/libjli.so", "lib/amd64/libjli.so", "lib/x86_64/libjli.so"
    };
    if (!resolve_jre_library(home, "libjli.so", jliCandidates, jliPath)) {
        LOGE("JRE verification failed: libjli.so not found under %s", home.c_str());
        return JNI_FALSE;
    }
    if (!load_jre_library(jliPath, true, &g_libjli)) return JNI_FALSE;

    std::string jvmPath;
    const std::vector<std::string> jvmCandidates = {
        "lib/server/libjvm.so", "lib/aarch64/server/libjvm.so",
        "lib/arm64/server/libjvm.so", "lib/amd64/server/libjvm.so",
        "lib/x86_64/server/libjvm.so", "lib/aarch64/libjvm.so",
        "lib/arm64/libjvm.so", "lib/libjvm.so"
    };
    if (!resolve_jre_library(home, "libjvm.so", jvmCandidates, jvmPath)) {
        LOGE("JRE verification failed: libjvm.so not found under %s", home.c_str());
        return JNI_FALSE;
    }
    if (!load_jre_library(jvmPath, true)) return JNI_FALSE;

    // Preload common OpenJDK libraries, then attempt every remaining .so in
    // the JRE lib tree. Optional libraries vary by Java release and vendor.
    const char *core[] = {
        "libverify.so", "libjava.so", "libnet.so", "libnio.so", "libzip.so",
        "libmanagement.so", "libawt.so", "libawt_headless.so",
        "libfreetype.so", "libfontmanager.so", "libjawt.so", "libjsig.so"
    };
    for (const char *name : core) {
        std::string path;
        if (find_library_recursive(join_path(home, "lib"), name, path)) load_jre_library(path, false);
        else LOGW("Optional JRE library absent: %s", name);
    }

    std::vector<std::string> libraries;
    collect_jre_libraries(join_path(home, "lib"), libraries);
    std::sort(libraries.begin(), libraries.end());
    for (const auto &path : libraries) load_jre_library(path, false);

    dlerror();
    g_jli_launch = reinterpret_cast<JLI_Launch_func>(dlsym(g_libjli, "JLI_Launch"));
    if (!g_jli_launch) {
        const char *error = dlerror();
        LOGE("JLI_Launch symbol resolution failed: %s", error ? error : "symbol missing");
        return JNI_FALSE;
    }

    g_loaded_jre_home = home;
    LOGI("JRE native load complete; JLI_Launch resolved from %s", jliPath.c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeLaunchJvm(
    JNIEnv *env, jclass, jobjectArray argsArray
) {
    if (!g_jli_launch) return -1;
    if (!argsArray) return -3;
    const jsize argc = env->GetArrayLength(argsArray);
    if (env->ExceptionCheck()) return -6;
    if (argc <= 0 || argc > 512) return -4;

    // calloc(argc + 1) provides the required argv[argc] == nullptr terminator.
    char **argv = static_cast<char **>(calloc(static_cast<size_t>(argc) + 1, sizeof(char *)));
    if (!argv) return -5;
    auto freeArguments = [&]() {
        for (jsize i = 0; i < argc; ++i) free(argv[i]);
        free(argv);
    };

    for (jsize i = 0; i < argc; ++i) {
        auto jstr = static_cast<jstring>(env->GetObjectArrayElement(argsArray, i));
        if (env->ExceptionCheck() || !jstr) {
            if (jstr) env->DeleteLocalRef(jstr);
            freeArguments();
            LOGE("Invalid or unreadable JVM argument at index %d", static_cast<int>(i));
            return -6;
        }
        const char *utf = env->GetStringUTFChars(jstr, nullptr);
        if (!utf || env->ExceptionCheck()) {
            env->DeleteLocalRef(jstr);
            freeArguments();
            LOGE("Could not convert JVM argument at index %d", static_cast<int>(i));
            return -7;
        }
        argv[i] = strdup(utf);
        env->ReleaseStringUTFChars(jstr, utf);
        env->DeleteLocalRef(jstr);
        if (!argv[i]) {
            freeArguments();
            LOGE("Out of memory copying JVM argument at index %d", static_cast<int>(i));
            return -5;
        }
    }
    argv[argc] = nullptr;

    const std::string fullVersion = read_jre_version(g_loaded_jre_home);
    const std::string dotVersion = jre_dot_version(fullVersion);
    LOGI("Prepared %d arguments; argv[%d]=NULL; Java %s",
         static_cast<int>(argc), static_cast<int>(argc), fullVersion.c_str());

    // EGL is owned by the setup thread until this point. Release it here,
    // then bind the same context to the native pthread before entering HotSpot.
    if (!flint_glfw_release_android_context_current()) {
        LOGE("Could not release EGL context from the ART-attached launch thread");
        freeArguments();
        return -9;
    }

    struct JliLaunchRequest {
        JLI_Launch_func launch;
        int argc;
        char **argv;
        std::string fullVersion;
        std::string dotVersion;
        jint result;
    };

    auto threadMain = [](void *opaque) -> void * {
        auto *request = static_cast<JliLaunchRequest *>(opaque);

        // A pthread inherits the creator's signal mask. HotSpot expects its
        // startup thread to receive the JVM's signal set normally.
        sigset_t unblockedSignals;
        sigemptyset(&unblockedSignals);
        const int maskResult = pthread_sigmask(SIG_SETMASK, &unblockedSignals, nullptr);
        if (maskResult != 0) {
            LOGW("Could not clear inherited signal mask on JLI pthread: %s",
                 strerror(maskResult));
        }

        if (!flint_glfw_make_android_context_current()) {
            LOGE("Could not bind Flint EGL context to the dedicated JLI pthread");
            request->result = -10;
            return nullptr;
        }

        // Reset ART signal handlers only after the worker successfully owns EGL
        // and is about to enter HotSpot; failed EGL hand-off leaves ART untouched.
        reset_signal_handlers_for_jli();

        LOGI("JLI_Launch entering on dedicated native pthread (not ART-attached)");
        request->result = request->launch(
            request->argc, request->argv, 0, nullptr, 0, nullptr,
            request->fullVersion.c_str(), request->dotVersion.c_str(),
            request->argv[0], "FlintLauncher",
            JNI_FALSE, JNI_TRUE, JNI_FALSE, 0
        );
        LOGI("JLI_Launch returned %d on dedicated native pthread", static_cast<int>(request->result));

        if (!flint_glfw_release_android_context_current()) {
            LOGE("Could not release EGL context from the dedicated JLI pthread");
        }
        return nullptr;
    };

    auto *request = new (std::nothrow) JliLaunchRequest{
        g_jli_launch,
        static_cast<int>(argc),
        argv,
        fullVersion,
        dotVersion,
        -100,
    };
    if (!request) {
        LOGE("Could not allocate the JLI pthread request");
        flint_glfw_make_android_context_current();
        freeArguments();
        return -5;
    }

    pthread_attr_t attributes;
    int attrResult = pthread_attr_init(&attributes);
    if (attrResult != 0) {
        LOGE("pthread_attr_init failed for JLI thread: %d", attrResult);
        flint_glfw_make_android_context_current();
        delete request;
        freeArguments();
        return -11;
    }
    attrResult = pthread_attr_setdetachstate(&attributes, PTHREAD_CREATE_JOINABLE);
    if (attrResult != 0) {
        LOGE("pthread_attr_setdetachstate failed for JLI thread: %d", attrResult);
        pthread_attr_destroy(&attributes);
        flint_glfw_make_android_context_current();
        delete request;
        freeArguments();
        return -12;
    }

    pthread_t jliThread{};
    const int createResult = pthread_create(&jliThread, &attributes, threadMain, request);
    pthread_attr_destroy(&attributes);
    if (createResult != 0) {
        LOGE("pthread_create failed for JLI thread: %d", createResult);
        flint_glfw_make_android_context_current();
        delete request;
        freeArguments();
        return -13;
    }

    const int joinResult = pthread_join(jliThread, nullptr);
    if (joinResult != 0) {
        LOGE("pthread_join failed for JLI thread: %d; retaining request and argv until process exit", joinResult);
        // The worker may still reference request/argv. Retain them rather than risk a use-after-free.
        return -14;
    }

    const jint code = request->result;
    delete request;
    freeArguments();
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


/* JNI input functions belong to libflint_native.so, which NativeBridge loads. */
extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativePushInputEvent(
    JNIEnv *, jclass, jint type, jint code, jint action, jint mods, jdouble x, jdouble y
) {
    flint::push_input_event({type, code, action, mods, x, y});
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativePollInputEvents(
    JNIEnv *env, jclass, jint maximum
) {
    const auto events = flint::poll_input_events(maximum > 0 && maximum < 1024 ? maximum : 1024);
    jlongArray output = env->NewLongArray(static_cast<jsize>(events.size() * 6));
    if (!output || events.empty()) return output;

    std::vector<jlong> packed;
    packed.reserve(events.size() * 6);
    for (const auto &event : events) {
        union { double d; uint64_t u; } x{}, y{};
        x.d = event.x;
        y.d = event.y;
        packed.push_back(static_cast<jlong>(event.type));
        packed.push_back(static_cast<jlong>(event.code));
        packed.push_back(static_cast<jlong>(event.action));
        packed.push_back(static_cast<jlong>(event.mods));
        packed.push_back(static_cast<jlong>(x.u));
        packed.push_back(static_cast<jlong>(y.u));
    }
    env->SetLongArrayRegion(output, 0, static_cast<jsize>(packed.size()), packed.data());
    return output;
}


extern "C" JNIEXPORT jlong JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwPresentedFrameCount(
    JNIEnv *, jclass
) {
    return static_cast<jlong>(flint_glfw_presented_frame_count());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeDroppedInputEventCount(
    JNIEnv *, jclass
) {
    return static_cast<jlong>(flint::dropped_input_event_count());
}


extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSetNativeLibraryDir(
    JNIEnv *env, jclass, jstring dir
) {
    if (!dir) {
        flint_host_set_native_library_dir(nullptr);
        return;
    }
    const char *path = env->GetStringUTFChars(dir, nullptr);
    flint_host_set_native_library_dir(path);
    env->ReleaseStringUTFChars(dir, path);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSetDisplayMetrics(
    JNIEnv *, jclass, jint width, jint height, jfloat refreshHz
) {
    flint_host_set_display_metrics(width, height, refreshHz);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativePreferPerformanceCores(
    JNIEnv *, jclass, jboolean enable
) {
    return flint_host_prefer_performance_cores(enable ? 1 : 0) == 0 ? JNI_TRUE : JNI_FALSE;
}
