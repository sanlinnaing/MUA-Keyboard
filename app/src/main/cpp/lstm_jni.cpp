#include <jni.h>
#include <android/log.h>
#include <vector>

#include "lstm_engine.h"

#define LOG_TAG "LstmNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_sanlin_mkeyboard_suggestion_LstmNative_create(JNIEnv*, jobject) {
    lstm_engine* engine = lstm_engine_create();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_sanlin_mkeyboard_suggestion_LstmNative_destroy(JNIEnv*, jobject, jlong handle) {
    auto* engine = reinterpret_cast<lstm_engine*>(handle);
    if (engine) {
        lstm_engine_destroy(engine);
    }
}

JNIEXPORT jint JNICALL
Java_com_sanlin_mkeyboard_suggestion_LstmNative_loadModel(
        JNIEnv* env, jobject, jlong handle, jbyteArray modelData) {
    auto* engine = reinterpret_cast<lstm_engine*>(handle);
    if (!engine || !modelData) return 0;

    jsize length = env->GetArrayLength(modelData);
    if (length == 0) return 0;

    jbyte* bytes = env->GetByteArrayElements(modelData, nullptr);
    if (!bytes) return 0;

    int result = lstm_engine_load_model(
            engine,
            reinterpret_cast<const uint8_t*>(bytes),
            static_cast<size_t>(length));

    env->ReleaseByteArrayElements(modelData, bytes, JNI_ABORT);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_sanlin_mkeyboard_suggestion_LstmNative_loadVocab(
        JNIEnv* env, jobject, jlong handle, jstring jsonStr) {
    auto* engine = reinterpret_cast<lstm_engine*>(handle);
    if (!engine || !jsonStr) return 0;

    const char* json = env->GetStringUTFChars(jsonStr, nullptr);
    if (!json) return 0;

    int result = lstm_engine_load_vocab(engine, json);

    env->ReleaseStringUTFChars(jsonStr, json);
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_sanlin_mkeyboard_suggestion_LstmNative_predict(
        JNIEnv* env, jobject, jlong handle, jintArray indices) {
    auto* engine = reinterpret_cast<lstm_engine*>(handle);
    if (!engine || !indices) return nullptr;

    int vocabSize = lstm_engine_get_vocab_size(engine);
    if (vocabSize == 0) return nullptr;

    jsize inputCount = env->GetArrayLength(indices);
    jint* inputIndices = env->GetIntArrayElements(indices, nullptr);
    if (!inputIndices) return nullptr;

    // Allocate output buffer
    std::vector<float> outputProbs(vocabSize);

    int result = lstm_engine_predict(
            engine,
            inputIndices,
            static_cast<int>(inputCount),
            outputProbs.data());

    env->ReleaseIntArrayElements(indices, inputIndices, JNI_ABORT);

    if (result == 0) return nullptr;

    // Create Java float array with results
    jfloatArray jOutput = env->NewFloatArray(vocabSize);
    if (!jOutput) return nullptr;

    env->SetFloatArrayRegion(jOutput, 0, vocabSize, outputProbs.data());
    return jOutput;
}

JNIEXPORT jint JNICALL
Java_com_sanlin_mkeyboard_suggestion_LstmNative_getVocabSize(
        JNIEnv*, jobject, jlong handle) {
    auto* engine = reinterpret_cast<lstm_engine*>(handle);
    if (!engine) return 0;
    return lstm_engine_get_vocab_size(engine);
}

JNIEXPORT jstring JNICALL
Java_com_sanlin_mkeyboard_suggestion_LstmNative_getSyllable(
        JNIEnv* env, jobject, jlong handle, jint index) {
    auto* engine = reinterpret_cast<lstm_engine*>(handle);
    if (!engine) return nullptr;

    const char* syllable = lstm_engine_get_syllable(engine, index);
    if (!syllable) return nullptr;

    return env->NewStringUTF(syllable);
}

JNIEXPORT jint JNICALL
Java_com_sanlin_mkeyboard_suggestion_LstmNative_getIndex(
        JNIEnv* env, jobject, jlong handle, jstring syllable) {
    auto* engine = reinterpret_cast<lstm_engine*>(handle);
    if (!engine || !syllable) return -1;

    const char* syll = env->GetStringUTFChars(syllable, nullptr);
    if (!syll) return -1;

    int result = lstm_engine_get_index(engine, syll);

    env->ReleaseStringUTFChars(syllable, syll);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_sanlin_mkeyboard_suggestion_LstmNative_getSequenceLength(
        JNIEnv*, jobject, jlong handle) {
    auto* engine = reinterpret_cast<lstm_engine*>(handle);
    if (!engine) return 5;
    return lstm_engine_get_sequence_length(engine);
}

}  // extern "C"
