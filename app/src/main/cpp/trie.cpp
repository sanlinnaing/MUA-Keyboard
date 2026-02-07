#include "trie.hpp"

#include <algorithm>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <vector>

namespace myanmar_trie {

namespace {
constexpr uint32_t kMagic = 0x3154504D; // "MPT1"

uint32_t crc32(const uint8_t *data, size_t len) {
    uint32_t crc = 0xFFFFFFFF;
    for (size_t i = 0; i < len; ++i) {
        crc ^= data[i];
        for (int j = 0; j < 8; ++j) {
            uint32_t mask = -(crc & 1u);
            crc = (crc >> 1) ^ (0xEDB88320u & mask);
        }
    }
    return ~crc;
}

bool read_u32(const std::vector<uint8_t> &buf, size_t &offset, uint32_t &out) {
    if (offset + 4 > buf.size()) return false;
    out = static_cast<uint32_t>(buf[offset]) |
          (static_cast<uint32_t>(buf[offset + 1]) << 8) |
          (static_cast<uint32_t>(buf[offset + 2]) << 16) |
          (static_cast<uint32_t>(buf[offset + 3]) << 24);
    offset += 4;
    return true;
}

bool read_i32(const std::vector<uint8_t> &buf, size_t &offset, int32_t &out) {
    uint32_t tmp = 0;
    if (!read_u32(buf, offset, tmp)) return false;
    out = static_cast<int32_t>(tmp);
    return true;
}
} // namespace

bool Trie::load(const std::string &path) {
    std::ifstream in(path, std::ios::binary);
    if (!in) return false;
    std::vector<uint8_t> buf((std::istreambuf_iterator<char>(in)),
                             std::istreambuf_iterator<char>());
    return parseBuffer(buf);
}

bool Trie::loadFromMemory(const uint8_t *data, size_t size) {
    if (!data || size == 0) return false;
    std::vector<uint8_t> buf(data, data + size);
    return parseBuffer(buf);
}

bool Trie::parseBuffer(const std::vector<uint8_t> &buf) {
    if (buf.size() < 16 + 4) return false;

    uint32_t stored_crc = 0;
    size_t payload_size = buf.size() - 4;
    stored_crc = static_cast<uint32_t>(buf[payload_size]) |
                 (static_cast<uint32_t>(buf[payload_size + 1]) << 8) |
                 (static_cast<uint32_t>(buf[payload_size + 2]) << 16) |
                 (static_cast<uint32_t>(buf[payload_size + 3]) << 24);

    uint32_t computed_crc = crc32(buf.data(), payload_size);
    if (computed_crc != stored_crc) return false;

    size_t offset = 0;
    uint32_t magic = 0;
    if (!read_u32(buf, offset, magic) || magic != kMagic) return false;
    uint32_t version = 0;
    uint32_t string_count = 0;
    uint32_t node_count = 0;
    if (!read_u32(buf, offset, version) || version != 2) return false;
    if (!read_u32(buf, offset, string_count)) return false;
    if (!read_u32(buf, offset, node_count)) return false;

    strings_.clear();
    strings_.reserve(string_count);
    for (uint32_t i = 0; i < string_count; ++i) {
        uint32_t len = 0;
        if (!read_u32(buf, offset, len)) return false;
        if (offset + len > payload_size) return false;
        std::string s(reinterpret_cast<const char *>(&buf[offset]), len);
        offset += len;
        strings_.push_back(std::move(s));
    }
    string_to_id_.clear();
    string_to_id_.reserve(strings_.size());
    for (uint32_t i = 0; i < strings_.size(); ++i) {
        string_to_id_[strings_[i]] = i;
    }

    nodes_.clear();
    nodes_.resize(node_count);
    for (uint32_t i = 0; i < node_count; ++i) {
        uint32_t label_len = 0;
        if (!read_u32(buf, offset, label_len)) return false;
        nodes_[i].label.resize(label_len);
        for (uint32_t j = 0; j < label_len; ++j) {
            uint32_t token_id = 0;
            if (!read_u32(buf, offset, token_id)) return false;
            nodes_[i].label[j] = token_id;
        }
        int32_t freq = -1;
        if (!read_i32(buf, offset, freq)) return false;
        nodes_[i].frequency = freq;

        uint32_t child_count = 0;
        if (!read_u32(buf, offset, child_count)) return false;
        nodes_[i].children.resize(child_count);
        for (uint32_t j = 0; j < child_count; ++j) {
            uint32_t token_id = 0;
            uint32_t child_idx = 0;
            if (!read_u32(buf, offset, token_id)) return false;
            if (!read_u32(buf, offset, child_idx)) return false;
            nodes_[i].children[j] = {token_id, child_idx};
        }
    }

    for (auto &node : nodes_) {
        node.child_index.clear();
        for (const auto &entry : node.children) {
            node.child_index[entry.first] = entry.second;
        }
    }

    return true;
}

std::string Trie::join_tokens(const std::vector<uint32_t> &tokens,
                              const std::vector<std::string> &strings) {
    std::string out;
    for (auto token : tokens) {
        if (token < strings.size()) {
            out += strings[token];
        }
    }
    return out;
}

std::string Trie::first_codepoint(const std::string &text) {
    if (text.empty()) return std::string();
    unsigned char c = static_cast<unsigned char>(text[0]);
    size_t len = 1;
    if ((c & 0x80) == 0x00) len = 1;
    else if ((c & 0xE0) == 0xC0) len = 2;
    else if ((c & 0xF0) == 0xE0) len = 3;
    else if ((c & 0xF8) == 0xF0) len = 4;
    if (len > text.size()) len = text.size();
    return text.substr(0, len);
}

std::string Trie::rest_codepoints(const std::string &text) {
    if (text.empty()) return std::string();
    unsigned char c = static_cast<unsigned char>(text[0]);
    size_t len = 1;
    if ((c & 0x80) == 0x00) len = 1;
    else if ((c & 0xE0) == 0xC0) len = 2;
    else if ((c & 0xF0) == 0xE0) len = 3;
    else if ((c & 0xF8) == 0xF0) len = 4;
    if (len > text.size()) len = text.size();
    return text.substr(len);
}

std::vector<uint32_t> Trie::syllables_to_partial_tokens(
    const std::vector<std::string> &syllables) const {
    std::vector<uint32_t> tokens;
    tokens.reserve(syllables.size() * 2);
    for (const auto &syll : syllables) {
        std::string cons = first_codepoint(syll);
        std::string tail = rest_codepoints(syll);
        if (!cons.empty()) {
            auto it = string_to_id_.find(cons);
            if (it != string_to_id_.end()) {
                tokens.push_back(it->second);
            } else {
                continue;
            }
            if (!tail.empty()) {
                auto it_tail = string_to_id_.find(tail);
                if (it_tail != string_to_id_.end()) {
                    tokens.push_back(it_tail->second);
                }
            }
        }
    }
    return tokens;
}

size_t Trie::common_prefix_len(const std::vector<uint32_t> &a,
                               size_t a_offset,
                               const std::vector<uint32_t> &b) {
    size_t i = 0;
    while (a_offset + i < a.size() && i < b.size() && a[a_offset + i] == b[i]) {
        ++i;
    }
    return i;
}

void Trie::collect(size_t node_idx,
                   std::vector<uint32_t> &path,
                   std::vector<Suggestion> &out,
                   size_t skip_label_prefix) const {
    const auto &node = nodes_[node_idx];
    std::vector<uint32_t> next_path = path;
    for (size_t i = skip_label_prefix; i < node.label.size(); ++i) {
        next_path.push_back(node.label[i]);
    }
    if (node.frequency >= 0) {
        out.push_back({join_tokens(next_path, strings_), node.frequency});
    }
    for (const auto &child : node.children) {
        collect(child.second, next_path, out, 0);
    }
}

std::vector<Suggestion> Trie::suggest_partial(const std::vector<std::string> &syllables,
                                              size_t top_k) const {
    if (nodes_.empty() || strings_.empty()) return {};
    std::vector<uint32_t> tokens = syllables_to_partial_tokens(syllables);
    if (tokens.empty()) return {};

    size_t node_idx = 0;
    std::vector<uint32_t> path;
    size_t offset = 0;

    while (offset < tokens.size()) {
        const auto &node = nodes_[node_idx];
        auto it = node.child_index.find(tokens[offset]);
        if (it == node.child_index.end()) {
            return {};
        }
        size_t child_idx = it->second;
        const auto &child = nodes_[child_idx];
        size_t common = common_prefix_len(tokens, offset, child.label);
        if (common == 0) return {};

        if (common < child.label.size() && offset + common == tokens.size()) {
            for (size_t i = 0; i < common; ++i) {
                path.push_back(child.label[i]);
            }
            std::vector<Suggestion> matches;
            collect(child_idx, path, matches, common);
            std::sort(matches.begin(), matches.end(),
                      [](const Suggestion &a, const Suggestion &b) {
                          return a.frequency > b.frequency;
                      });
            if (matches.size() > top_k) matches.resize(top_k);
            return matches;
        }

        if (common < child.label.size()) {
            return {};
        }

        for (size_t i = 0; i < child.label.size(); ++i) {
            path.push_back(child.label[i]);
        }
        offset += common;
        node_idx = child_idx;
    }

    std::vector<Suggestion> matches;
    collect(node_idx, path, matches, nodes_[node_idx].label.size());
    std::sort(matches.begin(), matches.end(),
              [](const Suggestion &a, const Suggestion &b) {
                  return a.frequency > b.frequency;
              });
    if (matches.size() > top_k) matches.resize(top_k);
    return matches;
}

} // namespace myanmar_trie
