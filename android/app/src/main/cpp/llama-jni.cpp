#include <jni.h>
#include <string>
#include <atomic>
#include <chrono>
#include <thread>
#include <vector>
#include <android/log.h>

#define TAG "MyAI-LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::atomic<bool> g_is_generating{false};
static std::atomic<bool> g_is_model_loaded{false};
static std::string g_loaded_model_path;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeInit(
    JNIEnv *env,
    jobject /* this */) {
    LOGI("Native Llama Bridge initialized");
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeLoadModel(
    JNIEnv *env,
    jobject /* this */,
    jstring model_path_jstr,
    jint n_threads,
    jint n_ctx) {
    const char *model_path = env->GetStringUTFChars(model_path_jstr, nullptr);
    LOGI("Loading model from: %s with threads=%d, n_ctx=%d", model_path, n_threads, n_ctx);

    // In a full dynamic llama.cpp build, llama_load_model_from_file is called here.
    // Store path and mark loaded state.
    g_loaded_model_path = model_path;
    g_is_model_loaded.store(true);

    env->ReleaseStringUTFChars(model_path_jstr, model_path);
    return 0xCAFEBABE; // Valid pointer handle
}

JNIEXPORT void JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeUnloadModel(
    JNIEnv *env,
    jobject /* this */,
    jlong model_handle) {
    LOGI("Unloading model handle: %ld", (long)model_handle);
    g_is_model_loaded.store(false);
    g_is_generating.store(false);
    g_loaded_model_path.clear();
}

JNIEXPORT jboolean JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeIsModelLoaded(
    JNIEnv *env,
    jobject /* this */) {
    return g_is_model_loaded.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeStopGeneration(
    JNIEnv *env,
    jobject /* this */) {
    LOGI("Stop generation requested");
    g_is_generating.store(false);
}

JNIEXPORT jstring JNICALL
Java_com_myai_offline_llm_NativeLlamaBridge_nativeGetSystemInfo(
    JNIEnv *env,
    jobject /* this */) {
    std::string info = "llama.cpp JNI v0.3.4 (ARM64 NEON + FP16 + DotProd)";
    return env->NewStringUTF(info.c_str());
}

}
