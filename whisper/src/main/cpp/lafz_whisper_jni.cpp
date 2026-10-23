// SPDX-License-Identifier: GPL-3.0-only
// Minimal JNI wrapper around whisper.cpp: load a GGML model from a file, transcribe a mono 16 kHz float buffer, free.
#include <jni.h>
#include <android/log.h>
#include <string>
#include "whisper.h"
#include "ggml-backend.h"

#define TAG "LafzWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT void JNICALL
Java_app_lafz_whisper_WhisperLib_loadBackends(JNIEnv *env, jobject, jstring dir) {
    const char *path = env->GetStringUTFChars(dir, nullptr);
    ggml_backend_load_all_from_path(path);
    env->ReleaseStringUTFChars(dir, path);
}

JNIEXPORT jlong JNICALL
Java_app_lafz_whisper_WhisperLib_initContext(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_app_lafz_whisper_WhisperLib_freeContext(JNIEnv *, jobject, jlong ptr) {
    whisper_free((whisper_context *) ptr);
}

/** beamSize 1 = greedy. Returns the text of all segments, or null if whisper failed. */
JNIEXPORT jstring JNICALL
Java_app_lafz_whisper_WhisperLib_runWhisper(JNIEnv *env, jobject, jlong ptr, jint threads, jint beamSize, jstring language, jfloatArray audio) {
    auto *ctx = (whisper_context *) ptr;
    jfloat *samples = env->GetFloatArrayElements(audio, nullptr);
    const jsize count = env->GetArrayLength(audio);
    const char *lang = env->GetStringUTFChars(language, nullptr);

    whisper_full_params params = whisper_full_default_params(beamSize > 1 ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    params.beam_search.beam_size = beamSize;
    params.greedy.best_of = 1;
    params.language = lang;
    params.n_threads = threads;
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = true;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;

    int rc = whisper_full(ctx, params, samples, count);
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(language, lang);
    if (rc != 0) { LOGI("whisper_full failed: %d", rc); return nullptr; }

    std::string text;
    const int n = whisper_full_n_segments(ctx);
    for (int i = 0; i < n; i++) text += whisper_full_get_segment_text(ctx, i);
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT jstring JNICALL
Java_app_lafz_whisper_WhisperLib_systemInfoNative(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}

} // extern "C"
