#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_set>
#include <vector>

#include "llama.h"

#define TAG "MyAI-LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct LlamaModelContext {
    std::string model_path;
    int n_threads = 0;
    int n_ctx = 0;
    llama_model *model = nullptr;
    const llama_vocab *vocab = nullptr;
    llama_context *context = nullptr;
    bool is_valid = false;
};

static std::atomic<bool> g_is_generating{false};
static std::atomic<LlamaModelContext *> g_active_context{nullptr};
static std::once_flag g_backend_init_once;
static std::mutex g_contexts_mutex;
static std::unordered_set<LlamaModelContext *> g_contexts;

static void ensure_backend_initialized() {
    std::call_once(g_backend_init_once, []() {
        llama_backend_init();
        LOGI("llama backend initialized");
    });
}

static void register_context(LlamaModelContext *ctx) {
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    g_contexts.insert(ctx);
}

static bool is_context_registered(LlamaModelContext *ctx) {
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    return g_contexts.find(ctx) != g_contexts.end();
}

static void release_context(LlamaModelContext *ctx) {
    if (!ctx) {
        return;
    }

    {
        std::lock_guard<std::mutex> lock(g_contexts_mutex);
        const auto it = g_contexts.find(ctx);
        if (it == g_contexts.end()) {
            return;
        }
        g_contexts.erase(it);
    }

    if (ctx->context) {
        llama_free(ctx->context);
        ctx->context = nullptr;
    }
    if (ctx->model) {
        llama_model_free(ctx->model);
        ctx->model = nullptr;
        ctx->vocab = nullptr;
    }
    ctx->is_valid = false;
    delete ctx;
}

static bool tokenize_prompt(
    const llama_vocab *vocab,
    const std::string &prompt,
    std::vector<llama_token> &tokens_out) {

    int32_t capacity = std::max<int32_t>(64, static_cast<int32_t>(prompt.size()) + 8);
    tokens_out.resize(capacity);

    int32_t n_tokens = llama_tokenize(
        vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.size()),
        tokens_out.data(),
        static_cast<int32_t>(tokens_out.size()),
        true,
        true);

    if (n_tokens < 0) {
        tokens_out.resize(static_cast<size_t>(-n_tokens));
        n_tokens = llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            tokens_out.data(),
            static_cast<int32_t>(tokens_out.size()),
            true,
            true);
    }

    if (n_tokens <= 0) {
        return false;
    }

    tokens_out.resize(static_cast<size_t>(n_tokens));
    return true;
}

static bool detokenize_tokens(
    const llama_vocab *vocab,
    const std::vector<llama_token> &tokens,
    std::string &text_out) {

    text_out.clear();
    if (tokens.empty()) {
        return true;
    }

    int32_t capacity = std::max<int32_t>(128, static_cast<int32_t>(tokens.size()) * 8);
    std::vector<char> buffer(static_cast<size_t>(capacity));

    int32_t n_chars = llama_detokenize(
        vocab,
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        buffer.data(),
        static_cast<int32_t>(buffer.size()),
        false,
        false);

    if (n_chars < 0) {
        buffer.resize(static_cast<size_t>(-n_chars));
        n_chars = llama_detokenize(
            vocab,
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            buffer.data(),
            static_cast<int32_t>(buffer.size()),
            false,
            false);
    }

    if (n_chars < 0) {
        return false;
    }

    text_out.assign(buffer.data(), static_cast<size_t>(n_chars));
    return true;
}

static std::string token_to_piece(const llama_vocab *vocab, llama_token token) {
    std::vector<char> piece(64);
    int32_t n_chars = llama_token_to_piece(
        vocab,
        token,
        piece.data(),
        static_cast<int32_t>(piece.size()),
        0,
        false);

    if (n_chars < 0) {
        piece.resize(static_cast<size_t>(-n_chars));
        n_chars = llama_token_to_piece(
            vocab,
            token,
            piece.data(),
            static_cast<int32_t>(piece.size()),
            0,
            false);
    }

    if (n_chars <= 0) {
        return "";
    }

    return std::string(piece.data(), static_cast<size_t>(n_chars));
}

