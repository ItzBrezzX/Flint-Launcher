#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <dirent.h>
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

using JNI_CreateJavaVM_func = jint (JNICALL *)(JavaVM **, void **, void *);

static std::mutex g_jre_mutex;
static void *g_libjvm = nullptr;
static JNI_CreateJavaVM_func g_create_java_vm = nullptr;
static JavaVM *g_embedded_vm = nullptr;
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

/*
 * JRE distributions do not all use the same lib/ layout. Keep the common
 * layouts above as fast paths, then mirror the Kotlin inventory's recursive
 * lookup so nested homes such as jre/lib/<arch>/... are supported as well.
 * Do not recurse through directory symlinks: archives may contain links and
 * following them could escape the runtime root or create a traversal loop.
 */
static std::string find_library_recursive(
    const std::string &directory,
    const std::string &library_name,
    unsigned int depth
) {
    if (depth > 12) return {};
    DIR *handle = opendir(directory.c_str());
    if (!handle) return {};

    std::string found;
    struct dirent *entry = nullptr;
    while ((entry = readdir(handle)) != nullptr) {
        const char *name = entry->d_name;
        if (strcmp(name, ".") == 0 || strcmp(name, "..") == 0) continue;

        const std::string candidate = directory + "/" + name;
        struct stat info {};
        if (stat(candidate.c_str(), &info) != 0) continue;

        if (S_ISREG(info.st_mode) && library_name == name) {
            found = candidate;
            break;
        }

        if (S_ISDIR(info.st_mode)) {
            struct stat link_info {};
            if (lstat(candidate.c_str(), &link_info) == 0 && S_ISLNK(link_info.st_mode)) {
                continue;
            }
            found = find_library_recursive(candidate, library_name, depth + 1);
            if (!found.empty()) break;
        }
    }
    closedir(handle);
    return found;
}

