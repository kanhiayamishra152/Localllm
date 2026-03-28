#include <jni.h>
#include <android/log.h>
#include <string>
#include <thread>
#include <atomic>
#include <vector>

#include "llama.h"
#include "common.h"

#define TAG "llama-android-jni"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::atomic<bool> stop_generation{false};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_localllm_llama_LlamaAndroid_nativeLoadModel(
    JNIEnv *env, jobject /*thiz*/,
    jstring model_path, jint context_size, jint n_gpu_layers, jint n_threads) {

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGi("Loading model from: %s", path);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = n_gpu_layers;

    llama_model *model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (model == nullptr) {
        LOGe("Failed to load model");
        return 0;
    }

    LOGi("Model loaded successfully");
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT jlong JNICALL
Java_com_localllm_llama_LlamaAndroid_nativeCreateContext(
    JNIEnv *env, jobject /*thiz*/,
    jlong model_ptr, jint context_size, jint n_threads) {

    auto *model = reinterpret_cast<llama_model *>(model_ptr);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = context_size;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    llama_context *ctx = llama_new_context_with_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGe("Failed to create context");
        return 0;
    }

    LOGi("Context created with size: %d, threads: %d", context_size, n_threads);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_localllm_llama_LlamaAndroid_nativeGenerate(
    JNIEnv *env, jobject /*thiz*/,
    jlong context_ptr, jstring prompt_str, jint max_tokens,
    jfloat temperature, jint top_k, jfloat top_p, jfloat repeat_penalty) {

    auto *ctx = reinterpret_cast<llama_context *>(context_ptr);
    const llama_model *model = llama_get_model(ctx);
    const char *prompt = env->GetStringUTFChars(prompt_str, nullptr);

    stop_generation.store(false);

    // Tokenize
    std::vector<llama_token> tokens(strlen(prompt) + 32);
    int n_tokens = llama_tokenize(model, prompt, strlen(prompt),
                                   tokens.data(), tokens.size(), true, false);
    tokens.resize(n_tokens);

    env->ReleaseStringUTFChars(prompt_str, prompt);

    // Clear KV cache
    llama_kv_cache_clear(ctx);

    // Evaluate prompt tokens
    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (int i = 0; i < (int)tokens.size(); i++) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(ctx, batch) != 0) {
        LOGe("Failed to decode prompt");
        llama_batch_free(batch);
        return env->NewStringUTF("");
    }
    llama_batch_free(batch);

    // Setup sampler
    auto *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));

    std::string result;
    int n_cur = tokens.size();

    for (int i = 0; i < max_tokens && !stop_generation.load(); i++) {
        llama_token new_token = llama_sampler_sample(smpl, ctx, -1);

        if (llama_token_is_eog(model, new_token)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(model, new_token, buf, sizeof(buf), 0, false);
        if (n > 0) {
            result.append(buf, n);
        }

        llama_batch single = llama_batch_init(1, 0, 1);
        llama_batch_add(single, new_token, n_cur, {0}, true);
        n_cur++;

        if (llama_decode(ctx, single) != 0) {
            llama_batch_free(single);
            break;
        }
        llama_batch_free(single);
    }

    llama_sampler_free(smpl);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_localllm_llama_LlamaAndroid_nativeGenerateStream(
    JNIEnv *env, jobject /*thiz*/,
    jlong context_ptr, jstring prompt_str, jint max_tokens,
    jfloat temperature, jint top_k, jfloat top_p, jfloat repeat_penalty,
    jobject callback) {

    auto *ctx = reinterpret_cast<llama_context *>(context_ptr);
    const llama_model *model = llama_get_model(ctx);
    const char *prompt = env->GetStringUTFChars(prompt_str, nullptr);

    stop_generation.store(false);

    // Get callback method
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID invokeMethod = env->GetMethodID(callbackClass, "invoke",
                                               "(Ljava/lang/Object;)Ljava/lang/Object;");

    // Tokenize
    std::vector<llama_token> tokens(strlen(prompt) + 32);
    int n_tokens = llama_tokenize(model, prompt, strlen(prompt),
                                   tokens.data(), tokens.size(), true, false);
    tokens.resize(n_tokens);
    env->ReleaseStringUTFChars(prompt_str, prompt);

    llama_kv_cache_clear(ctx);

    // Evaluate prompt
    llama_batch batch = llama_batch_init(tokens.size(), 0, 1);
    for (int i = 0; i < (int)tokens.size(); i++) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(ctx, batch) != 0) {
        LOGe("Failed to decode prompt");
        llama_batch_free(batch);
        return;
    }
    llama_batch_free(batch);

    // Sampler
    auto *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));

    int n_cur = tokens.size();

    for (int i = 0; i < max_tokens && !stop_generation.load(); i++) {
        llama_token new_token = llama_sampler_sample(smpl, ctx, -1);

        if (llama_token_is_eog(model, new_token)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(model, new_token, buf, sizeof(buf), 0, false);
        if (n > 0) {
            std::string piece(buf, n);
            jstring jPiece = env->NewStringUTF(piece.c_str());
            env->CallObjectMethod(callback, invokeMethod, jPiece);
            env->DeleteLocalRef(jPiece);
        }

        llama_batch single = llama_batch_init(1, 0, 1);
        llama_batch_add(single, new_token, n_cur, {0}, true);
        n_cur++;

        if (llama_decode(ctx, single) != 0) {
            llama_batch_free(single);
            break;
        }
        llama_batch_free(single);
    }

    llama_sampler_free(smpl);
}

JNIEXPORT void JNICALL
Java_com_localllm_llama_LlamaAndroid_nativeStopGeneration(
    JNIEnv *, jobject, jlong) {
    stop_generation.store(true);
}

JNIEXPORT void JNICALL
Java_com_localllm_llama_LlamaAndroid_nativeFreeContext(
    JNIEnv *, jobject, jlong context_ptr) {
    auto *ctx = reinterpret_cast<llama_context *>(context_ptr);
    if (ctx) {
        llama_free(ctx);
        LOGi("Context freed");
    }
}

JNIEXPORT void JNICALL
Java_com_localllm_llama_LlamaAndroid_nativeFreeModel(
    JNIEnv *, jobject, jlong model_ptr) {
    auto *model = reinterpret_cast<llama_model *>(model_ptr);
    if (model) {
        llama_free_model(model);
        LOGi("Model freed");
    }
}

JNIEXPORT jlong JNICALL
Java_com_localllm_llama_LlamaAndroid_nativeGetModelParamCount(
    JNIEnv *, jobject, jlong model_ptr) {
    auto *model = reinterpret_cast<llama_model *>(model_ptr);
    return model ? llama_model_n_params(model) : 0;
}

JNIEXPORT jint JNICALL
Java_com_localllm_llama_LlamaAndroid_nativeGetModelContextLength(
    JNIEnv *, jobject, jlong model_ptr) {
    // Return training context length from metadata if available
    return 4096; // default
}

JNIEXPORT jint JNICALL
Java_com_localllm_llama_LlamaAndroid_nativeGetModelEmbeddingLength(
    JNIEnv *, jobject, jlong model_ptr) {
    return 0;
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    llama_backend_init();
    LOGi("llama.cpp backend initialized");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    llama_backend_free();
}

} // extern "C"
