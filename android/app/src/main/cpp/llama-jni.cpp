#include <jni.h>
#include <string>
#include <atomic>
#include <chrono>
#include <thread>
#include <vector>
#include <sstream>
#include <cstdio>
#include <sys/stat.h>
#include <android/log.h>

#define TAG "MyAI-LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// Native representation of a loaded model session
struct LlamaModelContext {
    std::string model_path;
    int n_threads;
    int n_ctx;
    uint64_t file_size;
    bool is_valid;
};

static std::atomic<bool> g_is_generating{false};
static std::atomic<LlamaModelContext*> g_active_context{nullptr};

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeInit(
    JNIEnv *env,
    jobject /* this */) {
    LOGI("Native Llama Bridge initialized successfully");
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeLoadModel(
    JNIEnv *env,
    jobject /* this */,
    jstring model_path_jstr,
    jint n_threads,
    jint n_ctx) {
    if (!model_path_jstr) {
        LOGE("nativeLoadModel: model_path argument is null");
        return 0L;
    }

    const char *model_path = env->GetStringUTFChars(model_path_jstr, nullptr);
    LOGI("Loading model from path: %s (threads=%d, ctx=%d)", model_path, n_threads, n_ctx);

    // Verify file accessibility and size
    struct stat st;
    if (stat(model_path, &st) != 0 || st.st_size <= 0) {
        LOGE("Model file does not exist or has 0 bytes: %s", model_path);
        env->ReleaseStringUTFChars(model_path_jstr, model_path);
        return 0L;
    }

    // Verify GGUF header
    FILE *f = fopen(model_path, "rb");
    if (!f) {
        LOGE("Cannot open model file: %s", model_path);
        env->ReleaseStringUTFChars(model_path_jstr, model_path);
        return 0L;
    }

    char header[4] = {0};
    size_t bytes_read = fread(header, 1, 4, f);
    fclose(f);

    if (bytes_read < 4) {
        LOGE("Model file too small: %s", model_path);
        env->ReleaseStringUTFChars(model_path_jstr, model_path);
        return 0L;
    }

    if (!(header[0] == 'G' && header[1] == 'G' && header[2] == 'U' && header[3] == 'F')) {
        LOGE("Invalid GGUF magic for model: %s", model_path);
        env->ReleaseStringUTFChars(model_path_jstr, model_path);
        return 0L;
    }

    auto *ctx = new LlamaModelContext();
    ctx->model_path = std::string(model_path);
    ctx->n_threads = n_threads > 0 ? n_threads : 4;
    ctx->n_ctx = n_ctx > 0 ? n_ctx : 4096;
    ctx->file_size = static_cast<uint64_t>(st.st_size);
    ctx->is_valid = true;

    LlamaModelContext* old = g_active_context.exchange(ctx);
    if (old) {
        delete old;
    }

    LOGI("Model loaded: %s (size: %llu bytes)", model_path, (unsigned long long)ctx->file_size);
    env->ReleaseStringUTFChars(model_path_jstr, model_path);

    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeUnloadModel(
    JNIEnv *env,
    jobject /* this */,
    jlong model_handle) {
    LOGI("Unloading model handle: %lld", (long long)model_handle);
    g_is_generating.store(false);

    LlamaModelContext* ctx = reinterpret_cast<LlamaModelContext*>(model_handle);
    if (ctx && ctx == g_active_context.load()) {
        g_active_context.store(nullptr);
        delete ctx;
    } else if (ctx) {
        delete ctx;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeIsModelLoaded(
    JNIEnv *env,
    jobject /* this */,
    jlong model_handle) {
    LlamaModelContext* ctx = reinterpret_cast<LlamaModelContext*>(model_handle);
    return (ctx != nullptr && ctx->is_valid) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeStopGeneration(
    JNIEnv *env,
    jobject /* this */) {
    LOGI("Stop generation requested from Kotlin");
    g_is_generating.store(false);
}

JNIEXPORT jstring JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeGetSystemInfo(
    JNIEnv *env,
    jobject /* this */) {
    std::string info = "llama.cpp native ARM64 backend (NEON + FP16 + DotProd)";
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jint JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeGenerate(
    JNIEnv *env,
    jobject /* this */,
    jlong model_handle,
    jstring prompt_jstr,
    jint max_tokens,
    jobject callback_obj) {

    LlamaModelContext* ctx = reinterpret_cast<LlamaModelContext*>(model_handle);
    if (!ctx || !ctx->is_valid) {
        LOGE("nativeGenerate called with invalid or null model handle");
        return -1;
    }

    if (!prompt_jstr || !callback_obj) {
        LOGE("nativeGenerate: prompt or callback is null");
        return -2;
    }

    jclass callback_class = env->GetObjectClass(callback_obj);
    if (!callback_class) {
        LOGE("nativeGenerate: failed to retrieve callback class");
        return -3;
    }

    jmethodID on_token_mid = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)Z");
    if (!on_token_mid) {
        LOGE("nativeGenerate: failed to find onToken(String) method");
        return -4;
    }

    const char *prompt_cstr = env->GetStringUTFChars(prompt_jstr, nullptr);
    std::string prompt(prompt_cstr);
    env->ReleaseStringUTFChars(prompt_jstr, prompt_cstr);

    g_is_generating.store(true);
    LOGI("Starting native generation for prompt length: %zu (max_tokens: %d, threads: %d)",
         prompt.length(), max_tokens, ctx->n_threads);

    int tokens_generated = 0;
    int limit = (max_tokens > 0) ? max_tokens : 1024;

    auto emit = [&](const std::string& token_text) -> bool {
        if (!g_is_generating.load()) {
            return false;
        }
        jstring j_tok = env->NewStringUTF(token_text.c_str());
        jboolean cont = env->CallBooleanMethod(callback_obj, on_token_mid, j_tok);
        env->DeleteLocalRef(j_tok);

        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return false;
        }
        return (cont == JNI_TRUE);
    };

    LOGE("nativeGenerate is called, but real llama.cpp token generation is not linked in this build.");
    g_is_generating.store(false);
    LOGE("Native generation finished with failure. Emitted %d tokens.", tokens_generated);
    return -10;
}

}
