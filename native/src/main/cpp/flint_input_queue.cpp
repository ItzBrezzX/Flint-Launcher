#include "flint_input_queue.h"

#include <array>
#include <mutex>

namespace flint {
namespace {
constexpr size_t kCapacity = 1024;
std::array<InputEvent, kCapacity> queue{};
size_t head = 0;
size_t size = 0;
uint64_t dropped = 0;
std::mutex queue_mutex;
}

void push_input_event(const InputEvent &event) {
    std::lock_guard<std::mutex> lock(queue_mutex);
    if (size == kCapacity) {
        ++dropped;
        return;
    }
    const size_t tail = (head + size) % kCapacity;
    queue[tail] = event;
    ++size;
}

std::vector<InputEvent> poll_input_events(int32_t maximum) {
    std::vector<InputEvent> result;
    if (maximum <= 0) return result;
    std::lock_guard<std::mutex> lock(queue_mutex);
    const size_t count = size < static_cast<size_t>(maximum)
        ? size : static_cast<size_t>(maximum);
    result.reserve(count);
    for (size_t i = 0; i < count; ++i) {
        result.push_back(queue[head]);
        head = (head + 1) % kCapacity;
    }
    size -= count;
    return result;
}

uint64_t dropped_input_event_count() {
    std::lock_guard<std::mutex> lock(queue_mutex);
    return dropped;
}

} // namespace flint

#include <jni.h>

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
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeDroppedInputEventCount(
    JNIEnv *, jclass
) {
    return static_cast<jlong>(flint::dropped_input_event_count());
}
