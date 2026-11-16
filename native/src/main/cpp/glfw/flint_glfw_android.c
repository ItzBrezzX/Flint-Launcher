/*
 * Android backend for the GLFW shim: EGL context + window surface on the app's Surface, and
 * GL4ES (libGL.so, next to this library) as the OpenGL provider.
 *
 * Threading: the surface callbacks come from the Android UI thread; every EGL call below runs on
 * the JVM render thread (glfwCreateWindow/MakeContextCurrent/SwapBuffers). The two sides only meet
 * through g_window/g_generation under g_lock. A destroyed surface is noticed on the next swap and
 * the EGL surface is rebuilt when Android hands us a new one, so pause/resume keeps the context.
 */
#include "flint_glfw_android.h"

#include <android/log.h>
#include <dlfcn.h>
#include <EGL/egl.h>
#include <limits.h>
#include <pthread.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include "flint_glfw_core.h"

#define TAG "FlintGLFW"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_cond = PTHREAD_COND_INITIALIZER;
static ANativeWindow *g_window = NULL;          /* one reference owned by the UI publisher */
static ANativeWindow *g_surface_window = NULL; /* one reference held while EGLSurface uses it */
static unsigned g_generation = 0;
static int g_width = 0, g_height = 0;

static EGLDisplay g_display = EGL_NO_DISPLAY;
static EGLConfig g_config = NULL;
static EGLContext g_context = EGL_NO_CONTEXT;
static EGLSurface g_surface = EGL_NO_SURFACE;
static unsigned g_bound_generation = 0;
static int g_want_current = 0;
static int g_gl4es_ready = 0;
static void *g_gl = NULL;
static unsigned long long g_frames = 0;

/* ------------------------------------------------------- surface from Java */

void flint_android_surface_set(ANativeWindow *window, int width, int height) {
    ANativeWindow *release_window = NULL;
    pthread_mutex_lock(&g_lock);
    if (g_window == window) {
        /* ANativeWindow_fromSurface returned an extra reference to the same object. */
        release_window = window;
    } else {
        release_window = g_window;
        g_window = window;
        g_generation++;
    }
    g_width = width;
    g_height = height;
    pthread_cond_broadcast(&g_cond);
    pthread_mutex_unlock(&g_lock);
    if (release_window) ANativeWindow_release(release_window);
    flint_push_size(width, height);
}

void flint_android_surface_resize(int width, int height) {
    pthread_mutex_lock(&g_lock);
    g_width = width;
    g_height = height;
    pthread_mutex_unlock(&g_lock);
    flint_push_size(width, height);
}

void flint_android_surface_clear(void) {
    pthread_mutex_lock(&g_lock);
    ANativeWindow *release_window = g_window;
    g_window = NULL;
    g_generation++;
    pthread_cond_broadcast(&g_cond);
    pthread_mutex_unlock(&g_lock);
    if (release_window) ANativeWindow_release(release_window);
}

/* -------------------------------------------------------------- GL4ES glue */

static void *gl4es_handle(void) {
    if (g_gl) return g_gl;
    Dl_info info;
    char path[PATH_MAX];
    /* libGL.so sits in the same directory as this library (the app's native library dir). */
    if (dladdr((void *)&flint_android_install_platform, &info) && info.dli_fname) {
        const char *slash = strrchr(info.dli_fname, '/');
        if (slash) {
            size_t dirlen = (size_t)(slash - info.dli_fname);
            if (dirlen + sizeof("/libGL.so") < sizeof path) {
                memcpy(path, info.dli_fname, dirlen);
                strcpy(path + dirlen, "/libGL.so");
                g_gl = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
                if (!g_gl) LOGE("dlopen %s failed: %s", path, dlerror());
            }
        }
    }
    if (!g_gl) g_gl = dlopen("libGL.so", RTLD_NOW | RTLD_GLOBAL);
    return g_gl;
}

