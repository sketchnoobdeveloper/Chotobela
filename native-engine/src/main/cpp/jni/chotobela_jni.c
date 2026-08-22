/*
 * Chotobela Engine - JNI bridge
 * Implements symbols for com.chotobela.engine.NativeEngine.
 */
#include "../../engine-host/engine_host.h"
#include "../renderer/renderer_backend.h"
#include "../audio/audio_backend.h"
#include "../input/input_state.h"

#include <jni.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "ChotobelaEngine"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

JNIEXPORT jboolean JNICALL
Java_com_chotobela_engine_NativeEngine_nativeInit(JNIEnv *env, jclass clazz, jstring savesDir) {
    (void)clazz;
    const char *dir = (*env)->GetStringUTFChars(env, savesDir, NULL);
    if (!dir) return JNI_FALSE;
    int rc = cb_host_init(dir);
    cb_input_reset();
    (*env)->ReleaseStringUTFChars(env, savesDir, dir);
    return rc == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_chotobela_engine_NativeEngine_nativeDeinit(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    cb_audio_stop();
    cb_host_deinit();
}

JNIEXPORT jboolean JNICALL
Java_com_chotobela_engine_NativeEngine_nativeLoadRom(
        JNIEnv *env, jclass clazz, jstring coreId, jstring romPath) {
    (void)clazz;
    const char *core = (*env)->GetStringUTFChars(env, coreId, NULL);
    const char *path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!core || !path) {
        if (core) (*env)->ReleaseStringUTFChars(env, coreId, core);
        if (path) (*env)->ReleaseStringUTFChars(env, romPath, path);
        return JNI_FALSE;
    }
    int rc = cb_load_rom(core, path);
    if (rc != 0) ALOGE("load_rom failed rc=%d core=%s", rc, core);
    else ALOGI("rom loaded: %s", path);
    (*env)->ReleaseStringUTFChars(env, coreId, core);
    (*env)->ReleaseStringUTFChars(env, romPath, path);
    return rc == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_chotobela_engine_NativeEngine_nativeReset(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    cb_host_reset();
}

JNIEXPORT jboolean JNICALL
Java_com_chotobela_engine_NativeEngine_nativeStepFrame(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return cb_step_frame() == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jintArray JNICALL
Java_com_chotobela_engine_NativeEngine_nativeGetVideoSize(JNIEnv *env, jclass clazz) {
    (void)clazz;
    int w = 0, h = 0;
    cb_video_buffer(&w, &h);
    jint dims[2] = { w, h };
    jintArray arr = (*env)->NewIntArray(env, 2);
    if (arr) (*env)->SetIntArrayRegion(env, arr, 0, 2, dims);
    return arr;
}

JNIEXPORT jobject JNICALL
Java_com_chotobela_engine_NativeEngine_nativeGetFramebuffer(JNIEnv *env, jclass clazz) {
    (void)clazz;
    int w = 0, h = 0;
    const uint32_t *fb = cb_video_buffer(&w, &h);
    if (!fb || w <= 0 || h <= 0) return NULL;
    return (*env)->NewDirectByteBuffer(env, (void *)fb, (jlong)(w * h * 4));
}

JNIEXPORT jfloat JNICALL
Java_com_chotobela_engine_NativeEngine_nativeGetAspect(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return cb_video_aspect();
}

JNIEXPORT jint JNICALL
Java_com_chotobela_engine_NativeEngine_nativeAudioStart(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return cb_audio_start();
}

JNIEXPORT void JNICALL
Java_com_chotobela_engine_NativeEngine_nativeAudioStop(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    cb_audio_stop();
}

JNIEXPORT void JNICALL
Java_com_chotobela_engine_NativeEngine_nativeSetVolume(JNIEnv *env, jclass clazz, jfloat vol) {
    (void)env; (void)clazz;
    cb_audio_set_volume(vol);
}

JNIEXPORT void JNICALL
Java_com_chotobela_engine_NativeEngine_nativeSetButtons(JNIEnv *env, jclass clazz, jint mask) {
    (void)env; (void)clazz;
    g_cb_input.buttons = (uint32_t)mask;
    cb_set_buttons((uint32_t)mask);
}

JNIEXPORT void JNICALL
Java_com_chotobela_engine_NativeEngine_nativeSetAxis(
        JNIEnv *env, jclass clazz, jfloat x, jfloat y) {
    (void)env; (void)clazz;
    g_cb_input.axis_x = x;
    g_cb_input.axis_y = y;
}

JNIEXPORT jboolean JNICALL
Java_com_chotobela_engine_NativeEngine_nativeSaveState(JNIEnv *env, jclass clazz, jint slot) {
    (void)env; (void)clazz;
    return cb_save_state(slot) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_chotobela_engine_NativeEngine_nativeLoadState(JNIEnv *env, jclass clazz, jint slot) {
    (void)env; (void)clazz;
    return cb_load_state(slot) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_chotobela_engine_NativeEngine_nativeSlotSize(JNIEnv *env, jclass clazz, jint slot) {
    (void)env; (void)clazz;
    return cb_state_slot_size(slot);
}

JNIEXPORT jlong JNICALL
Java_com_chotobela_engine_NativeEngine_nativeSlotTime(JNIEnv *env, jclass clazz, jint slot) {
    (void)env; (void)clazz;
    return cb_state_slot_mtime(slot);
}
