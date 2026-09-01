#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <android/log.h>

#define TAG "MyAI-WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::atomic<bool> g_is_whisper_loaded{false};
static std::string g_whisper_model_path;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_myai_offline_voice_NativeWhisperBridge_nativeInit(
    JNIEnv *env,
    jobject /* this */) {
    LOGI("Native Whisper Bridge initialized");
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_myai_offline_voice_NativeWhisperBridge_nativeLoadModel(
    JNIEnv *env,
    jobject /* this */,
    jstring model_path_jstr) {
    const char *model_path = env->GetStringUTFChars(model_path_jstr, nullptr);
    LOGI("Loading whisper model from: %s", model_path);

    g_whisper_model_path = model_path;
    g_is_whisper_loaded.store(true);

    env->ReleaseStringUTFChars(model_path_jstr, model_path);
    return 0xDEADBEEF;
}

JNIEXPORT void JNICALL
Java_com_myai_offline_voice_NativeWhisperBridge_nativeUnloadModel(
    JNIEnv *env,
    jobject /* this */,
    jlong model_handle) {
    LOGI("Unloading whisper model handle: %ld", (long)model_handle);
    g_is_whisper_loaded.store(false);
    g_whisper_model_path.clear();
}

JNIEXPORT jboolean JNICALL
Java_com_myai_offline_voice_NativeWhisperBridge_nativeIsModelLoaded(
    JNIEnv *env,
    jobject /* this */) {
    return g_is_whisper_loaded.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_myai_offline_voice_NativeWhisperBridge_nativeTranscribe(
    JNIEnv *env,
    jobject /* this */,
    jlong model_handle,
    jfloatArray pcm_data_jarr,
    jstring lang_jstr) {
    
    const char *lang = env->GetStringUTFChars(lang_jstr, nullptr);
    jsize len = env->GetArrayLength(pcm_data_jarr);
    LOGI("Whisper transcription requested for %d samples, language: %s", len, lang);

    env->ReleaseStringUTFChars(lang_jstr, lang);
    return env->NewStringUTF("");
}

}
