#include <jni.h>
#include <android/log.h>
#include "ngram_engine.h"
#include <memory>

#define LOG_TAG "NgramNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::unique_ptr<ngram::NgramEngine> g_engine;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_sanlin_mkeyboard_suggestion_NgramNative_initialize(JNIEnv* env, jobject /* this */) {
    if (g_engine) {
        return JNI_TRUE;
    }

    g_engine = std::make_unique<ngram::NgramEngine>();
    LOGI("Ngram engine created");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_sanlin_mkeyboard_suggestion_NgramNative_loadVocabulary(
        JNIEnv* env, jobject /* this */, jbyteArray data) {
    if (!g_engine) {
        LOGE("Engine not initialized");
        return JNI_FALSE;
    }

    jsize len = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);

    bool result = g_engine->loadVocabulary(
            reinterpret_cast<const uint8_t*>(bytes), static_cast<size_t>(len));

    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    if (result) {
        LOGI("Vocabulary loaded: %zu words", g_engine->getVocabSize());
    } else {
        LOGE("Failed to load vocabulary");
    }

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_sanlin_mkeyboard_suggestion_NgramNative_loadBigrams(
        JNIEnv* env, jobject /* this */, jbyteArray data) {
    if (!g_engine) {
        LOGE("Engine not initialized");
        return JNI_FALSE;
    }

    jsize len = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);

    bool result = g_engine->loadBigrams(
            reinterpret_cast<const uint8_t*>(bytes), static_cast<size_t>(len));

    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    if (result) {
        LOGI("Bigrams loaded: %zu entries", g_engine->getBigramCount());
    } else {
        LOGE("Failed to load bigrams");
    }

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_sanlin_mkeyboard_suggestion_NgramNative_isReady(JNIEnv* env, jobject /* this */) {
    return (g_engine && g_engine->isReady()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_sanlin_mkeyboard_suggestion_NgramNative_getSuggestions(
        JNIEnv* env, jobject /* this */, jstring text, jint topK) {
    if (!g_engine || !g_engine->isReady()) {
        return nullptr;
    }

    const char* textChars = env->GetStringUTFChars(text, nullptr);
    std::string textStr(textChars);
    env->ReleaseStringUTFChars(text, textChars);

    auto suggestions = g_engine->getSuggestions(textStr, topK);

    // Create String array for results
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(
            static_cast<jsize>(suggestions.size() * 2), stringClass, nullptr);

    for (size_t i = 0; i < suggestions.size(); i++) {
        // Word
        jstring word = env->NewStringUTF(suggestions[i].word.c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i * 2), word);
        env->DeleteLocalRef(word);

        // Score as string
        std::string scoreStr = std::to_string(suggestions[i].score);
        jstring score = env->NewStringUTF(scoreStr.c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i * 2 + 1), score);
        env->DeleteLocalRef(score);
    }

    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_com_sanlin_mkeyboard_suggestion_NgramNative_predict(
        JNIEnv* env, jobject /* this */, jstring prevWord, jint topK) {
    if (!g_engine || !g_engine->isReady()) {
        return nullptr;
    }

    const char* wordChars = env->GetStringUTFChars(prevWord, nullptr);
    std::string wordStr(wordChars);
    env->ReleaseStringUTFChars(prevWord, wordChars);

    auto predictions = g_engine->predict(wordStr, topK);

    // Create String array for results
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(
            static_cast<jsize>(predictions.size() * 2), stringClass, nullptr);

    for (size_t i = 0; i < predictions.size(); i++) {
        // Word
        jstring word = env->NewStringUTF(predictions[i].word.c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i * 2), word);
        env->DeleteLocalRef(word);

        // Score as string
        std::string scoreStr = std::to_string(predictions[i].score);
        jstring score = env->NewStringUTF(scoreStr.c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i * 2 + 1), score);
        env->DeleteLocalRef(score);
    }

    return result;
}

JNIEXPORT void JNICALL
Java_com_sanlin_mkeyboard_suggestion_NgramNative_release(JNIEnv* env, jobject /* this */) {
    g_engine.reset();
    LOGI("Ngram engine released");
}

JNIEXPORT jint JNICALL
Java_com_sanlin_mkeyboard_suggestion_NgramNative_getVocabSize(JNIEnv* env, jobject /* this */) {
    if (!g_engine) return 0;
    return static_cast<jint>(g_engine->getVocabSize());
}

JNIEXPORT jint JNICALL
Java_com_sanlin_mkeyboard_suggestion_NgramNative_getBigramCount(JNIEnv* env, jobject /* this */) {
    if (!g_engine) return 0;
    return static_cast<jint>(g_engine->getBigramCount());
}

}  // extern "C"
