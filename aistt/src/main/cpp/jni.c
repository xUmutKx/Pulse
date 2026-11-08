// Pulse AI speech to text: a small JNI over whisper.cpp (MIT). One context per model file; segments with their times (10 ms units).
#include <jni.h>
#include <stdlib.h>
#include <stdbool.h>
#include <string.h>
#include <android/log.h>
#include "whisper.h"

#define TAG "PulseWhisper"

static volatile int g_abort = 0;
static volatile int g_progress = 0;

static bool abort_cb(void *user_data) { (void) user_data; return g_abort != 0; }
static void progress_cb(struct whisper_context *ctx, struct whisper_state *state, int progress, void *user_data) { (void) ctx; (void) state; (void) user_data; g_progress = progress; }

JNIEXPORT jlong JNICALL Java_com_umutk_pulse_ai_stt_Whisper_nInit(JNIEnv *env, jclass cls, jstring path) {
    (void) cls;
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    struct whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL Java_com_umutk_pulse_ai_stt_Whisper_nFree(JNIEnv *env, jclass cls, jlong ctx) {
    (void) env; (void) cls;
    if (ctx) whisper_free((struct whisper_context *) ctx);
}

JNIEXPORT jint JNICALL Java_com_umutk_pulse_ai_stt_Whisper_nFull(JNIEnv *env, jclass cls, jlong ctx, jint threads, jfloatArray audio, jstring lang, jstring prompt) {
    (void) cls;
    struct whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);
    p.beam_search.beam_size = 3;
    p.print_realtime = false; p.print_progress = false; p.print_timestamps = false; p.print_special = false;
    p.translate = false;
    p.n_threads = threads;
    p.no_context = false;
    p.single_segment = false;
    p.suppress_blank = true;
    p.max_len = 60;           // short segments: one sung line each, as far as the model can tell
    p.split_on_word = true;
    const char *l = lang ? (*env)->GetStringUTFChars(env, lang, NULL) : NULL;
    p.language = (l && l[0]) ? l : "auto";
    p.detect_language = false;
    const char *pr = prompt ? (*env)->GetStringUTFChars(env, prompt, NULL) : NULL;
    if (pr && pr[0]) p.initial_prompt = pr;
    p.abort_callback = abort_cb;
    p.progress_callback = progress_cb;
    g_abort = 0; g_progress = 0;
    jfloat *a = (*env)->GetFloatArrayElements(env, audio, NULL);
    jsize n = (*env)->GetArrayLength(env, audio);
    int r = whisper_full((struct whisper_context *) ctx, p, a, n);
    (*env)->ReleaseFloatArrayElements(env, audio, a, JNI_ABORT);
    if (l) (*env)->ReleaseStringUTFChars(env, lang, l);
    if (pr) (*env)->ReleaseStringUTFChars(env, prompt, pr);
    return r;
}

JNIEXPORT void JNICALL Java_com_umutk_pulse_ai_stt_Whisper_nAbort(JNIEnv *env, jclass cls) { (void) env; (void) cls; g_abort = 1; }
JNIEXPORT jint JNICALL Java_com_umutk_pulse_ai_stt_Whisper_nProgress(JNIEnv *env, jclass cls) { (void) env; (void) cls; return g_progress; }
JNIEXPORT jint JNICALL Java_com_umutk_pulse_ai_stt_Whisper_nSegments(JNIEnv *env, jclass cls, jlong ctx) { (void) env; (void) cls; return whisper_full_n_segments((struct whisper_context *) ctx); }
// the text as UTF-8 bytes (NewStringUTF would choke on a cut or 4-byte character); Kotlin decodes it
JNIEXPORT jbyteArray JNICALL Java_com_umutk_pulse_ai_stt_Whisper_nText(JNIEnv *env, jclass cls, jlong ctx, jint i) {
    (void) cls;
    const char *t = whisper_full_get_segment_text((struct whisper_context *) ctx, i);
    jsize n = t ? (jsize) strlen(t) : 0;
    jbyteArray out = (*env)->NewByteArray(env, n);
    if (n > 0) (*env)->SetByteArrayRegion(env, out, 0, n, (const jbyte *) t);
    return out;
}
JNIEXPORT jlong JNICALL Java_com_umutk_pulse_ai_stt_Whisper_nT0(JNIEnv *env, jclass cls, jlong ctx, jint i) { (void) env; (void) cls; return whisper_full_get_segment_t0((struct whisper_context *) ctx, i); }
JNIEXPORT jlong JNICALL Java_com_umutk_pulse_ai_stt_Whisper_nT1(JNIEnv *env, jclass cls, jlong ctx, jint i) { (void) env; (void) cls; return whisper_full_get_segment_t1((struct whisper_context *) ctx, i); }
