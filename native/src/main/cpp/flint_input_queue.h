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
};

struct InputEvent {
    int32_t type;
    int32_t code;
    int32_t action;
    int32_t mods;
    double x;
    double y;
};

void push_input_event(const InputEvent &event);
std::vector<InputEvent> poll_input_events(int32_t maximum);
uint64_t dropped_input_event_count();

} // namespace flint
