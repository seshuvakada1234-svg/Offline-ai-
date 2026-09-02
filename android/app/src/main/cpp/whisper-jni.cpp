#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cctype>
#include <cstdio>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_set>

#include <sys/stat.h>

#include "whisper.h"

#define TAG "MyAI-WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct WhisperModelContext {
    std::string model_path;
    whisper_context *context = nullptr;
    bool is_valid = false;
};

static std::atomic<WhisperModelContext *> g_active_whisper{nullptr};
static std::mutex g_whisper_contexts_mutex;
static std::unordered_set<WhisperModelContext *> g_whisper_contexts;

static void register_context(WhisperModelContext *ctx) {
    std::lock_guard<std::mutex> lock(g_whisper_contexts_mutex);
    g_whisper_contexts.insert(ctx);
}

static bool is_registered_context(WhisperModelContext *ctx) {
    std::lock_guard<std::mutex> lock(g_whisper_contexts_mutex);
    return g_whisper_contexts.find(ctx) != g_whisper_contexts.end();
}

static void release_context(WhisperModelContext *ctx) {
    if (!ctx) {
        return;
    }

    {
        std::lock_guard<std::mutex> lock(g_whisper_contexts_mutex);
        const auto it = g_whisper_contexts.find(ctx);
        if (it == g_whisper_contexts.end()) {
            return;
        }
        g_whisper_contexts.erase(it);
    }

    if (ctx->context) {
        whisper_free(ctx->context);
        ctx->context = nullptr;
    }

    ctx->is_valid = false;
    delete ctx;
}

