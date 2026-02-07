#include <jni.h>
#include <vector>
#include <android/log.h>

#include "trie_c_api.h"

#define LOG_TAG "MyanmarTrie"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_sanlin_mkeyboard_suggestion_TrieNative_create(JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(myanmar_trie_create());
}

extern "C" JNIEXPORT void JNICALL
Java_com_sanlin_mkeyboard_suggestion_TrieNative_destroy(JNIEnv *, jobject, jlong handle) {
    auto *ptr = reinterpret_cast<myanmar_trie_handle *>(handle);
    myanmar_trie_destroy(ptr);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sanlin_mkeyboard_suggestion_TrieNative_load(JNIEnv *env, jobject, jlong handle, jstring path) {
    auto *ptr = reinterpret_cast<myanmar_trie_handle *>(handle);
    if (!ptr || !path) return 0;
    const char *native_path = env->GetStringUTFChars(path, nullptr);
    int ok = myanmar_trie_load(ptr, native_path);
    env->ReleaseStringUTFChars(path, native_path);
    return ok;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sanlin_mkeyboard_suggestion_TrieNative_loadFromMemory(JNIEnv *env, jobject, jlong handle, jbyteArray data) {
    auto *ptr = reinterpret_cast<myanmar_trie_handle *>(handle);
    if (!ptr || !data) return 0;

    jsize length = env->GetArrayLength(data);
    if (length == 0) return 0;

    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) return 0;

    int ok = myanmar_trie_load_from_memory(ptr, reinterpret_cast<const uint8_t *>(bytes), static_cast<size_t>(length));
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return ok;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_sanlin_mkeyboard_suggestion_TrieNative_suggestPartial(
    JNIEnv *env,
    jobject,
    jlong handle,
    jobjectArray syllables,
    jint topK) {

    auto *ptr = reinterpret_cast<myanmar_trie_handle *>(handle);
    if (!ptr || !syllables) return nullptr;

    jsize count = env->GetArrayLength(syllables);
    std::vector<const char *> native_syllables;
    std::vector<jstring> jstrings;
    native_syllables.reserve(count);
    jstrings.reserve(count);

    for (jsize i = 0; i < count; ++i) {
        auto js = static_cast<jstring>(env->GetObjectArrayElement(syllables, i));
        if (!js) continue;
        jstrings.push_back(js);
        native_syllables.push_back(env->GetStringUTFChars(js, nullptr));
    }

    myanmar_trie_suggestion *results = nullptr;
    size_t size = myanmar_trie_suggest_partial(
        ptr,
        native_syllables.data(),
        native_syllables.size(),
        static_cast<size_t>(topK),
        &results);

    for (size_t i = 0; i < jstrings.size(); ++i) {
        env->ReleaseStringUTFChars(jstrings[i], native_syllables[i]);
        env->DeleteLocalRef(jstrings[i]);
    }

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray words = env->NewObjectArray(static_cast<jsize>(size), stringClass, nullptr);
    jintArray freqs = env->NewIntArray(static_cast<jsize>(size));
    std::vector<jint> freq_buf(size);

    for (jsize i = 0; i < static_cast<jsize>(size); ++i) {
        jstring word = env->NewStringUTF(results[i].word);
        env->SetObjectArrayElement(words, i, word);
        env->DeleteLocalRef(word);
        freq_buf[i] = results[i].frequency;
    }
    env->SetIntArrayRegion(freqs, 0, static_cast<jsize>(size), freq_buf.data());
    myanmar_trie_free_suggestions(results, size);

    jclass objectClass = env->FindClass("java/lang/Object");
    jobjectArray out = env->NewObjectArray(2, objectClass, nullptr);
    env->SetObjectArrayElement(out, 0, words);
    env->SetObjectArrayElement(out, 1, freqs);
    return out;
}
