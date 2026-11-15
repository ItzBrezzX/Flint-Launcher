// input_bridge.cpp
// Flint input bridge: Android touch / key / mouse / virtual-joystick events
// are translated into GLFW-style events and queued for glfwPollEvents.
// All code original to Flint.

#include "flint_common.h"
#include "input_bridge.h"
#include "glfw_stub.h"

#include <string.h>
#include <mutex>
#include <string>
#include <unordered_map>
#include <cmath>

namespace flint {

static std::mutex g_inputMutex;
static InputEventQueue g_queue;
static int g_mouseCapture = FLINT_GLFW_CURSOR_NORMAL;

// Touch-action → GLFW key mapping. Defaults match Minecraft PC controls.
static std::unordered_map<std::string, int> g_touchKeyMap = {
    { "jump",     FLINT_GLFW_KEY_SPACE },        // 32
    { "sneak",    FLINT_GLFW_KEY_LEFT_SHIFT },   // 340
    { "sprint",   FLINT_GLFW_KEY_LEFT_CONTROL }, // 341
    { "inventory", FLINT_GLFW_KEY_E },           // 69
    { "drop",     FLINT_GLFW_KEY_Q },             // 81
    { "chat",     84 },                           // T
    { "attack",   FLINT_GLFW_MOUSE_BUTTON_LEFT }, // mouse button 0 (handled as mouse)
    { "use",      FLINT_GLFW_MOUSE_BUTTON_RIGHT },// mouse button 1
    { "pick",     FLINT_GLFW_MOUSE_BUTTON_MIDDLE },// 2
    { "forward",  FLINT_GLFW_KEY_W },             // 87
    { "back",     FLINT_GLFW_KEY_S },             // 83
    { "left",     FLINT_GLFW_KEY_A },             // 65
    { "right",    FLINT_GLFW_KEY_D },             // 68
    { "up",       FLINT_GLFW_KEY_UP },
    { "down",     FLINT_GLFW_KEY_DOWN },
};

// Track synthetic key press state (for joystick → WASD and touch hold).
static std::unordered_map<int, bool> g_syntheticKeyDown;
static std::unordered_map<int, bool> g_syntheticMouseDown;

// Joystick deadzone.
static constexpr double kDeadzone = 0.25;

InputEventQueue& globalInputQueue() { return g_queue; }

static void postKey(int glfwKey, int action, int mods = 0) {
    if (glfwKey < 0) return;
    InputEvent ev;
    ev.type = InputEventType::Key;
    ev.ival1 = glfwKey;
    ev.ival2 = 0; // scancode (Android scancode not mapped in Spike 0)
    ev.ival3 = action;
    ev.ival4 = mods;
    g_queue.push(ev);
}

static void postMouseButton(int button, int action, int mods = 0) {
    InputEvent ev;
    ev.type = InputEventType::MouseButton;
    ev.ival1 = button;
    ev.ival2 = action;
    ev.ival3 = mods;
    g_queue.push(ev);
}

void injectKeyEvent(int glfwKey, int scancode, int action, int mods) {
    InputEvent ev;
    ev.type = InputEventType::Key;
    ev.ival1 = glfwKey;
    ev.ival2 = scancode;
    ev.ival3 = action;
    ev.ival4 = mods;
    g_queue.push(ev);
}

void injectCharEvent(uint32_t codepoint) {
    InputEvent ev;
    ev.type = InputEventType::Char;
    ev.codepoint = codepoint;
    g_queue.push(ev);
}

void injectCharModsEvent(uint32_t codepoint, int mods) {
    InputEvent ev;
    ev.type = InputEventType::CharMods;
    ev.codepoint = codepoint;
    ev.ival4 = mods;
    g_queue.push(ev);
}

void injectMouseButton(int button, int action, int mods) {
    postMouseButton(button, action, mods);
}

void injectCursorPos(double x, double y) {
    InputEvent ev;
    ev.type = InputEventType::CursorPos;
    ev.dval1 = x;
    ev.dval2 = y;
    g_queue.push(ev);
}

void injectCursorDelta(double dx, double dy) {
    InputEvent ev;
    ev.type = InputEventType::CursorPosDelta;
    ev.dval1 = dx;
    ev.dval2 = dy;
    g_queue.push(ev);
}

void injectScroll(double xoffset, double yoffset) {
    InputEvent ev;
    ev.type = InputEventType::Scroll;
    ev.dval1 = xoffset;
    ev.dval2 = yoffset;
    g_queue.push(ev);
}

// Virtual joystick. stick: 0=left (movement), 1=right (look/camera).
// mode: 0=look (camera → cursor delta), 1=movement (WASD keys).
void injectJoystickAxis(int stick, double axisX, double axisY, double sensitivity, int mode) {
    // Apply deadzone.
    if (std::fabs(axisX) < kDeadzone) axisX = 0.0;
    if (std::fabs(axisY) < kDeadzone) axisY = 0.0;

    if (mode == 0 || stick == 1) {
        // Look / camera: emit relative cursor delta.
        if (axisX != 0.0 || axisY != 0.0) {
            double s = sensitivity > 0 ? sensitivity : 1.0;
            injectCursorDelta(axisX * s, axisY * s);
        }
        return;
    }

    // Movement stick → synthetic WASD key press/release with edge detection.
    struct AxisMap { double axis; int positiveKey; int negativeKey; bool positiveDown; };
    // axisX: right positive → D; left negative → A.
    // axisY: in most joystick conventions up is negative; forward = up = W.
    auto updateAxis = [](double axis, int posKey, int negKey,
                         std::unordered_map<int, bool>& down) {
        bool wantPos = axis > 0.0;
        bool wantNeg = axis < 0.0;
        bool& posDown = down[posKey];
        bool& negDown = down[negKey];
        if (wantPos && !posDown) { postKey(posKey, FLINT_GLFW_PRESS); posDown = true; }
        if (!wantPos && posDown) { postKey(posKey, FLINT_GLFW_RELEASE); posDown = false; }
        if (wantNeg && !negDown) { postKey(negKey, FLINT_GLFW_PRESS); negDown = true; }
        if (!wantNeg && negDown) { postKey(negKey, FLINT_GLFW_RELEASE); negDown = false; }
    };
    updateAxis(axisX, FLINT_GLFW_KEY_D, FLINT_GLFW_KEY_A, g_syntheticKeyDown);
    // axisY: forward (up, typically negative) → W; back (positive) → S.
    updateAxis(-axisY, FLINT_GLFW_KEY_W, FLINT_GLFW_KEY_S, g_syntheticKeyDown);
}

void setTouchKeyMapping(const char* action, int glfwKey) {
    if (!action) return;
    std::lock_guard<std::mutex> lock(g_inputMutex);
    g_touchKeyMap[action] = glfwKey;
}

int getTouchKeyMapping(const char* action) {
    if (!action) return -1;
    std::lock_guard<std::mutex> lock(g_inputMutex);
    auto it = g_touchKeyMap.find(action);
    return it != g_touchKeyMap.end() ? it->second : -1;
}

void injectTouchAction(const char* action, bool pressed) {
    if (!action) return;
    int code = getTouchKeyMapping(action);
    if (code < 0) {
        FLINT_LOGW("injectTouchAction: unknown action '%s'", action);
        return;
    }
    const int act = pressed ? FLINT_GLFW_PRESS : FLINT_GLFW_RELEASE;
    if (code < 8) {
        // Mouse button range (0..7).
        bool& down = g_syntheticMouseDown[code];
        if (pressed == down) return; // debounce
        down = pressed;
        postMouseButton(code, act);
    } else {
        bool& down = g_syntheticKeyDown[code];
        if (pressed == down) return;
        down = pressed;
        postKey(code, act);
    }
}

void setMouseCaptureState(int state) {
    std::lock_guard<std::mutex> lock(g_inputMutex);
    g_mouseCapture = state;
}

int getMouseCaptureState() {
    std::lock_guard<std::mutex> lock(g_inputMutex);
    return g_mouseCapture;
}

void resetInputState() {
    std::lock_guard<std::mutex> lock(g_inputMutex);
    g_queue.clear();
    g_mouseCapture = FLINT_GLFW_CURSOR_NORMAL;
    // Release any synthetic-held keys/buttons.
    for (auto& kv : g_syntheticKeyDown) {
        if (kv.second) postKey(kv.first, FLINT_GLFW_RELEASE);
    }
    for (auto& kv : g_syntheticMouseDown) {
        if (kv.second) postMouseButton(kv.first, FLINT_GLFW_RELEASE);
    }
    g_syntheticKeyDown.clear();
    g_syntheticMouseDown.clear();
}

bool gamepadIsPresent(int /*jid*/) {
    return false; // TODO(Spike 1): wire Android InputDevice gamepad events.
}

} // namespace flint

