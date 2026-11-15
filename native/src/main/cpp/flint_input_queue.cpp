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
            ++dropped;
            return;
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

