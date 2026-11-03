#include <jni.h>

#include <stdarg.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>

#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>

#include <android/log.h>
#include <dobby.h>

#define LOG_TAG "rootdebug-native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/*
 * Baseline hook example: libc openat / close, observation only.
 * The hooks count calls and forward every call to the original function
 * unchanged. They run in whichever process loaded this library.
 */

static _Atomic long g_openat_count = 0;
static _Atomic long g_close_count = 0;
static int g_hooks_installed = 0;
static char g_hook_status[256] = "not installed";

static int (*orig_openat)(int dirfd, const char *path, int flags,
                          mode_t mode) = NULL;
static int (*orig_close)(int fd) = NULL;

static int replacement_openat(int dirfd, const char *path, int flags, ...) {
    mode_t mode = 0;
    if (flags & (O_CREAT | O_TMPFILE)) {
        va_list ap;
        va_start(ap, flags);
        mode = (mode_t) va_arg(ap, int);
        va_end(ap);
    }
    atomic_fetch_add_explicit(&g_openat_count, 1, memory_order_relaxed);
    return orig_openat(dirfd, path, flags, mode);
}

static int replacement_close(int fd) {
    atomic_fetch_add_explicit(&g_close_count, 1, memory_order_relaxed);
    return orig_close(fd);
}

/*
 * Symbol resolution: openat and close are exported by libc, which is
 * loaded in every process, so dlopen and dlsym return their addresses
 * directly.
 */

static void install_hooks_once(void) {
    if (g_hooks_installed) return;
    void *handle = dlopen("libc.so", RTLD_NOW | RTLD_NOLOAD);
    if (handle == NULL) {
        handle = dlopen("libc.so", RTLD_NOW);
    }
    if (handle == NULL) {
        snprintf(g_hook_status, sizeof(g_hook_status),
                 "dlopen libc.so failed: %s", dlerror());
        return;
    }
    void *openat_addr = dlsym(handle, "openat");
    void *close_addr = dlsym(handle, "close");
    if (openat_addr == NULL || close_addr == NULL) {
        snprintf(g_hook_status, sizeof(g_hook_status),
                 "symbol resolution failed (openat=%p close=%p)", openat_addr,
                 close_addr);
        return;
    }
    int r1 = DobbyHook(openat_addr, (dobby_dummy_func_t) replacement_openat,
                       (dobby_dummy_func_t *) &orig_openat);
    int r2 = DobbyHook(close_addr, (dobby_dummy_func_t) replacement_close,
                       (dobby_dummy_func_t *) &orig_close);
    if (r1 != 0 || r2 != 0) {
        snprintf(g_hook_status, sizeof(g_hook_status),
                 "DobbyHook failed (openat=%d close=%d)", r1, r2);
        return;
    }
    g_hooks_installed = 1;
    snprintf(g_hook_status, sizeof(g_hook_status), "installed");
    LOGI("baseline hooks: %s", g_hook_status);
}

/* ------------------------------------------------------------------ */
/* JNI surface                                                         */

static jstring native_diagnostic_ping(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return (*env)->NewStringUTF(env, "rootdebug-native-ok");
}

static jstring native_install_baseline_hooks(JNIEnv *env, jclass clazz) {
    (void) clazz;
    install_hooks_once();
    return (*env)->NewStringUTF(env, g_hook_status);
}

static jlongArray native_baseline_hook_counts(JNIEnv *env, jclass clazz) {
    (void) clazz;
    jlong values[2];
    values[0] =
        (jlong) atomic_load_explicit(&g_openat_count, memory_order_relaxed);
    values[1] =
        (jlong) atomic_load_explicit(&g_close_count, memory_order_relaxed);
    jlongArray array = (*env)->NewLongArray(env, 2);
    if (array != NULL) {
        (*env)->SetLongArrayRegion(env, array, 0, 2, values);
    }
    return array;
}

static const JNINativeMethod kMethods[] = {
    {"diagnosticPing", "()Ljava/lang/String;",
     (void *) native_diagnostic_ping},
    {"installBaselineHooks", "()Ljava/lang/String;",
     (void *) native_install_baseline_hooks},
    {"baselineHookCounts", "()[J", (void *) native_baseline_hook_counts},
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass clazz = (*env)->FindClass(env, "app/aoki/example/module/NativeBridge");
    if (clazz == NULL) {
        return JNI_ERR;
    }
    if ((*env)->RegisterNatives(env, clazz, kMethods,
                                (jint) (sizeof(kMethods) / sizeof(kMethods[0]))) != 0) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
