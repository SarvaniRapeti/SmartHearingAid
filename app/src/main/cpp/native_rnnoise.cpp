#include <jni.h>
#include <android/log.h>
#include "rnnoise.h"

#define TAG "RNNoiseNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static DenoiseState* st = nullptr;

extern "C"
JNIEXPORT void JNICALL
Java_com_hearing_hearingtest_LiveAudioEngine_rnnoiseInit(
        JNIEnv*, jobject
) {
    if (!st) {
        st = rnnoise_create(nullptr);
        LOGD("RNNoise initialized (native)");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hearing_hearingtest_LiveAudioEngine_rnnoiseProcess(
        JNIEnv* env,
        jobject,
        jfloatArray frame
) {
    if (!st) {
        LOGE("RNNoise process called but state is NULL");
        return;
    }

    jfloat* data = env->GetFloatArrayElements(frame, nullptr);
    if (!data) {
        LOGE("Failed to get float array");
        return;
    }

    // ✅ webrtc-rnnoise API (in-place processing)
    rnnoise_process_frame(st, data, data);

    LOGD("RNNoise processed frame");

    env->ReleaseFloatArrayElements(frame, data, 0);
}
