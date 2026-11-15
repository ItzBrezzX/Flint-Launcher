// flint_common.h
// Shared helpers for Flint's clean-room native bridge.
// All code in this file is original to Flint (permissive licence).
#pragma once

#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <cstddef>

#define FLINT_LOG_TAG "FlintNative"
#define FLINT_LOGI(...) __android_log_print(ANDROID_LOG_INFO, FLINT_LOG_TAG, __VA_ARGS__)
#define FLINT_LOGW(...) __android_log_print(ANDROID_LOG_WARN, FLINT_LOG_TAG, __VA_ARGS__)
#define FLINT_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, FLINT_LOG_TAG, __VA_ARGS__)
#define FLINT_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, FLINT_LOG_TAG, __VA_ARGS__)

namespace flint {

// Scoped JNI string -> UTF-8. Releases on scope exit.
class ScopedUtfChars {
public:
    ScopedUtfChars(JNIEnv* env, jstring str) : env_(env), str_(str) {
        if (str_) {
            chars_ = env->GetStringUTFChars(str_, nullptr);
        }
    }
    ~ScopedUtfChars() {
        if (str_ && chars_) env_->ReleaseStringUTFChars(str_, chars_);
    }
    const char* get() const { return chars_; }
    ScopedUtfChars(const ScopedUtfChars&) = delete;
    ScopedUtfChars& operator=(const ScopedUtfChars&) = delete;
private:
    JNIEnv* env_;
    jstring str_;
    const char* chars_ = nullptr;
};

// Returns true if the JNI environment has a pending exception, clears it, and logs it.
inline bool checkAndClearException(JNIEnv* env, const char* where) {
    if (env->ExceptionCheck()) {
        FLINT_LOGE("JNI exception pending in %s", where);
        env->ExceptionDescribe();
        env->ExceptionClear();
        return true;
    }
    return false;
}

// Monotonic clock in seconds (matches glfwGetTime semantics after init).
double monotonicSeconds();

} // namespace flint