static std::string find_jre_library(
    const std::string &home,
    const char *const *suffixes,
    const std::string &library_name
) {
    std::string path = first_existing(home, suffixes);
    if (!path.empty()) return path;
    return find_library_recursive(home, library_name, 0);
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

    if (g_libjvm != nullptr) {
        if (home == g_jre_home && g_create_java_vm != nullptr) return JNI_TRUE;
        if (home != g_jre_home) {
            g_jre_error = "A different JVM is already loaded in this app process; restart Flint before changing Java runtime.";
            LOGE("%s", g_jre_error.c_str());
            return JNI_FALSE;
        }
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

    const std::string jvm_path = find_jre_library(home, jvm_suffixes, "libjvm.so");
    if (jvm_path.empty()) {
        g_jre_error = "JRE layout incomplete: libjvm.so not found";
        LOGE("%s under %s", g_jre_error.c_str(), home.c_str());
        return JNI_FALSE;
    }

    configure_jre_environment(home);

    if (g_libjvm == nullptr) {
        g_libjvm = dlopen(jvm_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    }
    if (!g_libjvm) {
        const char *error = dlerror();
        g_jre_error = std::string("dlopen libjvm.so failed: ") +
            (error ? error : "unknown linker error");
        LOGE("%s", g_jre_error.c_str());
        return JNI_FALSE;
    }
    g_jre_home = home;

    dlerror();
    g_create_java_vm = reinterpret_cast<JNI_CreateJavaVM_func>(
        dlsym(g_libjvm, "JNI_CreateJavaVM")
    );
    if (!g_create_java_vm) {
        const char *error = dlerror();
        g_jre_error = std::string("JNI_CreateJavaVM symbol missing: ") +
            (error ? error : "unknown symbol error");
        LOGE("%s", g_jre_error.c_str());
        return JNI_FALSE;
    }

    g_jre_home = home;
    g_jre_error.clear();
    LOGI("JRE VM library loaded: libjvm=%s", jvm_path.c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeJreStatus(
    JNIEnv *env, jclass
) {
    std::lock_guard<std::mutex> guard(g_jre_mutex);
    if (g_create_java_vm != nullptr) {
        const std::string status = "jre-loaded:" + g_jre_home;
        return env->NewStringUTF(status.c_str());
    }
    const std::string status = g_jre_error.empty() ? "jre-not-loaded" : g_jre_error;
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeLaunchJvm(
    JNIEnv *env, jclass, jobjectArray argsArray
) {
    if (argsArray == nullptr) {
        LOGE("nativeLaunchJvm: null args");
        return -3;
    }

    const jsize argc = env->GetArrayLength(argsArray);
    if (argc < 2 || argc > 512) {
        LOGE("nativeLaunchJvm: bad argc %d", static_cast<int>(argc));
        return -4;
    }

    std::vector<std::string> argv;
    argv.reserve(static_cast<size_t>(argc));
    for (jsize i = 0; i < argc; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(argsArray, i));
        if (value == nullptr) {
            argv.emplace_back();
            continue;
        }
        const char *utf = env->GetStringUTFChars(value, nullptr);
        if (utf == nullptr) {
            env->DeleteLocalRef(value);
            LOGE("nativeLaunchJvm: could not read argument %d", static_cast<int>(i));
            return -5;
        }
        argv.emplace_back(utf);
        env->ReleaseStringUTFChars(value, utf);
        env->DeleteLocalRef(value);
    }

    std::vector<std::string> vm_option_strings;
    std::vector<std::string> app_arguments;
    std::string class_path;
    std::string requested_main_class;

    for (size_t i = 1; i < argv.size();) {
        const std::string &arg = argv[i];
        if (arg == "-cp" || arg == "-classpath" || arg == "--class-path") {
            if (i + 1 >= argv.size() || argv[i + 1].empty()) {
                LOGE("nativeLaunchJvm: classpath option has no value");
                return -6;
            }
            class_path = argv[i + 1];
            i += 2;
            continue;
        }
        if (arg == "-jar") {
            LOGE("nativeLaunchJvm: -jar mode is not supported by the embedded entry-point bridge");
            return -7;
        }
        if (!arg.empty() && arg[0] == '-') {
            vm_option_strings.push_back(arg);
            ++i;
            continue;
        }
        requested_main_class = arg;
        ++i;
        for (; i < argv.size(); ++i) app_arguments.push_back(argv[i]);
        break;
    }

    if (requested_main_class.empty()) {
        LOGE("nativeLaunchJvm: no Java main class was supplied");
        return -8;
    }
    if (!class_path.empty()) {
        vm_option_strings.push_back("-Djava.class.path=" + class_path);
    }

    JavaVM *vm = nullptr;
    {
        std::unique_lock<std::mutex> guard(g_jre_mutex);
        if (!g_create_java_vm || g_libjvm == nullptr) {
            LOGE("nativeLaunchJvm: JNI VM entry point unavailable (%s)", g_jre_error.c_str());
            return -1;
        }

        if (g_embedded_vm == nullptr) {
            bool has_java_home = false;
            bool has_library_path = false;
            for (const auto &option : vm_option_strings) {
                if (option.rfind("-Djava.home=", 0) == 0) has_java_home = true;
                if (option.rfind("-Djava.library.path=", 0) == 0) has_library_path = true;
            }
            if (!has_java_home) vm_option_strings.push_back("-Djava.home=" + g_jre_home);
            if (!has_library_path) {
                std::string native_path;
                for (const auto &directory : library_directories(g_jre_home)) {
                    if (!native_path.empty()) native_path += ":";
                    native_path += directory;
                }
                vm_option_strings.push_back("-Djava.library.path=" + native_path);
            }

            std::vector<JavaVMOption> options(vm_option_strings.size());
            for (size_t i = 0; i < vm_option_strings.size(); ++i) {
                options[i].optionString = const_cast<char *>(vm_option_strings[i].c_str());
                options[i].extraInfo = nullptr;
            }

            JavaVMInitArgs init_args {};
            init_args.version = JNI_VERSION_1_6;
            init_args.nOptions = static_cast<jint>(options.size());
            init_args.options = options.empty() ? nullptr : options.data();
            init_args.ignoreUnrecognized = JNI_FALSE;

            JavaVM *created_vm = nullptr;
            void *created_env = nullptr;
            LOGI("Creating embedded Java VM with %d option(s)", init_args.nOptions);
            const jint create_status = g_create_java_vm(&created_vm, &created_env, &init_args);
            if (create_status != JNI_OK || created_vm == nullptr || created_env == nullptr) {
                g_jre_error = "JNI_CreateJavaVM failed with status " + std::to_string(create_status);
                LOGE("%s", g_jre_error.c_str());
                return -9;
            }
            g_embedded_vm = created_vm;
            LOGI("JNI_CreateJavaVM succeeded");
        }
        vm = g_embedded_vm;
    }

    if (vm == nullptr) {
        LOGE("nativeLaunchJvm: embedded VM is unavailable");
        return -10;
    }

    JNIEnv *jvm_env = nullptr;
    void *current_env = nullptr;
    bool attached_here = false;
    const jint current_status = vm->GetEnv(&current_env, JNI_VERSION_1_6);
    if (current_status == JNI_EDETACHED) {
        JNIEnv *attached_env = nullptr;
        if (vm->AttachCurrentThread(&attached_env, nullptr) != JNI_OK || attached_env == nullptr) {
            LOGE("nativeLaunchJvm: could not attach launch thread to embedded VM");
            return -11;
        }
        jvm_env = attached_env;
        attached_here = true;
    } else if (current_status == JNI_OK && current_env != nullptr) {
        jvm_env = static_cast<JNIEnv *>(current_env);
    } else {
        LOGE("nativeLaunchJvm: GetEnv failed with status %d", current_status);
        return -12;
    }

    jint result = 0;
    jclass class_loader_class = jvm_env->FindClass("java/lang/ClassLoader");
    if (class_loader_class == nullptr) {
        LOGE("nativeLaunchJvm: could not resolve java.lang.ClassLoader");
        if (jvm_env->ExceptionCheck()) {
            jvm_env->ExceptionDescribe();
            jvm_env->ExceptionClear();
        }
        result = -13;
    }

    jobject system_loader = nullptr;
    if (result == 0) {
        jmethodID get_system_loader = jvm_env->GetStaticMethodID(
            class_loader_class,
            "getSystemClassLoader",
            "()Ljava/lang/ClassLoader;"
        );
        if (get_system_loader == nullptr) {
            LOGE("nativeLaunchJvm: ClassLoader.getSystemClassLoader is unavailable");
            if (jvm_env->ExceptionCheck()) {
                jvm_env->ExceptionDescribe();
                jvm_env->ExceptionClear();
            }
            result = -14;
        } else {
            system_loader = jvm_env->CallStaticObjectMethod(class_loader_class, get_system_loader);
            if (jvm_env->ExceptionCheck() || system_loader == nullptr) {
                LOGE("nativeLaunchJvm: could not obtain the Java system classloader");
                if (jvm_env->ExceptionCheck()) {
                    jvm_env->ExceptionDescribe();
                    jvm_env->ExceptionClear();
                }
                result = -15;
            }
        }
    }

    jclass main_class_object = nullptr;
    if (result == 0) {
        jmethodID load_class = jvm_env->GetMethodID(
            class_loader_class,
            "loadClass",
            "(Ljava/lang/String;)Ljava/lang/Class;"
        );
        jstring class_name = jvm_env->NewStringUTF(requested_main_class.c_str());
        if (load_class == nullptr || class_name == nullptr) {
            LOGE("nativeLaunchJvm: could not resolve ClassLoader.loadClass");
            if (jvm_env->ExceptionCheck()) {
                jvm_env->ExceptionDescribe();
                jvm_env->ExceptionClear();
            }
            result = -16;
        } else {
            jobject loaded = jvm_env->CallObjectMethod(system_loader, load_class, class_name);
            if (jvm_env->ExceptionCheck() || loaded == nullptr) {
                LOGE("nativeLaunchJvm: could not load Java main class %s", requested_main_class.c_str());
                if (jvm_env->ExceptionCheck()) {
                    jvm_env->ExceptionDescribe();
                    jvm_env->ExceptionClear();
                }
                result = -17;
            } else {
                main_class_object = static_cast<jclass>(loaded);
            }
        }
        if (class_name != nullptr) jvm_env->DeleteLocalRef(class_name);
    }

    if (result == 0) {
        jmethodID main_method = jvm_env->GetStaticMethodID(
            main_class_object,
            "main",
            "([Ljava/lang/String;)V"
        );
        jclass string_class = jvm_env->FindClass("java/lang/String");
        if (main_method == nullptr || string_class == nullptr) {
            LOGE("nativeLaunchJvm: Java main(String[]) method or String class is missing");
            if (jvm_env->ExceptionCheck()) {
                jvm_env->ExceptionDescribe();
                jvm_env->ExceptionClear();
            }
            result = -18;
        } else {
            jobjectArray java_args = jvm_env->NewObjectArray(
                static_cast<jsize>(app_arguments.size()),
                string_class,
                nullptr
            );
            if (java_args == nullptr) {
                LOGE("nativeLaunchJvm: could not allocate Java argument array");
                result = -19;
            } else {
                for (size_t i = 0; i < app_arguments.size(); ++i) {
                    jstring argument = jvm_env->NewStringUTF(app_arguments[i].c_str());
                    if (argument == nullptr) {
                        result = -20;
                        break;
                    }
                    jvm_env->SetObjectArrayElement(java_args, static_cast<jsize>(i), argument);
                    jvm_env->DeleteLocalRef(argument);
                    if (jvm_env->ExceptionCheck()) {
                        result = -21;
                        break;
                    }
                }
                if (result == 0) {
                    LOGI("Invoking embedded Java main class %s with %zu argument(s)",
                         requested_main_class.c_str(), app_arguments.size());
                    jvm_env->CallStaticVoidMethod(main_class_object, main_method, java_args);
                    if (jvm_env->ExceptionCheck()) {
                        LOGE("nativeLaunchJvm: Java main method threw an exception");
                        jvm_env->ExceptionDescribe();
                        jvm_env->ExceptionClear();
                        result = -22;
                    }
                }
                jvm_env->DeleteLocalRef(java_args);
            }
            jvm_env->DeleteLocalRef(string_class);
        }
    }

    if (system_loader != nullptr) jvm_env->DeleteLocalRef(system_loader);
    if (main_class_object != nullptr) jvm_env->DeleteLocalRef(main_class_object);
    if (class_loader_class != nullptr) jvm_env->DeleteLocalRef(class_loader_class);

    if (attached_here) {
        const jint detach_status = vm->DetachCurrentThread();
        if (detach_status != JNI_OK) {
            LOGE("nativeLaunchJvm: DetachCurrentThread failed with status %d", detach_status);
        }
    }
    LOGI("Embedded Java entry point returned status %d", result);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeShutdown(
    JNIEnv *, jclass
) {
    /*
     * HotSpot is not a safely unloadable Android plugin. Keep the VM
     * and libjvm handles for process lifetime; the OS reclaims them at exit.
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