static void init_gl4es_once(void) {
    if (g_gl4es_ready) return;
    void *gl = gl4es_handle();
    if (!gl) return;
    void (*initialize)(void) = (void (*)(void))dlsym(gl, "initialize_gl4es");
    if (!initialize) {
        LOGE("initialize_gl4es missing from libGL.so");
        return;
    }
    initialize();
    g_gl4es_ready = 1;
    LOGI("GL4ES initialised on the game's EGL context");
}

/* ---------------------------------------------------------------- EGL work */

static int choose_config(void) {
    static const EGLint rich[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24, EGL_STENCIL_SIZE, 8, EGL_NONE};
    static const EGLint basic[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
        EGL_DEPTH_SIZE, 16, EGL_STENCIL_SIZE, 8, EGL_NONE};
    static const EGLint minimal[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_DEPTH_SIZE, 16, EGL_NONE};
    const EGLint *attempts[] = {rich, basic, minimal};
    for (size_t i = 0; i < 3; i++) {
        EGLint count = 0;
        if (eglChooseConfig(g_display, attempts[i], &g_config, 1, &count) && count > 0) return 1;
    }
    LOGE("No usable EGL config (eglGetError=0x%x)", eglGetError());
    return 0;
}

static int platform_create_context(void) {
    if (g_context != EGL_NO_CONTEXT) return 1;
    g_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g_display == EGL_NO_DISPLAY || !eglInitialize(g_display, NULL, NULL)) {
        LOGE("eglInitialize failed (0x%x)", eglGetError());
        return 0;
    }
    if (!choose_config()) return 0;
    eglBindAPI(EGL_OPENGL_ES_API);
    const EGLint attribs[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    g_context = eglCreateContext(g_display, g_config, EGL_NO_CONTEXT, attribs);
    if (g_context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed (0x%x)", eglGetError());
        return 0;
    }
    return 1;
}

/* Brings the EGL surface in line with the latest Android window. Render thread only. */
static int ensure_surface(void) {
    /*
     * Hold a temporary native-window reference while using the snapshot. The UI
     * thread may clear/replace g_window immediately after releasing g_lock.
     */
    pthread_mutex_lock(&g_lock);
    ANativeWindow *window = g_window;
    if (window) ANativeWindow_acquire(window);
    unsigned generation = g_generation;
    pthread_mutex_unlock(&g_lock);

    if (g_surface != EGL_NO_SURFACE && generation == g_bound_generation) {
        if (window) ANativeWindow_release(window);
        return 1;
    }

    if (g_surface != EGL_NO_SURFACE) {
        eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(g_display, g_surface);
        g_surface = EGL_NO_SURFACE;
    }
    if (g_surface_window) {
        ANativeWindow_release(g_surface_window);
        g_surface_window = NULL;
    }

    g_bound_generation = generation;
    if (!window) return 0;

    EGLint format = 0;
    if (eglGetConfigAttrib(g_display, g_config, EGL_NATIVE_VISUAL_ID, &format) && format != 0) {
        if (ANativeWindow_setBuffersGeometry(window, 0, 0, format) != 0) {
            LOGE("ANativeWindow_setBuffersGeometry failed for EGL visual %d", format);
            ANativeWindow_release(window);
            return 0;
        }
    }

    g_surface = eglCreateWindowSurface(g_display, g_config, window, NULL);
    if (g_surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed (0x%x)", eglGetError());
        ANativeWindow_release(window);
        return 0;
    }

    /* Transfer the acquired snapshot reference to the EGL-surface lifetime. */
    g_surface_window = window;
    if (g_want_current && !eglMakeCurrent(g_display, g_surface, g_surface, g_context)) {
        LOGE("eglMakeCurrent after surface change failed (0x%x)", eglGetError());
        eglDestroySurface(g_display, g_surface);
        g_surface = EGL_NO_SURFACE;
        ANativeWindow_release(g_surface_window);
        g_surface_window = NULL;
        g_bound_generation = (unsigned)~0u;
        return 0;
    }
    return 1;
}
static int platform_wait_for_surface(int timeout_ms) {
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_sec += timeout_ms / 1000;
    deadline.tv_nsec += (long)(timeout_ms % 1000) * 1000000L;
    if (deadline.tv_nsec >= 1000000000L) {
        deadline.tv_sec++;
        deadline.tv_nsec -= 1000000000L;
    }
    pthread_mutex_lock(&g_lock);
    int ok = 1;
    while (!g_window) {
        if (pthread_cond_timedwait(&g_cond, &g_lock, &deadline) != 0) {
            ok = g_window != NULL;
            break;
        }
    }
    pthread_mutex_unlock(&g_lock);
    return ok;
}

