#include <jni.h>
#include <android/log.h>
#include <array>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <vector>

#define LOG_TAG "FlintGlfwEvents"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {
constexpr size_t kCapacity = 1024;
constexpr jint kStride = 5;
constexpr size_t kKeyCount = 512;
constexpr size_t kMouseButtonCount = 8;
constexpr jint kPress = 1;
constexpr jint kRepeat = 2;
constexpr jint kRelease = 0;

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
std::array<jint, kKeyCount> g_key_states{};
std::array<jint, kMouseButtonCount> g_mouse_states{};
float g_cursor_x = 0.0f;
float g_cursor_y = 0.0f;

void clear_input_state_locked() {
    g_key_states.fill(kRelease);
    g_mouse_states.fill(kRelease);
}

void enqueue(const Event& event) {
    std::lock_guard<std::mutex> lock(g_mutex);

    switch (event.type) {
        case 1: // KEY: key, scan code, action, modifiers
            if (event.a >= 0 && static_cast<size_t>(event.a) < kKeyCount) {
                if (event.c == kRelease || event.c == kPress || event.c == kRepeat) {
                    g_key_states[static_cast<size_t>(event.a)] = event.c;
                }
            }
            break;
        case 3: // CURSOR_POSITION: raw IEEE-754 float bits
            std::memcpy(&g_cursor_x, &event.a, sizeof(g_cursor_x));
            std::memcpy(&g_cursor_y, &event.b, sizeof(g_cursor_y));
            break;
        case 4: // MOUSE_BUTTON: button, action, modifiers
            if (event.a >= 0 && static_cast<size_t>(event.a) < kMouseButtonCount &&
                (event.b == kRelease || event.b == kPress)) {
                g_mouse_states[static_cast<size_t>(event.a)] = event.b;
            }
            break;
        case 6: // FOCUS
            g_window_focused = event.a != 0;
            if (!g_window_focused) clear_input_state_locked();
            break;
        case 7: // WINDOW_SIZE
            g_window_width = event.a > 0 ? event.a : 0;
            g_window_height = event.b > 0 ? event.b : 0;
            break;
        case 8: // WINDOW_CLOSE
            g_window_should_close = true;
            break;
        default:
            break;
    }

    if (g_size == kCapacity) {
        g_head = (g_head + 1) % kCapacity;
        --g_size;
        ++g_dropped;
    }
    const size_t tail = (g_head + g_size) % kCapacity;
    g_events[tail] = event;
    ++g_size;
}
} // namespace

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
    g_cursor_x = 0.0f;
    g_cursor_y = 0.0f;
    clear_input_state_locked();
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

extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwGetKeyState(
    JNIEnv*, jclass, jint key
) {
    if (key < 0 || static_cast<size_t>(key) >= kKeyCount) return kRelease;
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_key_states[static_cast<size_t>(key)];
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwGetMouseButtonState(
    JNIEnv*, jclass, jint button
) {
    if (button < 0 || static_cast<size_t>(button) >= kMouseButtonCount) return kRelease;
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_mouse_states[static_cast<size_t>(button)];
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlfwGetCursorPosition(
    JNIEnv* env, jclass
) {
    jfloat values[2];
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        values[0] = g_cursor_x;
        values[1] = g_cursor_y;
    }
    jfloatArray result = env->NewFloatArray(2);
    if (result != nullptr) env->SetFloatArrayRegion(result, 0, 2, values);
    return result;
}
