/*
 * JNI entry points called from Kotlin (GlfwBridge) on Android's UI thread. They only publish the
 * surface and queue input; the game thread picks everything up in glfwPollEvents.
 */
#include <android/native_window_jni.h>
#include <jni.h>

#include "flint_glfw_android.h"
#include "flint_glfw_core.h"

#define BRIDGE(name) Java_com_flintlauncher_android_nativebridge_GlfwBridge_##name

JNIEXPORT void JNICALL BRIDGE(nativeSurfaceCreated)(JNIEnv *env, jclass cls, jobject surface, jint w, jint h) {
    (void)cls;
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (window) flint_android_surface_set(window, w, h);
}
JNIEXPORT void JNICALL BRIDGE(nativeSurfaceChanged)(JNIEnv *env, jclass cls, jint w, jint h) {
    (void)env; (void)cls;
    flint_android_surface_resize(w, h);
}
JNIEXPORT void JNICALL BRIDGE(nativeSurfaceDestroyed)(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    flint_android_surface_clear();
}
JNIEXPORT void JNICALL BRIDGE(nativeSendKey)(JNIEnv *env, jclass cls, jint key, jint scancode, jint action, jint mods) {
    (void)env; (void)cls;
    flint_push_key(key, scancode, action, mods);
}
JNIEXPORT void JNICALL BRIDGE(nativeSendChar)(JNIEnv *env, jclass cls, jint codepoint, jint mods) {
    (void)env; (void)cls;
    flint_push_char((unsigned int)codepoint, mods);
}
JNIEXPORT void JNICALL BRIDGE(nativeSendMouseButton)(JNIEnv *env, jclass cls, jint button, jint action, jint mods) {
    (void)env; (void)cls;
    flint_push_mouse_button(button, action, mods);
}
JNIEXPORT void JNICALL BRIDGE(nativeSendCursorPos)(JNIEnv *env, jclass cls, jdouble x, jdouble y) {
    (void)env; (void)cls;
    flint_push_cursor_pos(x, y);
}
JNIEXPORT void JNICALL BRIDGE(nativeSendCursorDelta)(JNIEnv *env, jclass cls, jdouble dx, jdouble dy) {
    (void)env; (void)cls;
    flint_push_cursor_delta(dx, dy);
}
JNIEXPORT void JNICALL BRIDGE(nativeSendScroll)(JNIEnv *env, jclass cls, jdouble dx, jdouble dy) {
    (void)env; (void)cls;
    flint_push_scroll(dx, dy);
}
JNIEXPORT void JNICALL BRIDGE(nativeSendFocus)(JNIEnv *env, jclass cls, jboolean focused) {
    (void)env; (void)cls;
    flint_push_focus(focused ? 1 : 0);
}
JNIEXPORT void JNICALL BRIDGE(nativeRequestClose)(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    flint_push_close();
}
JNIEXPORT jboolean JNICALL BRIDGE(nativeIsCursorGrabbed)(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return flint_cursor_grabbed() ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jlong JNICALL BRIDGE(nativeFrameCount)(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return (jlong)flint_android_frame_count();
}
