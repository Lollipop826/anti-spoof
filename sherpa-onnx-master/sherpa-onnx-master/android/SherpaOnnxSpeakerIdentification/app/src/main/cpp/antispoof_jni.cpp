#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cmath>
#include <string>
#include <vector>
#include <algorithm>

#include "onnxruntime_c_api.h"

#define LOG_TAG "AntiSpoofJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

static const OrtApi* g_api = nullptr;
static OrtEnv* g_env = nullptr;
static OrtSession* g_sess = nullptr;
static OrtMemoryInfo* g_mem = nullptr;
static OrtAllocator* g_def_alloc = nullptr;
static std::string g_model_path;

static void Normalize(std::vector<float>& pcm) {
    if (pcm.empty()) return;
    double mean = 0.0; for (float x : pcm) mean += x; mean /= (double)pcm.size();
    double var = 0.0; for (float& x : pcm) { x = (float)(x - mean); var += (double)x * x; }
    double std = std::sqrt(var / (double)pcm.size() + 1e-6); if (std < 1e-12) std = 1.0;
    for (float& x : pcm) x = (float)(x / std);
}

static bool LoadOrtApi() {
    void* h = dlopen("libonnxruntime.so", RTLD_NOW);
    if (!h) { LOGE("dlopen libonnxruntime.so failed: %s", dlerror()); return false; }
    auto get_api = (const OrtApiBase*(*)())dlsym(h, "OrtGetApiBase");
    if (!get_api) { LOGE("dlsym OrtGetApiBase failed"); return false; }
    const OrtApiBase* base = get_api();
    const char* ver = base->GetVersionString ? base->GetVersionString() : "unknown";
    LOGI("ONNXRuntime version: %s", ver ? ver : "null");

    g_api = base->GetApi(ORT_API_VERSION);
    if (!g_api) {
        // Fallback: try older API versions to match bundled libonnxruntime
        for (int v = ORT_API_VERSION; v >= 1 && !g_api; --v) {
            g_api = base->GetApi(v);
            if (g_api) { LOGI("GetApi fallback succeeded with version %d", v); break; }
        }
    }
    if (!g_api) { LOGE("GetApi failed"); return false; }
    return true;
}