static bool emit_token(JNIEnv *env, jobject callback_obj, jmethodID on_token_mid, const std::string &token_text) {
    jstring j_tok = env->NewStringUTF(token_text.c_str());
    if (!j_tok) {
        LOGE("Failed to allocate jstring while streaming token");
        env->ExceptionClear();
        return false;
    }

    const jboolean keep_generating = env->CallBooleanMethod(callback_obj, on_token_mid, j_tok);
    env->DeleteLocalRef(j_tok);

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("Exception in Kotlin token callback");
        return false;
    }

    return keep_generating == JNI_TRUE;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeInit(
    JNIEnv * /* env */,
    jobject /* this */) {
    ensure_backend_initialized();
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

    ensure_backend_initialized();

    const char *model_path_c = env->GetStringUTFChars(model_path_jstr, nullptr);
    if (!model_path_c) {
        LOGE("nativeLoadModel: failed to read model path");
        return 0L;
    }

    const int resolved_threads = n_threads > 0
        ? n_threads
        : std::max(1, static_cast<int>(std::thread::hardware_concurrency()));
    const int resolved_ctx = n_ctx > 0 ? n_ctx : 4096;

    LOGI("Loading llama model from %s (threads=%d, ctx=%d)", model_path_c, resolved_threads, resolved_ctx);

    llama_model_params model_params = llama_model_default_params();

    llama_model *model = llama_model_load_from_file(model_path_c, model_params);
    if (!model) {
        LOGE("Failed to load llama model from: %s", model_path_c);
        env->ReleaseStringUTFChars(model_path_jstr, model_path_c);
        return 0L;
    }

    const llama_vocab *vocab = llama_model_get_vocab(model);
    if (!vocab) {
        LOGE("Failed to load vocab from model: %s", model_path_c);
        llama_model_free(model);
        env->ReleaseStringUTFChars(model_path_jstr, model_path_c);
        return 0L;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(resolved_ctx);
    ctx_params.n_batch = std::min<uint32_t>(ctx_params.n_ctx, 512);
    ctx_params.n_threads = resolved_threads;
    ctx_params.n_threads_batch = resolved_threads;

    llama_context *llama_ctx = llama_init_from_model(model, ctx_params);
    if (!llama_ctx) {
        LOGE("Failed to create llama context for model: %s", model_path_c);
        llama_model_free(model);
        env->ReleaseStringUTFChars(model_path_jstr, model_path_c);
        return 0L;
    }

    llama_set_n_threads(llama_ctx, resolved_threads, resolved_threads);

    auto *ctx = new LlamaModelContext();
    ctx->model_path = std::string(model_path_c);
    ctx->n_threads = resolved_threads;
    ctx->n_ctx = resolved_ctx;
    ctx->model = model;
    ctx->vocab = vocab;
    ctx->context = llama_ctx;
    ctx->is_valid = true;
    register_context(ctx);

    LlamaModelContext *old_ctx = g_active_context.exchange(ctx);
    if (old_ctx && old_ctx != ctx) {
        release_context(old_ctx);
    }

    env->ReleaseStringUTFChars(model_path_jstr, model_path_c);
    LOGI("Llama model loaded successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeUnloadModel(
    JNIEnv * /* env */,
    jobject /* this */,
    jlong model_handle) {

    g_is_generating.store(false);

    auto *ctx = reinterpret_cast<LlamaModelContext *>(model_handle);
    if (!ctx) {
        return;
    }

    LlamaModelContext *expected = ctx;
    g_active_context.compare_exchange_strong(expected, nullptr);

    LOGI("Unloading llama model handle: %lld", static_cast<long long>(model_handle));
    release_context(ctx);
}

JNIEXPORT jboolean JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeIsModelLoaded(
    JNIEnv * /* env */,
    jobject /* this */,
    jlong model_handle) {

    auto *ctx = reinterpret_cast<LlamaModelContext *>(model_handle);
    if (!ctx || !is_context_registered(ctx)) {
        return JNI_FALSE;
    }

    return (ctx->is_valid && ctx->model != nullptr && ctx->vocab != nullptr && ctx->context != nullptr)
        ? JNI_TRUE
        : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeStopGeneration(
    JNIEnv * /* env */,
    jobject /* this */) {
    g_is_generating.store(false);
}

JNIEXPORT jstring JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeGetSystemInfo(
    JNIEnv *env,
    jobject /* this */) {

    ensure_backend_initialized();
    const char *sys_info = llama_print_system_info();
    const std::string info = sys_info ? std::string(sys_info) : std::string("llama.cpp system info unavailable");
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

    auto *ctx = reinterpret_cast<LlamaModelContext *>(model_handle);
    if (!ctx || !is_context_registered(ctx) || !ctx->is_valid || !ctx->model || !ctx->vocab || !ctx->context) {
        LOGE("nativeGenerate called with invalid model handle");
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
    env->DeleteLocalRef(callback_class);
    if (!on_token_mid) {
        LOGE("nativeGenerate: failed to find onToken(String) method");
        return -4;
    }

    const char *prompt_cstr = env->GetStringUTFChars(prompt_jstr, nullptr);
    if (!prompt_cstr) {
        LOGE("nativeGenerate: failed to read prompt string");
        return -5;
    }

    std::string prompt(prompt_cstr);
    env->ReleaseStringUTFChars(prompt_jstr, prompt_cstr);

    std::vector<llama_token> prompt_tokens;
    if (!tokenize_prompt(ctx->vocab, prompt, prompt_tokens)) {
        LOGE("nativeGenerate: prompt tokenization failed");
        return -6;
    }

    llama_set_n_threads(ctx->context, ctx->n_threads, ctx->n_threads);
    llama_memory_t memory = llama_get_memory(ctx->context);
    if (memory) {
        llama_memory_clear(memory, false);
    }

    g_is_generating.store(true);

    const int token_limit = max_tokens > 0 ? max_tokens : 256;
    const int32_t batch_size = std::max<int32_t>(1, llama_n_batch(ctx->context));

    int32_t n_consumed = 0;
    while (n_consumed < static_cast<int32_t>(prompt_tokens.size())) {
        const int32_t n_eval = std::min<int32_t>(
            batch_size,
            static_cast<int32_t>(prompt_tokens.size()) - n_consumed);

        llama_batch batch = llama_batch_get_one(prompt_tokens.data() + n_consumed, n_eval);
        const int decode_status = llama_decode(ctx->context, batch);
        if (decode_status != 0) {
            LOGE("nativeGenerate: prompt decode failed (status=%d)", decode_status);
            g_is_generating.store(false);
            return -7;
        }
        n_consumed += n_eval;
    }

    llama_sampler *sampler = llama_sampler_init_greedy();
    if (!sampler) {
        LOGE("nativeGenerate: failed to initialize sampler");
        g_is_generating.store(false);
        return -8;
    }

    int tokens_generated = 0;
    int hard_failure = 0;
    std::vector<llama_token> generated_tokens;
    generated_tokens.reserve(static_cast<size_t>(token_limit));
    std::string emitted_text;

    for (int i = 0; i < token_limit && g_is_generating.load(); ++i) {
        const llama_token next_token = llama_sampler_sample(sampler, ctx->context, -1);
        llama_sampler_accept(sampler, next_token);

        if (llama_vocab_is_eog(ctx->vocab, next_token)) {
            break;
        }

        std::string piece = token_to_piece(ctx->vocab, next_token);
        if (piece == "<end_of_turn>" || piece == "<|im_end|>" || piece == "<|end|>" || piece == "<start_of_turn>") {
            break;
        }

        generated_tokens.push_back(next_token);
        tokens_generated++;

        std::string full_text;
        if (!detokenize_tokens(ctx->vocab, generated_tokens, full_text)) {
            full_text = emitted_text + piece;
        }

        bool stop_found = false;
        const std::string stop_markers[] = {"<end_of_turn>", "<|im_end|>", "<|end|>", "<start_of_turn>"};
        for (const auto &stop : stop_markers) {
            size_t pos = full_text.find(stop);
            if (pos != std::string::npos) {
                full_text = full_text.substr(0, pos);
                stop_found = true;
                break;
            }
        }

        std::string delta;
        if (full_text.size() >= emitted_text.size() &&
            full_text.compare(0, emitted_text.size(), emitted_text) == 0) {
            delta = full_text.substr(emitted_text.size());
        } else if (!stop_found) {
            delta = full_text;
        }

        emitted_text = full_text;

        if (!delta.empty()) {
            if (!emit_token(env, callback_obj, on_token_mid, delta)) {
                g_is_generating.store(false);
                break;
            }
        }

        if (stop_found || i + 1 >= token_limit || !g_is_generating.load()) {
            break;
        }

        llama_token eval_token = next_token;
        llama_batch next_batch = llama_batch_get_one(&eval_token, 1);
        const int decode_status = llama_decode(ctx->context, next_batch);
        if (decode_status != 0) {
            LOGE("nativeGenerate: decode failed during generation (status=%d)", decode_status);
            if (tokens_generated == 0) {
                hard_failure = -9;
            }
            break;
        }
    }

    llama_sampler_free(sampler);
    g_is_generating.store(false);

    if (tokens_generated == 0 && hard_failure != 0) {
        return hard_failure;
    }

    return tokens_generated;
}

}
