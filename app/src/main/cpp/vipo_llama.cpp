// JNI bridge between Vipo and llama.cpp.
//
// One handle owns a model, its context, batch, sampler and chat templates. A completion
// re-ingests the whole conversation through the model's own chat template, which keeps the
// bridge small and correct at the cost of re-processing the prompt on every turn.

#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>
#include <unistd.h>

#include "common.h"
#include "sampling.h"
#include "chat.h"
#include "llama.h"

#define LOG_TAG "VipoLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int BATCH_SIZE = 256;
constexpr int CONTEXT_HEADROOM = 8;

struct vipo_session {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    llama_batch batch {};
    common_sampler * sampler = nullptr;
    common_chat_templates_ptr templates;

    int n_ctx = 0;
    llama_pos pos = 0;
    int n_prompt_tokens = 0;
    int n_decoded = 0;
    int n_predict = 512;
    bool stop_requested = false;
    bool finished = true;
    bool supports_thinking = false;
    std::string thinking_start;
    std::string thinking_end;
    std::string pending_utf8;
};

std::string jstring_to_string(JNIEnv * env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars == nullptr ? std::string() : std::string(chars);
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

// A multi-byte character can straddle two tokens; hold those bytes back until they are complete.
bool is_valid_utf8(const std::string & text) {
    const auto * bytes = reinterpret_cast<const unsigned char *>(text.c_str());
    while (*bytes != 0x00) {
        int extra;
        if ((*bytes & 0x80) == 0x00) {
            extra = 0;
        } else if ((*bytes & 0xE0) == 0xC0) {
            extra = 1;
        } else if ((*bytes & 0xF0) == 0xE0) {
            extra = 2;
        } else if ((*bytes & 0xF8) == 0xF0) {
            extra = 3;
        } else {
            return false;
        }
        bytes += 1;
        for (int i = 0; i < extra; i++) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

vipo_session * as_session(jlong handle) {
    return reinterpret_cast<vipo_session *>(handle);
}

int decode_tokens(vipo_session * session, const std::vector<llama_token> & tokens) {
    for (size_t i = 0; i < tokens.size(); i += BATCH_SIZE) {
        const int chunk = std::min<int>(BATCH_SIZE, static_cast<int>(tokens.size() - i));
        common_batch_clear(session->batch);
        for (int j = 0; j < chunk; j++) {
            const bool last = (i + j) == tokens.size() - 1;
            common_batch_add(session->batch, tokens[i + j], session->pos + static_cast<int>(i) + j, { 0 }, last);
        }
        if (llama_decode(session->ctx, session->batch) != 0) {
            LOGE("llama_decode failed while ingesting the prompt");
            return 1;
        }
    }
    session->pos += static_cast<int>(tokens.size());
    return 0;
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_engine_LlamaNative_nativeInit(JNIEnv *, jobject) {
    llama_log_set([](ggml_log_level level, const char * text, void *) {
        if (level == GGML_LOG_LEVEL_ERROR) {
            LOGE("%s", text);
        }
    }, nullptr);
    llama_backend_init();
    LOGI("llama backend initialised");
}

JNIEXPORT jlong JNICALL
Java_com_example_engine_LlamaNative_nativeLoadModel(
        JNIEnv * env, jobject, jstring jpath, jint n_ctx, jint n_threads, jint n_gpu_layers) {
    const std::string path = jstring_to_string(env, jpath);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = n_gpu_layers;

    llama_model * model = llama_model_load_from_file(path.c_str(), model_params);
    if (model == nullptr) {
        LOGE("could not load model from %s", path.c_str());
        return 0;
    }

    auto * session = new vipo_session();
    session->model = model;

    const int trained_ctx = llama_model_n_ctx_train(model);
    int wanted_ctx = n_ctx > 0 ? n_ctx : 2048;
    if (trained_ctx > 0 && wanted_ctx > trained_ctx) {
        wanted_ctx = trained_ctx;
    }

    const int hardware_threads = static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN));
    int threads = n_threads > 0 ? n_threads : std::max(2, hardware_threads / 2);
    threads = std::max(1, std::min(threads, std::max(1, hardware_threads)));

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = wanted_ctx;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;

    session->ctx = llama_init_from_model(model, ctx_params);
    if (session->ctx == nullptr) {
        LOGE("could not create a context for %s", path.c_str());
        llama_model_free(model);
        delete session;
        return 0;
    }

    session->n_ctx = static_cast<int>(llama_n_ctx(session->ctx));
    session->batch = llama_batch_init(BATCH_SIZE, 0, 1);
    session->templates = common_chat_templates_init(model, "");

    LOGI("model loaded: ctx=%d threads=%d", session->n_ctx, threads);
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_com_example_engine_LlamaNative_nativeFreeModel(JNIEnv *, jobject, jlong handle) {
    auto * session = as_session(handle);
    if (session == nullptr) {
        return;
    }
    if (session->sampler != nullptr) {
        common_sampler_free(session->sampler);
    }
    session->templates.reset();
    llama_batch_free(session->batch);
    if (session->ctx != nullptr) {
        llama_free(session->ctx);
    }
    if (session->model != nullptr) {
        llama_model_free(session->model);
    }
    delete session;
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_LlamaNative_nativeModelDescription(JNIEnv * env, jobject, jlong handle) {
    auto * session = as_session(handle);
    if (session == nullptr) {
        return env->NewStringUTF("");
    }
    char buf[256] = {0};
    llama_model_desc(session->model, buf, sizeof(buf));
    return env->NewStringUTF(buf);
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_LlamaNative_nativeModelMeta(JNIEnv * env, jobject, jlong handle, jstring jkey) {
    auto * session = as_session(handle);
    if (session == nullptr) {
        return env->NewStringUTF("");
    }
    const std::string key = jstring_to_string(env, jkey);
    char buf[512] = {0};
    const int32_t written = llama_model_meta_val_str(session->model, key.c_str(), buf, sizeof(buf));
    if (written < 0) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(buf);
}

JNIEXPORT jint JNICALL
Java_com_example_engine_LlamaNative_nativeContextSize(JNIEnv *, jobject, jlong handle) {
    auto * session = as_session(handle);
    return session == nullptr ? 0 : session->n_ctx;
}

JNIEXPORT jlong JNICALL
Java_com_example_engine_LlamaNative_nativeModelParameterCount(JNIEnv *, jobject, jlong handle) {
    auto * session = as_session(handle);
    return session == nullptr ? 0 : static_cast<jlong>(llama_model_n_params(session->model));
}

JNIEXPORT jboolean JNICALL
Java_com_example_engine_LlamaNative_nativeSupportsThinking(JNIEnv *, jobject, jlong handle) {
    auto * session = as_session(handle);
    return session != nullptr && session->supports_thinking ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_example_engine_LlamaNative_nativePromptTokens(JNIEnv *, jobject, jlong handle) {
    auto * session = as_session(handle);
    return session == nullptr ? 0 : session->n_prompt_tokens;
}

JNIEXPORT jint JNICALL
Java_com_example_engine_LlamaNative_nativeStartCompletion(
        JNIEnv * env, jobject, jlong handle,
        jobjectArray jroles, jobjectArray jcontents,
        jfloat temperature, jfloat top_p, jint top_k, jfloat min_p,
        jfloat repeat_penalty, jlong seed, jint n_predict) {
    auto * session = as_session(handle);
    if (session == nullptr || session->ctx == nullptr) {
        return 1;
    }

    // Start from a clean slate: the whole conversation is re-ingested below.
    llama_memory_clear(llama_get_memory(session->ctx), true);
    session->pos = 0;
    session->n_decoded = 0;
    session->n_prompt_tokens = 0;
    session->stop_requested = false;
    session->finished = false;
    session->pending_utf8.clear();
    session->n_predict = n_predict > 0 ? n_predict : 512;

    if (session->sampler != nullptr) {
        common_sampler_free(session->sampler);
        session->sampler = nullptr;
    }

    common_params_sampling sparams;
    sparams.temp = temperature;
    sparams.top_p = top_p;
    sparams.top_k = top_k;
    sparams.min_p = min_p;
    sparams.penalty_repeat = repeat_penalty;
    if (seed >= 0) {
        sparams.seed = static_cast<uint32_t>(seed);
    }
    session->sampler = common_sampler_init(session->model, sparams);
    if (session->sampler == nullptr) {
        LOGE("could not create the sampler");
        return 2;
    }

    const jsize count = env->GetArrayLength(jroles);
    common_chat_templates_inputs inputs;
    inputs.add_generation_prompt = true;
    inputs.use_jinja = true;
    for (jsize i = 0; i < count; i++) {
        auto jrole = reinterpret_cast<jstring>(env->GetObjectArrayElement(jroles, i));
        auto jcontent = reinterpret_cast<jstring>(env->GetObjectArrayElement(jcontents, i));
        common_chat_msg msg;
        msg.role = jstring_to_string(env, jrole);
        msg.content = jstring_to_string(env, jcontent);
        inputs.messages.push_back(msg);
        env->DeleteLocalRef(jrole);
        env->DeleteLocalRef(jcontent);
    }

    std::string prompt;
    try {
        const common_chat_params params = common_chat_templates_apply(session->templates.get(), inputs);
        prompt = params.prompt;
        session->supports_thinking = params.supports_thinking;
        session->thinking_start = params.thinking_start_tag;
        session->thinking_end = params.thinking_end_tags.empty() ? std::string() : params.thinking_end_tags.front();
    } catch (const std::exception & e) {
        LOGE("chat template failed (%s), falling back to a plain prompt", e.what());
        prompt.clear();
        for (const auto & msg : inputs.messages) {
            prompt += msg.role + ": " + msg.content + "\n";
        }
        prompt += "assistant:";
    }

    std::vector<llama_token> tokens = common_tokenize(session->ctx, prompt, true, true);
    const int max_prompt = session->n_ctx - CONTEXT_HEADROOM - 64;
    if (max_prompt > 0 && static_cast<int>(tokens.size()) > max_prompt) {
        // Keep the tail: the most recent turns matter most.
        tokens.erase(tokens.begin(), tokens.end() - max_prompt);
    }
    session->n_prompt_tokens = static_cast<int>(tokens.size());

    if (tokens.empty()) {
        LOGE("empty prompt after tokenisation");
        return 3;
    }

    if (decode_tokens(session, tokens) != 0) {
        return 4;
    }
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_LlamaNative_nativeNextToken(JNIEnv * env, jobject, jlong handle) {
    auto * session = as_session(handle);
    if (session == nullptr || session->sampler == nullptr || session->finished) {
        return nullptr;
    }

    if (session->stop_requested ||
        session->n_decoded >= session->n_predict ||
        session->pos >= session->n_ctx - CONTEXT_HEADROOM) {
        session->finished = true;
        return nullptr;
    }

    const llama_token token = common_sampler_sample(session->sampler, session->ctx, -1);
    common_sampler_accept(session->sampler, token, true);

    if (llama_vocab_is_eog(llama_model_get_vocab(session->model), token)) {
        session->finished = true;
        return nullptr;
    }

    common_batch_clear(session->batch);
    common_batch_add(session->batch, token, session->pos, { 0 }, true);
    if (llama_decode(session->ctx, session->batch) != 0) {
        LOGE("llama_decode failed while generating");
        session->finished = true;
        return nullptr;
    }
    session->pos++;
    session->n_decoded++;

    session->pending_utf8 += common_token_to_piece(session->ctx, token, false);
    if (!is_valid_utf8(session->pending_utf8)) {
        return env->NewStringUTF("");
    }

    jstring result = env->NewStringUTF(session->pending_utf8.c_str());
    session->pending_utf8.clear();
    return result;
}

JNIEXPORT void JNICALL
Java_com_example_engine_LlamaNative_nativeStopCompletion(JNIEnv *, jobject, jlong handle) {
    auto * session = as_session(handle);
    if (session != nullptr) {
        session->stop_requested = true;
    }
}

} // extern "C"