static bool CreateSession(const char* model_path) {
    OrtStatus* s = nullptr;
    s = g_api->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "antispoof", &g_env); if (s) { LOGE("CreateEnv err"); g_api->ReleaseStatus(s); return false; }
    OrtSessionOptions* opt = nullptr; s = g_api->CreateSessionOptions(&opt); if (s) { LOGE("CreateSessionOptions err"); g_api->ReleaseStatus(s); return false; }
    s = g_api->SetIntraOpNumThreads(opt, 2); if (s) { LOGE("SetIntraOpNumThreads err"); g_api->ReleaseStatus(s); }
    s = g_api->SetSessionGraphOptimizationLevel(opt, ORT_ENABLE_BASIC); if (s) { LOGE("SetSessionGraphOptimizationLevel err"); g_api->ReleaseStatus(s); }
    s = g_api->CreateSession(g_env, model_path, opt, &g_sess); g_api->ReleaseSessionOptions(opt);
    if (s) { const char* em = g_api->GetErrorMessage(s); LOGE("CreateSession failed: %s (path=%s)", em?em:"<null>", model_path); g_api->ReleaseStatus(s); return false; }
    s = g_api->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &g_mem); if (s) { LOGE("CreateCpuMemoryInfo err"); g_api->ReleaseStatus(s); return false; }
    s = g_api->GetAllocatorWithDefaultOptions(&g_def_alloc); if (s) { LOGE("GetAllocatorWithDefaultOptions err"); g_api->ReleaseStatus(s); return false; }
    // Log input/output shapes for debug
    size_t in_cnt = 0, out_cnt = 0; s = g_api->SessionGetInputCount(g_sess, &in_cnt); if (s) { g_api->ReleaseStatus(s); }
    s = g_api->SessionGetOutputCount(g_sess, &out_cnt); if (s) { g_api->ReleaseStatus(s); }
    LOGI("session ready. inputs=%zu outputs=%zu", in_cnt, out_cnt);
    if (in_cnt > 0) {
        char* in_name = nullptr; s = g_api->SessionGetInputName(g_sess, 0, g_def_alloc, &in_name);
        if (!s && in_name) { LOGI("input[0] name=%s", in_name); g_api->AllocatorFree(g_def_alloc, in_name); }
        else if (s) { g_api->ReleaseStatus(s); }
    }
    return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_k2fsa_sherpa_onnx_speaker_identification_AntiSpoofJNI_init(
        JNIEnv* env, jobject /*thiz*/, jstring jModelPath) {
    const char* cpath = env->GetStringUTFChars(jModelPath, nullptr);
    g_model_path = cpath ? cpath : "";
    env->ReleaseStringUTFChars(jModelPath, cpath);
    if (g_model_path.empty()) { LOGE("Model path empty"); return JNI_FALSE; }
    if (!LoadOrtApi()) return JNI_FALSE;
    if (!CreateSession(g_model_path.c_str())) return JNI_FALSE;
    LOGI("AntiSpoof init ok: %s", g_model_path.c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_k2fsa_sherpa_onnx_speaker_identification_AntiSpoofJNI_run(
        JNIEnv* env, jobject /*thiz*/, jfloatArray jPcm, jint sampleRate) {
    if (!g_api || !g_sess) { LOGE("Not initialized"); return -1.0f; }
    OrtStatus* s = nullptr;
    if (sampleRate != 16000) { LOGE("Expect 16000Hz, got %d", sampleRate); }
    const jsize n = env->GetArrayLength(jPcm);
    if (n <= 0) { LOGE("Empty PCM input"); return -1.0f; }
    if (n != 16000 * 4) { LOGE("Expect 64000 samples, got %d", (int)n); }
    std::vector<float> pcm(n);
    env->GetFloatArrayRegion(jPcm, 0, n, pcm.data());

    // Determine input shape
    size_t in_cnt = 0; s = g_api->SessionGetInputCount(g_sess, &in_cnt); if (s) { LOGE("SessionGetInputCount failed"); g_api->ReleaseStatus(s); return -1.0f; }
    if (in_cnt == 0) { LOGE("No input in model"); return -1.0f; }
    char* in_name = nullptr; s = g_api->SessionGetInputName(g_sess, 0, g_def_alloc, &in_name);
    if (s) { LOGE("GetInputName failed"); g_api->ReleaseStatus(s); return -1.0f; }
    OrtTypeInfo* tinfo = nullptr; s = g_api->SessionGetInputTypeInfo(g_sess, 0, &tinfo);
    if (s) { LOGE("GetInputTypeInfo failed"); g_api->AllocatorFree(g_def_alloc, in_name); g_api->ReleaseStatus(s); return -1.0f; }
    const OrtTensorTypeAndShapeInfo* tshape = nullptr; s = g_api->CastTypeInfoToTensorInfo(tinfo, &tshape);
    if (s) { LOGE("CastTypeInfoToTensorInfo failed"); g_api->AllocatorFree(g_def_alloc, in_name); g_api->ReleaseTypeInfo(tinfo); g_api->ReleaseStatus(s); return -1.0f; }
    ONNXTensorElementDataType etype; s = g_api->GetTensorElementType(tshape, &etype);
    if (s) { LOGE("GetTensorElementType failed"); g_api->AllocatorFree(g_def_alloc, in_name); g_api->ReleaseTypeInfo(tinfo); g_api->ReleaseStatus(s); return -1.0f; }
    size_t rank = 0; s = g_api->GetDimensionsCount(tshape, &rank);
    if (s) { LOGE("GetDimensionsCount failed"); g_api->AllocatorFree(g_def_alloc, in_name); g_api->ReleaseTypeInfo(tinfo); g_api->ReleaseStatus(s); return -1.0f; }

    // Derive input shape: prefer filling the last dim with N, other <=0 dims as 1
    std::vector<int64_t> model_dims(rank, 1);
    if (rank > 0) {
        std::vector<int64_t> tmp(rank, 1);
        s = g_api->GetDimensions(tshape, tmp.data(), rank);
        if (s) { LOGE("GetDimensions failed"); g_api->AllocatorFree(g_def_alloc, in_name); g_api->ReleaseTypeInfo(tinfo); g_api->ReleaseStatus(s); return -1.0f; }
        model_dims = tmp;
    }
    // Match expected last-dim length from model. If fixed (e.g., 64600), pad/trim PCM accordingly.
    size_t N = (rank > 0 && model_dims[rank - 1] > 0) ? static_cast<size_t>(model_dims[rank - 1]) : static_cast<size_t>(n);
    if (pcm.size() < N) {
        pcm.resize(N, 0.0f); // pad zeros
    } else if (pcm.size() > N) {
        pcm.resize(N); // trim tail
    }

    // Now build dims (no normalization to match MyApplication7 behavior)
    std::vector<int64_t> dims(rank, 1);
    for (size_t i = 0; i < rank; ++i) {
        if (i == rank - 1) dims[i] = static_cast<int64_t>(N); // put samples in the last dim
        else dims[i] = (model_dims[i] > 0 ? model_dims[i] : 1);
    }

    // Do not Normalize(pcm); model expects raw scale as in MyApplication7

    if (etype != ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT) {
        LOGE("Input type not float. etype=%d", (int)etype);
        g_api->ReleaseTypeInfo(tinfo);
        g_api->AllocatorFree(g_def_alloc, in_name);
        return -1.0f;
    }
    g_api->ReleaseTypeInfo(tinfo);
    LOGI("AntiSpoofJNI: rank=%zu dims=... last=%lld N=%zu", rank, (long long)dims[rank-1], N);

    // Debug input stats
    {
        float mn = pcm.empty()?0.f:pcm[0], mx = mn; double ss = 0.0; size_t cnt = pcm.size();
        for (size_t i = 0; i < cnt; ++i) { float v = pcm[i]; if (v < mn) mn = v; if (v > mx) mx = v; ss += (double)v * v; }
        double rms = std::sqrt(ss / (double)(cnt>0?cnt:1));
        LOGI("Input stats: len=%zu min=%.5f max=%.5f rms=%.5f", cnt, mn, mx, rms);
    }

    OrtValue* in_val = nullptr;
    s = g_api->CreateTensorWithDataAsOrtValue(g_mem, pcm.data(), sizeof(float)*N, dims.data(), rank, ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &in_val);
    if (s) { LOGE("CreateTensor failed"); g_api->AllocatorFree(g_def_alloc, in_name); g_api->ReleaseStatus(s); return -1.0f; }

    // Prepare output name (first output)
    char* out_name = nullptr; s = g_api->SessionGetOutputName(g_sess, 0, g_def_alloc, &out_name);
    if (s) { LOGE("GetOutputName failed"); g_api->ReleaseValue(in_val); g_api->AllocatorFree(g_def_alloc, in_name); g_api->ReleaseStatus(s); return -1.0f; }

    const char* input_names[1] = { in_name };
    const OrtValue* input_vals[1] = { in_val };
    const char* output_names[1] = { out_name };
    OrtValue* out_val = nullptr;

    s = g_api->Run(g_sess, nullptr, input_names, input_vals, 1, output_names, 1, &out_val);
    // release names ASAP
    g_api->AllocatorFree(g_def_alloc, in_name);
    g_api->AllocatorFree(g_def_alloc, out_name);
    g_api->ReleaseValue(in_val);

    if (s) { const char* em = g_api->GetErrorMessage(s); LOGE("Run failed: %s", em?em:"<null>"); g_api->ReleaseStatus(s); return -1.0f; }

    // Read output tensor
    OrtTensorTypeAndShapeInfo* oinfo = nullptr; s = g_api->GetTensorTypeAndShape(out_val, &oinfo);
    if (s) { LOGE("GetTensorTypeAndShape failed"); g_api->ReleaseValue(out_val); g_api->ReleaseStatus(s); return -1.0f; }
    size_t elem_count = 0; s = g_api->GetTensorShapeElementCount(oinfo, &elem_count);
    if (s) { LOGE("GetTensorShapeElementCount failed"); g_api->ReleaseTensorTypeAndShapeInfo(oinfo); g_api->ReleaseValue(out_val); g_api->ReleaseStatus(s); return -1.0f; }
    g_api->ReleaseTensorTypeAndShapeInfo(oinfo);

    float* out_data = nullptr; s = g_api->GetTensorMutableData(out_val, (void**)&out_data);
    if (s) { LOGE("GetTensorMutableData failed"); g_api->ReleaseValue(out_val); g_api->ReleaseStatus(s); return -1.0f; }

    // Log first few outputs
    if (elem_count >= 2) {
        LOGI("Output[0]=%.5f Output[1]=%.5f (elem_count=%zu)", out_data[0], out_data[1], elem_count);
    } else if (elem_count == 1) {
        LOGI("Output[0]=%.5f (elem_count=1)", out_data[0]);
    }

    // Match MyApplication7: return raw logit/value (no sigmoid/softmax), take index 1 if 2-class
    float score = 0.0f;
    if (elem_count >= 2) {
        score = out_data[1];
    } else if (elem_count == 1) {
        score = out_data[0];
    } else {
        LOGE("Unexpected output elem_count=%zu", elem_count);
        score = 0.0f;
    }

    g_api->ReleaseValue(out_val);
    LOGI("AntiSpoof raw score=%.4f (no activation)", score);
    return score;
}

