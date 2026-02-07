#pragma once

#include <cstdint>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

namespace myanmar_trie {

struct Suggestion {
    std::string word;
    int32_t frequency;
};

class Trie {
public:
    bool load(const std::string &path);
    bool loadFromMemory(const uint8_t *data, size_t size);
    std::vector<Suggestion> suggest_partial(const std::vector<std::string> &syllables,
                                           size_t top_k = 5) const;

private:
    struct Node {
        std::vector<uint32_t> label;
        int32_t frequency = -1;
        std::vector<std::pair<uint32_t, uint32_t>> children; // token_id, node_index
        std::unordered_map<uint32_t, uint32_t> child_index; // token_id -> node_index
    };

    std::vector<std::string> strings_;
    std::vector<Node> nodes_;
    std::unordered_map<std::string, uint32_t> string_to_id_;

    bool parseBuffer(const std::vector<uint8_t> &buf);

    static std::string join_tokens(const std::vector<uint32_t> &tokens,
                                   const std::vector<std::string> &strings);
    static std::string first_codepoint(const std::string &text);
    static std::string rest_codepoints(const std::string &text);

    void collect(size_t node_idx,
                 std::vector<uint32_t> &path,
                 std::vector<Suggestion> &out,
                 size_t skip_label_prefix) const;

    static size_t common_prefix_len(const std::vector<uint32_t> &a,
                                    size_t a_offset,
                                    const std::vector<uint32_t> &b);

    std::vector<uint32_t> syllables_to_partial_tokens(
        const std::vector<std::string> &syllables) const;
};

} // namespace myanmar_trie