static int platform_get_surface_size(int *width, int *height) {
    pthread_mutex_lock(&g_lock);
    int ok = g_window != NULL && g_width > 0 && g_height > 0;
    if (ok) {
        *width = g_width;
        *height = g_height;
    }
    pthread_mutex_unlock(&g_lock);
    return ok;
}

static int platform_make_current(int make) {
    if (!make) {
        g_want_current = 0;
        if (g_display != EGL_NO_DISPLAY) eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        return 1;
    }
    if (g_context == EGL_NO_CONTEXT) return 0;
    g_want_current = 1;
    if (!platform_wait_for_surface(10000)) {
        LOGE("make_current: no surface after 10 s");
        return 0;
    }
    if (!ensure_surface()) return 0;
    if (!eglMakeCurrent(g_display, g_surface, g_surface, g_context)) {
        LOGE("eglMakeCurrent failed (0x%x)", eglGetError());
        return 0;
    }
    init_gl4es_once();
    return 1;
}

static int platform_swap_buffers(void) {
    if (!ensure_surface()) {
        usleep(30 * 1000); /* app is in the background: do not spin the game loop */
        return 0;
    }
    if (eglSwapBuffers(g_display, g_surface)) {
        __atomic_add_fetch(&g_frames, 1ULL, __ATOMIC_RELAXED);
    } else {
        EGLint err = eglGetError();
        if (err == EGL_BAD_SURFACE || err == EGL_BAD_NATIVE_WINDOW) {
            g_bound_generation = (unsigned)~0u; /* force a rebuild on the next call */
        } else if (err == EGL_CONTEXT_LOST) {
            LOGE("EGL context lost; the game must be restarted");
        }
        return 0;
    }
    return 1;
}

unsigned long long flint_android_frame_count(void) {
    return __atomic_load_n(&g_frames, __ATOMIC_RELAXED);
}

static void platform_swap_interval(int interval) {
    if (g_display != EGL_NO_DISPLAY) eglSwapInterval(g_display, interval);
}

static void *platform_get_proc_address(const char *name) {
    void *gl = gl4es_handle();
    void *address = gl ? dlsym(gl, name) : NULL;
    if (!address) address = (void *)eglGetProcAddress(name);
    return address;
}

static void platform_destroy_context(void) {
    g_want_current = 0;
    if (g_display == EGL_NO_DISPLAY) return;
    eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (g_surface != EGL_NO_SURFACE) eglDestroySurface(g_display, g_surface);
    g_surface = EGL_NO_SURFACE;
    if (g_surface_window) {
        ANativeWindow_release(g_surface_window);
        g_surface_window = NULL;
    }
    if (g_context != EGL_NO_CONTEXT) eglDestroyContext(g_display, g_context);
    g_context = EGL_NO_CONTEXT;
    g_gl4es_ready = 0;
}

static const FlintPlatform g_platform = {
    platform_wait_for_surface, platform_get_surface_size, platform_create_context,
    platform_make_current,     platform_swap_buffers,     platform_swap_interval,
    platform_get_proc_address, platform_destroy_context,
};

void flint_android_install_platform(void) { flint_set_platform(&g_platform); }

/*
 * Runs when libglfw.so is loaded, by ART (System.loadLibrary) or by LWJGL's dlopen inside the
 * JVM. Both resolve to the same library instance, so the surface the UI thread publishes is the
 * one the game thread waits on.
 */
__attribute__((constructor)) static void flint_glfw_load(void) { flint_android_install_platform(); }
