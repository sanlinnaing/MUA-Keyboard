#include "trie_c_api.h"
#include "trie.hpp"

#include <cstring>
#include <new>
#include <string>
#include <vector>

struct myanmar_trie_handle {
    myanmar_trie::Trie trie;
};

myanmar_trie_handle *myanmar_trie_create(void) {
    return new (std::nothrow) myanmar_trie_handle();
}

void myanmar_trie_destroy(myanmar_trie_handle *handle) {
    delete handle;
}

int myanmar_trie_load(myanmar_trie_handle *handle, const char *path) {
    if (!handle || !path) return 0;
    return handle->trie.load(path) ? 1 : 0;
}

int myanmar_trie_load_from_memory(myanmar_trie_handle *handle, const uint8_t *data, size_t size) {
    if (!handle || !data || size == 0) return 0;
    return handle->trie.loadFromMemory(data, size) ? 1 : 0;
}

size_t myanmar_trie_suggest_partial(
    myanmar_trie_handle *handle,
    const char **syllables,
    size_t syllable_count,
    size_t top_k,
    myanmar_trie_suggestion **suggestions_out) {

    if (!handle || !syllables || !suggestions_out) return 0;

    std::vector<std::string> input;
    input.reserve(syllable_count);
    for (size_t i = 0; i < syllable_count; ++i) {
        if (syllables[i]) {
            input.emplace_back(syllables[i]);
        }
    }

    auto results = handle->trie.suggest_partial(input, top_k);
    size_t count = results.size();
    if (count == 0) {
        *suggestions_out = nullptr;
        return 0;
    }

    auto *out = static_cast<myanmar_trie_suggestion *>(
        std::calloc(count, sizeof(myanmar_trie_suggestion)));
    if (!out) {
        *suggestions_out = nullptr;
        return 0;
    }

    for (size_t i = 0; i < count; ++i) {
        const auto &item = results[i];
        out[i].frequency = item.frequency;
        size_t len = item.word.size();
        char *word_copy = static_cast<char *>(std::malloc(len + 1));
        if (!word_copy) {
            for (size_t j = 0; j < i; ++j) {
                std::free(const_cast<char *>(out[j].word));
            }
            std::free(out);
            *suggestions_out = nullptr;
            return 0;
        }
        std::memcpy(word_copy, item.word.data(), len);
        word_copy[len] = '\0';
        out[i].word = word_copy;
    }

    *suggestions_out = out;
    return count;
}

void myanmar_trie_free_suggestions(myanmar_trie_suggestion *suggestions, size_t count) {
    if (!suggestions) return;
    for (size_t i = 0; i < count; ++i) {
        std::free(const_cast<char *>(suggestions[i].word));
    }
    std::free(suggestions);
}
