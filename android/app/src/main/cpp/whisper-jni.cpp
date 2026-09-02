#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <cstdio>
#include <sys/stat.h>
#include <android/log.h>

#define TAG "MyAI-WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct WhisperModelContext {
    std::string model_path;
    bool is_valid;
};

static std::atomic<WhisperModelContext*> g_active_whisper{nullptr};

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
    if (!model_path_jstr) {
        LOGE("nativeLoadModel: null path");
        return 0L;
    }

    const char *model_path = env->GetStringUTFChars(model_path_jstr, nullptr);
    LOGI("Loading whisper model from: %s", model_path);

    struct stat st;
    if (stat(model_path, &st) != 0 || st.st_size <= 0) {
        LOGE("Whisper model file not found or empty: %s", model_path);
        env->ReleaseStringUTFChars(model_path_jstr, model_path);
        return 0L;
    }

    FILE *f = fopen(model_path, "rb");
    if (!f) {
        LOGE("Failed to open whisper model file: %s", model_path);
        env->ReleaseStringUTFChars(model_path_jstr, model_path);
        return 0L;
    }

    char header[4] = {0};
    size_t bytes_read = fread(header, 1, 4, f);
    fclose(f);

    if (bytes_read < 4) {
        LOGE("Whisper model file header is too small: %s", model_path);
        env->ReleaseStringUTFChars(model_path_jstr, model_path);
        return 0L;
    }

    bool valid_magic =
        (header[0] == 'g' && header[1] == 'g' && header[2] == 'm' && header[3] == 'l') ||
        (header[0] == 'g' && header[1] == 'g' && header[2] == 'm' && header[3] == 'f') ||
        (header[0] == 'g' && header[1] == 'g' && header[2] == 'j' && header[3] == 't');

    if (!valid_magic) {
        LOGE("Invalid Whisper model header magic: %s", model_path);
        env->ReleaseStringUTFChars(model_path_jstr, model_path);
        return 0L;
    }

    auto *ctx = new WhisperModelContext();
    ctx->model_path = std::string(model_path);
    ctx->is_valid = true;

    WhisperModelContext* old = g_active_whisper.exchange(ctx);
    if (old) {
        delete old;
    }

    env->ReleaseStringUTFChars(model_path_jstr, model_path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_myai_offline_voice_NativeWhisperBridge_nativeUnloadModel(
    JNIEnv *env,
    jobject /* this */,
    jlong model_handle) {
    LOGI("Unloading whisper model handle: %lld", (long long)model_handle);
    WhisperModelContext* ctx = reinterpret_cast<WhisperModelContext*>(model_handle);
    if (ctx && ctx == g_active_whisper.load()) {
        g_active_whisper.store(nullptr);
        delete ctx;
    } else if (ctx) {
        delete ctx;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_myai_offline_voice_NativeWhisperBridge_nativeIsModelLoaded(
    JNIEnv *env,
    jobject /* this */) {
    WhisperModelContext* ctx = g_active_whisper.load();
    return (ctx != nullptr && ctx->is_valid) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_myai_offline_voice_NativeWhisperBridge_nativeTranscribe(
    JNIEnv *env,
    jobject /* this */,
    jlong model_handle,
    jfloatArray pcm_data_jarr,
    jstring lang_jstr) {

    WhisperModelContext* ctx = reinterpret_cast<WhisperModelContext*>(model_handle);
    if (!ctx || !ctx->is_valid) {
        LOGE("nativeTranscribe: invalid whisper handle");
        return env->NewStringUTF("");
    }

    const char *lang = lang_jstr ? env->GetStringUTFChars(lang_jstr, nullptr) : "auto";
    jsize len = pcm_data_jarr ? env->GetArrayLength(pcm_data_jarr) : 0;
    LOGI("Whisper transcription requested for %d samples, language: %s", len, lang);

    if (lang_jstr) {
        env->ReleaseStringUTFChars(lang_jstr, lang);
    }

    LOGE("nativeTranscribe called, but real whisper.cpp transcription is not linked in this build.");

    return env->NewStringUTF("");
}

}
