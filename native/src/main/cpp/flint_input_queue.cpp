#include "flint_input_queue.h"

#include <array>
#include <chrono>
#include <condition_variable>
#include <mutex>

namespace flint {
namespace {
constexpr size_t kCapacity = 1024;
std::array<InputEvent, kCapacity> queue{};
size_t head = 0;
size_t size = 0;
uint64_t dropped = 0;
std::mutex queue_mutex;
std::condition_variable queue_ready;
}

void push_input_event(const InputEvent &event) {
    {
        std::lock_guard<std::mutex> lock(queue_mutex);
        if (size == kCapacity) {
            const bool is_close =
                event.type == static_cast<int32_t>(InputEventType::Close);
            const bool is_release =
                (event.type == static_cast<int32_t>(InputEventType::Key) && event.action == 0) ||
                (event.type == static_cast<int32_t>(InputEventType::MouseButton) && event.action == 0) ||
                (event.type == static_cast<int32_t>(InputEventType::Focus) && event.action == 0);

            if (!is_close && !is_release) {
                ++dropped;
                return;
            }

            // Do not let pointer-motion floods discard key/button releases,
            // focus-loss cleanup or STOP. Evict the oldest queued record and
            // append the critical transition so the GLFW owner thread sees it.
            head = (head + 1) % kCapacity;
            --size;
            ++dropped;
        }
        const size_t tail = (head + size) % kCapacity;
        queue[tail] = event;
        ++size;
    }
    queue_ready.notify_one();
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

bool wait_for_input_event(double timeout_seconds) {
    std::unique_lock<std::mutex> lock(queue_mutex);
    if (size > 0) return true;
    if (timeout_seconds < 0.0) {
        queue_ready.wait(lock, [] { return size > 0; });
    } else if (timeout_seconds > 0.0) {
        queue_ready.wait_for(
            lock,
            std::chrono::duration<double>(timeout_seconds),
            [] { return size > 0; }
        );
    }
    return size > 0;
}

uint64_t dropped_input_event_count() {
    std::lock_guard<std::mutex> lock(queue_mutex);
    return dropped;
}

} // namespace flint

