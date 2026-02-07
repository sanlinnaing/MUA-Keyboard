#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct myanmar_trie_handle myanmar_trie_handle;

typedef struct myanmar_trie_suggestion {
    const char *word;
    int32_t frequency;
} myanmar_trie_suggestion;

myanmar_trie_handle *myanmar_trie_create(void);
void myanmar_trie_destroy(myanmar_trie_handle *handle);

int myanmar_trie_load(myanmar_trie_handle *handle, const char *path);
int myanmar_trie_load_from_memory(myanmar_trie_handle *handle, const uint8_t *data, size_t size);

// syllables: array of UTF-8 syllable strings
// suggestions_out is allocated by the library; call myanmar_trie_free_suggestions.
size_t myanmar_trie_suggest_partial(
    myanmar_trie_handle *handle,
    const char **syllables,
    size_t syllable_count,
    size_t top_k,
    myanmar_trie_suggestion **suggestions_out);

void myanmar_trie_free_suggestions(myanmar_trie_suggestion *suggestions, size_t count);

#ifdef __cplusplus
}
#endif
