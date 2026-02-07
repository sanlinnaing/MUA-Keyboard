#include "ngram_engine.h"
#include <algorithm>
#include <cstring>
#include <cctype>

namespace ngram {

NgramEngine::NgramEngine() = default;
NgramEngine::~NgramEngine() = default;

bool NgramEngine::loadVocabulary(const uint8_t* data, size_t size) {
    if (size < 12) return false;

    const uint8_t* ptr = data;

    // Read header
    uint32_t magic = *reinterpret_cast<const uint32_t*>(ptr); ptr += 4;
    uint32_t version = *reinterpret_cast<const uint32_t*>(ptr); ptr += 4;
    uint32_t count = *reinterpret_cast<const uint32_t*>(ptr); ptr += 4;

    if (magic != MAGIC || version != VERSION) {
        return false;
    }

    vocabulary_.clear();
    wordToIndex_.clear();
    vocabulary_.reserve(count);

    // Read words
    for (uint32_t i = 0; i < count && ptr < data + size; i++) {
        uint16_t wordLen = *reinterpret_cast<const uint16_t*>(ptr); ptr += 2;

        if (ptr + wordLen + 2 > data + size) break;

        std::string word(reinterpret_cast<const char*>(ptr), wordLen);
        ptr += wordLen;

        uint16_t freq = *reinterpret_cast<const uint16_t*>(ptr); ptr += 2;

        vocabulary_.push_back({word, freq});
        wordToIndex_[word] = static_cast<uint16_t>(i);
    }

    vocabularyLoaded_ = !vocabulary_.empty();
    return vocabularyLoaded_;
}

bool NgramEngine::loadBigrams(const uint8_t* data, size_t size) {
    if (size < 12) return false;

    const uint8_t* ptr = data;

    // Read header
    uint32_t magic = *reinterpret_cast<const uint32_t*>(ptr); ptr += 4;
    uint32_t version = *reinterpret_cast<const uint32_t*>(ptr); ptr += 4;
    uint32_t count = *reinterpret_cast<const uint32_t*>(ptr); ptr += 4;

    if (magic != MAGIC || version != VERSION) {
        return false;
    }

    bigrams_.clear();
    bigramsByFirst_.clear();
    bigrams_.reserve(count);

    // Read bigrams
    for (uint32_t i = 0; i < count && ptr + 6 <= data + size; i++) {
        uint16_t idx1 = *reinterpret_cast<const uint16_t*>(ptr); ptr += 2;
        uint16_t idx2 = *reinterpret_cast<const uint16_t*>(ptr); ptr += 2;
        uint16_t freq = *reinterpret_cast<const uint16_t*>(ptr); ptr += 2;

        bigrams_.push_back({idx1, idx2, freq});
        bigramsByFirst_[idx1].push_back({idx2, freq});
    }

    // Sort each bucket by frequency (descending)
    for (auto& pair : bigramsByFirst_) {
        std::sort(pair.second.begin(), pair.second.end(),
                  [](const auto& a, const auto& b) { return a.second > b.second; });
    }

    bigramsLoaded_ = !bigrams_.empty();
    return bigramsLoaded_;
}

std::string NgramEngine::extractLastWord(const std::string& text) {
    if (text.empty()) return "";

    // Find last space before the current word
    size_t lastSpace = text.rfind(' ');
    if (lastSpace == std::string::npos) {
        return "";  // No previous word
    }

    // Find the word before the last space
    size_t prevEnd = lastSpace;
    size_t prevStart = text.rfind(' ', prevEnd - 1);
    if (prevStart == std::string::npos) {
        prevStart = 0;
    } else {
        prevStart++;
    }

    std::string word = text.substr(prevStart, prevEnd - prevStart);

    // Convert to lowercase
    for (char& c : word) {
        c = std::tolower(c);
    }

    return word;
}

std::string NgramEngine::extractCurrentWord(const std::string& text) {
    if (text.empty()) return "";

    size_t lastSpace = text.rfind(' ');
    std::string word;

    if (lastSpace == std::string::npos) {
        word = text;
    } else {
        word = text.substr(lastSpace + 1);
    }

    // Convert to lowercase
    for (char& c : word) {
        c = std::tolower(c);
    }

    return word;
}

std::vector<Suggestion> NgramEngine::predict(const std::string& prevWord, int topK) {
    std::vector<Suggestion> results;

    if (!isReady() || prevWord.empty()) {
        return results;
    }

    // Find previous word in vocabulary
    auto it = wordToIndex_.find(prevWord);
    if (it == wordToIndex_.end()) {
        return results;
    }

    uint16_t prevIdx = it->second;

    // Look up bigrams starting with this word
    auto bigramIt = bigramsByFirst_.find(prevIdx);
    if (bigramIt == bigramsByFirst_.end()) {
        return results;
    }

    // Get top predictions
    const auto& candidates = bigramIt->second;
    int count = std::min(topK, static_cast<int>(candidates.size()));

    for (int i = 0; i < count; i++) {
        uint16_t nextIdx = candidates[i].first;
        uint16_t freq = candidates[i].second;

        if (nextIdx < vocabulary_.size()) {
            results.push_back({vocabulary_[nextIdx].word, freq});
        }
    }

    return results;
}

std::vector<Suggestion> NgramEngine::complete(const std::string& prefix, int topK) {
    std::vector<Suggestion> results;

    if (!vocabularyLoaded_ || prefix.empty()) {
        return results;
    }

    // Find words starting with prefix
    std::vector<std::pair<std::string, int>> matches;

    for (const auto& entry : vocabulary_) {
        if (entry.word.length() >= prefix.length() &&
            entry.word.compare(0, prefix.length(), prefix) == 0) {
            matches.push_back({entry.word, entry.frequency});
        }
    }

    // Sort by frequency
    std::sort(matches.begin(), matches.end(),
              [](const auto& a, const auto& b) { return a.second > b.second; });

    // Take top K
    int count = std::min(topK, static_cast<int>(matches.size()));
    for (int i = 0; i < count; i++) {
        results.push_back({matches[i].first, matches[i].second});
    }

    return results;
}

std::vector<Suggestion> NgramEngine::getSuggestions(const std::string& text, int topK) {
    std::vector<Suggestion> results;

    if (!isReady() || text.empty()) {
        return results;
    }

    std::string currentWord = extractCurrentWord(text);
    std::string prevWord = extractLastWord(text);

    std::unordered_map<std::string, int> seenWords;

    // If we have a previous word, get bigram predictions
    if (!prevWord.empty()) {
        auto predictions = predict(prevWord, topK * 2);

        for (const auto& pred : predictions) {
            // If current word is being typed, filter to matching predictions
            if (!currentWord.empty()) {
                if (pred.word.length() >= currentWord.length() &&
                    pred.word.compare(0, currentWord.length(), currentWord) == 0) {
                    if (seenWords.find(pred.word) == seenWords.end()) {
                        // Boost score for bigram matches
                        results.push_back({pred.word, pred.score + 1000});
                        seenWords[pred.word] = 1;
                    }
                }
            } else {
                // No current word, show all predictions
                if (seenWords.find(pred.word) == seenWords.end()) {
                    results.push_back({pred.word, pred.score + 1000});
                    seenWords[pred.word] = 1;
                }
            }
        }
    }

    // If we're typing a word, add completions
    if (!currentWord.empty() && currentWord.length() >= 2) {
        auto completions = complete(currentWord, topK * 2);

        for (const auto& comp : completions) {
            if (seenWords.find(comp.word) == seenWords.end()) {
                results.push_back({comp.word, comp.score});
                seenWords[comp.word] = 1;
            }
        }
    }

    // Sort by score
    std::sort(results.begin(), results.end(),
              [](const auto& a, const auto& b) { return a.score > b.score; });

    // Take top K
    if (results.size() > static_cast<size_t>(topK)) {
        results.resize(topK);
    }

    return results;
}

}  // namespace ngram
