#pragma once

#include <cstdint>
#include <vector>

namespace flint {

enum class InputEventType : int32_t {
    Key = 1,
    Character = 2,
    Cursor = 3,
    MouseButton = 4,
    Scroll = 5,
    Focus = 6,
    WindowSize = 7,
    FramebufferSize = 8,
    Wake = 9,
};

struct InputEvent {
    int32_t type;
    int32_t code;
    int32_t action;
    int32_t mods;
    double x;
    double y;
};

#if defined(__GNUC__)
#define FLINT_QUEUE_API __attribute__((visibility("default")))
#else
#define FLINT_QUEUE_API
#endif

FLINT_QUEUE_API void push_input_event(const InputEvent &event);
FLINT_QUEUE_API std::vector<InputEvent> poll_input_events(int32_t maximum);
FLINT_QUEUE_API bool wait_for_input_event(double timeout_seconds);
FLINT_QUEUE_API uint64_t dropped_input_event_count();

} // namespace flint