// ---------------------------------------------------------------------------
// JNI entry points (Kotlin: NativeBridge.nativeInject* / nativeInput*).
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeInjectKey(
    JNIEnv*, jclass, jint key, jint scancode, jint action, jint mods) {
    flint::injectKeyEvent(key, scancode, action, mods);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeInjectChar(
    JNIEnv*, jclass, jint codepoint) {
    flint::injectCharEvent((uint32_t)codepoint);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeInjectMouseButton(
    JNIEnv*, jclass, jint button, jint action, jint mods) {
    flint::injectMouseButton(button, action, mods);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeInjectCursorPos(
    JNIEnv*, jclass, jdouble x, jdouble y) {
    flint::injectCursorPos(x, y);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeInjectCursorDelta(
    JNIEnv*, jclass, jdouble dx, jdouble dy) {
    flint::injectCursorDelta(dx, dy);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeInjectScroll(
    JNIEnv*, jclass, jdouble xoffset, jdouble yoffset) {
    flint::injectScroll(xoffset, yoffset);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeInjectJoystickAxis(
    JNIEnv*, jclass, jint stick, jdouble axisX, jdouble axisY, jdouble sensitivity, jint mode) {
    flint::injectJoystickAxis(stick, axisX, axisY, sensitivity, mode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSetTouchKeyMapping(
    JNIEnv* env, jclass, jstring action, jint glfwKey) {
    flint::ScopedUtfChars s(env, action);
    flint::setTouchKeyMapping(s.get(), glfwKey);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGetTouchKeyMapping(
    JNIEnv* env, jclass, jstring action) {
    flint::ScopedUtfChars s(env, action);
    return flint::getTouchKeyMapping(s.get());
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeInjectTouchAction(
    JNIEnv* env, jclass, jstring action, jboolean pressed) {
    flint::ScopedUtfChars s(env, action);
    flint::injectTouchAction(s.get(), pressed == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSetMouseCapture(
    JNIEnv*, jclass, jint state) {
    flint::setMouseCaptureState(state);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGetMouseCapture(JNIEnv*, jclass) {
    return flint::getMouseCaptureState();
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeResetInput(JNIEnv*, jclass) {
    flint::resetInputState();
}
