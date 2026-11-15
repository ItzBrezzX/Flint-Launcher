#include <jni.h>
#include <android/log.h>
#include <array>
#include <cstdint>
#include <mutex>
#include <vector>

#define LOG_TAG "FlintGlfwEvents"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {
constexpr size_t kCapacity = 1024;
constexpr jint kStride = 5;

// Stable internal event protocol. These are Flint event IDs, not GLFW ABI values.
struct Event {
    jint type;
    jint a;
    jint b;
    jint c;
    jint d;
};

std::mutex g_mutex;
std::array<Event, kCapacity> g_events{};
size_t g_head = 0;
size_t g_size = 0;
uint64_t g_dropped = 0;
int32_t g_window_width = 0;
int32_t g_window_height = 0;
bool g_window_focused = false;
bool g_window_should_close = false;

void enqueue(const Event& event) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (event.type == 6) g_window_focused = event.a != 0;
    if (event.type == 7) {
        g_window_width = event.a > 0 ? event.a : 0;
        g_window_height = event.b > 0 ? event.b : 0;
    }
    if (event.type == 8) g_window_should_close = true;
    if (g_size == kCapacity) {
        // Preserve the newest input/window state while bounding memory use.
        g_head = (g_head + 1) % kCapacity;
        --g_size;
        ++g_dropped;
    }
    const size_t tail = (g_head + g_size) % kCapacity;
    g_events[tail] = event;
    ++g_size;
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativePostGlfwEvent(
    JNIEnv*, jclass, jint type, jint a, jint b, jint c, jint d
) {
    if (type < 1 || type > 8) return;
    enqueue(Event{type, a, b, c, d});
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeDrainGlfwEvents(
    JNIEnv* env, jclass
) {
    std::vector<Event> batch;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        batch.reserve(g_size);
        while (g_size > 0) {
            batch.push_back(g_events[g_head]);
            g_head = (g_head + 1) % kCapacity;
            --g_size;
        }
    }

    if (batch.empty()) return env->NewIntArray(0);

    std::vector<jint> flattened;
    flattened.reserve(batch.size() * kStride);
    for (const Event& event : batch) {
        flattened.push_back(event.type);
        flattened.push_back(event.a);
        flattened.push_back(event.b);
        flattened.push_back(event.c);
        flattened.push_back(event.d);
    }

    jintArray result = env->NewIntArray(static_cast<jsize>(flattened.size()));
    if (result == nullptr) return nullptr;
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(flattened.size()), flattened.data());
    return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwDroppedEventCount(
    JNIEnv*, jclass
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return static_cast<jlong>(g_dropped);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwResetWindowState(
    JNIEnv*, jclass
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_window_width = 0;
    g_window_height = 0;
    g_window_focused = false;
    g_window_should_close = false;
    g_head = 0;
    g_size = 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwWindowShouldClose(
    JNIEnv*, jclass
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_window_should_close ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwSetWindowShouldClose(
    JNIEnv*, jclass, jboolean should_close
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_window_should_close = should_close == JNI_TRUE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwGetWindowSize(
    JNIEnv* env, jclass
) {
    jint values[3];
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        values[0] = g_window_width;
        values[1] = g_window_height;
        values[2] = g_window_focused ? 1 : 0;
    }
    jintArray result = env->NewIntArray(3);
    if (result != nullptr) env->SetIntArrayRegion(result, 0, 3, values);
    return result;
}