static std::string trim_whitespace(std::string value) {
    const auto is_not_space = [](unsigned char ch) {
        return !std::isspace(ch);
    };

    const auto begin = std::find_if(value.begin(), value.end(), is_not_space);
    if (begin == value.end()) {
        return "";
    }

    const auto end = std::find_if(value.rbegin(), value.rend(), is_not_space).base();
    return std::string(begin, end);
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_myai_offline_voice_NativeWhisperBridge_nativeInit(
    JNIEnv * /* env */,
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
        LOGE("nativeLoadModel: null model path");
        return 0L;
    }

    const char *model_path_c = env->GetStringUTFChars(model_path_jstr, nullptr);
    if (!model_path_c) {
        LOGE("nativeLoadModel: failed to read model path string");
        return 0L;
    }

    LOGI("Loading whisper model from: %s", model_path_c);

    struct stat st;
    if (stat(model_path_c, &st) != 0 || st.st_size <= 0) {
        LOGE("Whisper model file not found or empty: %s", model_path_c);
        env->ReleaseStringUTFChars(model_path_jstr, model_path_c);
        return 0L;
    }

    FILE *f = fopen(model_path_c, "rb");
    if (!f) {
        LOGE("Failed to open whisper model file: %s", model_path_c);
        env->ReleaseStringUTFChars(model_path_jstr, model_path_c);
        return 0L;
    }

    char header[4] = {0};
    const size_t bytes_read = fread(header, 1, 4, f);
    fclose(f);

    if (bytes_read < 4) {
        LOGE("Whisper model header is too small: %s", model_path_c);
        env->ReleaseStringUTFChars(model_path_jstr, model_path_c);
        return 0L;
    }

    const bool valid_magic =
        (header[0] == 'l' && header[1] == 'm' && header[2] == 'g' && header[3] == 'g') ||
        (header[0] == 'f' && header[1] == 'm' && header[2] == 'g' && header[3] == 'g') ||
        (header[0] == 't' && header[1] == 'j' && header[2] == 'g' && header[3] == 'g');

    if (!valid_magic) {
        LOGE("Invalid Whisper model header magic: %c%c%c%c", header[0], header[1], header[2], header[3]);
        env->ReleaseStringUTFChars(model_path_jstr, model_path_c);
        return 0L;
    }

    whisper_context_params context_params = whisper_context_default_params();
    context_params.use_gpu = false;
    context_params.flash_attn = false;

    whisper_context *whisper_ctx = whisper_init_from_file_with_params(model_path_c, context_params);
    if (!whisper_ctx) {
        LOGE("whisper_init_from_file_with_params failed for %s", model_path_c);
        env->ReleaseStringUTFChars(model_path_jstr, model_path_c);
        return 0L;
    }

    auto *ctx = new WhisperModelContext();
    ctx->model_path = std::string(model_path_c);
    ctx->context = whisper_ctx;
    ctx->is_valid = true;
    register_context(ctx);

    WhisperModelContext *old_ctx = g_active_whisper.exchange(ctx);
    if (old_ctx && old_ctx != ctx) {
        release_context(old_ctx);
    }

    env->ReleaseStringUTFChars(model_path_jstr, model_path_c);
    LOGI("Whisper model loaded successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_myai_offline_voice_NativeWhisperBridge_nativeUnloadModel(
    JNIEnv * /* env */,
    jobject /* this */,
    jlong model_handle) {

    auto *ctx = reinterpret_cast<WhisperModelContext *>(model_handle);
    if (!ctx) {
        return;
    }

    WhisperModelContext *expected = ctx;
    g_active_whisper.compare_exchange_strong(expected, nullptr);

    LOGI("Unloading whisper model handle: %lld", static_cast<long long>(model_handle));
    release_context(ctx);
}

JNIEXPORT jboolean JNICALL
Java_com_myai_offline_voice_NativeWhisperBridge_nativeIsModelLoaded(
    JNIEnv * /* env */,
    jobject /* this */) {

    WhisperModelContext *ctx = g_active_whisper.load();
    if (!ctx || !is_registered_context(ctx)) {
        return JNI_FALSE;
    }

    return (ctx->is_valid && ctx->context != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_myai_offline_voice_NativeWhisperBridge_nativeTranscribe(
    JNIEnv *env,
    jobject /* this */,
    jlong model_handle,
    jfloatArray pcm_data_jarr,
    jstring lang_jstr) {

    auto *ctx = reinterpret_cast<WhisperModelContext *>(model_handle);
    if (!ctx || !is_registered_context(ctx) || !ctx->is_valid || !ctx->context) {
        LOGE("nativeTranscribe: invalid whisper handle");
        return env->NewStringUTF("");
    }

    if (!pcm_data_jarr) {
        LOGE("nativeTranscribe: pcm_data is null");
        return env->NewStringUTF("");
    }

    const jsize n_samples = env->GetArrayLength(pcm_data_jarr);
    if (n_samples <= 0) {
        return env->NewStringUTF("");
    }

    jboolean is_copy = JNI_FALSE;
    jfloat *pcm_data = env->GetFloatArrayElements(pcm_data_jarr, &is_copy);
    if (!pcm_data) {
        LOGE("nativeTranscribe: failed to access PCM data");
        return env->NewStringUTF("");
    }

    std::string language = "auto";
    if (lang_jstr) {
        const char *lang_c = env->GetStringUTFChars(lang_jstr, nullptr);
        if (lang_c) {
            language = lang_c;
            env->ReleaseStringUTFChars(lang_jstr, lang_c);
        }
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = std::max(1, std::min(8, static_cast<int>(std::thread::hardware_concurrency())));
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    params.no_timestamps = true;
    params.no_context = true;

    if (language.empty() || language == "auto") {
        params.language = nullptr;
        params.detect_language = true;
    } else {
        params.language = language.c_str();
        params.detect_language = false;
    }

    LOGI("Running whisper_full with %d samples, language=%s", n_samples, language.c_str());

    const int result = whisper_full(
        ctx->context,
        params,
        reinterpret_cast<const float *>(pcm_data),
        static_cast<int>(n_samples));

    env->ReleaseFloatArrayElements(pcm_data_jarr, pcm_data, JNI_ABORT);

    if (result != 0) {
        LOGE("whisper_full failed with code %d", result);
        return env->NewStringUTF("");
    }

    const int n_segments = whisper_full_n_segments(ctx->context);
    std::string transcript;
    for (int i = 0; i < n_segments; ++i) {
        const char *segment = whisper_full_get_segment_text(ctx->context, i);
        if (segment) {
            transcript += segment;
        }
    }

    transcript = trim_whitespace(transcript);
    LOGI("Whisper transcription complete: %d segments, %zu chars", n_segments, transcript.size());
    return env->NewStringUTF(transcript.c_str());
}

}
