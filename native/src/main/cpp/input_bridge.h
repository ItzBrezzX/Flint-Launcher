// input_bridge.h
// Flint input bridge: Android touch / key / mouse events are translated into
// GLFW-style events and pushed into a lock-free-ish (mutex-protected) queue
// that flint_glfwPollEvents drains and dispatches to registered callbacks.
//
// All code original to Flint. No Pojav/Boardwalk input code copied.
#pragma once

#include <cstdint>
#include <mutex>
#include <vector>
#include <deque>

namespace flint {

enum class InputEventType {
    None = 0,
    Key,            // key, scancode, action, mods
    Char,           // codepoint
    CharMods,       // codepoint, mods
    MouseButton,    // button, action, mods
    CursorPos,      // xpos, ypos (absolute, window coords)
    CursorPosDelta, // dx, dy (relative, for captured mouse)
    Scroll,         // xoffset, yoffset
    CursorEnter,    // entered (0/1)
    WindowSize,     // width, height (window coords)
    FramebufferSize,// width, height (pixels)
    WindowClose,
    WindowFocus,    // focused (0/1)
    WindowIconify,  // iconified (0/1)
    WindowRefresh,
    WindowPos,      // xpos, ypos
    DropFiles,      // count + paths (stub: count only)
    MonitorEvent,   // monitor index, event (connected/disconnected)
};

struct InputEvent {
    InputEventType type = InputEventType::None;
    int ival1 = 0; // key / button / action / width / xpos / entered / focused
    int ival2 = 0; // scancode / mods / height / ypos / iconified / count
    int ival3 = 0; // action (for key/mouse) / monitor event
    int ival4 = 0; // mods (for key/mouse) / extra
    double dval1 = 0.0; // cursor x / scroll x
    double dval2 = 0.0; // cursor y / scroll y
    uint32_t codepoint = 0; // for char events
};

// Thread-safe event queue. Producers (Android input thread, JNI) call push();
// consumer (render/main thread, inside glfwPollEvents) calls drainAll().
class InputEventQueue {
public:
    void push(const InputEvent& ev) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (queue_.size() < kMaxQueued) {
            queue_.push_back(ev);
        } else {
            // Drop oldest to keep latency bounded under input flood.
            queue_.pop_front();
            queue_.push_back(ev);
        }
    }
    void drainAll(std::vector<InputEvent>& out) {
        out.clear();
        std::lock_guard<std::mutex> lock(mutex_);
        out.reserve(queue_.size());
        while (!queue_.empty()) {
            out.push_back(queue_.front());
            queue_.pop_front();
        }
    }
    bool empty() {
        std::lock_guard<std::mutex> lock(mutex_);
        return queue_.empty();
    }
    void clear() {
        std::lock_guard<std::mutex> lock(mutex_);
        queue_.clear();
    }
private:
    static constexpr size_t kMaxQueued = 4096;
    std::mutex mutex_;
    std::deque<InputEvent> queue_;
};

// Global queue instance (single window model for Spike 0).
InputEventQueue& globalInputQueue();

// ---------------------------------------------------------------------------
// Input injection API (called from JNI on Android input threads).
// All functions are thread-safe and non-blocking.
// ---------------------------------------------------------------------------

// Physical key event (Android KeyEvent → GLFW key code). action: 0=release,1=press,2=repeat.
void injectKeyEvent(int glfwKey, int scancode, int action, int mods);

// Unicode character from Android text input (for chat / text fields).
void injectCharEvent(uint32_t codepoint);
void injectCharModsEvent(uint32_t codepoint, int mods);

// Mouse button (from touch tap or physical mouse). action 0/1.
void injectMouseButton(int button, int action, int mods);

// Absolute cursor position in window coordinates (touch / trackpad / mouse).
void injectCursorPos(double x, double y);

// Relative mouse delta (captured pointer mode / virtual joystick look).
// Accumulates into cursor pos and fires a cursor-pos callback with the new abs pos.
void injectCursorDelta(double dx, double dy);

// Scroll wheel / two-finger drag.
void injectScroll(double xoffset, double yoffset);

// Virtual joystick: axis values in [-1,1]. Mapped to either:
//  - camera look (right stick) → cursor delta (with sensitivity), or
//  - movement (left stick) → synthetic key press/release for W/A/S/D.
// mode: 0 = look (camera), 1 = movement keys.
void injectJoystickAxis(int stick, double axisX, double axisY, double sensitivity, int mode);

// Key mapping for touch buttons: jump/sneak/sprint/inventory → GLFW key codes.
// Defaults: jump=SPACE(32), sneak=LEFT_SHIFT(340), sprint=LEFT_CONTROL(341),
// inventory=E(69), drop=Q(81), chat=T(84) — set via setTouchKeyMapping.
void setTouchKeyMapping(const char* action, int glfwKey);
int  getTouchKeyMapping(const char* action);

// Press/release a mapped touch action (called when a virtual button is touched).
void injectTouchAction(const char* action, bool pressed);

// Mouse capture state: 0=normal,1=hidden,2=disabled,3=flint-captured.
void setMouseCaptureState(int state);
int  getMouseCaptureState();

// Reset all input state (on new window / game restart).
void resetInputState();

// Gamepad stub: always reports not present. TODO(Spike 1): wire Android InputDevice.
bool gamepadIsPresent(int jid);

} // namespace flint
